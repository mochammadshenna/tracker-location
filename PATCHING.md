# Panduan — Bypass Deteksi Absensi di Xiaomi

App ini jalan sebagai **mock location provider** + **modul LSPosed**.
Begitu dibuka, spoofing langsung aktif otomatis di lokasi Kantor.

---

## Method 1: Root + LSPosed (100% Aman)

### Step 1: Buka kunci bootloader Xiaomi

```
Settings → About → Tap MIUI version 7x → Developer mode
Settings → Additional settings → Developer options → Mi Unlock Status
→ Tambah akun → Download Mi Unlock Tool dari en.miui.com/unlock
→ Tunggu 7-14 hari → Buka kunci dengan Mi Unlock Tool
```

### Step 2: Install Magisk

```
fastboot flash recovery twrp.img
Reboot ke recovery
adb push Magisk-v*.zip /sdcard/
Install dari TWRP → Reboot
Install Magisk app → Verifikasi root
```

### Step 3: Install LSPosed

```
Download LSPosed dari GitHub
Magisk → Modules → Install from storage → pilih LSPosed-*.zip
Reboot → Buka LSPosed app
```

### Step 4: Aktifkan modul

```
LSPosed → Modules → Enable "TrackerLocation"
Scope yang di centang:
  ☑ System Framework (WAJIB)
  ☑ App absensi kamu
  ☑ Google Play Services (opsional)
Reboot
```

### Step 5: Selesai

```
Buka TrackerLocation → spoofing langsung jalan otomatis
Buka app absensi → lokasi kamu sudah di Kantor
→ isMock() = false
→ isFromMockProvider() = false
→ Settings aman
```

---

## Method 2: LSPatch (Coba Dulu — No Root)

Patch APK absensi langsung, nggak perlu unlock bootloader.

### Step 1: Install LSPatch Manager

File APK sudah ada di repo ini: **`LSPatch.apk`**
Buka file manager → tap `LSPatch.apk` → Install

### Step 2: Extract APK absensi

Install APK Extractor dari Play Store → Extract APK app absensi kamu

### Step 3: Patch dengan modul kita

```
Buka LSPatch → Create → pilih file APK absensi
Centang "Use module" → pilih TrackerLocation
→ Start patch → Install hasilnya
```

### Step 4: Jalankan

```
Buka TrackerLocation → spoofing otomatis jalan di Kantor
Buka app absensi → lokasi sudah termanipulasi
```

### Catatan

- ✅ Bisa dicoba langsung tanpa root
- ❌ Beberapa app deteksi perubahan signature APK
- Kalau app absensi error setelah di-patch → berarti LSPatch nggak work, lanjut ke LSPosed

---

## Method 3: Smali Patcher (Alternatif Root)

Kalau LSPosed bermasalah di HyperOS:

```
Di PC Windows:
1. Download Smali Patcher dari XDA
2. HP connect USB debugging
3. Pilih "Mock location fix"
4. Run → hasilnya Magisk module .zip
5. Transfer ke HP → Install di Magisk
6. Reboot
```

---

## Method 3: Mock Location Bawaan (Tanpa Root)

Jalan sebagai mock location app standar — **mudah dideteksi** absensi.

```
Settings → Developer options → Select mock location app
→ Pilih TrackerLocation
Buka app → spoofing otomatis jalan
```

Rentan terdeteksi. Hanya untuk testing.

---

## Cara Kerja

1. **Buka app** → spoofing otomatis aktif di koordinat Kantor
2. **Ganti mode pergerakan** → spoofing menyesuaikan otomatis
3. **Status "Aktif"** → lokasi sedang dimanipulasi
4. **Tekan Berhenti** → spoofing mati
