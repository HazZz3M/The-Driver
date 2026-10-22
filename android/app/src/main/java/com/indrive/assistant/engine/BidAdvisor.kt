package com.indrive.assistant.engine

import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.ParsedRideData

/**
 * Rates inDrive's counter-bid buttons on the Ride-request screen and picks the best one.
 *
 * "Best" is not simply the highest: it is the cheapest bid that already meets the driver's
 * profit target (a good or excellent rating), because a lower bid is more likely to be taken by
 * the passenger. When no bid reaches the target, the one with the highest net profit is best.
 */
object BidAdvisor {

    data class RatedBid(val chip: ParsedRideData.BidChip, val analysis: FareAnalysisResult)

    fun rate(card: ParsedRideData, settings: DriverSettings): List<RatedBid> = card.bidChips.map { chip ->
        RatedBid(
            chip,
            FareCalculatorEngine.analyze(
                pickupDistanceKm = card.pickupDistanceKm,
                tripDistanceKm = card.tripDistanceKm,
                estimatedMinutes = FareCalculatorEngine.estimateMinutes(card.totalDistanceKm),
                offeredFare = chip.value.toDouble(),
                settings = settings,
                pickupAddress = card.pickupAddress,
                dropoffAddress = card.dropoffAddress,
                passengerName = card.passengerName
            )
        )
    }

    fun best(rated: List<RatedBid>): RatedBid? {
        if (rated.isEmpty()) return null
        val meetsTarget = rated.filter {
            it.analysis.profitabilityLevel == ProfitabilityLevel.GOOD_PROFIT ||
                it.analysis.profitabilityLevel == ProfitabilityLevel.EXCELLENT_PROFIT
        }
        return meetsTarget.minByOrNull { it.chip.value } ?: rated.maxByOrNull { it.analysis.netProfit }
    }
}
