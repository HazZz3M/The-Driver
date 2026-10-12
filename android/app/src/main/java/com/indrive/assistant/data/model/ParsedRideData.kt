package com.indrive.assistant.data.model

data class ParsedRideData(
    val passengerName: String = "",
    val passengerRating: String = "",
    val fare: Double,
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    /** Whether each distance was actually read from the card (false = placeholder value). */
    val pickupKnown: Boolean = true,
    val tripKnown: Boolean = true,
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    /** Place-like texts found on the card, offered in the HUD for 1-tap zone tagging. */
    val addressCandidates: List<String> = emptyList(),
    val counterBids: List<Int> = emptyList(),
    val screenBounds: android.graphics.Rect? = null,
    val anchorBounds: android.graphics.Rect? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Disambiguates two cards that would otherwise share the same identity. */
    val duplicateIndex: Int = 0
) {
    val totalDistanceKm: Double
        get() = pickupDistanceKm + tripDistanceKm

    /** At least one distance is a placeholder: profit is approximate. */
    val distanceEstimated: Boolean
        get() = !pickupKnown || !tripKnown

    /** No distance could be read at all: profit is meaningless. */
    val distanceUnknown: Boolean
        get() = !pickupKnown && !tripKnown

    /**
     * Identity of a card across re-scans. Deliberately excludes distances (which jitter between
     * parses) so a card keeps one popup instead of being destroyed and recreated.
     */
    val fingerprint: String
        get() = "${passengerName.trim()}|${pickupAddress.trim()}|${dropoffAddress.trim()}|${fare.toInt()}#$duplicateIndex"
}
