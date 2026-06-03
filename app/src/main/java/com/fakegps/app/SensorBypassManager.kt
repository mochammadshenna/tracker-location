package com.fakegps.app

import android.content.Context
import android.provider.Settings
import android.util.Log

object SensorBypassManager {
    private const val TAG = "SensorBypass"

    fun disableAllSensors(context: Context) {
        disableWiFiScanning(context)
        disableBLEScanning(context)
        disableLocationThrottling(context)
    }

    fun disableWiFiScanning(context: Context) {
        try {
            Settings.Global.putInt(context.contentResolver, "wifi_scan_always_enabled", 0)
            Log.d(TAG, "WiFi always-scanning disabled")
        } catch (e: SecurityException) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS not granted for wifi_scan")
        }
    }

    fun disableBLEScanning(context: Context) {
        try {
            Settings.Global.putInt(context.contentResolver, "ble_scan_always_enabled", 0)
            Log.d(TAG, "BLE always-scanning disabled")
        } catch (e: SecurityException) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS not granted for ble_scan")
        }
    }

    private fun disableLocationThrottling(context: Context) {
        try {
            Settings.Global.putString(
                context.contentResolver,
                "locationPackageBlacklist",
                ""
            )
            Settings.Global.putString(
                context.contentResolver,
                "locationPackageWhitelist",
                ""
            )
            Log.d(TAG, "Location throttling blacklist/whitelist cleared")
        } catch (e: SecurityException) {
            // Best-effort
        }
    }
}
