# Patching Guide — Bypass Absensi Detection on Xiaomi

This project works as both a **normal app** and an **LSPosed/LSPatch module**.
Choose your path:

```
                    ┌──────────────────┐
                    │  Your Xiaomi     │
                    │  + Absensi App   │
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                              ▼
    ┌──────────────────┐         ┌──────────────────────┐
    │ LSPatch (No Root) │         │ LSPosed (Root via    │
    │ Fast — No wait    │         │ Magisk) Stronger     │
    │ 10 min setup      │         │ 7-14 day bootloader  │
    │                   │         │ wait needed          │
    │ May fail if app   │         │                      │
    │ detects modified  │         │ 100% undetectable    │
    │ APK signature     │         │ System-wide hooks    │
    └────────┬──────────┘         └──────────┬───────────┘
             │                               │
             ▼                               ▼
    Try this FIRST                   Do this while waiting
```

---

## Method 1: LSPatch (No Root — Try Today)

LSPatch patches ONLY your attendance app — no root, no bootloader unlock.

### Step 1: Extract your absensi APK

```bash
# Via ADB (requires one-time USB debug setup):
adb shell pm list packages | grep -i "absensi\|kantorkita\|ipresens"
adb shell pm path <nama.package>
adb pull <path_from_above> absensi.apk
```

Or use APK Extractor from Play Store.

### Step 2: Install LSPatch

```bash
# Download LSPatch manager:
# https://github.com/LSPosed/LSPatch/releases
adb install LSPatch-v*.apk
```

### Step 3: Patch the APK with our module

```bash
# Methods:
# A) LSPatch GUI:
#    Open LSPatch → Create → Pick APK → 
#    Enable "Use module" → select TrackerLocation
#    → Start patch

# B) Via ADB (more reliable):
#    Install LSPatch in "manager" mode first
#    Then patch with:
lspatch absensi.apk -m /path/to/tracker-location.apk
```

### Step 4: Install patched APK

```bash
adb install patched-absensi.apk
```

### Step 5: Start spoofing

```
1. Open TrackerLocation app
2. Toggle "Work" preset ON
3. Tap START SPOOFING
4. Open your absensi app → it receives clean location data
   → isMock() returns false
   → isFromMockProvider() returns false
   → Settings read as "mock not enabled"
```

### ⚠️ LSPatch Limitations

- **App signature changes** — some apps detect this via Play Integrity/SafetyNet
- If your absensi app refuses to open after patching, LSPatch won't work for it
- If it does work, you're done. No root needed.

---

## Method 2: Root + LSPosed (100% Reliable)

### Step 1: Start Xiaomi bootloader unlock NOW

```bash
# On Xiaomi:
# 1. Settings → About → Tap MIUI version 7x → Developer mode
# 2. Settings → Developer options → Mi Unlock Status → Add account
# 3. Download Mi Unlock Tool from en.miui.com/unlock
# 4. Wait 7-14 days (Xiaomi's requirement)
# 5. Unlock bootloader with Mi Unlock Tool
```

### Step 2: Install Magisk

```bash
# While waiting, download:
# - Magisk: https://github.com/topjohnwu/Magisk
# - LSPosed: https://github.com/LSPosed/LSPosed
# - Our module (already built)

# After unlock:
# 1. fastboot flash recovery twrp.img
# 2. Reboot to recovery
# 3. adb push Magisk-v*.zip /sdcard/
# 4. Install Magisk from TWRP
# 5. Reboot
# 6. Install Magisk app → Verify root
```

### Step 3: Install LSPosed

```bash
# In Magisk app:
# 1. Modules → Install from storage → LSPosed-*.zip
# 2. Reboot
# 3. Open LSPosed app
```

### Step 4: Enable our module

```bash
# In LSPosed:
# 1. Modules → Enable "TrackerLocation"
# 2. Scope:
#    ☑ System Framework (ALWAYS needed)
#    ☑ Your absensi app (per-app hooks)
#    ☑ Google Play Services (optional)
# 3. Reboot
```

### Step 5: Done

```
- No Developer Options mock app selection needed
- No ACCESS_MOCK_LOCATION permission
- ALL apps see isMock() = false
- Absensi app cannot detect anything
```

---

## Method 3: Smali Patcher (Alternative Root)

If LSPosed causes issues on HyperOS:

```bash
# On Windows PC:
# 1. Download Smali Patcher from XDA
# 2. Connect phone with USB debugging
# 3. Select "Mock location" fix
# 4. Run → generates Magisk module
# 5. Transfer .zip to phone → Install in Magisk
# 6. Reboot
```

Smali Patcher modifies `services.jar` at the smali level to permanently
remove mock location detection. Works alongside or instead of LSPosed.

---

## Detection: Testing if it works

Download "Mock Detection Test" from Play Store.
Or use this ADB command:

```bash
adb shell settings put global development_settings_enabled 1
adb shell settings secure mock_location 1
```

If our module is active, the absensi app will see these as "disabled"
because we intercept Settings reads and overwrite them.

---

## Quick Decision Flow

```
Can you wait 7-14 days for bootloader unlock?
  ├── YES → Start unlock process now.
  │         While waiting: try LSPatch.
  │         When unlock finishes: Magisk + LSPosed.
  │
  └── NO  → Try LSPatch only.
              ├── Works? → You're done.
              └── Fails? → You must root.
```
