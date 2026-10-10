package com.indrive.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // FUEL, OIL_CHANGE, MAINTENANCE, CAR_WASH, OTHER
    val amount: Double,
    
    // Fuel Refill Specific Fields (تفويلة البنزين)
    val liters: Double? = null,
    val pricePerLiter: Double? = null,
    val kmSinceLastRefill: Double? = null,
    val consumptionLPer100Km: Double? = null, // (liters / km) * 100
    val fuelCostPerKm: Double? = null,        // amount / km

    // Engine Oil Specific Fields (غيار الزيت)
    val oilPricePerLiter: Double? = null,
    val oilVolumeMl: Int? = null,
    val oilLifespanKm: Double? = null,        // الزيت نزل بعد كام كم
    val oilCostPerKm: Double? = null,         // amount / oilLifespanKm

    val odometerKm: Double? = null,
    val description: String = ""
)

