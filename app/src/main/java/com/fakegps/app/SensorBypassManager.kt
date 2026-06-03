package com.fakegps.app

import android.content.Context
import android.provider.Settings
import android.util.Log

object SensorBypassManager {
    fun disableWiFiScanning(context: Context) {
        try {
            // Membutuhkan izin WRITE_SECURE_SETTINGS via ADB
            Settings.Global.putInt(context.contentResolver, "wifi_scan_always_enabled", 0)
            Settings.Global.putInt(context.contentResolver, "ble_scan_always_enabled", 0)
            Log.d("BYPASS", "Sensor pembocor berhasil dimatikan via Secure Settings")
        } catch (e: SecurityException) {
            Log.e("BYPASS", "Gagal mematikan sensor. Jalankan perintah ADB: adb shell pm grant com.fakegps.app android.permission.WRITE_SECURE_SETTINGS")
        }
    }
}