package com.indrive.assistant.data.model

data class ParsedRideData(
    val fare: Double,
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    val counterBids: List<Int> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalDistanceKm: Double
        get() = pickupDistanceKm + tripDistanceKm
}
