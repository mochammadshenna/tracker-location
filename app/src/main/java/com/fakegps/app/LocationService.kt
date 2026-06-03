package com.fakegps.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var isMocking = false
    private var targetLat = 0.0
    private var targetLng = 0.0

    companion object {
        private const val NOTIF_ID = 101
        private const val CHANNEL_ID = "mock_channel"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Buat Notification Channel untuk Android 8.0 (Oreo) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Mock Location Status", 
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan status pemalsuan lokasi secara real-time"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ambil data koordinat, default mengarah ke Monas jika null
        targetLat = intent?.getDoubleExtra("LAT", -6.175392) ?: -6.175392
        targetLng = intent?.getDoubleExtra("LNG", 106.827153) ?: 106.827153

        // Notifikasi awal saat service baru hidup
        val initialNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 Menginisiasi Mesin Fake GPS...")
            .setContentText("Mempersiapkan injeksi koordinat sistem.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIF_ID, initialNotification)

        if (!isMocking) {
            isMocking = true
            
            // Panggil isolasi sensor jika menggunakan kelas SensorBypassManager
            // SensorBypassManager.disableWiFiScanning(this)
            
            try {
                // Beri tahu kernel bahwa kita mengambil alih location provider
                fusedLocationClient.setMockMode(true)
                // Mulai siklus injeksi koordinat
                handler.post(mockRunnable)
            } catch (e: SecurityException) {
                Log.e("LocationService", "Akses Ditolak: Aplikasi belum dipilih di Developer Options")
                stopSelf()
            }
        }
        
        // Memastikan service akan di-restart oleh OS jika terbunuh karena kehabisan memori
        return START_STICKY
    }

    private val mockRunnable = object : Runnable {
        override fun run() {
            if (!isMocking) return
            
            // 1. JITTER ALGORITHM: Menambahkan noise mikroskopis (sekitar 1-2 meter)
            // Algoritma ini memecah deteksi pola "koordinat statis" dari aplikasi keamanan
            val jitterLat = targetLat + (Math.random() - 0.5) * 0.00002
            val jitterLng = targetLng + (Math.random() - 0.5) * 0.00002

            // 2. PEMBENTUKAN OBJEK LOKASI PALSU
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = jitterLat
                longitude = jitterLng
                altitude = 15.0 + (Math.random() * 3.0) // Fluktuasi elevasi
                accuracy = 3.0f + Math.random().toFloat() // Akurasi tinggi (HDOP bagus)
                time = System.currentTimeMillis()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
            }
            
            // 3. INJEKSI KE SISTEM & UPDATE UI
            try {
                fusedLocationClient.setMockLocation(location)
                
                // Update Notifikasi agar UI real-time tanpa membuat HP bergetar terus-menerus
                val updatedNotif = NotificationCompat.Builder(this@LocationService, CHANNEL_ID)
                    .setContentTitle("📍 Pemalsuan Lokasi Aktif")
                    .setContentText("Tx: ${String.format("%.6f", jitterLat)}, ${String.format("%.6f", jitterLng)}")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .build()
                    
                notificationManager.notify(NOTIF_ID, updatedNotif)
                
            } catch (e: Exception) { 
                Log.e("LocationService", "Terjadi kegagalan transmisi lokasi: ${e.message}")
            }
            
            // 4. REKURSI: Loop dieksekusi setiap 1 detik (1000ms) menyerupai satelit 1Hz
            handler.postDelayed(this, 1000)
        }
    }

    override fun onDestroy() {
        isMocking = false
        // Sangat penting: kembalikan kendali ke satelit fisik agar sistem tidak error
        try { 
            fusedLocationClient.setMockMode(false) 
        } catch (e: Exception) {
            Log.e("LocationService", "Gagal melepaskan Mock Mode.")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}