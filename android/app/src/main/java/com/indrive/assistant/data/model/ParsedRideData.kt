package com.indrive.assistant.data.model

import java.util.UUID

data class ParsedRideData(
    val id: String = UUID.randomUUID().toString(),
    val passengerName: String = "عميل إن درايف",
    val passengerRating: String = "",
    val fare: Double,
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    val counterBids: List<Int> = emptyList(),
    val screenBounds: android.graphics.Rect? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalDistanceKm: Double
        get() = pickupDistanceKm + tripDistanceKm

    val fingerprint: String
        get() = "${passengerName.trim()}_${fare.toInt()}_${String.format("%.1f", pickupDistanceKm)}_${String.format("%.1f", tripDistanceKm)}"
}
