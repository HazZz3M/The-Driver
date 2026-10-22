package com.indrive.assistant.service

import android.content.Context
import android.location.Geocoder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.engine.FareCalculatorEngine
import org.json.JSONObject
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fills in the trip length of request cards that don't show it (inDrive's list cards show the
 * pickup distance only). Two sources, each switched on by the driver:
 *
 * - **History (B):** every trip length read on a "Ride request" map is saved for that
 *   pickup → destination pair, on the phone. When the same pair shows up again it is reused.
 * - **Addresses (A):** both addresses are looked up with Android's Geocoder (Google's service,
 *   so the address text leaves the phone) and the straight-line distance × [ROAD_FACTOR] is used
 *   as an estimate. The card stays marked as estimated ("≈") and is never auto-accepted.
 *
 * Called from the accessibility worker thread; geocoding runs on its own thread.
 */
object TripDistanceResolver {
    private const val TAG = "TripDistance"

    /** Roads are longer than the straight line; ~1.3 is typical for city driving. */
    private const val ROAD_FACTOR = 1.3
    /** Estimates above this are treated as a wrong geocode (another city with the same street name). */
    private const val MAX_PLAUSIBLE_KM = 80.0
    private const val MAX_LEARNED = 800
    private const val PREFS = "trip_history"
    private const val KEY_LEARNED = "learned_trips"

    // Egypt's bounding box: keeps the Geocoder from matching places abroad.
    private const val LAT_MIN = 22.0
    private const val LAT_MAX = 31.8
    private const val LNG_MIN = 24.7
    private const val LNG_MAX = 36.9

    private class Learned(var km: Double, var count: Int, var updated: Long)

    // ---- History (worker thread only) ----
    private var learned: LinkedHashMap<String, Learned>? = null

    private fun learnedMap(context: Context): LinkedHashMap<String, Learned> {
        learned?.let { return it }
        val map = LinkedHashMap<String, Learned>()
        try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LEARNED, null)
            if (raw != null) {
                val json = JSONObject(raw)
                for (key in json.keys()) {
                    val o = json.getJSONObject(key)
                    map[key] = Learned(o.getDouble("km"), o.optInt("n", 1), o.optLong("t", 0L))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read trip history", e)
        }
        learned = map
        return map
    }

    private fun saveLearned(context: Context, map: Map<String, Learned>) {
        try {
            val json = JSONObject()
            for ((k, v) in map) json.put(k, JSONObject().put("km", v.km).put("n", v.count).put("t", v.updated))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LEARNED, json.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save trip history", e)
        }
    }

    /** pickup → destination, normalised so spelling variants (أ/ا, ة/ه) share one entry.
     *  Same formula as InDriveAccessibilityService.rideKey, so OCR results can be learned by key. */
    fun pairKey(card: ParsedRideData): String? {
        if (card.pickupAddress.isBlank() || card.dropoffAddress.isBlank()) return null
        return FareCalculatorEngine.normalizeArabicText(card.pickupAddress) + "|" +
            FareCalculatorEngine.normalizeArabicText(card.dropoffAddress)
    }

    /** Saves a trip length read on a Ride-request map for the pair [key] (see [pairKey]). */
    fun learn(context: Context, key: String, km: Double) {
        if (km <= 0.0 || km > 200.0) return
        val map = learnedMap(context)
        val entry = map.remove(key)
        if (entry == null) {
            map[key] = Learned(km, 1, System.currentTimeMillis())
        } else {
            // Running average: pickup points on the same street differ slightly between rides.
            val n = (entry.count + 1).coerceAtMost(10)
            entry.km = entry.km + (km - entry.km) / n
            entry.count = n
            entry.updated = System.currentTimeMillis()
            map[key] = entry // re-insert: most recently used last
        }
        while (map.size > MAX_LEARNED) map.remove(map.keys.first())
        saveLearned(context, map)
    }

    fun learnedCount(context: Context): Int = learnedMap(context).size

    fun clearHistory(context: Context) {
        learnedMap(context).clear()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LEARNED).apply()
    }

    // ---- Addresses (geocoding) ----
    private val geoThread by lazy { HandlerThread("trip-geocoder").apply { start() } }
    private val geoHandler by lazy { Handler(geoThread.looper) }
    /** address (normalised) → lat/lng, or null when it could not be found. Shared across threads. */
    private val geocoded = java.util.concurrent.ConcurrentHashMap<String, DoubleArray>()
    private val notFound = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private val pending = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private fun requestGeocode(context: Context, address: String, onDone: () -> Unit) {
        val key = FareCalculatorEngine.normalizeArabicText(address)
        if (geocoded.containsKey(key) || key in notFound || !pending.add(key)) return
        geoHandler.post {
            try {
                if (!Geocoder.isPresent()) {
                    notFound.add(key)
                    return@post
                }
                val geocoder = Geocoder(context, Locale("ar", "EG"))
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(address, 1, LAT_MIN, LNG_MIN, LAT_MAX, LNG_MAX)
                val hit = results?.firstOrNull()
                if (hit != null) geocoded[key] = doubleArrayOf(hit.latitude, hit.longitude) else notFound.add(key)
                if (geocoded.size > 400) geocoded.clear()
                if (notFound.size > 400) notFound.clear()
                if (hit != null) onDone()
            } catch (e: Exception) {
                // No network / service unavailable: try again on a later scan.
                Log.w(TAG, "Geocoding failed for '$address'", e)
            } finally {
                pending.remove(key)
            }
        }
    }

    private fun estimateFromAddresses(context: Context, card: ParsedRideData, onUpdated: () -> Unit): Double? {
        if (card.pickupAddress.isBlank() || card.dropoffAddress.isBlank()) return null
        val a = geocoded[FareCalculatorEngine.normalizeArabicText(card.pickupAddress)]
        val b = geocoded[FareCalculatorEngine.normalizeArabicText(card.dropoffAddress)]
        if (a == null) requestGeocode(context, card.pickupAddress, onUpdated)
        if (b == null) requestGeocode(context, card.dropoffAddress, onUpdated)
        if (a == null || b == null) return null
        val km = haversineKm(a[0], a[1], b[0], b[1]) * ROAD_FACTOR
        return if (km in 0.3..MAX_PLAUSIBLE_KM) (km * 10).let { Math.round(it) / 10.0 } else null
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /**
     * Returns [card] with its trip length filled from history or an address estimate when it is
     * not shown on the card. [onUpdated] runs (on the geocoder thread) when a pending lookup
     * finishes, so the caller can re-scan.
     */
    fun resolve(
        context: Context,
        card: ParsedRideData,
        useHistory: Boolean,
        useAddresses: Boolean,
        onUpdated: () -> Unit
    ): ParsedRideData {
        if (card.tripKnown) return card
        if (useHistory) {
            val key = pairKey(card)
            val hit = key?.let { learnedMap(context)[it] }
            if (hit != null) {
                return card.copy(tripDistanceKm = Math.round(hit.km * 10) / 10.0, tripKnown = true, tripSource = ParsedRideData.SOURCE_HISTORY)
            }
        }
        if (useAddresses) {
            val km = estimateFromAddresses(context, card, onUpdated)
            if (km != null) return card.copy(tripDistanceKm = km, tripSource = ParsedRideData.SOURCE_ADDRESSES)
        }
        return card
    }
}
