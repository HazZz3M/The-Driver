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
    /** Say the verdict aloud for each new request ("رابح، صافي 42"). */
    val speakVerdict: Boolean = false,
    val showFareComparison: Boolean = true,
    val showProfit: Boolean = true,
    val showDistance: Boolean = true,
    val showCostBreakdown: Boolean = false,
    val showZones: Boolean = true,
    /** 0 = never auto-collapse the expanded card */
    val autoCollapseSeconds: Int = 0,
    /** Read the trip length drawn on the Ride-request map with on-device OCR (Android 11+). */
    val readMapDistance: Boolean = true,
    /** B: reuse trip lengths read earlier for the same pickup → destination (kept on the phone). */
    val tripFromHistory: Boolean = true,
    /** A: estimate the trip length from the two addresses (Geocoder; sends address text to Google). */
    val tripFromAddresses: Boolean = false,
    /** C: open unread requests automatically, read the map, and go back (taps inside inDrive). */
    val tripAutoOpen: Boolean = false,
    /** C timings: the longest a request stays open, and the pause after closing before the next one. */
    val autoOpenStaySeconds: Double = 7.0,
    val autoOpenGapSeconds: Double = 8.0,
    /** C: press Back as soon as the trip length is read (otherwise wait the full stay time). */
    val autoOpenBackWhenRead: Boolean = true,
    /** Circles over the Ride-request counter-bid buttons: OFF | HIGHLIGHT | AUTO_TAP (taps the best). */
    val bidChipsMode: String = "HIGHLIGHT"
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
        put("speakVerdict", speakVerdict)
        put("showFareComparison", showFareComparison)
        put("showProfit", showProfit)
        put("showDistance", showDistance)
        put("showCostBreakdown", showCostBreakdown)
        put("showZones", showZones)
        put("autoCollapseSeconds", autoCollapseSeconds)
        put("readMapDistance", readMapDistance)
        put("tripFromHistory", tripFromHistory)
        put("tripFromAddresses", tripFromAddresses)
        put("tripAutoOpen", tripAutoOpen)
        put("autoOpenStaySeconds", autoOpenStaySeconds)
        put("autoOpenGapSeconds", autoOpenGapSeconds)
        put("autoOpenBackWhenRead", autoOpenBackWhenRead)
        put("bidChipsMode", bidChipsMode)
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
                speakVerdict = json.optBoolean("speakVerdict", fallback.speakVerdict),
                showFareComparison = json.optBoolean("showFareComparison", fallback.showFareComparison),
                showProfit = json.optBoolean("showProfit", fallback.showProfit),
                showDistance = json.optBoolean("showDistance", fallback.showDistance),
                showCostBreakdown = json.optBoolean("showCostBreakdown", fallback.showCostBreakdown),
                showZones = json.optBoolean("showZones", fallback.showZones),
                autoCollapseSeconds = json.optInt("autoCollapseSeconds", fallback.autoCollapseSeconds).coerceIn(0, 120),
                readMapDistance = json.optBoolean("readMapDistance", fallback.readMapDistance),
                tripFromHistory = json.optBoolean("tripFromHistory", fallback.tripFromHistory),
                tripFromAddresses = json.optBoolean("tripFromAddresses", fallback.tripFromAddresses),
                tripAutoOpen = json.optBoolean("tripAutoOpen", fallback.tripAutoOpen),
                autoOpenStaySeconds = json.optDouble("autoOpenStaySeconds", fallback.autoOpenStaySeconds),
                autoOpenGapSeconds = json.optDouble("autoOpenGapSeconds", fallback.autoOpenGapSeconds),
                autoOpenBackWhenRead = json.optBoolean("autoOpenBackWhenRead", fallback.autoOpenBackWhenRead),
                bidChipsMode = json.optString("bidChipsMode", fallback.bidChipsMode)
            )
        }
    }
}
