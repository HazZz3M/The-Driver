package com.indrive.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.indrive.assistant.data.model.ParsedRideData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InDriveAccessibilityService : AccessibilityService() {

    private var lastScanTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "InDriveAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()
        // Debounce: Avoid processing more than once every 400ms to eliminate UI stutter and endless loops
        if (now - lastScanTimestamp < 400L) {
            return
        }
        lastScanTimestamp = now

        val root = rootInActiveWindow ?: return
        try {
            val texts = mutableListOf<String>()
            collectAllTexts(root, texts)

            val parsedRides = parseAllRidesFromScreen(texts)
            for (ride in parsedRides) {
                // Deduplicate to avoid repeating the exact same ride hundreds of times per minute
                if (ride.fingerprint == lastEmittedFingerprint && (now - lastEmittedTimestamp) < 12000L) {
                    continue
                }

                lastEmittedFingerprint = ride.fingerprint
                lastEmittedTimestamp = now
                Log.d(TAG, "Detected new inDrive ride for ${ride.passengerName}: ${ride.fare} EGP")
                _latestRide.value = ride
                FloatingHudService.onNewRideParsed(ride)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing screen nodes: ${e.message}")
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "InDriveAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            texts.add(text)
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc != text) {
            texts.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllTexts(child, texts)
                child.recycle()
            }
        }
    }

    companion object {
        private const val TAG = "InDriveA11y"
        private var instance: InDriveAccessibilityService? = null
        private var lastEmittedFingerprint = ""
        private var lastEmittedTimestamp = 0L

        private val _latestRide = MutableStateFlow<ParsedRideData?>(null)
        val latestRide: StateFlow<ParsedRideData?> = _latestRide.asStateFlow()

        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${InDriveAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun scanCurrentScreen(): List<ParsedRideData> {
            val service = instance ?: return emptyList()
            val root = service.rootInActiveWindow ?: return emptyList()
            try {
                val texts = mutableListOf<String>()
                service.collectAllTexts(root, texts)
                val parsedList = parseAllRidesFromScreen(texts)
                for (parsed in parsedList) {
                    _latestRide.value = parsed
                    FloatingHudService.onNewRideParsed(parsed)
                }
                return parsedList
            } catch (e: Exception) {
                Log.e(TAG, "Manual scan error: ${e.message}")
                return emptyList()
            } finally {
                root.recycle()
            }
        }

        fun normalizeArabicDigits(str: String): String {
            val arabicNumerals = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
            val sb = java.lang.StringBuilder()
            for (c in str) {
                val idx = arabicNumerals.indexOf(c)
                if (idx != -1) {
                    sb.append(idx)
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        fun parseAllRidesFromScreen(texts: List<String>): List<ParsedRideData> {
            val single = parseRideScreen(texts)
            return if (single != null) listOf(single) else emptyList()
        }

        fun parseRideScreen(texts: List<String>): ParsedRideData? {
            if (texts.isEmpty()) return null

            val normalizedTexts = texts.map { normalizeArabicDigits(it.trim()) }

            // Verify if this is an inDrive ride request screen
            val hasRideKeywords = normalizedTexts.any {
                it.contains("Ride request", ignoreCase = true) ||
                it.contains("طلب مشوار", ignoreCase = true) ||
                it.contains("Accept for", ignoreCase = true) ||
                it.contains("قبول مقابل", ignoreCase = true) ||
                it.contains("Fair price", ignoreCase = true) ||
                it.contains("Offer your fare", ignoreCase = true) ||
                it.contains("اقترح سعرك", ignoreCase = true)
            }

            if (!hasRideKeywords) return null

            // 1. Extract Passenger Name & Rating
            var passengerName = "عميل إن درايف"
            var passengerRating = ""
            val ratingRegex = Regex("""^(\d\.\d)(?:\s*\(\d+\))?$""")
            val ratingIdx = normalizedTexts.indexOfFirst { ratingRegex.matches(it) }
            if (ratingIdx > 0) {
                val candidateName = texts[ratingIdx - 1].trim()
                // Filter out non-names like "11 min", "B", "A", etc.
                if (!candidateName.contains("min", ignoreCase = true) &&
                    !candidateName.contains("km", ignoreCase = true) &&
                    !candidateName.equals("A", ignoreCase = true) &&
                    !candidateName.equals("B", ignoreCase = true) &&
                    candidateName.length in 2..30) {
                    passengerName = candidateName
                }
                passengerRating = texts[ratingIdx]
            }

            // 2. Extract Fare
            var fare: Double? = null
            for (t in normalizedTexts) {
                val acceptMatch = Regex("""(?:Accept for EGP|قبول مقابل)\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(t)
                if (acceptMatch != null) {
                    fare = acceptMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                    break
                }
            }

            if (fare == null) {
                for (t in normalizedTexts) {
                    val fareMatch = Regex("""^(?:EGP|ج\.?م|جنيه)\s*(\d+(?:[.,]\d+)?)$""", RegexOption.IGNORE_CASE).find(t)
                        ?: Regex("""^(\d+(?:[.,]\d+)?)\s*(?:EGP|ج\.?م|جنيه)$""", RegexOption.IGNORE_CASE).find(t)
                    if (fareMatch != null) {
                        fare = fareMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                        break
                    }
                }
            }

            // 3. Extract Distances
            val distances = mutableListOf<Double>()
            for (t in normalizedTexts) {
                val kmMatches = Regex("""(?:~)?\s*(\d+(?:[.,]\d+)?)\s*(?:km|كم)""", RegexOption.IGNORE_CASE).findAll(t)
                for (m in kmMatches) {
                    val d = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (d != null && d > 0.1 && !distances.contains(d)) {
                        distances.add(d)
                    }
                }
            }

            var pickupKm = 1.5
            var tripKm = 5.0

            if (distances.size >= 2) {
                pickupKm = distances.minOrNull() ?: 1.5
                tripKm = distances.maxOrNull() ?: 5.0
            } else if (distances.size == 1) {
                tripKm = distances[0]
                pickupKm = 1.5
            }

            // 4. Extract Counter Bids
            val counterBids = mutableListOf<Int>()
            for (t in normalizedTexts) {
                val bidMatch = Regex("""^EGP\s*(\d+)$""", RegexOption.IGNORE_CASE).find(t)
                if (bidMatch != null) {
                    val b = bidMatch.groupValues[1].toIntOrNull()
                    if (b != null && b.toDouble() != fare && !counterBids.contains(b)) {
                        counterBids.add(b)
                    }
                }
            }

            // 5. Extract Addresses
            var pickupAddress = ""
            var dropoffAddress = ""
            val fairPriceIdx = normalizedTexts.indexOfFirst { it.equals("Fair price", ignoreCase = true) || it.contains("سعر مناسب") }
            if (fairPriceIdx != -1 && fairPriceIdx + 2 < texts.size) {
                pickupAddress = texts[fairPriceIdx + 1]
                dropoffAddress = texts[fairPriceIdx + 2]
            }

            if (fare != null && fare > 0.0) {
                return ParsedRideData(
                    passengerName = passengerName,
                    passengerRating = passengerRating,
                    fare = fare,
                    pickupDistanceKm = pickupKm,
                    tripDistanceKm = tripKm,
                    pickupAddress = pickupAddress,
                    dropoffAddress = dropoffAddress,
                    counterBids = counterBids.sorted()
                )
            }

            return null
        }
    }
}
