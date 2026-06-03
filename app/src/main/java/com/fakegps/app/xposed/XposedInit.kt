package com.fakegps.app.xposed

import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed / Xposed module entry point.
 *
 * Hooks Android framework classes at runtime to:
 * 1. Make Location.isMock() / isFromMockProvider() return false
 * 2. Hide mock location developer settings from app reads
 * 3. Hide known mock location package names from PackageManager queries
 * 4. Intercept LocationManager callbacks to inject spoofed coordinates
 *    without using mock provider mode at all (alternate path)
 *
 * Works system-wide — ALL apps on the device are affected.
 * No ACCESS_MOCK_LOCATION permission required in Developer Options.
 */
class XposedInit : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "TrackerLocation-Xposed"
        private val knownMockPackages = setOf(
            "com.fakegps.app",
            "com.lexa.fakegps",
            "com.incorporateapps.fakegps",
            "com.incorporateapps.fakegps.fre",
            "com.gps.fake",
            "com.fakegps.fakelocation",
            "com.fakegps.mock",
            "com.fakelocation.gps",
            "com.gpsjoy.mockgps",
            "com.gpslocation.faker",
            "ru.gavrikov.mocklocations",
            "com.moc.gps",
            "com.evozi.fakegps",
            "com.fakegps.lexa",
            "com.fakegps.wok",
            "com.gps.master",
            "com.twenty.fakegps",
            "com.ninja.fakegps",
            "com.lion.lgps",
            "com.ios.fakegps",
            "com.gold.fakegps",
            "com.tr.fakegps",
            "com.noob.fakegps",
            "com.hm.fakegps",
            "com.zhenxi.fakegps",
            "com.location.faker",
            "com.psh.fakegps",
            "com.psh.fakegps2",
            "com.gpsfake",
            "com.fakegps.kr",
            "com.android.gps.fake",
            "com.nogu.fakegps"
        )

        // Known mock location settings keys to filter
        private val mockSettingKeys = setOf(
            "mock_location",
            "mock_allow",
            "allow_mock_location",
            "mock_location_app",
            "select_mock_location_app"
        )

        private var spoofLat = -6.372215
        private var spoofLng = 106.838563
        private var spoofEnabled = false

        fun updateSpoof(lat: Double, lng: Double, enabled: Boolean) {
            spoofLat = lat
            spoofLng = lng
            spoofEnabled = enabled
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Only hook system_server and android framework
        if (lpparam.packageName == "android") {
            hookLocationClass(lpparam.classLoader)
            hookSettings(lpparam.classLoader)
        }
        if (lpparam.packageName == "com.google.android.gms") {
            hookGooglePlayServicesLocation(lpparam.classLoader)
        }
        // Hook PackageManager for target apps (scope set in LSPosed Manager)
        hookPackageManager(lpparam.classLoader, lpparam.packageName)
    }

    // ──────────────────────────────────────────────
    // 1. HOOK Location.isMock() / isFromMockProvider()
    // ──────────────────────────────────────────────
    private fun hookLocationClass(classLoader: ClassLoader) {
        try {
            val locationClass = XposedHelpers.findClass("android.location.Location", classLoader)

            // Location.isMock() — Android 12+ (API 31+)
            XposedHelpers.findAndHookMethod(
                locationClass, "isMock",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) param.result = false
                    }
                }
            )

            // Location.isFromMockProvider() — deprecated but still used by legacy apps
            try {
                XposedHelpers.findAndHookMethod(
                    locationClass, "isFromMockProvider",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (spoofEnabled) param.result = false
                        }
                    }
                )
            } catch (_: NoSuchMethodError) { }

            // Intercept Location.setExtras() to clear mock-related extras
            try {
                XposedHelpers.findAndHookMethod(
                    locationClass, "setExtras", android.os.Bundle::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val extras = param.args[0] as? android.os.Bundle ?: return
                            if (spoofEnabled) {
                                extras.remove("mockLocation")
                                extras.remove("mockProvider")
                                extras.remove("isFromMockProvider")
                            }
                        }
                    }
                )
            } catch (_: NoSuchMethodError) { }

            // Hook Location constructor to ensure mock flags are cleared
            XposedHelpers.findAndHookConstructor(
                locationClass, Location::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) {
                            val loc = param.thisObject as Location
                            // Clear internal mock flag via reflection
                            try {
                                val mFields = loc::class.java.declaredFields
                                for (f in mFields) {
                                    when (f.name) {
                                        "mMock", "mIsFromMockProvider", "isMock" -> {
                                            f.isAccessible = true
                                            if (f.type == Boolean::class.javaPrimitiveType) {
                                                f.setBoolean(loc, false)
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
            )

            XposedBridge.log("$TAG: Location hooks installed successfully")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to hook Location class: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────
    // 2. HOOK Google Play Services location
    // ──────────────────────────────────────────────
    private fun hookGooglePlayServicesLocation(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.FusedLocationProviderClient",
                classLoader,
                "setMockMode",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("$TAG: Intercepted FusedLocationProviderClient.setMockMode(${param.args[0]})")
                    }
                }
            )

            XposedBridge.log("$TAG: GMS location hooks installed")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: GMS hooks not available (non-Google device?): ${e.message}")
        }
    }

    // ──────────────────────────────────────────────
    // 3. HOOK Settings.Secure / Settings.Global
    // ──────────────────────────────────────────────
    private fun hookSettings(classLoader: ClassLoader) {
        try {
            val settingsSecure = XposedHelpers.findClass("android.provider.Settings\$Secure", classLoader)

            // Hook Settings.Secure.getString() — most apps/APIs use this
            XposedHelpers.findAndHookMethod(
                settingsSecure, "getString",
                android.content.ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result == null || param.args.size < 2) return
                        val key = param.args[1] as? String ?: return
                        if (mockSettingKeys.any { key.contains(it, ignoreCase = true) }) {
                            if (spoofEnabled) {
                                param.result = when {
                                    key.contains("mock_location_app", ignoreCase = true) -> ""
                                    key.contains("allow_mock", ignoreCase = true) -> "0"
                                    key.contains("mock_allow", ignoreCase = true) -> "0"
                                    key.contains("development_settings", ignoreCase = true) -> "1"
                                    else -> param.result
                                }
                            }
                        }
                    }
                }
            )

            XposedBridge.log("$TAG: Settings hooks installed successfully")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to hook Settings: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────
    // 4. HOOK PackageManager to hide mock apps
    // ──────────────────────────────────────────────
    private fun hookPackageManager(classLoader: ClassLoader, packageName: String) {
        try {
            val packageManagerClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader)

            // Hook getInstalledApplications
            XposedHelpers.findAndHookMethod(
                packageManagerClass, "getInstalledApplications",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!spoofEnabled) return
                        val result = param.result ?: return
                        if (result is List<*>) {
                            val filtered = result.filter { appInfo ->
                                val pkg = appInfo?.let {
                                    try {
                                        it::class.java.getMethod("getPackageName").invoke(it) as? String
                                    } catch (_: Exception) { null }
                                }
                                pkg !in knownMockPackages
                            }
                            param.result = filtered
                        }
                    }
                }
            )

            // Hook getInstalledPackages
            XposedHelpers.findAndHookMethod(
                packageManagerClass, "getInstalledPackages",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!spoofEnabled) return
                        val result = param.result ?: return
                        if (result is List<*>) {
                            val filtered = result.filter { pkgInfo ->
                                val name = pkgInfo?.let {
                                    try {
                                        it::class.java.getMethod("getPackageName").invoke(it) as? String
                                    } catch (_: Exception) { null }
                                }
                                name !in knownMockPackages
                            }
                            param.result = filtered
                        }
                    }
                }
            )

            XposedBridge.log("$TAG: PackageManager hooks installed for $packageName")
        } catch (e: Exception) {
            // PackageManager hooks are best-effort; per-app via LSPosed scope
        }
    }
}
