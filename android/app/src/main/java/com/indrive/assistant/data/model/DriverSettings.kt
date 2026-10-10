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
    val blacklistedZones: List<String> = listOf("شبرا الخيمة", "حلوان", "المرج")
)
