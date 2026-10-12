package com.indrive.assistant.data.model

data class ParsedRideData(
    val passengerName: String = "",
    val passengerRating: String = "",
    val fare: Double,
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    /** True when at least one distance could not be read and a placeholder was used. */
    val distanceEstimated: Boolean = false,
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    val counterBids: List<Int> = emptyList(),
    val screenBounds: android.graphics.Rect? = null,
    val anchorBounds: android.graphics.Rect? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Disambiguates two cards that would otherwise share the same identity. */
    val duplicateIndex: Int = 0
) {
    val totalDistanceKm: Double
        get() = pickupDistanceKm + tripDistanceKm

    /**
     * Identity of a card across re-scans. Deliberately excludes distances (which jitter between
     * parses) so a card keeps one popup instead of being destroyed and recreated.
     */
    val fingerprint: String
        get() = "${passengerName.trim()}|${pickupAddress.trim()}|${dropoffAddress.trim()}|${fare.toInt()}#$duplicateIndex"
}
