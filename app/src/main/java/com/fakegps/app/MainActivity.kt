package com.fakegps.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fakegps.app.xposed.XposedInit

class MainActivity : AppCompatActivity() {

    private val btnStop by lazy { findViewById<Button>(R.id.btnStopMocking) }
    private val tvStatus by lazy { findViewById<TextView>(R.id.tvStatus) }
    private val spinnerMovement by lazy { findViewById<Spinner>(R.id.spinnerMovement) }
    private val tvXposedStatus by lazy { findViewById<TextView>(R.id.tvXposedStatus) }

    private var isXposedModuleActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkXposedModuleStatus()
        setupMovementSpinner()
        setupStopButton()
        autoStartSpoofing()
    }

    override fun onResume() {
        super.onResume()
        updateServiceRunningStatus()
    }

    private fun checkXposedModuleStatus() {
        try {
            val pm = packageManager
            val isModule = try {
                pm.getActivityInfo(
                    ComponentName(this, javaClass),
                    PackageManager.GET_META_DATA
                ).metaData?.getBoolean("xposedmodule", false) == true
            } catch (_: Exception) { false }

            isXposedModuleActive = try {
                Class.forName("de.robv.android.xposed.XposedBridge")
                true
            } catch (_: ClassNotFoundException) {
                false
            }

            if (isXposedModuleActive) {
                tvXposedStatus.visibility = TextView.VISIBLE
                tvXposedStatus.text = "Modul Aktif — Lokasi aman dari deteksi"
                tvXposedStatus.setBackgroundColor(0x1A4CAF50.toInt())
            } else if (isModule) {
                tvXposedStatus.visibility = TextView.VISIBLE
                tvXposedStatus.text = "Modul terinstall tapi belum aktif"
                tvXposedStatus.setBackgroundColor(0x1AFF9800.toInt())
            }
        } catch (_: Exception) { }
    }

    private fun setupMovementSpinner() {
        val modes = MovementMode.values().map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMovement.adapter = adapter
        spinnerMovement.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                if (isServiceRunning()) {
                    restartSpoofing()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun autoStartSpoofing() {
        val preset = PresetManager.presets.first()
        startSpoofing(preset.latitude, preset.longitude, preset.altitude, preset.accuracy)
    }

    private fun startSpoofing(lat: Double, lng: Double, alt: Double, acc: Float) {
        val modeIndex = spinnerMovement.selectedItemPosition
        val mode = MovementMode.values().getOrElse(modeIndex) { MovementMode.STATIONARY }

        if (isXposedModuleActive) {
            XposedInit.updateSpoof(lat, lng, true)
        }

        val serviceIntent = Intent(this, LocationService::class.java).apply {
            putExtra("LAT", lat)
            putExtra("LNG", lng)
            putExtra("ALT", alt)
            putExtra("ACC", acc)
            putExtra("MODE", mode.ordinal)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updateServiceRunningStatus()
        Toast.makeText(this, "Spoofing otomatis: ${mode.label}", Toast.LENGTH_SHORT).show()
    }

    private fun restartSpoofing() {
        stopService(Intent(this, LocationService::class.java))
        autoStartSpoofing()
    }

    private fun setupStopButton() {
        btnStop.setOnClickListener {
            if (isXposedModuleActive) {
                XposedInit.updateSpoof(0.0, 0.0, false)
            }
            stopService(Intent(this, LocationService::class.java))
            updateServiceRunningStatus()
            Toast.makeText(this, "Spoofing dihentikan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateServiceRunningStatus() {
        val isRunning = isServiceRunning()
        if (isRunning) {
            tvStatus.text = "Aktif — Lokasi Termanipulasi"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            btnStop.visibility = Button.VISIBLE
        } else {
            tvStatus.text = "Tidak Aktif"
            tvStatus.setTextColor(0xFFD32F2F.toInt())
            btnStop.visibility = Button.GONE
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (LocationService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
