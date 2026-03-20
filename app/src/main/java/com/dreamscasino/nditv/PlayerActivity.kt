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
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private var sourceName: String? = null
    
    // Double click to go back logic
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 800 // Reduced sensitivity for easier double click

    companion object {
        const val RESULT_CONNECTION_LOST = 100
        const val RESULT_MANUAL_EXIT = 101
        
        init {
            Log.d("NDI_Debug", "PlayerActivity: Static init - Loading libraries")
            try {
                System.loadLibrary("ndi")
                System.loadLibrary("ndijni")
                Log.d("NDI_Debug", "PlayerActivity: Libraries loaded successfully")
            } catch (e: Exception) {
                Log.e("NDI_Debug", "PlayerActivity: Static init - Lib load error: ${e.message}")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("NDI_Debug", "PlayerActivity: Static init - Lib link error: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("NDI_Debug", "PlayerActivity: onCreate started")
        super.onCreate(savedInstanceState)

        try {
            // Keep screen on
            Log.d("NDI_Debug", "PlayerActivity: Setting KEEP_SCREEN_ON flag")
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            Log.d("NDI_Debug", "PlayerActivity: Hiding System UI")
            hideSystemUI()

            sourceName = intent.getStringExtra("NDI_SOURCE_NAME")
            Log.d("NDI_Debug", "PlayerActivity: NDI_SOURCE_NAME = $sourceName")

            Log.d("NDI_Debug", "PlayerActivity: Creating SurfaceView")
            surfaceView = SurfaceView(this)
            surfaceView.isFocusable = true
            surfaceView.isFocusableInTouchMode = true
            surfaceView.requestFocus()
            
            setContentView(surfaceView)
            
            Log.d("NDI_Debug", "PlayerActivity: Adding SurfaceHolder callback")
            surfaceView.holder.addCallback(this)
            
            Log.d("NDI_Debug", "PlayerActivity: onCreate finished successfully")
        } catch (e: Exception) {
            Log.e("NDI_Debug", "PlayerActivity: Exception in onCreate: ${e.message}", e)
            finish()
        }
    }

    private fun hideSystemUI() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Log.d("NDI_Debug", "PlayerActivity: Using WindowInsetsController (API >= 30)")
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    Log.d("NDI_Debug", "PlayerActivity: System bars hidden via controller")
                } ?: Log.w("NDI_Debug", "PlayerActivity: WindowInsetsController is null")
            } else {
                Log.d("NDI_Debug", "PlayerActivity: Using systemUiVisibility (API < 30)")
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
            Log.e("NDI_Debug", "PlayerActivity: Error in hideSystemUI: ${e.message}")
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("NDI_Debug", "PlayerActivity: surfaceCreated")
        val surface = holder.surface
        if (surface.isValid && sourceName != null) {
            Log.d("NDI_Debug", "PlayerActivity: Starting NDI receiver thread")
            Thread {
                try {
                    Log.d("NDI_Debug", "PlayerActivity: Calling native startNdiReceiver")
                    startNdiReceiver(sourceName!!, surface)
                    Log.d("NDI_Debug", "PlayerActivity: native startNdiReceiver returned")
                    
                    // If we get here, the receiver loop ended.
                    // If it wasn't requested by onDestroy, it's a connection drop.
                    if (!isDestroyed && !isFinishing) {
                        runOnUiThread {
                            android.widget.Toast.makeText(this@PlayerActivity, "Error: Conexión perdida con la fuente", android.widget.Toast.LENGTH_LONG).show()
                            setResult(RESULT_CONNECTION_LOST)
                            finish()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NDI_Debug", "PlayerActivity: Thread - Receiver error: ${e.message}")
                    runOnUiThread { finish() }
                } catch (e: UnsatisfiedLinkError) {
                    Log.e("NDI_Debug", "PlayerActivity: Thread - JNI error: ${e.message}")
                    runOnUiThread { finish() }
                }
            }.start()
        } else {
            Log.w("NDI_Debug", "PlayerActivity: Surface invalid or sourceName is null")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("NDI_Debug", "PlayerActivity: surfaceChanged ($width x $height)")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("NDI_Debug", "PlayerActivity: surfaceDestroyed")
        try {
            Log.d("NDI_Debug", "PlayerActivity: Calling native stopNdiReceiver")
            stopNdiReceiver()
            Log.d("NDI_Debug", "PlayerActivity: native stopNdiReceiver returned")
        } catch (e: Exception) {
            Log.e("NDI_Debug", "PlayerActivity: Stop error: ${e.message}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NDI_Debug", "PlayerActivity: Stop JNI error")
        }
    }

    private external fun startNdiReceiver(sourceName: String, surface: Surface)
    private external fun stopNdiReceiver()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        
        // Log absolutely everything to see what the remote is doing
        if (action == KeyEvent.ACTION_DOWN) {
            Log.d("NDI_Debug", "PlayerActivity: KEY DOWN detected: $keyCode")
            
            // Check for center click (Enter, Dpad center, or Select)
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                keyCode == KeyEvent.KEYCODE_ENTER || 
                keyCode == 23 || // Common OK code
                keyCode == 66) { // Common Enter code
                
                val clickTime = System.currentTimeMillis()
                val delta = clickTime - lastClickTime
                Log.d("NDI_Debug", "PlayerActivity: Click detected. Delta: $delta ms")
                
                if (delta < DOUBLE_CLICK_TIME_DELTA) {
                    Log.d("NDI_Debug", "PlayerActivity: DOUBLE CLICK CONFIRMED. Closing player.")
                    setResult(RESULT_MANUAL_EXIT)
                    finish()
                    return true
                }
                
                lastClickTime = clickTime
                Log.d("NDI_Debug", "PlayerActivity: First click registered. Waiting for second.")
                android.widget.Toast.makeText(this, "Presiona de nuevo para salir", android.widget.Toast.LENGTH_SHORT).show()
                return true
            }
            
            // Allow back button to work normally as backup
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                Log.d("NDI_Debug", "PlayerActivity: BACK key detected. Closing.")
                setResult(RESULT_MANUAL_EXIT)
                finish()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
}
