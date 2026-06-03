package com.fakegps.app

import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

enum class MovementMode(val label: String) {
    STATIONARY("Diam — Tanpa Pergerakan"),
    WALKING("Jalan ~1.4 m/s"),
    JOGGING("Lari ~3 m/s"),
    DRIVING("Mobil ~10 m/s"),
    RANDOM_WALK("Acak — Jitter Natural")
}

data class MovementState(
    val lat: Double,
    val lng: Double,
    val speed: Float,
    val bearing: Float,
    val bearingDelta: Float = 0f
)

object MovementSimulator {
    private const val EARTH_RADIUS = 6371000.0
    private val random = Random()

    private var currentBearing = 0f
    private var bearingChangeTimer = 0

    fun simulate(
        baseLat: Double,
        baseLng: Double,
        mode: MovementMode,
        tickMs: Long = 1000L,
        state: MovementState? = null
    ): MovementState {
        return when (mode) {
            MovementMode.STATIONARY -> simulateStationary(baseLat, baseLng)
            MovementMode.WALKING -> simulateMovement(baseLat, baseLng, 1.4, tickMs)
            MovementMode.JOGGING -> simulateMovement(baseLat, baseLng, 3.0, tickMs)
            MovementMode.DRIVING -> simulateMovement(baseLat, baseLng, 10.0, tickMs)
            MovementMode.RANDOM_WALK -> simulateRandomWalk(baseLat, baseLng, state)
        }
    }

    private fun simulateStationary(baseLat: Double, baseLng: Double): MovementState {
        val jitterLat = (random.nextDouble() - 0.5) * 0.000004
        val jitterLng = (random.nextDouble() - 0.5) * 0.000004
        return MovementState(
            lat = baseLat + jitterLat,
            lng = baseLng + jitterLng,
            speed = 0f,
            bearing = currentBearing
        )
    }

    private fun simulateMovement(
        baseLat: Double, baseLng: Double,
        speedMs: Double, tickMs: Long
    ): MovementState {
        bearingChangeTimer++
        if (bearingChangeTimer > 5 + random.nextInt(10)) {
            currentBearing += (random.nextFloat() - 0.5f) * 30f
            currentBearing = ((currentBearing % 360) + 360) % 360
            bearingChangeTimer = 0
        }

        val distance = speedMs * (tickMs / 1000.0)
        val bearingRad = Math.toRadians(currentBearing.toDouble())

        val dLat = distance * cos(bearingRad) / EARTH_RADIUS
        val dLng = distance * sin(bearingRad) / (EARTH_RADIUS * cos(Math.toRadians(baseLat)))

        return MovementState(
            lat = baseLat + Math.toDegrees(dLat),
            lng = baseLng + Math.toDegrees(dLng),
            speed = speedMs.toFloat(),
            bearing = currentBearing
        )
    }

    private fun simulateRandomWalk(
        baseLat: Double, baseLng: Double,
        state: MovementState? = null
    ): MovementState {
        val rangeM = 50.0
        val rangeDeg = rangeM / EARTH_RADIUS * 180.0 / Math.PI

        val dLat = if (state != null) {
            (state.lat - baseLat) * 0.8 + (random.nextDouble() - 0.5) * rangeDeg * 0.2
        } else {
            (random.nextDouble() - 0.5) * rangeDeg
        }

        val dLng = if (state != null) {
            (state.lng - baseLng) * 0.8 + (random.nextDouble() - 0.5) * rangeDeg * 0.2
        } else {
            (random.nextDouble() - 0.5) * rangeDeg
        }

        val maxOffset = rangeDeg
        val lat = (baseLat + dLat.coerceIn(-maxOffset, maxOffset))
        val lng = (baseLng + dLng.coerceIn(-maxOffset, maxOffset))

        val speed = Math.sqrt(
            Math.pow((lat - baseLat) * EARTH_RADIUS * Math.PI / 180.0, 2.0) +
            Math.pow((lng - baseLng) * EARTH_RADIUS * Math.PI / 180.0 * cos(Math.toRadians(baseLat)), 2.0)
        ).toFloat()

        return MovementState(
            lat = lat, lng = lng,
            speed = speed,
            bearing = currentBearing
        )
    }
}
