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
    val smartBids: List<SmartBidOption>,

    // Geographic Zone Analysis
    val isFavoriteZone: Boolean = false,
    val matchedFavoriteZone: String? = null,
    val isBlacklistedZone: Boolean = false,
    val matchedBlacklistZone: String? = null,

    // Passenger lists
    val isFavoritePassenger: Boolean = false,
    val isBlacklistedPassenger: Boolean = false
)

object FareCalculatorEngine {

    /** City driving estimate; must match MINUTES_PER_KM in lib/engine/fare_calculator_engine.dart. */
    const val MINUTES_PER_KM = 2.5

    private val ALEF_VARIANTS = Regex("[أإآ]")
    // \p{P} is the Unicode punctuation category (covers Arabic "،" "؛"), unlike ASCII-only \p{Punct}.
    private val PUNCT_OR_SPACE = Regex("[\\p{P}\\s]+")

    fun estimateMinutes(totalDistanceKm: Double): Int =
        (totalDistanceKm * MINUTES_PER_KM).toInt().coerceIn(5, 300)

    fun normalizeArabicText(str: String): String {
        return str.lowercase()
            .replace(ALEF_VARIANTS, "ا")
            .replace("ى", "ي")
            .replace("ة", "ه")
            .replace(PUNCT_OR_SPACE, " ")
            .trim()
    }

    /** Returns the first zone whose normalized name appears in [address], or null. */
    fun matchZone(address: String?, zones: List<String>): String? {
        if (address.isNullOrBlank()) return null
        val normAddress = normalizeArabicText(address)
        for (z in zones) {
            val normZ = normalizeArabicText(z)
            if (normZ.isNotEmpty() && normAddress.contains(normZ)) return z
        }
        return null
    }

