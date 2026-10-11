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
            val sb = java.lang.StringBuilder()
            for (c in str) {
                when (c) {
                    in '٠'..'٩' -> sb.append(c - '٠')
                    in '۰'..'۹' -> sb.append(c - '۰')
                    '٫', '،' -> sb.append('.')
                    else -> sb.append(c)
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

            val anchorCenters = anchorNodes.map { node ->
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                android.graphics.Point(r.centerX(), r.centerY())
            }

            for ((index, anchor) in anchorNodes.withIndex()) {
                val anchorBounds = android.graphics.Rect()
                anchor.getBoundsInScreen(anchorBounds)

                val otherCenters = anchorCenters.filterIndexed { i, _ -> i != index }
                val cardNode = findEnclosingCardContainer(anchor, otherCenters) ?: anchor
                val bounds = android.graphics.Rect()
                cardNode.getBoundsInScreen(bounds)

                // Deduplicate by card vertical bounds or anchor position
                val boundsKey = if (bounds.height() in 120..1300) {
                    "${bounds.left}_${bounds.top}_${bounds.bottom}"
                } else {
                    "${anchorBounds.left}_${anchorBounds.top}_${anchorBounds.bottom}"
                }

                if (seenBounds.contains(boundsKey)) {
                    continue
                }
                seenBounds.add(boundsKey)

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

        private fun findEnclosingCardContainer(
            node: AccessibilityNodeInfo,
            otherAnchorCenters: List<android.graphics.Point>
        ): AccessibilityNodeInfo? {
            var curr: AccessibilityNodeInfo? = node
            var bestCandidate: AccessibilityNodeInfo? = node

            while (curr != null) {
                val parent = curr.parent ?: break
                val parentBounds = android.graphics.Rect()
                parent.getBoundsInScreen(parentBounds)

                // If parent contains ANY other anchor on the screen, stop climbing to avoid merging cards
                val containsOtherAnchor = otherAnchorCenters.any { pt ->
                    parentBounds.contains(pt.x, pt.y)
                }
                if (containsOtherAnchor) {
                    return bestCandidate
                }

                // If parent is a scroll container (RecyclerView, ListView, Compose LazyColumn, etc.)
                val pClass = parent.className?.toString() ?: ""
                val isListContainer = parent.isScrollable ||
                    pClass.contains("RecyclerView", ignoreCase = true) ||
                    pClass.contains("ListView", ignoreCase = true) ||
                    pClass.contains("ViewPager", ignoreCase = true) ||
                    pClass.contains("ScrollView", ignoreCase = true)

                if (isListContainer) {
                    return curr
                }

                if (parentBounds.height() > 1300) {
                    return bestCandidate
                }

                if (parentBounds.height() in 120..1150 && parentBounds.width() >= 250) {
                    bestCandidate = parent
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

            // 3. Extract Distances (Supports km, meters, Arabic numerals, RTL reversals)
            val distances = mutableListOf<Double>()
            for (t in normalizedTexts) {
                val isCurrency = t.contains("EGP", ignoreCase = true) ||
                        t.contains("ج.م") || t.contains("جنيه") || t.contains("جم")

                // Pattern 1: standard km ("1.8 km", "~1.8 كم", "1.8كم")
                val kmRegex1 = Regex("""(?:~)?\s*(\d+(?:[.,]\d+)?)\s*(?:km|كم)\b""", RegexOption.IGNORE_CASE)
                for (m in kmRegex1.findAll(t)) {
                    val d = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (d != null && d > 0.05 && !distances.contains(d)) {
                        distances.add(d)
                    }
                }

                // Pattern 2: RTL reversed km ("كم 1.8", "km 1.8")
                val kmRegex2 = Regex("""\b(?:km|كم)\s*(?:~)?\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
                for (m in kmRegex2.findAll(t)) {
                    val d = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (d != null && d > 0.05 && !distances.contains(d)) {
                        distances.add(d)
                    }
                }

                // Pattern 3: Meters (e.g., "600 م", "~600 م", "600 متر", "500m", "م 600")
                if (!isCurrency) {
                    val mRegex1 = Regex("""(?:~)?\s*(\d+)\s*(?:متر|م|m)\b""", RegexOption.IGNORE_CASE)
                    for (m in mRegex1.findAll(t)) {
                        val meters = m.groupValues[1].toDoubleOrNull()
                        if (meters != null && meters in 30.0..999.0) {
                            val km = (Math.round((meters / 1000.0) * 10.0) / 10.0)
                            if (!distances.contains(km)) {
                                distances.add(km)
                            }
                        }
                    }

                    val mRegex2 = Regex("""\b(?:متر|م)\s*(?:~)?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
                    for (m in mRegex2.findAll(t)) {
                        val meters = m.groupValues[1].toDoubleOrNull()
                        if (meters != null && meters in 30.0..999.0) {
                            val km = (Math.round((meters / 1000.0) * 10.0) / 10.0)
                            if (!distances.contains(km)) {
                                distances.add(km)
                            }
                        }
                    }
                }
            }

            var pickupKm = 1.2
            var tripKm = 5.0

            if (distances.size >= 2) {
                if (distances.size >= 3) {
                    val maxD = distances.maxOrNull() ?: distances[0]
                    val parts = distances.filter { it != maxD }
                    if (parts.size >= 2 && Math.abs(maxD - parts.sum()) < 0.4) {
                        pickupKm = parts[0]
                        tripKm = parts[1]
                    } else {
                        pickupKm = distances[0]
                        tripKm = distances[1]
                    }
                } else {
                    val d1 = distances[0]
                    val d2 = distances[1]
                    if (d1 > 7.0 && d2 <= 5.0) {
                        pickupKm = d2
                        tripKm = d1
                    } else {
                        pickupKm = d1
                        tripKm = d2
                    }
                }
            } else if (distances.size == 1) {
                tripKm = distances[0]
                pickupKm = 1.0
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
