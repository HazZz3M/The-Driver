package com.indrive.assistant.engine

import com.indrive.assistant.data.model.DriverSettings
import kotlin.math.ceil
import kotlin.math.max

enum class ProfitabilityLevel {
    LOSS_DANGER,      // Money-losing ride (costs > fare)
    POOR_MARGIN,      // Below target minimum profit per km
    GOOD_PROFIT,      // Meets driver target profit
    EXCELLENT_PROFIT  // High-yield profitable ride
}

data class SmartBidOption(
    val title: String,
    val suggestedFare: Double,
    val netProfit: Double,
    val profitPerKm: Double,
    val description: String,
    val tag: String
)

data class FareAnalysisResult(
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    val totalDistanceKm: Double,
    val estimatedMinutes: Int,
    val offeredFare: Double,
    
    // Cost breakdown
    val fuelCost: Double,
    val fuelLitersUsed: Double,
    val commissionCost: Double,
    val wearAndTearCost: Double,
    val totalOperatingCost: Double,
    
    // Profit outcomes
    val netProfit: Double,
    val profitMarginPercent: Double,
    val profitPerKm: Double,
    val profitPerHour: Double,
    
    // Evaluation
    val profitabilityLevel: ProfitabilityLevel,
    val statusMessage: String,
    val warningNote: String?,
    
    // Counter-Offer Recommendations
    val breakEvenFare: Double,
    val recommendedFare: Double,
    val aggressiveFare: Double,
    val smartBids: List<SmartBidOption>
)

object FareCalculatorEngine {

    fun analyze(
        pickupDistanceKm: Double,
        tripDistanceKm: Double,
        estimatedMinutes: Int,
        offeredFare: Double,
        settings: DriverSettings
    ): FareAnalysisResult {
        val totalDistanceKm = max(0.1, pickupDistanceKm + tripDistanceKm)
        val validMinutes = max(1, estimatedMinutes)
        
        // 1. Calculate Fuel Usage & Cost
        val fuelLitersUsed = totalDistanceKm * (settings.fuelConsumptionLPer100Km / 100.0)
        val fuelCost = fuelLitersUsed * settings.fuelPricePerLiter

        // 2. inDrive Platform Commission
        val commissionRate = settings.inDriveCommissionPercent / 100.0
        val commissionCost = offeredFare * commissionRate

        // 3. Wear & Tear (Tires, Motor Oil, Depreciation)
        val wearAndTearCost = totalDistanceKm * settings.wearAndTearPerKm

        // 4. Operating Costs & Net Profit
        val totalOperatingCost = fuelCost + commissionCost + wearAndTearCost
        val netProfit = offeredFare - totalOperatingCost
        val profitMarginPercent = if (offeredFare > 0) (netProfit / offeredFare) * 100.0 else 0.0
        val profitPerKm = netProfit / totalDistanceKm
        val profitPerHour = (netProfit / validMinutes) * 60.0

        // 5. Evaluate Profitability
        val (level, statusMessage, warningNote) = evaluateProfitability(
            netProfit = netProfit,
            profitPerKm = profitPerKm,
            minTargetProfitPerKm = settings.minProfitPerKm,
            fuelCost = fuelCost,
            offeredFare = offeredFare
        )

        // 6. Calculate Smart Counter-Bids
        // Formula: NeededGross = (FuelCost + WearCost + DesiredProfit) / (1 - CommissionRate)
        val nonCommissionCosts = fuelCost + wearAndTearCost
        val netDivisor = max(0.01, 1.0 - commissionRate)

        // Break Even Floor
        val rawBreakEven = nonCommissionCosts / netDivisor
        val breakEvenFare = roundToSmartStep(rawBreakEven)

        // Recommended Sweet Spot (Yields target profit per km)
        val targetProfit = totalDistanceKm * settings.minProfitPerKm
        val rawRecommended = (nonCommissionCosts + targetProfit) / netDivisor
        val recommendedFare = max(breakEvenFare + 10.0, roundToSmartStep(rawRecommended))

        // Aggressive Bid (Target profit * 1.5 or rush surge)
        val rawAggressive = (nonCommissionCosts + (targetProfit * 1.6)) / netDivisor
        val aggressiveFare = max(recommendedFare + 15.0, roundToSmartStep(rawAggressive))

        // Create Smart Bid Cards
        val smartBids = listOf(
            SmartBidOption(
                title = "Floor / Break-Even",
                suggestedFare = breakEvenFare,
                netProfit = max(0.0, breakEvenFare - (breakEvenFare * commissionRate) - nonCommissionCosts),
                profitPerKm = max(0.0, (breakEvenFare - (breakEvenFare * commissionRate) - nonCommissionCosts) / totalDistanceKm),
                description = "Lowest bid to prevent loss. Covers gas and commission completely.",
                tag = "MINIMUM"
            ),
            SmartBidOption(
                title = "Recommended Bid",
                suggestedFare = recommendedFare,
                netProfit = recommendedFare - (recommendedFare * commissionRate) - nonCommissionCosts,
                profitPerKm = (recommendedFare - (recommendedFare * commissionRate) - nonCommissionCosts) / totalDistanceKm,
                description = "Fair counter-offer: high passenger acceptance + healthy margin.",
                tag = "OPTIMAL"
            ),
            SmartBidOption(
                title = "Aggressive Bid",
                suggestedFare = aggressiveFare,
                netProfit = aggressiveFare - (aggressiveFare * commissionRate) - nonCommissionCosts,
                profitPerKm = (aggressiveFare - (aggressiveFare * commissionRate) - nonCommissionCosts) / totalDistanceKm,
                description = "Premium counter-offer for heavy traffic, rain, or long return trips.",
                tag = "HIGH MARGIN"
            )
        )

        return FareAnalysisResult(
            pickupDistanceKm = pickupDistanceKm,
            tripDistanceKm = tripDistanceKm,
            totalDistanceKm = totalDistanceKm,
            estimatedMinutes = validMinutes,
            offeredFare = offeredFare,
            fuelCost = fuelCost,
            fuelLitersUsed = fuelLitersUsed,
            commissionCost = commissionCost,
            wearAndTearCost = wearAndTearCost,
            totalOperatingCost = totalOperatingCost,
            netProfit = netProfit,
            profitMarginPercent = profitMarginPercent,
            profitPerKm = profitPerKm,
            profitPerHour = profitPerHour,
            profitabilityLevel = level,
            statusMessage = statusMessage,
            warningNote = warningNote,
            breakEvenFare = breakEvenFare,
            recommendedFare = recommendedFare,
            aggressiveFare = aggressiveFare,
            smartBids = smartBids
        )
    }

