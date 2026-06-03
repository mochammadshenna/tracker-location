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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fakegps.app.xposed.XposedInit
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private val inputLat by lazy { findViewById<EditText>(R.id.inputLatitude) }
    private val inputLng by lazy { findViewById<EditText>(R.id.inputLongitude) }
    private val btnStart by lazy { findViewById<Button>(R.id.btnStartMocking) }
    private val btnStop by lazy { findViewById<Button>(R.id.btnStopMocking) }
    private val tvStatus by lazy { findViewById<TextView>(R.id.tvStatus) }
    private val presetsContainer by lazy { findViewById<LinearLayout>(R.id.presetsContainer) }
    private val spinnerMovement by lazy { findViewById<Spinner>(R.id.spinnerMovement) }
    private val tvXposedStatus by lazy { findViewById<TextView>(R.id.tvXposedStatus) }

    private var activePresetName: String? = null
    private var selectedLat = -6.372215
    private var selectedLng = 106.838563
    private var selectedAlt = 15.0
    private var selectedAcc = 5.0f
    private var isXposedModuleActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkXposedModuleStatus()
        setupPresets()
        setupMovementSpinner()
        setupButtons()
        updateServiceRunningStatus()
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
                tvXposedStatus.text = "✓ LSPosed Module Active — System-wide hooks enabled. Mock detection bypassed for ALL apps."
                tvXposedStatus.setBackgroundColor(0x1A4CAF50.toInt())
            } else if (isModule) {
                tvXposedStatus.visibility = TextView.VISIBLE
                tvXposedStatus.text = "ℹ LSPosed module installed but not active. Enable in LSPosed Manager → System Framework.\nUsing fallback mock mode (detectable by some apps)."
                tvXposedStatus.setBackgroundColor(0x1AFF9800.toInt())
            }
        } catch (_: Exception) { }
    }

    private fun setupPresets() {
        for (preset in PresetManager.presets) {
            val switch = SwitchMaterial(this).apply {
                val label = preset.name + if (preset.description.isNotEmpty()) " — ${preset.description}" else ""
                setText(label)
                setLineSpacing(0f, 1.2f)
                textSize = 14f
                tag = preset
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        // Uncheck all other presets
                        for (i in 0 until presetsContainer.childCount) {
                            val child = presetsContainer.getChildAt(i)
                            if (child is SwitchMaterial && child != this) {
                                child.isChecked = false
                            }
                        }
                        activePresetName = preset.name
                        selectedLat = preset.latitude
                        selectedLng = preset.longitude
                        selectedAlt = preset.altitude
                        selectedAcc = preset.accuracy
                        inputLat.setText(preset.latitude.toString())
                        inputLng.setText(preset.longitude.toString())
                        inputLat.isEnabled = false
                        inputLng.isEnabled = false
                    } else {
                        if (activePresetName == preset.name) {
                            activePresetName = null
                            inputLat.isEnabled = true
                            inputLng.isEnabled = true
                        }
                    }
                }
            }
            presetsContainer.addView(switch)
        }
    }

    private fun setupMovementSpinner() {
        val modes = MovementMode.values().map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMovement.adapter = adapter
    }

    private fun setupButtons() {
        btnStart.setOnClickListener {
            val lat = inputLat.text.toString().toDoubleOrNull()
            val lng = inputLng.text.toString().toDoubleOrNull()

            if (lat == null || lng == null) {
                Toast.makeText(this, "Enter valid coordinates first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                Toast.makeText(this, "Coordinates out of valid range", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedLat = lat
            selectedLng = lng

            val modeIndex = spinnerMovement.selectedItemPosition
            val mode = MovementMode.values().getOrElse(modeIndex) { MovementMode.STATIONARY }

            // Update Xposed module if active
            if (isXposedModuleActive) {
                XposedInit.updateSpoof(lat, lng, true)
            }

            val serviceIntent = Intent(this, LocationService::class.java).apply {
                putExtra("LAT", lat)
                putExtra("LNG", lng)
                putExtra("ALT", selectedAlt)
                putExtra("ACC", selectedAcc)
                putExtra("MODE", mode.ordinal)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            updateServiceRunningStatus()
            Toast.makeText(this, "Started: ${mode.label}", Toast.LENGTH_LONG).show()
        }

        btnStop.setOnClickListener {
            if (isXposedModuleActive) {
                XposedInit.updateSpoof(selectedLat, selectedLng, false)
            }
            stopService(Intent(this, LocationService::class.java))
            updateServiceRunningStatus()
            Toast.makeText(this, "Location spoofing stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateServiceRunningStatus() {
        val isRunning = isServiceRunning()
        if (isRunning) {
            tvStatus.text = "● Active — Spoofing Location"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        } else {
            tvStatus.text = "● Not Active"
            tvStatus.setTextColor(0xFFD32F2F.toInt())
            btnStart.isEnabled = true
            btnStop.isEnabled = false
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
