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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Mock Location Status", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetLat = intent?.getDoubleExtra("LAT", -6.372215) ?: -6.372215
        targetLng = intent?.getDoubleExtra("LNG", 106.838563) ?: 106.838563

        val initialNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 Menginisiasi Mesin Fake GPS...")
            .setContentText("Mempersiapkan injeksi koordinat sistem.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIF_ID, initialNotification)

        if (!isMocking) {
            isMocking = true
            SensorBypassManager.disableWiFiScanning(this)
            try {
                fusedLocationClient.setMockMode(true)
                handler.post(mockRunnable)
            } catch (e: SecurityException) {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private val mockRunnable = object : Runnable {
        override fun run() {
            if (!isMocking) return
            
            // Jitter Algorithm (Variasi 1-2 Meter)
            val jitterLat = targetLat + (Math.random() - 0.5) * 0.00002
            val jitterLng = targetLng + (Math.random() - 0.5) * 0.00002

            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = jitterLat
                longitude = jitterLng
                altitude = 15.0 + (Math.random() * 3.0)
                accuracy = 3.0f + Math.random().toFloat()
                time = System.currentTimeMillis()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
            }
            
            try {
                fusedLocationClient.setMockLocation(location)
                
                // NOTIFIKASI DIUBAH DI SINI
                val updatedNotif = NotificationCompat.Builder(this@LocationService, CHANNEL_ID)
                    .setContentTitle("📍 Success Dedi")
                    .setContentText("Tx: ${String.format("%.6f", jitterLat)}, ${String.format("%.6f", jitterLng)}")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .build()
                    
                notificationManager.notify(NOTIF_ID, updatedNotif)
            } catch (e: Exception) { }
            
            handler.postDelayed(this, 1000)
        }
    }

    override fun onDestroy() {
        isMocking = false
        try { fusedLocationClient.setMockMode(false) } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}