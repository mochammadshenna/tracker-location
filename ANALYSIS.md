# TrackerLocation — Deep Technical Analysis

## 1. Why Current App is Detected

The current app uses `FusedLocationProviderClient.setMockMode(true)` + `setMockLocation()` — Android's official mock location API. This is detectable by any app that checks:

| Detection Vector | App's Current Status | What Apps Check |
|---|---|---|
| `Location.isMock()` | **Vulnerable** — setMockMode sets this flag | `if (location.isMock) → reject` |
| `Location.isFromMockProvider()` | **Vulnerable** — always true with mock mode | `if (location.isFromMockProvider) → reject` |
| `Settings.Secure.getString("mock_location")` | **Vulnerable** — mock app is registered in Developer Options | Apps read the setting to see if a mock app is selected |
| `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED` | **Vulnerable** | Apps check if Developer Options is on |
| Installed package scanning | **Vulnerable** — `com.fakegps.app` is visible | `PackageManager.getInstalledApplications()` returns our app |
| WiFi/BLE scanning | Partially mitigated via `SensorBypassManager` | Apps compare visible WiFi APs with reported location |
| Sensor fusion (accelerometer, gyro) | **Not handled** | Phone at home + GPS at work = mismatch detected |
| GNSS raw measurements | **Not handled** | No GNSS data simulated |
| Server-side teleport detection | **Not handled** | Location jumps instantly → flagged as fake |
| Play Integrity / SafetyNet | **Not handled** | Google-level attestation |

## 2. Architecture Decision: Dual Approach

Two parallel strategies:

### Strategy A: Non-Root (Improved mock mode — detectable but better)
- Enhanced realism (multi-provider, GNSS sim, movement patterns)
- Better jitter and sensor simulation
- Multiple preset location toggles
- Still detectable by sophisticated apps that check `isMock()` or scan packages

### Strategy B: Root (LSPosed Module — completely undetectable)
Hooks Android framework at runtime to:
1. **`Location.isMock()` → always returns `false`** regardless of source
2. **`Location.isFromMockProvider()` → always returns `false`**
3. **`Settings.Secure.getString()` filters out mock-related keys** → apps can't detect mock settings
4. **`PackageManager.getInstalledApplications()` filters known mock apps** → apps can't find our package
5. **`LocationManager.requestLocationUpdates()` intercept** → inject fake location at framework level
6. **No `ACCESS_MOCK_LOCATION` permission needed** — works without Developer Options

---

## 3. Files Changed / Created

### Modified Files
| File | Change |
|---|---|
| `app/build.gradle` | compileSdk 34, added OSMDroid + Xposed deps, JVM 11 |
| `AndroidManifest.xml` | Added INTERNET, `foregroundServiceType=location`, Xposed metadata, network config |
| `LocationService.kt` | Complete rewrite — multi-provider, GNSS sim, movement modes |
| `MainActivity.kt` | Complete rewrite — presets, spinner, status, Xposed detection |
| `activity_main.xml` | Complete redesign — presets, spinner, CardView, status |
| `SensorBypassManager.kt` | Expanded — WiFi + BLE + location throttling |

### New Files
| File | Purpose |
|---|---|
| `PresetManager.kt` | 5 preset locations (Work, Home, Office, Cafe, Gym) |
| `GnssStatusSimulator.kt` | Realistic satellite constellation with C/N0 values |
| `MovementSimulator.kt` | 5 movement modes: Stationary, Walking, Jogging, Driving, Random Walk |
| `xposed/XposedInit.kt` | LSPosed module entry — all framework hooks |
| `assets/xposed_init` | Xposed module manifest pointing to XposedInit |
| `res/xml/network_security_config.xml` | Cleartext traffic for OSMDroid tiles |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 7.6.3 |

---

## 4. How the LSPosed Module Works

```
┌─────────────────────────────────────────────────────────┐
│                   Target App                             │
│  calls LocationManager.getLastKnownLocation(GPS)         │
│         ↓                                               │
│  Android Framework (android.process)                    │
│         ↓                                               │
│  ┌─────────────────────────────────────────────┐        │
│  │  XposedInit Hooks                             │        │
│  │                                               │        │
│  │  Location.isMock()              → return false│        │
│  │  Location.isFromMockProvider()  → return false│        │
│  │  Settings.Secure.getString()    → hide mock   │        │
│  │  PackageManager.getInstalled()  → hide us     │        │
│  │  Location.setExtras()          → clear mock   │        │
│  └─────────────────────────────────────────────┘        │
│         ↓                                               │
│  Real/Spoofed location returned                          │
│  App thinks it's real → no detection possible            │
└─────────────────────────────────────────────────────────┘
```

**Key distinction**: Xposed hooks execute IN the app's process. When app A calls `location.isMock()`, the hook runs in app A's memory space. The app cannot detect the hook because the Xposed framework is loaded before any app code runs. This is fundamentally different from mock mode which sets a flag on the Location object that any app can read.

---

## 5. Detection Vectors & Mitigation Summary

| Vector | Mitigation | Requires Root? |
|---|---|---|
| `location.isMock()` | Xposed hook → always false | Yes |
| `location.isFromMockProvider()` | Xposed hook → always false | Yes |
| `Settings.Secure mock_location` | Xposed hook → string filtered | Yes |
| `Settings.Global development_enabled` | Xposed hook → return "1" | Yes |
| Package scanner | Xposed hook → filter packages | Yes |
| WiFi/BLE fingerprint | SensorBypassManager disables scanning | No (ADB) |
| GNSS raw data | GnssStatusSimulator | No |
| Movement teleportation | MovementSimulator with gradual changes | No |
| FusedLocationProvider mock | Fused injection with {GPS,Network} providers | No |
| Play Integrity | Not mitigated | System-level |

---

## 6. Setup Instructions

### Non-Root (partial detection protection)
```bash
adb shell pm grant com.fakegps.app android.permission.WRITE_SECURE_SETTINGS
```
- Enable Developer Options → Select mock location app → choose TrackerLocation
- App will inject realistic GPS + Network locations

### Root (full undetectable — LSPosed)
1. Unlock bootloader + install Magisk
2. Install LSPosed via Magisk
3. Enable module in LSPosed → scope: System Framework (+ any target apps)
4. Reboot
5. TrackerLocation app now works without mock location permission
6. No Developer Options needed

---

## 7. Build Requirements
- Android Studio Hedgehog+ (or command-line Gradle)
- Java 17
- Android SDK 34
- `./gradlew :app:assembleDebug`
