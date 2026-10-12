package com.indrive.assistant.data.model

import org.json.JSONObject

/**
 * What the floating HUD shows and how it behaves. Owned by Flutter (HudSettings in
 * lib/data/models/hud_settings.dart) and mirrored here through the settings sync.
 */
data class HudPrefs(
    val style: String = "COMPACT_CIRCLE",
    val size: String = "MEDIUM",
    /** NET_PROFIT | PROFIT_PER_KM | OFFER | RECOMMENDED */
    val primaryValue: String = "NET_PROFIT",
    val opacity: Double = 1.0,
    /** LEFT | RIGHT edge of the screen for the compact badge */
    val side: String = "LEFT",
    val showLossBadges: Boolean = true,
    val vibrate: Boolean = true,
    val vibrateProfitableOnly: Boolean = false,
    val showFareComparison: Boolean = true,
    val showProfit: Boolean = true,
    val showDistance: Boolean = true,
    val showCostBreakdown: Boolean = false,
    val showZones: Boolean = true,
    /** 0 = never auto-collapse the expanded card */
    val autoCollapseSeconds: Int = 0,
    /** Read the trip length drawn on the Ride-request map with on-device OCR (Android 11+). */
    val readMapDistance: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("style", style)
        put("size", size)
        put("primaryValue", primaryValue)
        put("opacity", opacity)
        put("side", side)
        put("showLossBadges", showLossBadges)
        put("vibrate", vibrate)
        put("vibrateProfitableOnly", vibrateProfitableOnly)
        put("showFareComparison", showFareComparison)
        put("showProfit", showProfit)
        put("showDistance", showDistance)
        put("showCostBreakdown", showCostBreakdown)
        put("showZones", showZones)
        put("autoCollapseSeconds", autoCollapseSeconds)
        put("readMapDistance", readMapDistance)
    }

    companion object {
        fun fromJson(json: JSONObject?, fallback: HudPrefs = HudPrefs()): HudPrefs {
            if (json == null) return fallback
            return HudPrefs(
                style = json.optString("style", fallback.style),
                size = json.optString("size", fallback.size),
                primaryValue = json.optString("primaryValue", fallback.primaryValue),
                opacity = json.optDouble("opacity", fallback.opacity).coerceIn(0.4, 1.0),
                side = json.optString("side", fallback.side),
                showLossBadges = json.optBoolean("showLossBadges", fallback.showLossBadges),
                vibrate = json.optBoolean("vibrate", fallback.vibrate),
                vibrateProfitableOnly = json.optBoolean("vibrateProfitableOnly", fallback.vibrateProfitableOnly),
                showFareComparison = json.optBoolean("showFareComparison", fallback.showFareComparison),
                showProfit = json.optBoolean("showProfit", fallback.showProfit),
                showDistance = json.optBoolean("showDistance", fallback.showDistance),
                showCostBreakdown = json.optBoolean("showCostBreakdown", fallback.showCostBreakdown),
                showZones = json.optBoolean("showZones", fallback.showZones),
                autoCollapseSeconds = json.optInt("autoCollapseSeconds", fallback.autoCollapseSeconds).coerceIn(0, 120),
                readMapDistance = json.optBoolean("readMapDistance", fallback.readMapDistance)
            )
        }
    }
}
