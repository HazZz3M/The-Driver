package com.indrive.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.indrive.assistant.InDriveAssistantApp
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.engine.FareCalculatorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InDriveAccessibilityService : AccessibilityService() {

    private var lastScanTimestamp = 0L
    private var lastAutoAcceptedFingerprint = ""
    private var lastAutoAcceptTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "InDriveAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()
        // Debounce to eliminate lag
        if (now - lastScanTimestamp < 250L) {
            return
        }
        lastScanTimestamp = now

        val root = rootInActiveWindow
        if (root == null) {
            FloatingHudService.onCardsSync(emptyList())
            return
        }

        try {
            val pkg = root.packageName?.toString() ?: ""
            // Ensure popups disappear immediately if user leaves inDrive
            val isTargetApp = pkg.contains("indrive", ignoreCase = true) ||
                    pkg.contains("sinet", ignoreCase = true) ||
                    pkg.contains("citymobil", ignoreCase = true) ||
                    pkg.contains("driver", ignoreCase = true) ||
                    pkg.contains("assistant", ignoreCase = true)

            if (!isTargetApp) {
                FloatingHudService.onCardsSync(emptyList())
                return
            }

            val parsedRides = findRideCards(root)
            _latestRide.value = parsedRides.firstOrNull()

            // Synchronize popup hints on each visible card
            FloatingHudService.onCardsSync(parsedRides)

            // Evaluate Auto-Accept & Auto-Bidding automation
            checkAndTriggerAutoAccept(parsedRides)
        } catch (e: Exception) {
            Log.e(TAG, "Error in accessibility event processing: ${e.message}")
        } finally {
            root.recycle()
        }
    }

    private fun checkAndTriggerAutoAccept(rides: List<ParsedRideData>) {
        if (rides.isEmpty()) return
        val app = InDriveAssistantApp.instance ?: return
        val settings = app.repository.getDriverSettings()

        if (!settings.autoAcceptEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastAutoAcceptTimestamp < 5000L) {
            return
        }

        for (card in rides) {
            val anchor = card.anchorBounds ?: continue
            if (card.fingerprint == lastAutoAcceptedFingerprint) continue

            // 1. Blacklist check
            if (!settings.autoAcceptIgnoreBlacklist) {
                if (settings.isBlacklisted(card.pickupAddress) || settings.isBlacklisted(card.dropoffAddress)) {
                    Log.d(TAG, "Auto-accept: Skipped due to blacklisted zone")
                    continue
                }
            }

            // 2. Favorite only check
            if (settings.autoAcceptOnlyFavoriteZones) {
                if (!settings.isFavorite(card.pickupAddress) && !settings.isFavorite(card.dropoffAddress)) {
                    Log.d(TAG, "Auto-accept: Skipped because zone is not in favorites")
                    continue
                }
            }

            // 3. Max pickup distance check
            if (card.pickupDistanceKm > settings.autoAcceptMaxPickupKm) {
                Log.d(TAG, "Auto-accept: Skipped because pickup distance ${card.pickupDistanceKm} > ${settings.autoAcceptMaxPickupKm}")
                continue
            }

            // 4. Financial thresholds check
            val analysis = FareCalculatorEngine.analyze(
                pickupDistanceKm = card.pickupDistanceKm,
                tripDistanceKm = card.tripDistanceKm,
                estimatedMinutes = ((card.totalDistanceKm) * 2.3).toInt(),
                offeredFare = card.fare,
                settings = settings,
                pickupAddress = card.pickupAddress,
                dropoffAddress = card.dropoffAddress
            )

            if (analysis.netProfit < settings.autoAcceptMinNetProfit) {
                Log.d(TAG, "Auto-accept: Net profit ${analysis.netProfit} < ${settings.autoAcceptMinNetProfit}")
                continue
            }

            if (analysis.profitPerKm < settings.autoAcceptMinProfitPerKm) {
                Log.d(TAG, "Auto-accept: Profit/km ${analysis.profitPerKm} < ${settings.autoAcceptMinProfitPerKm}")
                continue
            }

            // All conditions met! Auto-accept this offer
            lastAutoAcceptedFingerprint = card.fingerprint
            lastAutoAcceptTimestamp = now
            val targetX = anchor.centerX().toFloat()
            val targetY = anchor.centerY().toFloat()
            val delayMs = (settings.autoAcceptDelaySeconds * 1000).toLong().coerceIn(200L, 8000L)

            Log.i(TAG, "Auto-accepting ride at ($targetX, $targetY) after ${delayMs}ms. Net profit: ${analysis.netProfit}")

            Handler(Looper.getMainLooper()).postDelayed({
                dispatchClickGesture(targetX, targetY)
                try {
                    Toast.makeText(
                        applicationContext,
                        "تم القبول التلقائي للطلب (ربح متوقع: ${analysis.netProfit.toInt()} ج.م)",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: Exception) {}
            }, delayMs)

            break
        }
    }

    fun dispatchClickGesture(x: Float, y: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val clickPath = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(clickPath, 0, 60)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Gesture click completed at ($x, $y)")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Gesture click cancelled at ($x, $y)")
                }
            }, null)
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
                val parsedList = findRideCards(root)
                _latestRide.value = parsedList.firstOrNull()
                FloatingHudService.onCardsSync(parsedList)
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

        fun findRideCards(root: AccessibilityNodeInfo): List<ParsedRideData> {
            val results = mutableListOf<ParsedRideData>()
            val seenBounds = mutableSetOf<String>()

            // 1. Search for offer anchor nodes (Accept buttons, fare tags, offer prompts)
            val anchorNodes = mutableListOf<AccessibilityNodeInfo>()
            findOfferAnchors(root, anchorNodes)

            for (anchor in anchorNodes) {
                val cardNode = findEnclosingCardContainer(anchor) ?: anchor
                val bounds = android.graphics.Rect()
                cardNode.getBoundsInScreen(bounds)

                // Deduplicate by vertical screen position
                val boundsKey = "${bounds.top}_${bounds.bottom}"
                if (seenBounds.contains(boundsKey)) {
                    continue
                }
                seenBounds.add(boundsKey)

                val anchorBounds = android.graphics.Rect()
                anchor.getBoundsInScreen(anchorBounds)

                val texts = mutableListOf<String>()
                collectNodeTexts(cardNode, texts)

                val parsed = parseRideScreen(texts, bounds, anchorBounds)
                if (parsed != null) {
                    results.add(parsed)
                }
            }

            // Fallback: If no distinct containers isolated, parse whole screen
            if (results.isEmpty()) {
                val allTexts = mutableListOf<String>()
                collectNodeTexts(root, allTexts)
                val single = parseRideScreen(allTexts, null)
                if (single != null) {
                    results.add(single)
                }
            }

            return results
        }

        private fun findOfferAnchors(node: AccessibilityNodeInfo?, anchors: MutableList<AccessibilityNodeInfo>) {
            if (node == null) return
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val combined = "$text $desc"

            val isAnchor = combined.contains("Accept for", ignoreCase = true) ||
                    combined.contains("قبول مقابل", ignoreCase = true) ||
                    combined.contains("Offer your fare", ignoreCase = true) ||
                    combined.contains("اقترح سعرك", ignoreCase = true) ||
                    Regex("""^(?:EGP|ج\.?م|جنيه)\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(combined) ||
                    Regex("""^\d+\s*(?:EGP|ج\.?م|جنيه)""", RegexOption.IGNORE_CASE).containsMatchIn(combined)

            if (isAnchor) {
                anchors.add(node)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    findOfferAnchors(child, anchors)
                }
            }
        }

        private fun findEnclosingCardContainer(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var curr: AccessibilityNodeInfo? = node
            var bestCandidate: AccessibilityNodeInfo? = null

            while (curr != null) {
                val bounds = android.graphics.Rect()
                curr.getBoundsInScreen(bounds)

                if (bounds.height() in 140..1500 && bounds.width() >= 300) {
                    bestCandidate = curr
                }

                val parent = curr.parent
                if (parent != null) {
                    val pClass = parent.className?.toString() ?: ""
                    if (pClass.contains("RecyclerView", ignoreCase = true) ||
                        pClass.contains("ListView", ignoreCase = true) ||
                        pClass.contains("ViewPager", ignoreCase = true)) {
                        return curr
                    }
                }
                curr = parent
            }
            return bestCandidate
        }

        private fun collectNodeTexts(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
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
                    collectNodeTexts(child, texts)
                }
            }
        }

        fun parseAllRidesFromScreen(texts: List<String>): List<ParsedRideData> {
            val single = parseRideScreen(texts, null)
            return if (single != null) listOf(single) else emptyList()
        }

        fun parseRideScreen(
            texts: List<String>,
            bounds: android.graphics.Rect? = null,
            anchorBounds: android.graphics.Rect? = null
        ): ParsedRideData? {
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
                    counterBids = counterBids.sorted(),
                    screenBounds = bounds,
                    anchorBounds = anchorBounds
                )
            }

            return null
        }
    }
}
