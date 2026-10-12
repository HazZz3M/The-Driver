package com.indrive.assistant.data.model

import com.indrive.assistant.engine.FareCalculatorEngine

data class DriverSettings(
    val fuelConsumptionLPer100Km: Double = 8.5,
    val fuelPricePerLiter: Double = 15.25, // Default Egyptian 92 Octane
    val inDriveCommissionPercent: Double = 10.0, // inDrive average fee (~9.5 - 10.5%)
    val wearAndTearPerKm: Double = 1.2, // Tires, depreciation, oil in EGP
    val targetDailyEarnings: Double = 1500.0,
    val minProfitPerKm: Double = 8.0,
    /** Trip length assumed when a request card doesn't show it (inDrive list cards show pickup only). */
    val assumedTripKm: Double = 4.0,
    val currency: String = "EGP",
    val favoriteZones: List<String> = listOf("المعادي", "التجمع", "مصر الجديدة", "الدقي", "الشيخ زايد"),
    val blacklistedZones: List<String> = listOf("شبرا الخيمة", "حلوان", "المرج"),

    // Auto-Accept Automation
    val autoAcceptEnabled: Boolean = false,
    val autoAcceptMinProfitPerKm: Double = 10.0,
    val autoAcceptMinNetProfit: Double = 25.0,
    val autoAcceptMaxPickupKm: Double = 3.5,
    val autoAcceptOnlyFavoriteZones: Boolean = false,
    val autoAcceptRejectBlacklisted: Boolean = true,
    val autoAcceptDelaySeconds: Double = 1.5
) {
    fun matchedFavorite(zone: String?): String? = FareCalculatorEngine.matchZone(zone, favoriteZones)

    fun matchedBlacklist(zone: String?): String? = FareCalculatorEngine.matchZone(zone, blacklistedZones)

    fun isFavorite(zone: String?): Boolean = matchedFavorite(zone) != null

    fun isBlacklisted(zone: String?): Boolean = matchedBlacklist(zone) != null
}
