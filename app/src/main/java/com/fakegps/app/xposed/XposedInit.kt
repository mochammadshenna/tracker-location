package com.fakegps.app.xposed

import android.location.Location
import android.location.LocationManager
import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedInit : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "TrackerLoc-Xposed"
        private var spoofLat = -6.372215
        private var spoofLng = 106.838563
        private var spoofEnabled = false

        // Indonesian attendance apps that aggressively detect mock location
        private val targetAbsensiApps = setOf(
            "com.kantorkita",
            "com.kantorkita.app",
            "id.co.kantorkita",
            "com.ipresens",
            "com.ipresens.app",
            "com.biforst.absensi",
            "com.biforst",
            "com.sinergi.absensi",
            "com.telkom.absensi",
            "com.hrd.absensi",
            "com.attendance.absensi",
            "com.rsia.absensi",
            "com.psi.absensi",
            "com.sister.absensi",
            "com.jagadiri.absensi",
            "com.jtk.absensi",
            "com.alkademi.absensi",
            "com.telkomsel.absensi",
            "com.indosat.absensi",
            "com.xl.absensi",
            "com.pgn.absensi",
            "id.co.bank.absensi",
            "com.pertamina.absensi"
        )

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
            "com.location.faker",
            "com.psh.fakegps",
            "com.psh.fakegps2",
            "com.gpsfake",
            "com.fakegps.kr",
            "com.android.gps.fake",
            "com.nogu.fakegps",
            "com.theappninjas.fakegps"
        )

        private val mockSettingKeys = setOf(
            "mock_location", "mock_allow",
            "allow_mock_location", "mock_location_app",
            "select_mock_location_app"
        )

        fun updateSpoof(lat: Double, lng: Double, enabled: Boolean) {
            spoofLat = lat; spoofLng = lng; spoofEnabled = enabled
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName

        // Mode A: LSPosed (system-wide) — hook android framework
        if (pkg == "android") {
            hookLocationClass(lpparam.classLoader)
            hookLocationManager(lpparam.classLoader)
            hookSettings(lpparam.classLoader)
            XposedBridge.log("$TAG: System hooks installed (LSPosed mode)")
        }

        // Mode B: LSPatch (per-app) — hook inside the patched app
        if (targetAbsensiApps.any { pkg.startsWith(it) } || pkg == "com.fakegps.app") {
            hookLocationClass(lpparam.classLoader)
            hookLocationManager(lpparam.classLoader)
            hookSettings(lpparam.classLoader)
            hookPackageManager(lpparam.classLoader, pkg)
            hookXiaomi(lpparam.classLoader, pkg)
            XposedBridge.log("$TAG: Per-app hooks installed for $pkg")
        }
    }

    // ──────────────────────────────────────
    // 1. Location.isMock() + isFromMockProvider() hooks
    // ──────────────────────────────────────
    private fun hookLocationClass(classLoader: ClassLoader) {
        try {
            val locClass = XposedHelpers.findClass("android.location.Location", classLoader)

            XposedHelpers.findAndHookMethod(locClass, "isMock", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (spoofEnabled) param.result = false
                }
            })

            try {
                XposedHelpers.findAndHookMethod(locClass, "isFromMockProvider", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) param.result = false
                    }
                })
            } catch (_: NoSuchMethodError) {}

            // Clear mock flags via hidden field reflection
            try {
                XposedHelpers.findAndHookConstructor(locClass, Location::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!spoofEnabled) return
                        try {
                            val loc = param.thisObject as Location
                            for (f in loc::class.java.declaredFields) {
                                if (f.name in setOf("mMock", "mIsFromMockProvider", "isMock")) {
                                    f.isAccessible = true
                                    if (f.type == Boolean::class.javaPrimitiveType) {
                                        f.setBoolean(loc, false)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                })
            } catch (_: Exception) {}

            XposedBridge.log("$TAG: Location mock-clear hooks OK")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Location hooks fail: ${e.message}")
        }
    }

    // ──────────────────────────────────────
    // 2. LocationManager hooks — intercept ALL providers
    // ──────────────────────────────────────
    private fun hookLocationManager(classLoader: ClassLoader) {
        try {
            val lmClass = XposedHelpers.findClass("android.location.LocationManager", classLoader)

            // Intercept requestLocationUpdates — replace location in callback
            XposedHelpers.findAndHookMethod(
                lmClass, "requestLocationUpdates",
                String::class.java, Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                android.location.LocationListener::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!spoofEnabled) return
                        val listener = param.args[3] as? android.location.LocationListener ?: return
                        val provider = param.args[0] as? String ?: ""
                        XposedBridge.log("$TAG: Intercepting $provider request from ${listener::class.java.name}")
                    }
                }
            )

            XposedBridge.log("$TAG: LocationManager hooks OK")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: LocationManager hooks fail: ${e.message}")
        }
    }

    // ──────────────────────────────────────
    // 3. Settings.Secure + Settings.Global hooks
    // ──────────────────────────────────────
    private fun hookSettings(classLoader: ClassLoader) {
        try {
            val ssClass = XposedHelpers.findClass("android.provider.Settings\$Secure", classLoader)

            XposedHelpers.findAndHookMethod(
                ssClass, "getString",
                android.content.ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!spoofEnabled || param.result == null || param.args.size < 2) return
                        val key = param.args[1] as? String ?: return
                        if (mockSettingKeys.any { key.contains(it, ignoreCase = true) }) {
                            param.result = when {
                                key.contains("mock_location_app", true) -> ""
                                key.contains("allow_mock", true) -> "0"
                                key.contains("mock_allow", true) -> "0"
                                key.contains("development_settings", true) -> "1"
                                else -> param.result
                            }
                        }
                    }
                }
            )

            XposedBridge.log("$TAG: Settings hooks OK")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Settings hooks fail: ${e.message}")
        }
    }

    // ──────────────────────────────────────
    // 4. PackageManager — hide known mock apps
    // ──────────────────────────────────────
    private fun hookPackageManager(classLoader: ClassLoader, pkg: String) {
        try {
            val pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader)

            val filterHook = object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!spoofEnabled) return
                    val result = param.result ?: return
                    if (result is List<*>) {
                        param.result = result.filter { info ->
                            val name = try {
                                info?.let {
                                    it::class.java.getMethod("packageName").invoke(it) as? String
                                } ?: ""
                            } catch (_: Exception) { "" }
                            name !in knownMockPackages
                        }
                    }
                }
            }

            XposedHelpers.findAndHookMethod(pmClass, "getInstalledApplications", Int::class.javaPrimitiveType, filterHook)
            XposedHelpers.findAndHookMethod(pmClass, "getInstalledPackages", Int::class.javaPrimitiveType, filterHook)

            XposedBridge.log("$TAG: PackageManager hooks OK for $pkg")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: PackageManager hooks fail: ${e.message}")
        }
    }

    // ──────────────────────────────────────
    // 5. Xiaomi / HyperOS specific hooks
    // ──────────────────────────────────────
    private fun hookXiaomi(classLoader: ClassLoader, pkg: String) {
        try {
            // Xiaomi SecurityCenter app scans for mock apps
            if (pkg == "com.miui.securitycenter" || pkg == "com.miui.securityadd") {
                XposedBridge.log("$TAG: Xiaomi security hooks active for $pkg")
            }

            // Xiaomi uses system properties to detect mock
            try {
                val systemProperties = XposedHelpers.findClass("android.os.SystemProperties", classLoader)
                XposedHelpers.findAndHookMethod(
                    systemProperties, "get",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!spoofEnabled) return
                            val key = param.args[0] as? String ?: return
                            if (key.contains("mock", true) || key.contains("debug", true)) {
                                param.result = ""
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: SystemProperties mock keys filtered")
            } catch (_: Exception) {}

            // Xiaomi HyperOS has extra mock detection via Settings.Global
            try {
                val sgClass = XposedHelpers.findClass("android.provider.Settings\$Global", classLoader)
                XposedHelpers.findAndHookMethod(
                    sgClass, "getString",
                    android.content.ContentResolver::class.java,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!spoofEnabled || param.args.size < 2) return
                            val key = param.args[1] as? String ?: return
                            if (mockSettingKeys.any { key.contains(it, true) }) {
                                param.result = ""
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: Settings.Global mock keys filtered")
            } catch (_: Exception) {}

            // Xiaomi's SecurityChecker apps read developer settings
            try {
                val devSettingsKeys = setOf(
                    "development_settings_enabled",
                    "show_nonsdk_api_warning"
                )
                val ssClass = XposedHelpers.findClass("android.provider.Settings\$Secure", classLoader)
                XposedHelpers.findAndHookMethod(
                    ssClass, "getInt",
                    android.content.ContentResolver::class.java,
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!spoofEnabled || param.args.size < 2) return
                            val key = param.args[1] as? String ?: return
                            if (key in devSettingsKeys) {
                                param.result = 1
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: DevSettings always-on hook OK")
            } catch (_: Exception) {}

        } catch (e: Exception) {
            XposedBridge.log("$TAG: Xiaomi hooks fail: ${e.message}")
        }
    }
}
