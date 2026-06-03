package com.fakegps.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    // Koordinat Statis untuk Lokasi "Work"
    private val WORK_LAT = -6.372215
    private val WORK_LNG = 106.838563

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val switchWork = findViewById<SwitchCompat>(R.id.switchWorkMode)
        val inputLat = findViewById<EditText>(R.id.inputLatitude)
        val inputLng = findViewById<EditText>(R.id.inputLongitude)
        val btnStart = findViewById<Button>(R.id.btnStartMocking)
        val btnStop = findViewById<Button>(R.id.btnStopMocking)

        // Logika Toggle Work Mode
        switchWork.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Kunci ke koordinat kerja dan matikan input manual
                inputLat.setText(WORK_LAT.toString())
                inputLng.setText(WORK_LNG.toString())
                inputLat.isEnabled = false
                inputLng.isEnabled = false
                Toast.makeText(this, "Mode Work Aktif", Toast.LENGTH_SHORT).show()
            } else {
                // Buka kembali input manual
                inputLat.text.clear()
                inputLng.text.clear()
                inputLat.isEnabled = true
                inputLng.isEnabled = true
            }
        }

        btnStart.setOnClickListener {
            val lat = inputLat.text.toString().toDoubleOrNull()
            val lng = inputLng.text.toString().toDoubleOrNull()

            if (lat != null && lng != null) {
                val serviceIntent = Intent(this, LocationService::class.java).apply {
                    putExtra("LAT", lat)
                    putExtra("LNG", lng)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Mempublikasikan Koordinat ke Sistem...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Harap masukkan koordinat atau aktifkan Mode Work", Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, LocationService::class.java))
            Toast.makeText(this, "Pemalsuan Lokasi Dihentikan", Toast.LENGTH_SHORT).show()
        }
    }
}