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

std::atomic<bool> isFinderRunning(false);
std::atomic<bool> isReceiverRunning(false);

extern "C" JNIEXPORT void JNICALL
Java_com_dreamscasino_nditv_MainActivity_startNdiDiscovery(JNIEnv* env, jobject thiz) {
    if (!NDIlib_initialize()) {
        LOGE("Cannot run NDI.");
        return;
    }

    isFinderRunning = true;
    
    // We need a global ref for the object to callback to it from a different thread
    jobject mainActivityObj = env->NewGlobalRef(thiz);
    JavaVM* jvm;
    env->GetJavaVM(&jvm);

    std::thread([jvm, mainActivityObj]() {
        NDIlib_find_create_t NDI_find_create_desc;
        NDI_find_create_desc.show_local_sources = true;
        NDIlib_find_instance_t pNDI_find = NDIlib_find_create_v2(&NDI_find_create_desc);
        
        if (!pNDI_find) {
            LOGE("Failed to create NDI find instance.");
            return;
        }

        while (isFinderRunning) {
            uint32_t no_sources = 0;
            const NDIlib_source_t* p_sources = NDIlib_find_get_current_sources(pNDI_find, &no_sources);
            
            if (no_sources > 0) {
                JNIEnv* pEnv;
                if (jvm->AttachCurrentThread(&pEnv, NULL) == JNI_OK) {
                    jclass mainActivityClass = pEnv->GetObjectClass(mainActivityObj);
                    jmethodID onSourceFoundMethod = pEnv->GetMethodID(mainActivityClass, "onSourceFound", "(Ljava/lang/String;)V");
                    
                    for (uint32_t i = 0; i < no_sources; i++) {
                        jstring sourceName = pEnv->NewStringUTF(p_sources[i].p_ndi_name);
                        pEnv->CallVoidMethod(mainActivityObj, onSourceFoundMethod, sourceName);
                        pEnv->DeleteLocalRef(sourceName);
                    }
                    jvm->DetachCurrentThread();
                }
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(1000));
        }
        
        NDIlib_find_destroy(pNDI_find);
        
        JNIEnv* pEnv;
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

extern "C" JNIEXPORT void JNICALL
Java_com_dreamscasino_nditv_PlayerActivity_startNdiReceiver(JNIEnv* env, jobject thiz, jstring sourceName, jobject surface) {
    if (!NDIlib_initialize()) {
        LOGE("Cannot run NDI.");
        return;
    }

    const char* c_sourceName = env->GetStringUTFChars(sourceName, NULL);
    std::string ndiSourceName(c_sourceName);
    env->ReleaseStringUTFChars(sourceName, c_sourceName);
    
    // Setup NDI Receiver
    NDIlib_source_t source_t;
    source_t.p_ndi_name = ndiSourceName.c_str();
    
    NDIlib_recv_create_v3_t recv_create_desc;
    recv_create_desc.color_format = NDIlib_recv_color_format_e_RGBX_RGBA;
    recv_create_desc.bandwidth = NDIlib_recv_bandwidth_highest;
    recv_create_desc.allow_video_fields = false; // Disable de-interlacing to save CPU
    recv_create_desc.p_ndi_recv_name = "NDI TV Player";
    
    NDIlib_recv_instance_t pNDI_recv = NDIlib_recv_create_v3(&recv_create_desc);
    if (!pNDI_recv) {
        LOGE("Failed to create NDI receiver.");
        return;
    }
    
    NDIlib_recv_connect(pNDI_recv, &source_t);
    
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get ANativeWindow.");
        NDIlib_recv_destroy(pNDI_recv);
        return;
    }
    
    isReceiverRunning = true;
    
    int none_count = 0;
    int current_xres = 0;
    int current_yres = 0;
    
    while (isReceiverRunning) {
        NDIlib_video_frame_v2_t video_frame;
        NDIlib_audio_frame_v2_t audio_frame;
        NDIlib_metadata_frame_t metadata_frame;
        
        NDIlib_frame_type_e frame_type = NDIlib_recv_capture_v2(pNDI_recv, &video_frame, &audio_frame, &metadata_frame, 1000);
        
        switch (frame_type) {
            case NDIlib_frame_type_none:
                none_count++;
                // If no frame for 10 seconds, assume connection lost
                if (none_count > 10) {
                    LOGD("No data for 10 seconds. Receiver exiting.");
                    isReceiverRunning = false;
                }
                break;
            
            case NDIlib_frame_type_video:
                none_count = 0;
                if (video_frame.xres > 0 && video_frame.yres > 0) {
                    // Only update geometry if resolution changed to save CPU time
                    if (video_frame.xres != current_xres || video_frame.yres != current_yres) {
                        ANativeWindow_setBuffersGeometry(window, video_frame.xres, video_frame.yres, WINDOW_FORMAT_RGBA_8888);
                        current_xres = video_frame.xres;
                        current_yres = video_frame.yres;
                    }
                    
                    ANativeWindow_Buffer buffer;
                    if (ANativeWindow_lock(window, &buffer, NULL) == 0) {
                        uint8_t* out_pixels = static_cast<uint8_t*>(buffer.bits);
                        uint8_t* in_pixels = video_frame.p_data;
                        
                        // Optimize memory copy: if strides match, perform a single bulk copy
                        int buffer_stride_bytes = buffer.stride * 4;
                        if (video_frame.line_stride_in_bytes == buffer_stride_bytes) {
                            memcpy(out_pixels, in_pixels, (size_t)buffer_stride_bytes * video_frame.yres);
                        } else {
                            int copy_width = std::min((int)buffer_stride_bytes, video_frame.line_stride_in_bytes);
                            for (int y = 0; y < video_frame.yres; y++) {
                                memcpy(out_pixels + (y * buffer_stride_bytes), 
                                       in_pixels + (y * video_frame.line_stride_in_bytes), 
                                       copy_width);
                            }
                        }
                        ANativeWindow_unlockAndPost(window);
                    }
                }
                NDIlib_recv_free_video_v2(pNDI_recv, &video_frame);
                break;
            
            case NDIlib_frame_type_audio:
                none_count = 0;
                NDIlib_recv_free_audio_v2(pNDI_recv, &audio_frame);
                break;
            
            case NDIlib_frame_type_metadata:
                none_count = 0;
                NDIlib_recv_free_metadata(pNDI_recv, &metadata_frame);
                break;
            
            case NDIlib_frame_type_status_change:
                LOGD("NDI Status Change");
                break;
            
            case NDIlib_frame_type_error:
                LOGE("NDI Frame Type Error");
                isReceiverRunning = false;
                break;
        }
    }
    
    NDIlib_recv_destroy(pNDI_recv);
    ANativeWindow_release(window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_dreamscasino_nditv_PlayerActivity_stopNdiReceiver(JNIEnv* env, jobject thiz) {
    isReceiverRunning = false;
}
