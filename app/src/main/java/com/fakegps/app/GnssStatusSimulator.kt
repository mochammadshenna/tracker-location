package com.fakegps.app

import android.location.GnssStatus
import android.os.Build
import java.util.Random

object GnssStatusSimulator {
    private val random = Random()
    private var satellites = mutableListOf<SatelliteInfo>()
    private var lastUpdateTime = 0L

    data class SatelliteInfo(
        val svid: Int,
        val constellationType: Int,
        val cn0: Float,
        val usedInFix: Boolean
    )

    fun initialize() {
        rebuildConstellation()
    }

    private fun rebuildConstellation() {
        satellites.clear()
        val constellations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intArrayOf(
                GnssStatus.CONSTELLATION_GPS,
                GnssStatus.CONSTELLATION_GLONASS,
                GnssStatus.CONSTELLATION_GALILEO,
                GnssStatus.CONSTELLATION_BEIDOU
            )
        } else {
            intArrayOf(0)
        }

        for (const in constellations) {
            val count = 4 + random.nextInt(6)
            for (i in 0 until count) {
                val svid = when (const) {
                    0 -> 1 + random.nextInt(31)
                    GnssStatus.CONSTELLATION_GPS -> 1 + random.nextInt(31)
                    GnssStatus.CONSTELLATION_GLONASS -> 65 + random.nextInt(24)
                    GnssStatus.CONSTELLATION_GALILEO -> 201 + random.nextInt(36)
                    GnssStatus.CONSTELLATION_BEIDOU -> 401 + random.nextInt(63)
                    else -> 1 + random.nextInt(31)
                }
                satellites.add(
                    SatelliteInfo(
                        svid = svid,
                        constellationType = const,
                        cn0 = 20f + random.nextFloat() * 25f,
                        usedInFix = random.nextFloat() > 0.15f
                    )
                )
            }
        }
    }

    fun getVisibleSatelliteCount(): Int = satellites.size

    fun getUsedInFixCount(): Int = satellites.count { it.usedInFix }

    fun getAverageCn0(): Float {
        if (satellites.isEmpty()) return 0f
        return satellites.map { it.cn0 }.average().toFloat()
    }

    fun getCn0ForSvid(svid: Int): Float {
        return satellites.find { it.svid == svid }?.cn0 ?: 0f
    }

    fun getConstellationType(svid: Int): Int {
        return satellites.find { it.svid == svid }?.constellationType ?: 0
    }

    fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > 10000) {
            satellites = satellites.map { sat ->
                sat.copy(
                    cn0 = (sat.cn0 + (random.nextFloat() - 0.5f) * 6f)
                        .coerceIn(15f, 50f),
                    usedInFix = random.nextFloat() > 0.1f
                )
            }.toMutableList()
            if (random.nextFloat() < 0.05f) {
                rebuildConstellation()
            }
            lastUpdateTime = now
        }
    }
}
