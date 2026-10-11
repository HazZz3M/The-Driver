package com.indrive.assistant.data.model

data class DriverSettings(
    val fuelConsumptionLPer100Km: Double = 8.5,
    val fuelPricePerLiter: Double = 15.0, // Default Egyptian 92 Octane (~15.00 EGP)
    val inDriveCommissionPercent: Double = 10.0, // inDrive average fee (~9.5 - 10.5%)
    val wearAndTearPerKm: Double = 1.2, // Tires, depreciation, oil in EGP
    val targetDailyEarnings: Double = 1500.0,
    val minProfitPerKm: Double = 8.0,
    val currency: String = "EGP",
    val favoriteZones: List<String> = listOf("المعادي", "التجمع", "مصر الجديدة", "الدقي", "الشيخ زايد"),
    val blacklistedZones: List<String> = listOf("شبرا الخيمة", "حلوان", "المرج"),

    // Auto-Accept & Auto-Bidding Automation
    val autoAcceptEnabled: Boolean = false,
    val autoAcceptMinProfitPerKm: Double = 10.0,
    val autoAcceptMinNetProfit: Double = 25.0,
    val autoAcceptMaxPickupKm: Double = 3.5,
    val autoAcceptOnlyFavoriteZones: Boolean = false,
    val autoAcceptIgnoreBlacklist: Boolean = true,
    val autoBidMode: String = "RECOMMENDED", // "RECOMMENDED" or "PASSENGER_FARE"
    val autoAcceptDelaySeconds: Double = 1.5
) {
    fun isFavorite(zone: String?): Boolean {
        if (zone.isNullOrBlank()) return false
        val clean = zone.trim().lowercase()
        return favoriteZones.any { it.isNotBlank() && clean.contains(it.trim().lowercase()) }
    }

    fun isBlacklisted(zone: String?): Boolean {
        if (zone.isNullOrBlank()) return false
        val clean = zone.trim().lowercase()
        return blacklistedZones.any { it.isNotBlank() && clean.contains(it.trim().lowercase()) }
    }
}
