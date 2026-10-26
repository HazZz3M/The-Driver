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
    val currency: String = "ج.م",
    // No zones until the driver picks them (mirrors driver_settings.dart).
    val favoriteZones: List<String> = emptyList(),
    val blacklistedZones: List<String> = emptyList(),

    // Auto-Accept Automation
    val autoAcceptEnabled: Boolean = false,
    val autoAcceptMinProfitPerKm: Double = 10.0,
    val autoAcceptMinNetProfit: Double = 25.0,
    val autoAcceptMaxPickupKm: Double = 3.5,
    val autoAcceptOnlyFavoriteZones: Boolean = false,
    val autoAcceptRejectBlacklisted: Boolean = true,
    val autoAcceptDelaySeconds: Double = 1.5,

    // Pickup pricing (mirrors driver_settings.dart; see FareCalculatorEngine.fairPrice)
    val pickupPricingEnabled: Boolean = true,
    val pickupProfitPerKm: Double = 3.0,
    /** Share of the inDrive commission counted on pickup km, in %. */
    val pickupCommissionSharePercent: Double = 50.0,
    /** When pickup pricing applies (no rule on = always); see FareCalculatorEngine.pickupRuleApplies. */
    val pickupRuleLongerThanTrip: Boolean = false,
    val pickupRuleOverKm: Boolean = false,
    val pickupRuleThresholdKm: Double = 2.0,
    val pickupRuleMatchAll: Boolean = false,
    /** Pickup km pricing when the rules don't hold: COST_ONLY | FREE | AS_TRIP. */
    val pickupShortMode: String = "COST_ONLY",

    /** Auto-accept: when several requests qualify, take the best one (ranked by [autoAcceptRankBy]). */
    val autoAcceptPickBest: Boolean = false,
    /** PROFIT_PER_KM | NET_PROFIT | MARGIN */
    val autoAcceptRankBy: String = "PROFIT_PER_KM",

    /** Auto-hide: swipe away losing and/or blocked (zone or passenger) request cards. */
    val autoDismissLoss: Boolean = false,
    val autoDismissBlocked: Boolean = false,
    /** RIGHT | LEFT */
    val autoDismissDirection: String = "RIGHT",
    val autoDismissDelaySeconds: Double = 2.0,

    // Passenger lists (matched by the name shown on the card)
    val favoritePassengers: List<String> = emptyList(),
    val blacklistedPassengers: List<String> = emptyList()
) {
    fun isFavoritePassenger(name: String?): Boolean = FareCalculatorEngine.matchPassenger(name, favoritePassengers) != null

    fun isBlacklistedPassenger(name: String?): Boolean = FareCalculatorEngine.matchPassenger(name, blacklistedPassengers) != null

    fun matchedFavorite(zone: String?): String? = FareCalculatorEngine.matchZone(zone, favoriteZones)

    fun matchedBlacklist(zone: String?): String? = FareCalculatorEngine.matchZone(zone, blacklistedZones)

    fun isFavorite(zone: String?): Boolean = matchedFavorite(zone) != null

    fun isBlacklisted(zone: String?): Boolean = matchedBlacklist(zone) != null
}