    private fun evaluateProfitability(
        netProfit: Double,
        profitPerKm: Double,
        minTargetProfitPerKm: Double,
        fuelCost: Double,
        offeredFare: Double
    ): Triple<ProfitabilityLevel, String, String?> {
        return when {
            netProfit < 0 -> {
                Triple(
                    ProfitabilityLevel.LOSS_DANGER,
                    "رحلة خاسرة - التكلفة التشغيلية تتجاوز العرض",
                    "تكلفة الوقود والاستهلاك والعمولة (${String.format("%.1f", fuelCost)} ج.م) تتجاوز عرض الراكب (${String.format("%.1f", offeredFare)} ج.م). يوصى بالمزايدة لرفع السعر أو رفض الطلب."
                )
            }
            profitPerKm < minTargetProfitPerKm -> {
                Triple(
                    ProfitabilityLevel.POOR_MARGIN,
                    "هامش ربح منخفض دون المستهدف",
                    "صافي العائد أقل من الحد الأدنى المطلوب (${String.format("%.1f", minTargetProfitPerKm)} ج.م/كم). يفضل المزايدة لرفع السعر."
                )
            }
            profitPerKm >= minTargetProfitPerKm * 1.5 -> {
                Triple(
                    ProfitabilityLevel.EXCELLENT_PROFIT,
                    "عائد ممتاز وعالي الربحية",
                    null
                )
            }
            else -> {
                Triple(
                    ProfitabilityLevel.GOOD_PROFIT,
                    "عرض رابح ومجدٍ تشغيلياً",
                    null
                )
            }
        }
    }

    /**
     * Rounds fares to realistic Egyptian street ride-hailing steps (e.g. 5 or 10 EGP)
     */
    fun roundToSmartStep(fare: Double, step: Double = 5.0): Double {
        return ceil(fare / step) * step
    }
}
