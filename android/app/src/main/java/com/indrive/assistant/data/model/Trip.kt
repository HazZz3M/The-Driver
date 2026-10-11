package com.indrive.assistant.data.model

data class Trip(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    val estimatedMinutes: Int,
    val offeredFare: Double,
    val fuelCost: Double,
    val commissionCost: Double,
    val wearAndTearCost: Double,
    val netProfit: Double,
    val profitPerKm: Double,
    val status: String = "COMPLETED", // OFFERED, ACCEPTED, REJECTED, COMPLETED
    val pickupLocation: String = "",
    val dropoffLocation: String = "",
    val notes: String = ""
) {
    val totalDistanceKm: Double
        get() = pickupDistanceKm + tripDistanceKm
}
