package com.indrive.assistant.data.local

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.engine.FareAnalysisResult
import com.indrive.assistant.service.InDriveAccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Every ride request the driver saw in inDrive, kept on the phone for the trip analysis and
 * recommendations (lib/ui/screens/insights_screen.dart). Nothing leaves the phone unless the
 * driver exports and shares the log.
 *
 * A request is one record from the first time its card appears until it disappears for
 * [SAME_REQUEST_GAP_MS]. Opening it adds the full text of the Ride-request screen (a text
 * snapshot, like the diagnostic capture) and the bid buttons; clicks add the outcome
 * (bid sent / accepted). The driver can rate accepted rides afterwards.
 *
 * Thread-safe: written by the accessibility worker, read and updated by the Flutter bridge.
 */
object RequestLog {
    private const val TAG = "RequestLog"
    private const val FILE = "request_log.json"
    private const val MAX_RECORDS = 5000
    /** A card unseen for this long and shown again counts as a new request. */
    private const val SAME_REQUEST_GAP_MS = 20 * 60 * 1000L
    private const val SAVE_DELAY_MS = 4000L
    private const val MAX_SNAPSHOT_LINES = 120

    const val STATUS_SEEN = "SEEN"
    const val STATUS_OPENED = "OPENED"
    const val STATUS_BID_SENT = "BID_SENT"
    const val STATUS_ACCEPTED = "ACCEPTED"
    private val STATUS_ORDER = listOf(STATUS_SEEN, STATUS_OPENED, STATUS_BID_SENT, STATUS_ACCEPTED)

