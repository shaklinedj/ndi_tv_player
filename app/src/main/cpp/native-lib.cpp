#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "Processing.NDI.Lib.h"

#define LOG_TAG "NDI_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// --------------------------------------------------------------------------
// Global state — only one finder/receiver allowed at any time.
// --------------------------------------------------------------------------
std::atomic<bool> isFinderRunning(false);
std::atomic<bool> isReceiverRunning(false);

// --------------------------------------------------------------------------
//  DISCOVERY
// --------------------------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_dreamscasino_nditv_MainActivity_startNdiDiscovery(JNIEnv* env, jobject thiz) {
    if (!NDIlib_initialize()) {
        LOGE("Cannot run NDI.");
        return;
    }

    // Stop any lingering discovery before starting a new one
    isFinderRunning = false;
    isFinderRunning = true;

    // Global ref so we can call back from the thread
    jobject mainActivityObj = env->NewGlobalRef(thiz);
    JavaVM* jvm;
    env->GetJavaVM(&jvm);

    std::thread([jvm, mainActivityObj]() {
        NDIlib_find_create_t find_desc;
        find_desc.show_local_sources = true;
        NDIlib_find_instance_t pNDI_find = NDIlib_find_create_v2(&find_desc);

        if (!pNDI_find) {
            LOGE("Failed to create NDI find instance.");
            // Still have to release global ref to avoid a leak
            JNIEnv* pEnv;
            if (jvm->AttachCurrentThread(&pEnv, NULL) == JNI_OK) {
                pEnv->DeleteGlobalRef(mainActivityObj);
                jvm->DetachCurrentThread();
            }
            return;
        }

        // Cache the method ID outside the hot loop (saves a lookup per cycle)
        JNIEnv* pEnv = nullptr;
        jmethodID onSourceFoundMethod = nullptr;

        while (isFinderRunning) {
            // Wait up to 1 second for any source change instead of busy-sleeping
            if (!NDIlib_find_wait_for_sources(pNDI_find, 1000 /*ms*/)) {
                // No change — loop back without JNI overhead
                continue;
            }

            uint32_t no_sources = 0;
            const NDIlib_source_t* p_sources =
                NDIlib_find_get_current_sources(pNDI_find, &no_sources);

            if (no_sources == 0) continue;

            if (jvm->AttachCurrentThread(&pEnv, NULL) != JNI_OK) continue;

            // Resolve method ID only once
            if (onSourceFoundMethod == nullptr) {
                jclass cls = pEnv->GetObjectClass(mainActivityObj);
                onSourceFoundMethod = pEnv->GetMethodID(
                    cls, "onSourceFound", "(Ljava/lang/String;)V");
                pEnv->DeleteLocalRef(cls);
            }

            for (uint32_t i = 0; i < no_sources; i++) {
                jstring sourceName = pEnv->NewStringUTF(p_sources[i].p_ndi_name);
                pEnv->CallVoidMethod(mainActivityObj, onSourceFoundMethod, sourceName);
                pEnv->DeleteLocalRef(sourceName);         // Release immediately
            }

            jvm->DetachCurrentThread();
            pEnv = nullptr;
        }

        NDIlib_find_destroy(pNDI_find);

        // Clean up global ref
        if (jvm->AttachCurrentThread(&pEnv, NULL) == JNI_OK) {
            pEnv->DeleteGlobalRef(mainActivityObj);
            jvm->DetachCurrentThread();
        }
    }).detach();
}

extern "C" JNIEXPORT void JNICALL
Java_com_dreamscasino_nditv_MainActivity_stopNdiDiscovery(JNIEnv* env, jobject thiz) {
    isFinderRunning = false;
}

