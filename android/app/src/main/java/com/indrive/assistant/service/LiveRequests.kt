package com.indrive.assistant.service

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.engine.FareAnalysisResult
import com.indrive.assistant.engine.FareCalculatorEngine
import com.indrive.assistant.engine.ProfitabilityLevel

/**
 * The requests shown on the requests board: every request read from inDrive in the last
 * [HudPrefs.liveMapKeepSeconds], with its verdict and a snapshot of inDrive's own map of the
 * trip (taken when the request screen was open).
 *
 * Fed by the accessibility worker ([update], [setSnapshot]); read by the board views on the main
 * thread ([snapshot], [addListener]). A request stays for a while after it leaves inDrive's
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
        /** LIVE | ACCEPTED | HIDDEN | BID_SENT | WORKING */
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
    /** The driver's action is being carried out in inDrive (opening, typing the price…). */
    const val STATUS_WORKING = "WORKING"

    private val entries = LinkedHashMap<String, Entry>() // guarded by this
    /** Snapshots of inDrive's trip map, by key (most recent last). Guarded by this. */
    private val snapshots = LinkedHashMap<String, Bitmap>()
    private const val MAX_SNAPSHOTS = 40
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

    /** Worker thread: the requests just read from inDrive. */
    fun update(read: List<Pair<ParsedRideData, FareAnalysisResult>>, onRequestScreen: Boolean) {
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
            // On a request screen the list isn't visible: the others are not "gone".
            for (e in entries.values) if (e.key !in seen) e.onScreen = false
            openKey = if (onRequestScreen) read.singleOrNull()?.let { keyOf(it.first) } else null
        }
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

    fun setSnapshot(key: String, bitmap: Bitmap) {
        synchronized(this) {
            snapshots.remove(key)
            snapshots[key] = bitmap
            while (snapshots.size > MAX_SNAPSHOTS) snapshots.remove(snapshots.keys.first())
        }
        changed()
    }

    fun hasSnapshot(key: String): Boolean = synchronized(this) { snapshots.containsKey(key) }

    fun snapshotOf(key: String): Bitmap? = synchronized(this) { snapshots[key] }

    /** Requests seen in the last [keepMs], newest first. Expired ones are dropped. */
    fun snapshot(keepMs: Long): List<Entry> = synchronized(this) {
        val now = System.currentTimeMillis()
        entries.values.removeAll { !it.onScreen && now - it.lastSeen > keepMs }
        entries.values.sortedByDescending { it.firstSeen }
    }

    fun get(key: String): Entry? = synchronized(this) { entries[key] }

    fun clear() {
        synchronized(this) {
            entries.clear()
            snapshots.clear()
        }
        changed()
    }

    // ---- Listeners (board views) ----

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
            }, 120)
        }
    }
}
