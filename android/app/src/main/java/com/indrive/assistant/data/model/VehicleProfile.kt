package com.indrive.assistant.data.model

import java.util.UUID

data class VehicleProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g. "هيونداي فيرنا", "بي واي دي F3 (غاز)", "سكوتر بينيلي"
    val modelYear: String = "2020",
    val fuelType: String = "بنزين 92", // "بنزين 92", "بنزين 95", "بنزين 80", "غاز طبيعي / CNG"
    val fuelConsumptionLPer100Km: Double = 8.5,
    val fuelPricePerLiter: Double = 15.25,
    val wearAndTearPerKm: Double = 1.2,
    val inDriveCommissionPercent: Double = 10.0,
    val minProfitPerKm: Double = 8.0,
    val notes: String = ""
) {
    fun toDriverSettings(currency: String, targetDailyEarnings: Double, favoriteZones: List<String>, blacklistedZones: List<String>): DriverSettings {
        return DriverSettings(
            fuelConsumptionLPer100Km = fuelConsumptionLPer100Km,
            fuelPricePerLiter = fuelPricePerLiter,
            inDriveCommissionPercent = inDriveCommissionPercent,
            wearAndTearPerKm = wearAndTearPerKm,
            targetDailyEarnings = targetDailyEarnings,
            minProfitPerKm = minProfitPerKm,
            currency = currency,
            favoriteZones = favoriteZones,
            blacklistedZones = blacklistedZones
        )
    }
}