// --------------------------------------------------------------------------
//  RECEIVER
// --------------------------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_dreamscasino_nditv_PlayerActivity_startNdiReceiver(
        JNIEnv* env, jobject thiz, jstring sourceName, jobject surface) {

    if (!NDIlib_initialize()) {
        LOGE("Cannot run NDI.");
        return;
    }

    // ------- Copy source name before the JNI string object can be GC'd -------
    const char* c_sourceName = env->GetStringUTFChars(sourceName, NULL);
    std::string ndiSourceName(c_sourceName);
    env->ReleaseStringUTFChars(sourceName, c_sourceName);

    // ------- Create receiver -------
    NDIlib_source_t source_t;
    source_t.p_ndi_name = ndiSourceName.c_str();

    NDIlib_recv_create_v3_t recv_desc;
    recv_desc.color_format    = NDIlib_recv_color_format_e_RGBX_RGBA;
    recv_desc.bandwidth       = NDIlib_recv_bandwidth_highest;
    recv_desc.allow_video_fields = false; // Progressive-only: saves CPU
    recv_desc.p_ndi_recv_name = "NDI TV Player";

    NDIlib_recv_instance_t pNDI_recv = NDIlib_recv_create_v3(&recv_desc);
    if (!pNDI_recv) {
        LOGE("Failed to create NDI receiver.");
        return;
    }

    NDIlib_recv_connect(pNDI_recv, &source_t);

    // ------- Acquire window -------
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get ANativeWindow.");
        NDIlib_recv_destroy(pNDI_recv);
        return;
    }

    // ------- Cache JNI callback before entering the hot loop -------
    jclass  playerClass       = env->GetObjectClass(thiz);
    jmethodID firstFrameMethod = env->GetMethodID(playerClass, "onFirstFrameReceived", "()V");
    env->DeleteLocalRef(playerClass);   // Keep only what we need

    isReceiverRunning = true;

    int  none_count   = 0;
    int  current_xres = 0;
    int  current_yres = 0;
    bool first_frame  = true;

    // ===== HOT LOOP =====
    while (isReceiverRunning) {
        NDIlib_video_frame_v2_t   video_frame;
        NDIlib_audio_frame_v2_t   audio_frame;
        NDIlib_metadata_frame_t   metadata_frame;

        NDIlib_frame_type_e frame_type = NDIlib_recv_capture_v2(
            pNDI_recv, &video_frame, &audio_frame, &metadata_frame, 1000 /*ms*/);

        switch (frame_type) {

            // ── No data ───────────────────────────────────────────────────
            case NDIlib_frame_type_none:
                if (++none_count > 10) {           // 10 s without any data
                    LOGD("No data for 10 s. Receiver exiting.");
                    isReceiverRunning = false;
                }
                break;

            // ── Video ─────────────────────────────────────────────────────
            case NDIlib_frame_type_video:
                none_count = 0;

                if (video_frame.xres > 0 && video_frame.yres > 0) {

                    // Notify Kotlin on the very first frame (show UI)
                    if (first_frame) {
                        env->CallVoidMethod(thiz, firstFrameMethod);
                        first_frame = false;
                    }

                    // Only reconfigure window when resolution actually changes
                    if (video_frame.xres != current_xres ||
                        video_frame.yres != current_yres) {

                        ANativeWindow_setBuffersGeometry(
                            window,
                            video_frame.xres, video_frame.yres,
                            WINDOW_FORMAT_RGBA_8888);

                        current_xres = video_frame.xres;
                        current_yres = video_frame.yres;
                        LOGD("Resolution: %d x %d", current_xres, current_yres);
                    }

                    ANativeWindow_Buffer buffer;
                    if (ANativeWindow_lock(window, &buffer, NULL) == 0) {
                        uint8_t* dst = static_cast<uint8_t*>(buffer.bits);
                        uint8_t* src = video_frame.p_data;

                        int buf_stride = buffer.stride * 4;  // RGBA = 4 bytes/pixel

                        // Fast path: contiguous block when strides match
                        if (video_frame.line_stride_in_bytes == buf_stride) {
                            memcpy(dst, src, (size_t)buf_stride * video_frame.yres);
                        } else {
                            int copy_w = std::min(buf_stride,
                                                  video_frame.line_stride_in_bytes);
                            for (int y = 0; y < video_frame.yres; ++y) {
                                memcpy(dst + (y * buf_stride),
                                       src + (y * video_frame.line_stride_in_bytes),
                                       copy_w);
                            }
                        }

                        ANativeWindow_unlockAndPost(window);
                    }
                }

                // IMPORTANT: always free the frame to avoid NDI internal buffer starvation
                NDIlib_recv_free_video_v2(pNDI_recv, &video_frame);
                break;

            // ── Audio — drop immediately ───────────────────────────────────
            case NDIlib_frame_type_audio:
                none_count = 0;
                NDIlib_recv_free_audio_v2(pNDI_recv, &audio_frame);
                break;

            // ── Metadata — drop immediately ────────────────────────────────
            case NDIlib_frame_type_metadata:
                none_count = 0;
                NDIlib_recv_free_metadata(pNDI_recv, &metadata_frame);
                break;

            case NDIlib_frame_type_status_change:
                LOGD("NDI status change.");
                break;

            case NDIlib_frame_type_error:
                LOGE("NDI frame error — stopping receiver.");
                isReceiverRunning = false;
                break;

            default:
                break;
        }
    }

    // ===== CLEANUP =====
    NDIlib_recv_destroy(pNDI_recv);     // Frees internal NDI buffers
    ANativeWindow_release(window);      // Releases the Surface reference
    LOGD("Receiver thread exited cleanly.");
}

extern "C" JNIEXPORT void JNICALL
Java_com_dreamscasino_nditv_PlayerActivity_stopNdiReceiver(JNIEnv* env, jobject thiz) {
    isReceiverRunning = false;
}