    fun analyze(
        pickupDistanceKm: Double,
        tripDistanceKm: Double,
        estimatedMinutes: Int,
        offeredFare: Double,
        settings: DriverSettings,
        pickupAddress: String = "",
        dropoffAddress: String = "",
        passengerName: String = ""
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

        // 5. Calculate Smart Counter-Bids
        // Formula: NeededGross = (FuelCost + WearCost + DesiredProfit) / (1 - CommissionRate)
        val nonCommissionCosts = fuelCost + wearAndTearCost
        val netDivisor = max(0.01, 1.0 - commissionRate)

        // Break Even Floor
        val rawBreakEven = nonCommissionCosts / netDivisor
        val breakEvenFare = roundToSmartStep(rawBreakEven)

        // Recommended Sweet Spot (the fair price)
        val rawRecommended = fairPrice(pickupDistanceKm, totalDistanceKm - max(0.0, pickupDistanceKm), settings)
        val recommendedFare = max(breakEvenFare + 10.0, roundToSmartStep(rawRecommended))

        // Net profit targeted on this ride: what the fair price leaves after all costs.
        // Without pickup pricing this is exactly totalKm × minProfitPerKm.
        val targetProfit = max(0.0, rawRecommended * (1.0 - commissionRate) - nonCommissionCosts)
        val targetProfitPerKm = targetProfit / totalDistanceKm

        // Aggressive Bid (Target profit * 1.5 or rush surge)
        val rawAggressive = (nonCommissionCosts + (targetProfit * 1.6)) / netDivisor
        val aggressiveFare = max(recommendedFare + 15.0, roundToSmartStep(rawAggressive))

        // 6. Geographic Zone Analysis
        val combinedAddress = "$pickupAddress $dropoffAddress"
        val matchedFavorite = matchZone(combinedAddress, settings.favoriteZones)
        val matchedBlacklist = matchZone(combinedAddress, settings.blacklistedZones)

        // 7. Evaluate Profitability
        var (level, statusMessage, warningNote) = evaluateProfitability(
            netProfit = netProfit,
            profitPerKm = profitPerKm,
            minTargetProfitPerKm = targetProfitPerKm,
            totalOperatingCost = totalOperatingCost,
            offeredFare = offeredFare
        )

        // Apply Zone Business Rules:
        if (matchedBlacklist != null) {
            // Rule: Accept only if fare is exceptionally high
            if (offeredFare >= aggressiveFare || profitPerKm >= targetProfitPerKm * 1.4) {
                level = ProfitabilityLevel.GOOD_PROFIT
                statusMessage = "مقبول فقط لارتفاع السعر الاستثنائي"
                warningNote = "تنبيه: الرحلة تمر بمنطقة غير مرغوبة ($matchedBlacklist). القبول مشروط بالسعر المرتفع."
            } else {
                level = ProfitabilityLevel.LOSS_DANGER
                statusMessage = "مرفوض - منطقة غير مرغوبة ($matchedBlacklist)"
                warningNote = "الرحلة تمر بمنطقة غير مرغوبة ($matchedBlacklist)، والعرض المالي لا يبرر التوجه إليها."
            }
        } else if (matchedFavorite != null) {
            if (level == ProfitabilityLevel.GOOD_PROFIT || level == ProfitabilityLevel.EXCELLENT_PROFIT) {
                statusMessage = "قبول فوري - منطقة مفضلة ($matchedFavorite)"
            } else if (level == ProfitabilityLevel.POOR_MARGIN) {
                statusMessage = "منطقة مفضلة ($matchedFavorite) - ارفع السعر قليلاً"
            }
        }

        // Passenger lists: a blocked passenger is always a rejection; a favourite only adds a note.
        val blockedPassenger = matchPassenger(passengerName, settings.blacklistedPassengers)
        val favoritePassenger = if (blockedPassenger == null) matchPassenger(passengerName, settings.favoritePassengers) else null
        if (blockedPassenger != null) {
            level = ProfitabilityLevel.LOSS_DANGER
            statusMessage = "عميل محظور ($blockedPassenger)"
            warningNote = "العميل ده في قائمتك المحظورة."
        } else if (favoritePassenger != null && level != ProfitabilityLevel.LOSS_DANGER) {
            statusMessage = "عميل مميز ($favoritePassenger) - $statusMessage"
        }

        // Create Smart Bid Cards
        val smartBids = listOf(
            SmartBidOption(
                title = "الحد الأدنى (التعادل)",
                suggestedFare = breakEvenFare,
                netProfit = max(0.0, breakEvenFare - (breakEvenFare * commissionRate) - nonCommissionCosts),
                profitPerKm = max(0.0, (breakEvenFare - (breakEvenFare * commissionRate) - nonCommissionCosts) / totalDistanceKm),
                description = "Lowest bid to prevent loss. Covers gas and commission completely.",
                tag = "MINIMUM"
            ),
            SmartBidOption(
                title = "العرض الموصى به",
                suggestedFare = recommendedFare,
                netProfit = recommendedFare - (recommendedFare * commissionRate) - nonCommissionCosts,
                profitPerKm = (recommendedFare - (recommendedFare * commissionRate) - nonCommissionCosts) / totalDistanceKm,
                description = "Fair counter-offer: high passenger acceptance + healthy margin.",
                tag = "OPTIMAL"
            ),
            SmartBidOption(
                title = "عرض الذروة",
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
            smartBids = smartBids,
            isFavoriteZone = (matchedFavorite != null),
            matchedFavoriteZone = matchedFavorite,
            isBlacklistedZone = (matchedBlacklist != null),
            matchedBlacklistZone = matchedBlacklist,
            isFavoritePassenger = favoritePassenger != null,
            isBlacklistedPassenger = blockedPassenger != null
        )
    }

    /**
     * The fair price before rounding (mirrors fairPrice in fare_calculator_engine.dart).
     * Trip km: fuel + wear + profit per km, grossed up for the full commission. With pickup pricing
     * on, pickup km use their own profit per km and only a share of the commission.
     */
    fun fairPrice(pickupDistanceKm: Double, tripDistanceKm: Double, settings: DriverSettings): Double {
        val costPerKm = (settings.fuelConsumptionLPer100Km / 100.0) * settings.fuelPricePerLiter + settings.wearAndTearPerKm
        val c = settings.inDriveCommissionPercent / 100.0
        val pickupKm = max(0.0, pickupDistanceKm)
        val tripKm = max(0.0, tripDistanceKm)
        if (!settings.pickupPricingEnabled) {
            return (pickupKm + tripKm) * (costPerKm + settings.minProfitPerKm) / max(0.01, 1.0 - c)
        }
        val share = (settings.pickupCommissionSharePercent / 100.0).coerceIn(0.0, 1.0)
        val tripPart = tripKm * (costPerKm + settings.minProfitPerKm) / max(0.01, 1.0 - c)
        val pickupPart = if (pickupRuleApplies(pickupKm, tripKm, settings)) {
            pickupKm * (costPerKm + settings.pickupProfitPerKm) / max(0.01, 1.0 - c * share)
        } else when (settings.pickupShortMode) {
            "FREE" -> 0.0 // the driver absorbs a short pickup
            "AS_TRIP" -> pickupKm * (costPerKm + settings.minProfitPerKm) / max(0.01, 1.0 - c)
            else -> pickupKm * costPerKm / max(0.01, 1.0 - c * share) // COST_ONLY: covered, no profit
        }
        return tripPart + pickupPart
    }

    /** Mirrors pickupRuleApplies in fare_calculator_engine.dart. No rule switched on = always. */
    fun pickupRuleApplies(pickupKm: Double, tripKm: Double, s: DriverSettings): Boolean {
        val rules = buildList {
            if (s.pickupRuleLongerThanTrip) add(pickupKm > tripKm)
            if (s.pickupRuleOverKm) add(pickupKm > s.pickupRuleThresholdKm)
        }
        if (rules.isEmpty()) return true
        return if (s.pickupRuleMatchAll) rules.all { it } else rules.any { it }
    }

    /** Exact (normalised) name match; first names are short, so a substring match would be wrong. */
    fun matchPassenger(name: String?, list: List<String>): String? {
        if (name.isNullOrBlank()) return null
        val n = normalizeArabicText(name)
        return list.firstOrNull { normalizeArabicText(it) == n }
    }

    private fun evaluateProfitability(
        netProfit: Double,
        profitPerKm: Double,
        minTargetProfitPerKm: Double,
        totalOperatingCost: Double,
        offeredFare: Double
    ): Triple<ProfitabilityLevel, String, String?> {
        return when {
            netProfit < 0 -> {
                Triple(
                    ProfitabilityLevel.LOSS_DANGER,
                    "رحلة خاسرة - التكلفة التشغيلية تتجاوز العرض",
                    "تكلفة الوقود والاستهلاك والعمولة (${String.format(java.util.Locale.US, "%.1f", totalOperatingCost)} ج.م) تتجاوز عرض الراكب (${String.format(java.util.Locale.US, "%.1f", offeredFare)} ج.م). يوصى بالمزايدة لرفع السعر أو رفض الطلب."
                )
            }
            profitPerKm < minTargetProfitPerKm -> {
                Triple(
                    ProfitabilityLevel.POOR_MARGIN,
                    "هامش ربح منخفض دون المستهدف",
                    "صافي العائد أقل من الحد الأدنى المطلوب (${String.format(java.util.Locale.US, "%.1f", minTargetProfitPerKm)} ج.م/كم). يفضل المزايدة لرفع السعر."
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
