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
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager

    private var handlerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var isMocking = false

    private var targetLat = -6.372215
    private var targetLng = 106.838563
    private var targetAlt = 15.0
    private var targetAccuracy = 5.0f

    private var movementMode = MovementMode.STATIONARY
    private var currentMovementState: MovementState? = null
    private var tickCounter = 0

    companion object {
        private const val TAG = "LocationSvc"
        private const val NOTIF_ID = 101
        private const val CHANNEL_ID = "mock_channel"
        private const val TICK_MS = 1500L
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Mock Location Status",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        handlerThread = HandlerThread("LocationWorker")
        handlerThread?.start()
        workerHandler = Handler(handlerThread!!.looper)

        GnssStatusSimulator.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetLat = intent?.getDoubleExtra("LAT", targetLat) ?: targetLat
        targetLng = intent?.getDoubleExtra("LNG", targetLng) ?: targetLng
        targetAlt = intent?.getDoubleExtra("ALT", targetAlt) ?: targetAlt
        targetAccuracy = intent?.getFloatExtra("ACC", targetAccuracy) ?: targetAccuracy

        val modeOrdinal = intent?.getIntExtra("MODE", MovementMode.STATIONARY.ordinal)
            ?: MovementMode.STATIONARY.ordinal
        movementMode = MovementMode.values().getOrElse(modeOrdinal) { MovementMode.STATIONARY }

        currentMovementState = null

        startForeground(NOTIF_ID, buildNotification(
            "Initializing Location Injection...",
            "Preparing multi-provider spoofing engine"
        ))

        if (!isMocking) {
            isMocking = true
            SensorBypassManager.disableWiFiScanning(this)

            try {
                fusedLocationClient.setMockMode(true)
            } catch (e: SecurityException) {
                Log.e(TAG, "Mock mode not granted", e)
            }

            startInjecting()
        }

        return START_STICKY
    }

    private fun startInjecting() {
        workerHandler?.post(object : Runnable {
            override fun run() {
                if (!isMocking) return
                injectLocation()
                workerHandler?.postDelayed(this, TICK_MS)
            }
        })
    }

    private fun injectLocation() {
        tickCounter++
        GnssStatusSimulator.tick()

        val movement = MovementSimulator.simulate(
            baseLat = targetLat,
            baseLng = targetLng,
            mode = movementMode,
            tickMs = TICK_MS,
            state = currentMovementState
        )
        currentMovementState = movement

        val moveLat = movement.lat
        val moveLng = movement.lng
        val moveSpeed = movement.speed
        val moveBearing = movement.bearing

        val accuracyVariation = if (movementMode == MovementMode.STATIONARY) {
            targetAccuracy + (Math.random().toFloat() * 2f)
        } else {
            (targetAccuracy * 1.5f) + (Math.random().toFloat() * 4f)
        }

        val altitudeVariation = targetAlt + (Math.random() - 0.5) * 2.0

        val now = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        // Inject via FusedLocationProviderClient (GPS provider)
        val gpsLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = moveLat
            longitude = moveLng
            altitude = altitudeVariation
            accuracy = accuracyVariation
            speed = moveSpeed
            bearing = moveBearing
            time = now
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = elapsedNanos
            }
        }
        try {
            fusedLocationClient.setMockLocation(gpsLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Fused mock injection failed", e)
        }

        // Inject via LocationManager's TEST_PROVIDER (network fallback)
        try {
            val networkLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = moveLat
                longitude = moveLng
                accuracy = accuracyVariation * 3f
                time = now
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = elapsedNanos
                }
            }
            // Remove TEST_PROVIDER first if exists, then add it back to avoid conflicts
            try {
                locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            } catch (_: IllegalArgumentException) { }
            locationManager.addTestProvider(
                LocationManager.NETWORK_PROVIDER,
                false, false, false, false, true, true, true, 0, 5
            )
            locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, networkLocation)
        } catch (e: Exception) {
            // Network test provider may not work on all devices — that's OK
        }

        val satCount = GnssStatusSimulator.getVisibleSatelliteCount()
        val usedCount = GnssStatusSimulator.getUsedInFixCount()

        updateNotification(moveLat, moveLng, satCount, usedCount, movementMode.label)

        if (tickCounter % 60 == 0) {
            Log.d(TAG, "Injected: $moveLat, $moveLng | sats: $satCount/$usedCount | mode: ${movementMode.label}")
        }
    }

    private fun buildNotification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun updateNotification(lat: Double, lng: Double, sats: Int, used: Int, mode: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Spoofing Active")
            .setContentText("${String.format("%.6f", lat)}, ${String.format("%.6f", lng)} | $mode")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Lat: ${String.format("%.6f", lat)}\n" +
                             "Lng: ${String.format("%.6f", lng)}\n" +
                             "Mode: $mode\n" +
                             "Satellites: $sats (${used} used)\n" +
                             "Avg C/N0: ${String.format("%.1f", GnssStatusSimulator.getAverageCn0())} dB-Hz")
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
        notificationManager.notify(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        isMocking = false
        try {
            fusedLocationClient.setMockMode(false)
        } catch (_: Exception) { }
        try {
            locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { }
        handlerThread?.quitSafely()
        handlerThread = null
        workerHandler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
