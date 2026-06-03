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
            name = "Work",
            latitude = -6.372215,
            longitude = 106.838563,
            altitude = 15.0,
            accuracy = 5.0f,
            description = "Depok, Indonesia"
        ),
        LocationPreset(
            name = "Home",
            latitude = -6.234567,
            longitude = 106.789012,
            altitude = 30.0,
            accuracy = 8.0f,
            description = "Jakarta Selatan"
        ),
        LocationPreset(
            name = "Office",
            latitude = -6.175110,
            longitude = 106.865036,
            altitude = 50.0,
            accuracy = 3.0f,
            description = "SCBD, Jakarta Pusat"
        ),
        LocationPreset(
            name = "Cafe",
            latitude = -6.258278,
            longitude = 106.810890,
            altitude = 10.0,
            accuracy = 6.0f,
            description = "Kemang, Jakarta"
        ),
        LocationPreset(
            name = "Gym",
            latitude = -6.224681,
            longitude = 106.803129,
            altitude = 8.0,
            accuracy = 4.0f,
            description = "Senayan, Jakarta"
        )
    )
}
