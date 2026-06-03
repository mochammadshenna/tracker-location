package com.fakegps.app

data class LocationPreset(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 15.0,
    val accuracy: Float = 5.0f,
    val description: String = ""
)

object PresetManager {
    val presets = listOf(
        LocationPreset(
            name = "Kantor",
            latitude = -6.385384,
            longitude = 106.850242,
            altitude = 15.0,
            accuracy = 5.0f,
            description = "Depok, Indonesia"
        )
    )
}
