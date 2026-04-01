package com.dreamscasino.nditv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var loadingLayout: View
    private var sourceName: String? = null

    // True while the native receiver thread is active
    @Volatile private var receiverStarted = false

    // Double-click to exit
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 800L

    companion object {
        const val RESULT_CONNECTION_LOST = 100
        const val RESULT_MANUAL_EXIT     = 101

        init {
            try {
                System.loadLibrary("ndi")
                System.loadLibrary("ndijni")
                Log.d("NDI_Debug", "PlayerActivity: Libraries loaded.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("NDI_Debug", "PlayerActivity: Library load failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        sourceName = intent.getStringExtra("NDI_SOURCE_NAME")
        Log.d("NDI_Debug", "PlayerActivity: source = $sourceName")

        setContentView(R.layout.activity_player)
        surfaceView  = findViewById(R.id.surfaceView)
        loadingLayout = findViewById(R.id.loadingLayout)

        surfaceView.requestFocus()
        surfaceView.holder.addCallback(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure the native loop exits even if surfaceDestroyed wasn't called
        if (receiverStarted) {
            Log.d("NDI_Debug", "PlayerActivity: onDestroy — stopping receiver")
            safeStopReceiver()
        }
    }

    // -------------------------------------------------------------------------
    // SurfaceHolder.Callback
    // -------------------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("NDI_Debug", "PlayerActivity: surfaceCreated")
        val surface = holder.surface
        if (!surface.isValid || sourceName == null) {
            Log.w("NDI_Debug", "PlayerActivity: surface invalid or no source.")
            return
        }
        startReceiverThread(surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("NDI_Debug", "PlayerActivity: surfaceChanged $width×$height")
        // Nothing — the native loop handles resolution changes internally.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("NDI_Debug", "PlayerActivity: surfaceDestroyed — stopping receiver")
        // Block briefly so the native thread has time to see the flag and stop
        // before Android destroys the underlying window.
        safeStopReceiver()
    }

    // -------------------------------------------------------------------------
    // NDI thread
    // -------------------------------------------------------------------------

    private fun startReceiverThread(surface: Surface) {
        if (receiverStarted) return
        receiverStarted = true

        Thread {
            try {
                startNdiReceiver(sourceName!!, surface)
                Log.d("NDI_Debug", "PlayerActivity: receiver returned normally.")

                if (!isDestroyed && !isFinishing) {
                    runOnUiThread {
                        Toast.makeText(
                            this@PlayerActivity,
                            "Conexión perdida con la fuente",
                            Toast.LENGTH_LONG
                        ).show()
                        setResult(RESULT_CONNECTION_LOST)
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("NDI_Debug", "PlayerActivity: receiver error: ${e.message}")
                if (!isDestroyed && !isFinishing) runOnUiThread { finish() }
            } catch (e: UnsatisfiedLinkError) {
                Log.e("NDI_Debug", "PlayerActivity: JNI error: ${e.message}")
                if (!isDestroyed && !isFinishing) runOnUiThread { finish() }
            } finally {
                receiverStarted = false
            }
        }.apply {
            name = "NDI-Receiver"   // Named thread — easier to spot in profilers
            priority = Thread.MAX_PRIORITY - 1  // High but not above system threads
        }.start()
    }

    private fun safeStopReceiver() {
        try { stopNdiReceiver() } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // JNI callback — called by C++ on the first decoded video frame
    // -------------------------------------------------------------------------

    fun onFirstFrameReceived() {
        Log.d("NDI_Debug", "PlayerActivity: first frame — hiding loader")
        runOnUiThread { loadingLayout.visibility = View.GONE }
    }

    // -------------------------------------------------------------------------
    // Native declarations
    // -------------------------------------------------------------------------

    private external fun startNdiReceiver(sourceName: String, surface: Surface)
    private external fun stopNdiReceiver()

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            23, 66 -> {
                val now   = System.currentTimeMillis()
                val delta = now - lastClickTime
                if (delta < DOUBLE_CLICK_TIME_DELTA) {
                    Log.d("NDI_Debug", "PlayerActivity: double-click — exit")
                    setResult(RESULT_MANUAL_EXIT)
                    finish()
                } else {
                    lastClickTime = now
                    Toast.makeText(this, getString(R.string.toast_press_again_to_exit), Toast.LENGTH_SHORT).show()
                }
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                setResult(RESULT_MANUAL_EXIT)
                finish()
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun hideSystemUI() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        } catch (e: Exception) {
            Log.e("NDI_Debug", "PlayerActivity: hideSystemUI error: ${e.message}")
        }
    }
}
