package com.indrive.assistant.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.engine.FareAnalysisResult
import com.indrive.assistant.engine.FareCalculatorEngine
import com.indrive.assistant.engine.ProfitabilityLevel

/**
 * The requests shown on the live map: every request read from inDrive in the last
 * [HudPrefs.liveMapKeepSeconds], with its verdict and where its pickup and destination are.
 *
 * Fed by the accessibility worker ([update]); read by the map views on the main thread
 * ([snapshot], [addListener]). A request stays on the map for a while after it leaves inDrive's
 * screen (e.g. while another request is open), drawn faded until it shows again.
 */
object LiveRequests {

    class Entry(
        val key: String,
        @Volatile var card: ParsedRideData,
        @Volatile var analysis: FareAnalysisResult,
        val firstSeen: Long,
        @Volatile var lastSeen: Long,
        /** On inDrive's screen at the last read (actions only work then). */
        @Volatile var onScreen: Boolean,
        /** [lat, lng] once geocoded. */
        @Volatile var pickup: DoubleArray? = null,
        @Volatile var dropoff: DoubleArray? = null,
        /** LIVE | ACCEPTED | HIDDEN | BID_SENT */
        @Volatile var status: String = STATUS_LIVE,
        @Volatile var statusDetail: String = ""
    ) {
        /** EXCELLENT | GOOD | POOR | LOSS | UNKNOWN (same as the HUD alerts). */
        val level: String
            get() = when {
                card.distanceUnknown -> "UNKNOWN"
                analysis.profitabilityLevel == ProfitabilityLevel.EXCELLENT_PROFIT -> "EXCELLENT"
                analysis.profitabilityLevel == ProfitabilityLevel.GOOD_PROFIT -> "GOOD"
                analysis.profitabilityLevel == ProfitabilityLevel.POOR_MARGIN -> "POOR"
                else -> "LOSS"
            }
    }

    const val STATUS_LIVE = "LIVE"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_HIDDEN = "HIDDEN"
    const val STATUS_BID_SENT = "BID_SENT"

    private val entries = LinkedHashMap<String, Entry>() // guarded by this
    private val listeners = mutableListOf<() -> Unit>() // main thread only
    private val main = Handler(Looper.getMainLooper())
    private var notifyPosted = false // main thread only

    /** The request screen open in inDrive right now, if any (its key). */
    @Volatile var openKey: String? = null
        private set

    /** Same pickup → destination on the list and on the Ride-request screen. */
    fun keyOf(card: ParsedRideData): String {
        if (card.pickupAddress.isBlank() || card.dropoffAddress.isBlank()) return card.fingerprint
        return FareCalculatorEngine.normalizeArabicText(card.pickupAddress) + "|" +
            FareCalculatorEngine.normalizeArabicText(card.dropoffAddress)
    }

    /** Worker thread: the requests just read from inDrive (empty when inDrive left the screen). */
    fun update(context: Context, read: List<Pair<ParsedRideData, FareAnalysisResult>>, onRequestScreen: Boolean) {
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        synchronized(this) {
            for ((card, analysis) in read) {
                val key = keyOf(card)
                seen.add(key)
                val e = entries[key]
                if (e == null) {
                    entries[key] = Entry(key, card, analysis, now, now, true)
                } else {
                    // The Ride-request screen knows more (bid buttons, the trip length): keep it.
                    e.card = if (!onRequestScreen && e.card.bidChips.isNotEmpty()) card.copy(bidChips = e.card.bidChips) else card
                    e.analysis = analysis
                    e.lastSeen = now
                    e.onScreen = true
                }
            }
            for (e in entries.values) if (e.key !in seen) e.onScreen = false
            openKey = if (onRequestScreen) read.singleOrNull()?.let { keyOf(it.first) } else null
        }
        geocodeMissing(context)
        changed()
    }

    /** inDrive left the screen: keep the requests (faded) until they expire. */
    fun inDriveGone() {
        synchronized(this) {
            for (e in entries.values) e.onScreen = false
            openKey = null
        }
        changed()
    }

    fun setStatus(key: String, status: String, detail: String = "") {
        synchronized(this) {
            val e = entries[key] ?: return
            e.status = status
            e.statusDetail = detail
        }
        changed()
    }

    /** Requests seen in the last [keepMs], newest first. Expired ones are dropped. */
    fun snapshot(keepMs: Long): List<Entry> = synchronized(this) {
        val now = System.currentTimeMillis()
        entries.values.removeAll { !it.onScreen && now - it.lastSeen > keepMs }
        entries.values.sortedByDescending { it.firstSeen }
    }

    fun get(key: String): Entry? = synchronized(this) { entries[key] }

    fun clear() {
        synchronized(this) { entries.clear() }
        changed()
    }

    private fun geocodeMissing(context: Context) {
        val todo = synchronized(this) { entries.values.filter { it.pickup == null || it.dropoff == null } }
        for (e in todo) {
            if (e.pickup == null) e.pickup = TripDistanceResolver.coordinatesOf(context, e.card.pickupAddress) { refill(context) }
            if (e.dropoff == null) e.dropoff = TripDistanceResolver.coordinatesOf(context, e.card.dropoffAddress) { refill(context) }
        }
    }

    /** A geocoding lookup finished (geocoder thread): place what can be placed now. */
    private fun refill(context: Context) {
        geocodeMissing(context)
        changed()
    }

    // ---- Listeners (map views) ----

    fun addListener(l: () -> Unit) {
        main.post { listeners.add(l) }
    }

    fun removeListener(l: () -> Unit) {
        main.post { listeners.remove(l) }
    }

    /** Coalesces bursts of updates into one redraw. Any thread. */
    private fun changed() {
        main.post {
            if (notifyPosted) return@post
            notifyPosted = true
            main.postDelayed({
                notifyPosted = false
                listeners.toList().forEach { it() }
            }, 150)
        }
    }
}
