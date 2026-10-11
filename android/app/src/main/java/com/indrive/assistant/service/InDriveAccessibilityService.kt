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

            // 1. Collect screen texts for contextual fallback & map pins
            val allScreenTexts = mutableListOf<String>()
            collectNodeTexts(root, allScreenTexts)
            val normalizedAll = allScreenTexts.map { normalizeArabicDigits(it.trim()) }

            // Check if this screen is an Incoming Offer Modal (has Skip / تخطي)
            val hasSkipButton = normalizedAll.any { it.equals("Skip", ignoreCase = true) || it.equals("تخطي", ignoreCase = true) }

            // 2. Search for individual offer anchor nodes across the screen
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

                // Ensure bounds are tied to the card item, not the entire root window
                val cardBounds = if (bounds.top > 60 && bounds.height() in 100..1400) {
                    bounds
                } else if (anchorBounds.top > 60) {
                    // Fallback using anchor position so HUD never jumps to 0,0
                    val top = (anchorBounds.top - 180).coerceAtLeast(80)
                    android.graphics.Rect(anchorBounds.left, top, anchorBounds.right, anchorBounds.bottom)
                } else {
                    bounds
                }

                // Deduplicate by card vertical bounds or anchor position
                val boundsKey = if (cardBounds.height() in 100..1400) {
                    "${cardBounds.left}_${cardBounds.top}_${cardBounds.bottom}"
                } else {
                    "${anchorBounds.left}_${anchorBounds.top}_${anchorBounds.bottom}"
                }

                if (seenBounds.contains(boundsKey)) {
                    continue
                }
                seenBounds.add(boundsKey)

                val texts = mutableListOf<String>()
                collectNodeTexts(cardNode, texts)

                val parsed = parseRideScreen(texts, cardBounds, anchorBounds, if (hasSkipButton) allScreenTexts else null)
                if (parsed != null) {
                    results.add(parsed)
                }
            }

            // Fallback: If no distinct containers isolated, parse whole screen
            if (results.isEmpty()) {
                val rootBounds = android.graphics.Rect()
                root.getBoundsInScreen(rootBounds)
                val fallbackBounds = if (hasSkipButton) {
                    android.graphics.Rect(0, (rootBounds.height() * 0.45).toInt(), rootBounds.width(), rootBounds.height())
                } else {
                    android.graphics.Rect(0, 320, rootBounds.width(), rootBounds.height())
                }
                val single = parseRideScreen(allScreenTexts, fallbackBounds, null, allScreenTexts)
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
            val norm = normalizeArabicDigits(combined.trim())

            val isAnchor = norm.contains("Accept for", ignoreCase = true) ||
                    norm.contains("قبول مقابل", ignoreCase = true) ||
                    norm.contains("Offer your fare", ignoreCase = true) ||
                    norm.contains("اقترح سعرك", ignoreCase = true) ||
                    norm.contains("تقديم عرض", ignoreCase = true) ||
                    norm.contains("قدم عرض", ignoreCase = true) ||
                    norm.equals("Skip", ignoreCase = true) ||
                    norm.equals("تخطي", ignoreCase = true) ||
                    Regex("""(?:EGP|ج\.?م|جنيه|جم)\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(norm) ||
                    Regex("""\d+\s*(?:EGP|ج\.?م|جنيه|جم)""", RegexOption.IGNORE_CASE).containsMatchIn(norm) ||
                    Regex("""~\s*\d+(?:[.,]\d+)?\s*(?:km|كم|متر|م)""", RegexOption.IGNORE_CASE).containsMatchIn(norm)

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
            val single = parseRideScreen(texts, null, null, texts)
            return if (single != null) listOf(single) else emptyList()
        }

        fun parseRideScreen(
            texts: List<String>,
            bounds: android.graphics.Rect? = null,
            anchorBounds: android.graphics.Rect? = null,
            allScreenTexts: List<String>? = null
        ): ParsedRideData? {
            if (texts.isEmpty()) return null

            val normalizedTexts = texts.map { normalizeArabicDigits(it.trim()) }
            val normalizedAll = allScreenTexts?.map { normalizeArabicDigits(it.trim()) } ?: normalizedTexts

            val isModal = normalizedTexts.any { it.equals("Skip", ignoreCase = true) || it.equals("تخطي", ignoreCase = true) } ||
                    normalizedAll.any { it.equals("Skip", ignoreCase = true) || it.equals("تخطي", ignoreCase = true) }

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
                    val fareMatch = Regex("""(?:EGP|ج\.?م|جنيه|جم)\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(t)
                        ?: Regex("""(\d+(?:[.,]\d+)?)\s*(?:EGP|ج\.?م|جنيه|جم)""", RegexOption.IGNORE_CASE).find(t)
                    if (fareMatch != null) {
                        fare = fareMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                        break
                    }
                }
            }

            if (fare == null && isModal) {
                for (t in normalizedAll) {
                    val fareMatch = Regex("""(?:EGP|ج\.?م|جنيه|جم)\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(t)
                        ?: Regex("""(\d+(?:[.,]\d+)?)\s*(?:EGP|ج\.?م|جنيه|جم)""", RegexOption.IGNORE_CASE).find(t)
                    if (fareMatch != null) {
                        fare = fareMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                        break
                    }
                }
            }

            // 3. Extract Distances (Supports km, meters, Arabic numerals, RTL reversals, Point A / Point B pins)
            val textsForDistances = if (isModal) normalizedAll else normalizedTexts
            val (pickupKm, tripKm) = extractDistancesFromTexts(textsForDistances)

            // 4. Extract Counter Bids
            val counterBids = mutableListOf<Int>()
            val textsForBids = if (isModal) normalizedAll else normalizedTexts
            for (t in textsForBids) {
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

        private fun extractDistancesFromTexts(normalizedTexts: List<String>): Pair<Double, Double> {
            var pickupKm: Double? = null
            var tripKm: Double? = null

            val allDistances = mutableListOf<Double>()
            val tildeDistances = mutableListOf<Double>()
            val nonTildeDistances = mutableListOf<Double>()

            // Strategy 1: Map Marker association (A = pickup, B = trip)
            for (i in normalizedTexts.indices) {
                val t = normalizedTexts[i]
                val prev = if (i > 0) normalizedTexts[i - 1] else ""
                val next = if (i < normalizedTexts.size - 1) normalizedTexts[i + 1] else ""

                val isNearA = t.equals("A", ignoreCase = true) || prev.equals("A", ignoreCase = true) || next.equals("A", ignoreCase = true)
                val isNearB = t.equals("B", ignoreCase = true) || prev.equals("B", ignoreCase = true) || next.equals("B", ignoreCase = true)

                val dists = extractAllDistancesFromString(t) + extractAllDistancesFromString(prev) + extractAllDistancesFromString(next)
                if (isNearA && dists.isNotEmpty()) {
                    if (pickupKm == null) pickupKm = dists.first()
                }
                if (isNearB && dists.isNotEmpty()) {
                    if (tripKm == null) tripKm = dists.first()
                }
            }

            // Strategy 2: Collect all distances across texts
            for (t in normalizedTexts) {
                val isCurrency = t.contains("EGP", ignoreCase = true) ||
                        t.contains("ج.م") || t.contains("جنيه") || t.contains("جم")
                if (isCurrency) continue

                val hasTilde = t.contains("~")
                val found = extractAllDistancesFromString(t)
                for (d in found) {
                    if (!allDistances.contains(d)) {
                        allDistances.add(d)
                    }
                    if (hasTilde && !tildeDistances.contains(d)) {
                        tildeDistances.add(d)
                    } else if (!hasTilde && !nonTildeDistances.contains(d)) {
                        nonTildeDistances.add(d)
                    }
                }
            }

            // Pickup indicated by tilde (~3.2 km or ~800 m)
            if (pickupKm == null && tildeDistances.isNotEmpty()) {
                pickupKm = tildeDistances.first()
            }

            // Trip is non-tilde distance
            if (tripKm == null) {
                val candidates = nonTildeDistances.filter { it != pickupKm }
                if (candidates.isNotEmpty()) {
                    tripKm = candidates.first()
                }
            }

            // Fallback resolution
            if (pickupKm == null && allDistances.size >= 2) {
                pickupKm = allDistances[0]
                tripKm = allDistances[1]
            } else if (pickupKm != null && tripKm == null && allDistances.size >= 2) {
                tripKm = allDistances.firstOrNull { it != pickupKm } ?: 3.5
            } else if (pickupKm == null && allDistances.size == 1) {
                pickupKm = allDistances[0]
                tripKm = 3.5
            } else if (tripKm == null && allDistances.size == 1) {
                tripKm = allDistances[0]
                pickupKm = 1.0
            }

            return Pair(pickupKm ?: 1.0, tripKm ?: 4.0)
        }

        private fun extractAllDistancesFromString(str: String): List<Double> {
            val list = mutableListOf<Double>()
            if (str.isEmpty()) return list

            // 1. Matches like 3.2 km, 3,2 كم, ~3.2 km, 11 min 2,3 km
            val kmRegex = Regex("""(?:~)?\s*(\d+(?:[.,]\d+)?)\s*(?:km|كم|ك\.?م)""", RegexOption.IGNORE_CASE)
            kmRegex.findAll(str).forEach { match ->
                val v = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                if (v != null && v in 0.05..150.0 && !list.contains(v)) {
                    list.add(v)
                }
            }

            // 2. Prefix format: km 3.2, كم 3.2
            val prefixKmRegex = Regex("""(?:km|كم|ك\.?م)\s*(?:~)?\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
            prefixKmRegex.findAll(str).forEach { match ->
                val v = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                if (v != null && v in 0.05..150.0 && !list.contains(v)) {
                    list.add(v)
                }
            }

            // 3. Meters: 600 م, 600 متر, ~800m
            val mRegex = Regex("""(?:~)?\s*(\d+)\s*(?:متر|م|m)(?:\s|$|[^\p{L}\d])""", RegexOption.IGNORE_CASE)
            mRegex.findAll(str).forEach { match ->
                val meters = match.groupValues[1].toDoubleOrNull()
                if (meters != null && meters in 30.0..999.0) {
                    val km = Math.round((meters / 1000.0) * 10.0) / 10.0
                    if (!list.contains(km)) {
                        list.add(km)
                    }
                }
            }

            return list
        }
    }
}
