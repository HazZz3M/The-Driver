package com.indrive.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.indrive.assistant.InDriveAssistantApp
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.engine.FareCalculatorEngine

class InDriveAccessibilityService : AccessibilityService() {

    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var scanScheduled = false
    @Volatile private var pendingAutoAcceptFingerprint: String? = null

    // Accessed only on the worker thread.
    private var lastAutoAcceptedFingerprint = ""
    private var lastAutoAcceptTimestamp = 0L

    private val scanRunnable = Runnable {
        scanScheduled = false
        scanActiveWindow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        workerThread = HandlerThread("a11y-ride-parser").apply { start() }
        worker = Handler(workerThread.looper)
        instance = this
        Log.d(TAG, "InDriveAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::worker.isInitialized) return
        val pkg = event.packageName?.toString() ?: return

        when {
            isTargetPackage(pkg, packageName) -> {
                // Throttle with a guaranteed trailing scan: the first event schedules a scan,
                // later events within the window are folded into it, so the final screen state
                // is always the one that gets parsed.
                if (!scanScheduled) {
                    scanScheduled = true
                    worker.postDelayed(scanRunnable, SCAN_DELAY_MS)
                }
            }
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isTransientPackage(pkg, packageName) -> {
                // The driver switched to a different app: hide all ride popups.
                worker.removeCallbacks(scanRunnable)
                scanScheduled = false
                pendingAutoAcceptFingerprint = null
                FloatingHudService.onCardsSync(emptyList())
            }
            // System UI, keyboards and our own overlay windows: keep popups as they are.
        }
    }

    /** Runs on the worker thread. */
    private fun scanActiveWindow() {
        val root = rootInActiveWindow ?: return // transient: keep current popups
        try {
            val pkg = root.packageName?.toString() ?: return
            if (!isTargetPackage(pkg, packageName)) {
                if (!isTransientPackage(pkg, packageName)) FloatingHudService.onCardsSync(emptyList())
                return
            }
            val rides = findRideCards(root)
            FloatingHudService.onCardsSync(rides)
            maybeScheduleAutoAccept(rides)
        } catch (e: Exception) {
            Log.e(TAG, "Error while scanning inDrive screen", e)
        } finally {
            root.recycle()
        }
    }

    // =========================================================================
    // AUTO-ACCEPT
    // =========================================================================

    private fun maybeScheduleAutoAccept(rides: List<ParsedRideData>) {
        if (rides.isEmpty()) return
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        if (!settings.autoAcceptEnabled) return
        if (pendingAutoAcceptFingerprint != null) return
        if (System.currentTimeMillis() - lastAutoAcceptTimestamp < AUTO_ACCEPT_COOLDOWN_MS) return

        val candidate = rides.firstOrNull { it.fingerprint != lastAutoAcceptedFingerprint && qualifiesForAutoAccept(it, settings) }
            ?: return

        pendingAutoAcceptFingerprint = candidate.fingerprint
        val delayMs = (settings.autoAcceptDelaySeconds * 1000).toLong().coerceIn(300L, 8000L)
        Log.i(TAG, "Auto-accept armed for ${candidate.fingerprint} in ${delayMs}ms")
        worker.postDelayed({ executeAutoAccept(candidate.fingerprint) }, delayMs)
    }

    /**
     * Re-reads the screen right before acting, confirms the same card is still visible and still
     * qualifies, then clicks that card's own Accept button node. Never taps raw coordinates.
     */
    private fun executeAutoAccept(fingerprint: String) {
        pendingAutoAcceptFingerprint = null
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        if (!settings.autoAcceptEnabled) return

        val root = rootInActiveWindow ?: return
        try {
            if (!isTargetPackage(root.packageName?.toString() ?: "", packageName)) return
            val card = findRideCards(root).firstOrNull { it.fingerprint == fingerprint }
            if (card == null) {
                Log.i(TAG, "Auto-accept aborted: card is no longer on screen")
                return
            }
            if (!qualifiesForAutoAccept(card, settings)) {
                Log.i(TAG, "Auto-accept aborted: card no longer qualifies")
                return
            }
            val cardBounds = card.screenBounds ?: card.anchorBounds
            if (cardBounds == null) {
                Log.i(TAG, "Auto-accept aborted: card bounds unknown")
                return
            }
            val clicked = clickAcceptButtonInside(root, cardBounds)
            if (clicked) {
                lastAutoAcceptedFingerprint = fingerprint
                lastAutoAcceptTimestamp = System.currentTimeMillis()
                val analysis = analyze(card, settings)
                mainHandler.post {
                    Toast.makeText(
                        applicationContext,
                        "تم القبول التلقائي للطلب (ربح متوقع: ${analysis.netProfit.toInt()} ج.م)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.w(TAG, "Auto-accept aborted: no Accept button found inside the card")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-accept failed", e)
        } finally {
            root.recycle()
        }
    }

    private fun qualifiesForAutoAccept(card: ParsedRideData, settings: DriverSettings): Boolean {
        if (card.distanceEstimated) return false
        if (settings.autoAcceptRejectBlacklisted &&
            (settings.isBlacklisted(card.pickupAddress) || settings.isBlacklisted(card.dropoffAddress))
        ) return false
        if (settings.autoAcceptOnlyFavoriteZones &&
            !settings.isFavorite(card.pickupAddress) && !settings.isFavorite(card.dropoffAddress)
        ) return false
        if (card.pickupDistanceKm > settings.autoAcceptMaxPickupKm) return false

        val analysis = analyze(card, settings)
        return analysis.netProfit >= settings.autoAcceptMinNetProfit &&
            analysis.profitPerKm >= settings.autoAcceptMinProfitPerKm
    }

    private fun analyze(card: ParsedRideData, settings: DriverSettings) = FareCalculatorEngine.analyze(
        pickupDistanceKm = card.pickupDistanceKm,
        tripDistanceKm = card.tripDistanceKm,
        estimatedMinutes = FareCalculatorEngine.estimateMinutes(card.totalDistanceKm),
        offeredFare = card.fare,
        settings = settings,
        pickupAddress = card.pickupAddress,
        dropoffAddress = card.dropoffAddress
    )

    /** Finds an "Accept" button whose centre lies inside [cardBounds] and performs ACTION_CLICK on it. */
    private fun clickAcceptButtonInside(root: AccessibilityNodeInfo, cardBounds: Rect): Boolean {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        findAcceptNodes(root, candidates)
        try {
            for (node in candidates) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (!cardBounds.contains(r.centerX(), r.centerY())) continue
                var target: AccessibilityNodeInfo? = node
                var depth = 0
                while (target != null && !target.isClickable && depth < 4) {
                    target = target.parent
                    depth++
                }
                if (target != null && target.isClickable && target.isEnabled) {
                    return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            return false
        } finally {
            // The root is owned (and recycled) by the caller.
            candidates.filter { it !== root }.forEach { it.recycle() }
        }
    }

    private fun findAcceptNodes(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val label = normalizeArabicDigits("${node.text ?: ""} ${node.contentDescription ?: ""}".trim())
        if (label.isNotEmpty() && ACCEPT_BUTTON_REGEX.containsMatchIn(label)) out.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAcceptNodes(child, out)
            if (out.none { it === child }) child.recycle()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "InDriveAccessibilityService interrupted")
    }

    override fun onDestroy() {
        if (::workerThread.isInitialized) {
            worker.removeCallbacksAndMessages(null)
            workerThread.quitSafely()
        }
        if (instance == this) {
            instance = null
        }
        FloatingHudService.onCardsSync(emptyList())
        super.onDestroy()
    }

    companion object {
        private const val TAG = "InDriveA11y"
        private const val SCAN_DELAY_MS = 200L
        private const val AUTO_ACCEPT_COOLDOWN_MS = 5000L

        @Volatile
        private var instance: InDriveAccessibilityService? = null

        // ---- Precompiled patterns (previously rebuilt for every node on every scan) ----
        private val CURRENCY_PREFIX_AMOUNT = Regex("""(?:EGP|ج\.?م|جنيه|جم)\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        private val CURRENCY_SUFFIX_AMOUNT = Regex("""(\d+(?:[.,]\d+)?)\s*(?:EGP|ج\.?م|جنيه|جم)""", RegexOption.IGNORE_CASE)
        private val TILDE_DISTANCE = Regex("""~\s*\d+(?:[.,]\d+)?\s*(?:km|كم|متر|م)""", RegexOption.IGNORE_CASE)
        private val ACCEPT_FOR_FARE = Regex("""(?:Accept for EGP|Accept for|قبول مقابل)\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        private val ACCEPT_BUTTON_REGEX = Regex("""^(?:Accept|قبول)(?:\s|$)|Accept for|قبول مقابل""", RegexOption.IGNORE_CASE)
        private val COUNTER_BID = Regex("""^EGP\s*(\d+)$""", RegexOption.IGNORE_CASE)
        private val RATING = Regex("""^(\d\.\d)(?:\s*\(\d+\))?$""")
        private val KM_SUFFIX = Regex("""(?:~)?\s*(\d+(?:[.,]\d+)?)\s*(?:km|كم|ك\.?م)""", RegexOption.IGNORE_CASE)
        private val KM_PREFIX = Regex("""(?:km|كم|ك\.?م)\s*(?:~)?\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        private val METERS = Regex("""(?:~)?\s*(\d+)\s*(?:متر|م|m)(?:\s|$|[^\p{L}\d])""", RegexOption.IGNORE_CASE)
        private val ANCHOR_PHRASES = listOf("Accept for", "قبول مقابل", "Offer your fare", "اقترح سعرك", "تقديم عرض", "قدم عرض")

        // Placeholders used only when a distance cannot be read; such cards are flagged as estimated.
        private const val PLACEHOLDER_PICKUP_KM = 1.0
        private const val PLACEHOLDER_TRIP_KM = 4.0

        /** inDrive ships as sinet.startup.inDriver (and regional "indriver" variants). Never our own app. */
        fun isTargetPackage(pkg: String, ownPackage: String): Boolean {
            if (pkg.isEmpty() || pkg == ownPackage) return false
            return pkg.startsWith("sinet.startup", ignoreCase = true) ||
                pkg.contains("indriver", ignoreCase = true) ||
                pkg.contains("indrive", ignoreCase = true)
        }

        /** Windows that briefly take focus without the driver leaving inDrive. */
        private fun isTransientPackage(pkg: String, ownPackage: String): Boolean {
            return pkg == ownPackage ||
                pkg == "com.android.systemui" ||
                pkg.contains("inputmethod", ignoreCase = true) ||
                pkg.contains("keyboard", ignoreCase = true)
        }

        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${InDriveAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                if (colonSplitter.next().equals(expectedServiceName, ignoreCase = true)) return true
            }
            return false
        }

        fun normalizeArabicDigits(str: String): String {
            val sb = StringBuilder(str.length)
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
            val parsedCards = mutableListOf<ParsedRideData>()
            val seenBounds = mutableSetOf<String>()

            // 1. Collect screen texts for contextual fallback & map pins
            val allScreenTexts = mutableListOf<String>()
            collectNodeTexts(root, allScreenTexts)
            val normalizedAll = allScreenTexts.map { normalizeArabicDigits(it.trim()) }

            // Incoming-offer modal (has Skip / تخطي)
            val hasSkipButton = normalizedAll.any { isSkipLabel(it) }

            // 2. Search for individual offer anchor nodes across the screen
            val anchorNodes = mutableListOf<AccessibilityNodeInfo>()
            findOfferAnchors(root, anchorNodes)

            val anchorCenters = anchorNodes.map { node ->
                val r = Rect()
                node.getBoundsInScreen(r)
                Point(r.centerX(), r.centerY())
            }

            for ((index, anchor) in anchorNodes.withIndex()) {
                val anchorBounds = Rect()
                anchor.getBoundsInScreen(anchorBounds)

                val otherCenters = anchorCenters.filterIndexed { i, _ -> i != index }
                val cardNode = findEnclosingCardContainer(anchor, otherCenters) ?: anchor
                val bounds = Rect()
                cardNode.getBoundsInScreen(bounds)

                // Ensure bounds are tied to the card item, not the entire root window
                val cardBounds = if (bounds.top > 60 && bounds.height() in 100..1400) {
                    bounds
                } else if (anchorBounds.top > 60) {
                    val top = (anchorBounds.top - 180).coerceAtLeast(80)
                    Rect(anchorBounds.left, top, anchorBounds.right, anchorBounds.bottom)
                } else {
                    bounds
                }

                val boundsKey = if (cardBounds.height() in 100..1400) {
                    "${cardBounds.left}_${cardBounds.top}_${cardBounds.bottom}"
                } else {
                    "${anchorBounds.left}_${anchorBounds.top}_${anchorBounds.bottom}"
                }
                if (!seenBounds.add(boundsKey)) continue

                val texts = mutableListOf<String>()
                collectNodeTexts(cardNode, texts)

                parseRideScreen(texts, cardBounds, anchorBounds, if (hasSkipButton) allScreenTexts else null)
                    ?.let { parsedCards.add(it) }
            }
            // The root is owned (and recycled) by the caller.
            anchorNodes.filter { it !== root }.forEach { it.recycle() }

            // Fallback: If no distinct containers isolated, parse whole screen
            if (parsedCards.isEmpty()) {
                val rootBounds = Rect()
                root.getBoundsInScreen(rootBounds)
                val fallbackBounds = if (hasSkipButton) {
                    Rect(0, (rootBounds.height() * 0.45).toInt(), rootBounds.width(), rootBounds.height())
                } else {
                    Rect(0, 320, rootBounds.width(), rootBounds.height())
                }
                parseRideScreen(allScreenTexts, fallbackBounds, null, allScreenTexts)?.let { parsedCards.add(it) }
            }

            // Give identical-looking cards distinct identities so each keeps its own popup.
            val seenFingerprints = mutableMapOf<String, Int>()
            return parsedCards.map { card ->
                val n = seenFingerprints.getOrDefault(card.fingerprint, 0)
                seenFingerprints[card.fingerprint] = n + 1
                if (n == 0) card else card.copy(duplicateIndex = n)
            }
        }

        private fun isSkipLabel(s: String) = s.equals("Skip", ignoreCase = true) || s.equals("تخطي", ignoreCase = true)

        private fun findOfferAnchors(node: AccessibilityNodeInfo, anchors: MutableList<AccessibilityNodeInfo>) {
            val norm = normalizeArabicDigits("${node.text ?: ""} ${node.contentDescription ?: ""}".trim())

            val isAnchor = norm.isNotEmpty() && (
                ANCHOR_PHRASES.any { norm.contains(it, ignoreCase = true) } ||
                    isSkipLabel(norm) ||
                    CURRENCY_PREFIX_AMOUNT.containsMatchIn(norm) ||
                    CURRENCY_SUFFIX_AMOUNT.containsMatchIn(norm) ||
                    TILDE_DISTANCE.containsMatchIn(norm)
                )
            if (isAnchor) anchors.add(node)

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findOfferAnchors(child, anchors)
                if (anchors.none { it === child }) child.recycle()
            }
        }

        private fun findEnclosingCardContainer(
            node: AccessibilityNodeInfo,
            otherAnchorCenters: List<Point>
        ): AccessibilityNodeInfo? {
            var curr: AccessibilityNodeInfo = node
            var bestCandidate: AccessibilityNodeInfo = node

            while (true) {
                val parent = curr.parent ?: break
                val parentBounds = Rect()
                parent.getBoundsInScreen(parentBounds)

                // If parent contains ANY other anchor on the screen, stop climbing to avoid merging cards
                if (otherAnchorCenters.any { parentBounds.contains(it.x, it.y) }) return bestCandidate

                val pClass = parent.className?.toString() ?: ""
                val isListContainer = parent.isScrollable ||
                    pClass.contains("RecyclerView", ignoreCase = true) ||
                    pClass.contains("ListView", ignoreCase = true) ||
                    pClass.contains("ViewPager", ignoreCase = true) ||
                    pClass.contains("ScrollView", ignoreCase = true)
                if (isListContainer) return curr

                if (parentBounds.height() > 1300) return bestCandidate

                if (parentBounds.height() in 120..1150 && parentBounds.width() >= 250) {
                    bestCandidate = parent
                }
                curr = parent
            }
            return bestCandidate
        }

        private fun collectNodeTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) texts.add(text)
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrEmpty() && desc != text) texts.add(desc)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectNodeTexts(child, texts)
                child.recycle()
            }
        }

        fun parseRideScreen(
            texts: List<String>,
            bounds: Rect? = null,
            anchorBounds: Rect? = null,
            allScreenTexts: List<String>? = null
        ): ParsedRideData? {
            if (texts.isEmpty()) return null

            val normalizedTexts = texts.map { normalizeArabicDigits(it.trim()) }
            val normalizedAll = allScreenTexts?.map { normalizeArabicDigits(it.trim()) } ?: normalizedTexts
            val isModal = normalizedTexts.any { isSkipLabel(it) } || normalizedAll.any { isSkipLabel(it) }

            // 1. Passenger Name & Rating
            var passengerName = ""
            var passengerRating = ""
            val ratingIdx = normalizedTexts.indexOfFirst { RATING.matches(it) }
            if (ratingIdx > 0) {
                val candidateName = texts[ratingIdx - 1].trim()
                if (!candidateName.contains("min", ignoreCase = true) &&
                    !candidateName.contains("km", ignoreCase = true) &&
                    !candidateName.equals("A", ignoreCase = true) &&
                    !candidateName.equals("B", ignoreCase = true) &&
                    candidateName.length in 2..30
                ) {
                    passengerName = candidateName
                }
                passengerRating = texts[ratingIdx]
            }

            // 2. Fare: prefer the explicit "Accept for N" button label
            var fare: Double? = null
            for (t in normalizedTexts) {
                val m = ACCEPT_FOR_FARE.find(t) ?: continue
                fare = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                if (fare != null) break
            }
            if (fare == null) fare = firstCurrencyAmount(normalizedTexts)
            if (fare == null && isModal) fare = firstCurrencyAmount(normalizedAll)

            // 3. Distances
            val textsForDistances = if (isModal) normalizedAll else normalizedTexts
            val distances = extractDistancesFromTexts(textsForDistances)

            // 4. Counter Bids
            val counterBids = mutableListOf<Int>()
            for (t in (if (isModal) normalizedAll else normalizedTexts)) {
                val b = COUNTER_BID.find(t)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (b.toDouble() != fare && !counterBids.contains(b)) counterBids.add(b)
            }

            // 5. Addresses
            var pickupAddress = ""
            var dropoffAddress = ""
            val fairPriceIdx = normalizedTexts.indexOfFirst { it.equals("Fair price", ignoreCase = true) || it.contains("سعر مناسب") }
            if (fairPriceIdx != -1 && fairPriceIdx + 2 < texts.size) {
                pickupAddress = texts[fairPriceIdx + 1]
                dropoffAddress = texts[fairPriceIdx + 2]
            }

            if (fare == null || fare <= 0.0) return null
            return ParsedRideData(
                passengerName = passengerName,
                passengerRating = passengerRating,
                fare = fare,
                pickupDistanceKm = distances.pickupKm ?: PLACEHOLDER_PICKUP_KM,
                tripDistanceKm = distances.tripKm ?: PLACEHOLDER_TRIP_KM,
                distanceEstimated = distances.pickupKm == null || distances.tripKm == null,
                pickupAddress = pickupAddress,
                dropoffAddress = dropoffAddress,
                counterBids = counterBids.sorted(),
                screenBounds = bounds,
                anchorBounds = anchorBounds
            )
        }

        private fun firstCurrencyAmount(texts: List<String>): Double? {
            for (t in texts) {
                val m = CURRENCY_PREFIX_AMOUNT.find(t) ?: CURRENCY_SUFFIX_AMOUNT.find(t) ?: continue
                val v = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                if (v != null) return v
            }
            return null
        }

        private data class Distances(val pickupKm: Double?, val tripKm: Double?)

        private fun extractDistancesFromTexts(normalizedTexts: List<String>): Distances {
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
                if (isNearA && dists.isNotEmpty() && pickupKm == null) pickupKm = dists.first()
                if (isNearB && dists.isNotEmpty() && tripKm == null) {
                    tripKm = dists.firstOrNull { it != pickupKm } ?: dists.first()
                }
            }

            // Strategy 2: Collect all distances across texts
            for (t in normalizedTexts) {
                val isCurrency = t.contains("EGP", ignoreCase = true) || t.contains("ج.م") || t.contains("جنيه") || t.contains("جم")
                if (isCurrency) continue

                val hasTilde = t.contains("~")
                for (d in extractAllDistancesFromString(t)) {
                    if (!allDistances.contains(d)) allDistances.add(d)
                    if (hasTilde) {
                        if (!tildeDistances.contains(d)) tildeDistances.add(d)
                    } else if (!nonTildeDistances.contains(d)) {
                        nonTildeDistances.add(d)
                    }
                }
            }

            // Pickup indicated by tilde (~3.2 km or ~800 m)
            if (pickupKm == null && tildeDistances.isNotEmpty()) pickupKm = tildeDistances.first()

            // Trip is a non-tilde distance
            if (tripKm == null) tripKm = nonTildeDistances.firstOrNull { it != pickupKm }

            // Positional fallback when both are present but unlabeled
            if (pickupKm == null && tripKm == null && allDistances.size >= 2) {
                pickupKm = allDistances[0]
                tripKm = allDistances[1]
            } else if (pickupKm != null && tripKm == null) {
                tripKm = allDistances.firstOrNull { it != pickupKm }
            }

            // Anything still unknown stays null: the caller marks the card as estimated.
            return Distances(pickupKm, tripKm)
        }

        private fun extractAllDistancesFromString(str: String): List<Double> {
            val list = mutableListOf<Double>()
            if (str.isEmpty()) return list

            for (regex in listOf(KM_SUFFIX, KM_PREFIX)) {
                regex.findAll(str).forEach { match ->
                    val v = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (v != null && v in 0.05..150.0 && !list.contains(v)) list.add(v)
                }
            }

            METERS.findAll(str).forEach { match ->
                val meters = match.groupValues[1].toDoubleOrNull()
                if (meters != null && meters in 30.0..999.0) {
                    val km = Math.round((meters / 1000.0) * 10.0) / 10.0
                    if (!list.contains(km)) list.add(km)
                }
            }
            return list
        }
    }
}