    private val lock = Any()
    private var records: MutableList<JSONObject>? = null // newest last
    private var appContext: Context? = null
    private val ioThread by lazy { HandlerThread("request-log-io").apply { start() } }
    private val io by lazy { Handler(ioThread.looper) }
    private val saveRunnable = Runnable { saveNow() }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun loaded(context: Context): MutableList<JSONObject> {
        records?.let { return it }
        appContext = context.applicationContext
        val list = mutableListOf<JSONObject>()
        try {
            val f = file(context)
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(it) }
            }
        } catch (e: Exception) {
            // Keep the unreadable file aside instead of overwriting it.
            Log.w(TAG, "Request log unreadable; starting a new one", e)
            try { file(context).renameTo(File(context.filesDir, "request_log_corrupt_${System.currentTimeMillis()}.json")) } catch (_: Exception) {}
        }
        // Builds before 1.17.1 also logged other inDrive screens (ride in progress, balance, cancel,
        // the reply countdown) as requests: drop those once, so the analysis isn't skewed.
        val cleaned = list.filterNot { isJunk(it) }
        if (cleaned.size != list.size) {
            Log.i(TAG, "Removed ${list.size - cleaned.size} non-request records")
            list.clear()
            list.addAll(cleaned)
            records = list
            scheduleSave()
        }
        records = list
        return list
    }

    private fun isJunk(r: JSONObject): Boolean {
        val from = r.optString("pickupAddress")
        val to = r.optString("dropoffAddress")
        if (from.isNotBlank() && InDriveAccessibilityService.isNotAPlace(from)) return true
        if (to.isNotBlank() && InDriveAccessibilityService.isNotAPlace(to)) return true
        // Nothing was read but an amount: not a request card.
        return from.isBlank() && to.isBlank() && !r.optBoolean("pickupKnown") && !r.optBoolean("tripKnown")
    }

    private fun scheduleSave() {
        io.removeCallbacks(saveRunnable)
        io.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    private fun saveNow() {
        val ctx = appContext ?: return
        val json = synchronized(lock) { JSONArray(records ?: return).toString() }
        try {
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(json)
            tmp.renameTo(file(ctx))
        } catch (e: Exception) {
            Log.w(TAG, "Could not save request log", e)
        }
    }

    private fun findRecent(list: List<JSONObject>, fingerprint: String, now: Long): JSONObject? {
        for (i in list.indices.reversed()) {
            val r = list[i]
            if (now - r.optLong("lastSeen") > SAME_REQUEST_GAP_MS) continue
            if (r.optString("fingerprint") == fingerprint) return r
        }
        return null
    }

    private fun raiseStatus(r: JSONObject, status: String) {
        val current = STATUS_ORDER.indexOf(r.optString("status", STATUS_SEEN))
        if (STATUS_ORDER.indexOf(status) > current) r.put("status", status)
    }

    /**
     * Records the cards on screen. [onRequestScreen] = the full-screen request is open: the card
     * is marked opened and [screenTexts] is kept as its text snapshot.
     */
    fun recordSeen(
        context: Context,
        rides: List<ParsedRideData>,
        analyses: List<FareAnalysisResult>,
        onRequestScreen: Boolean,
        screenTexts: List<String>
    ) {
        if (rides.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val list = loaded(context)
            rides.forEachIndexed { i, ride ->
                val a = analyses.getOrNull(i)
                val r = findRecent(list, ride.fingerprint, now) ?: JSONObject().also {
                    it.put("id", now * 100 + (i % 100))
                    it.put("firstSeen", now)
                    it.put("fingerprint", ride.fingerprint)
                    it.put("status", STATUS_SEEN)
                    list.add(it)
                }
                r.put("lastSeen", now)
                r.put("fare", ride.fare)
                // Distances only improve (a read value replaces a placeholder, never the reverse).
                if (ride.pickupKnown || !r.has("pickupKm")) { r.put("pickupKm", ride.pickupDistanceKm); r.put("pickupKnown", ride.pickupKnown) }
                if (ride.tripKnown || !r.optBoolean("tripKnown")) {
                    r.put("tripKm", ride.tripDistanceKm); r.put("tripKnown", ride.tripKnown); r.put("tripSource", ride.tripSource)
                }
                if (ride.pickupAddress.isNotBlank()) r.put("pickupAddress", ride.pickupAddress)
                if (ride.dropoffAddress.isNotBlank()) r.put("dropoffAddress", ride.dropoffAddress)
                if (ride.passengerName.isNotBlank()) r.put("passengerName", ride.passengerName)
                if (ride.passengerRating.isNotBlank()) r.put("passengerRating", ride.passengerRating)
                if (ride.counterBids.isNotEmpty()) r.put("counterBids", JSONArray(ride.counterBids))
                if (ride.bidChips.isNotEmpty()) r.put("bidChips", JSONArray(ride.bidChips.map { it.value }))
                if (a != null) {
                    r.put("netProfit", a.netProfit)
                    r.put("profitPerKm", a.profitPerKm)
                    r.put("level", a.profitabilityLevel.name)
                    r.put("recommendedFare", a.recommendedFare)
                }
                if (onRequestScreen && rides.size == 1) {
                    raiseStatus(r, STATUS_OPENED)
                    if (screenTexts.isNotEmpty()) r.put("snapshot", JSONArray(screenTexts.take(MAX_SNAPSHOT_LINES)))
                }
            }
            while (list.size > MAX_RECORDS) list.removeAt(0)
        }
        scheduleSave()
    }

    /** Outcome of a click in inDrive (or of auto-accept) on the card [fingerprint]. */
    fun recordOutcome(context: Context, fingerprint: String, status: String, bidValue: Int? = null) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val r = findRecent(loaded(context), fingerprint, now) ?: return
            raiseStatus(r, status)
            if (bidValue != null) r.put("bidValue", bidValue)
            if (status == STATUS_ACCEPTED) r.put("acceptedAt", now)
        }
        scheduleSave()
    }

    /** Accepted rides from the last [withinMs] that are not rated yet (newest first). */
    fun unratedAccepted(context: Context, withinMs: Long): List<JSONObject> = synchronized(lock) {
        val now = System.currentTimeMillis()
        loaded(context).filter {
            it.optString("status") == STATUS_ACCEPTED && !it.has("rating") && now - it.optLong("acceptedAt") <= withinMs
        }.reversed()
    }

    fun rate(context: Context, id: Long, stars: Int, tags: List<String>, note: String): Boolean {
        synchronized(lock) {
            val r = loaded(context).firstOrNull { it.optLong("id") == id } ?: return false
            r.put("rating", stars.coerceIn(1, 5))
            r.put("ratingTags", JSONArray(tags))
            r.put("ratingNote", note)
            r.put("ratedAt", System.currentTimeMillis())
        }
        scheduleSave()
        return true
    }

    /** The whole log as a JSON array (oldest first). */
    fun asJson(context: Context): String = synchronized(lock) { JSONArray(loaded(context)).toString() }

    fun clear(context: Context) {
        synchronized(lock) { loaded(context).clear() }
        io.removeCallbacks(saveRunnable)
        io.post { saveNow() }
    }

    /** Writes the log to the cache folder (shareable through the FileProvider) and returns the file. */
    fun export(context: Context, csv: Boolean): File {
        saveNow()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())
        val out = File(dir, "the-driver-requests_$stamp.${if (csv) "csv" else "json"}")
        val list = synchronized(lock) { loaded(context).map { JSONObject(it.toString()) } }
        if (!csv) {
            out.writeText(JSONArray(list).toString(2))
            return out
        }
        val cols = listOf(
            "id", "firstSeen", "status", "fare", "bidValue", "pickupKm", "pickupKnown", "tripKm", "tripKnown", "tripSource",
            "pickupAddress", "dropoffAddress", "passengerName", "passengerRating", "netProfit", "profitPerKm", "level",
            "recommendedFare", "rating", "ratingTags", "ratingNote"
        )
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("﻿") // BOM: Excel opens the Arabic text correctly
        sb.append(cols.joinToString(",")).append('\n')
        for (r in list) {
            sb.append(cols.joinToString(",") { c ->
                when (c) {
                    "firstSeen" -> cell(fmt.format(java.util.Date(r.optLong(c))))
                    "ratingTags" -> cell(r.optJSONArray(c)?.let { a -> (0 until a.length()).joinToString(" | ") { a.optString(it) } } ?: "")
                    else -> cell(if (r.has(c)) r.opt(c).toString() else "")
                }
            }).append('\n')
        }
        out.writeText(sb.toString())
        return out
    }
}
