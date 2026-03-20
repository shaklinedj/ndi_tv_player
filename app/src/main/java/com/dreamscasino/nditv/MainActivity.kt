package com.dreamscasino.nditv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvStatus: TextView
    private val sources = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private var ndiLibLoaded = false
    private var multicastLock: WifiManager.MulticastLock? = null

    // All dangerous permissions that require user approval (Android requests these one by one automatically)
    private val dangerousPermissions: List<String> = buildList {
        // Location is needed for WiFi scanning on Android 10-12
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        // Android 13+ uses NEARBY_WIFI_DEVICES instead of location for WiFi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        // Android 14+ may require post notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private var pendingPermissions = mutableListOf<String>()

    // Single permission launcher — called repeatedly for one-by-one flow
    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val current = pendingPermissions.removeFirstOrNull()
        if (granted) {
            Log.d("NDI", "Permission granted: $current")
        } else {
            Log.w("NDI", "Permission denied: $current")
            Toast.makeText(this, "Permiso denegado: $current", Toast.LENGTH_SHORT).show()
        }
        // Continue requesting the next permission, or start if all done
        requestNextPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Evitar que la pantalla se apague/bloquee
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Load NDI native libraries in correct order
        ndiLibLoaded = try {
            System.loadLibrary("ndi")       // NDI base SDK
            System.loadLibrary("ndijni")    // Our JNI bridge
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NDI", "Failed to load NDI libraries: ${e.message}")
            false
        }

        setContentView(R.layout.activity_main)
        
        listView = findViewById(R.id.listView)
        tvStatus = findViewById(R.id.tvStatus)

        adapter = ArrayAdapter(this, R.layout.ndi_list_item, R.id.text1, sources)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val sourceName = sources[position]
            Log.d("NDI_Debug", "MainActivity: Item clicked at position $position. Source: $sourceName")
            
            try {
                Log.d("NDI_Debug", "MainActivity: Starting PlayerActivity for $sourceName")
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("NDI_SOURCE_NAME", sourceName)
                }
                startActivity(intent)
                Log.d("NDI_Debug", "MainActivity: startActivity called successfully")
            } catch (e: Exception) {
                Log.e("NDI_Debug", "MainActivity: Error starting PlayerActivity: ${e.message}", e)
                Toast.makeText(this, "Error al abrir reproductor: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        if (!ndiLibLoaded) {
            tvStatus.text = "Error: Librería NDI no soportada\n(Arquitectura arm64-v8a requerida)"
            tvStatus.setTextColor(android.graphics.Color.RED)
            Toast.makeText(this, "Error: librería NDI no cargada", Toast.LENGTH_LONG).show()
            return
        }

        tvStatus.text = "Verificando permisos..."

        // Build list of permissions not yet granted
        pendingPermissions = dangerousPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toMutableList()

        if (pendingPermissions.isEmpty()) {
            if (ndiLibLoaded) startDiscovery()
        } else {
            Toast.makeText(
                this,
                "Se solicitarán ${pendingPermissions.size} permiso(s) necesario(s)",
                Toast.LENGTH_LONG
            ).show()
            requestNextPermission()
        }
    }

    /**
     * Pops the next permission from the queue and requests it.
     * When the queue is empty, starts NDI discovery.
     */
    private fun requestNextPermission() {
        if (pendingPermissions.isEmpty()) {
            startDiscovery()
            return
        }
        val next = pendingPermissions.first() // don't remove yet, remove on callback
        Log.d("NDI", "Requesting permission: $next")
        singlePermissionLauncher.launch(next)
    }

    private fun startDiscovery() {
        // Acquire WiFi Multicast Lock — required for NDI mDNS discovery on Android
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("NDIMulticastLock")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()
        Log.d("NDI", "Multicast lock acquired")

        tvStatus.text = "Buscando fuentes NDI en la red..."
        sources.clear()
        adapter.notifyDataSetChanged()

        Thread {
            try {
                startNdiDiscovery()
            } catch (e: Exception) {
                Log.e("NDI", "Discovery error: ${e.message}")
                runOnUiThread {
                    tvStatus.text = "Fallo en la búsqueda:\n${e.message}"
                    tvStatus.setTextColor(android.graphics.Color.RED)
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { stopNdiDiscovery() } catch (e: Exception) { /* ignore */ }
        multicastLock?.release()
    }

    private external fun startNdiDiscovery()
    private external fun stopNdiDiscovery()

    fun onSourceFound(name: String) {
        runOnUiThread {
            if (sources.isEmpty()) {
                tvStatus.text = "Selecciona una transmisión:"
            }
            if (!sources.contains(name)) {
                sources.add(name)
                adapter.notifyDataSetChanged()
                if (sources.size == 1) {
                    listView.requestFocus()
                    listView.setSelection(0)
                }
                Log.d("NDI", "Found source: $name")
            }
        }
    }
}
