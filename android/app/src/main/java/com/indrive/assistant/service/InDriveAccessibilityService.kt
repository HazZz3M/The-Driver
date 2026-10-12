package com.indrive.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.indrive.assistant.InDriveAssistantApp
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.engine.FareCalculatorEngine
import java.io.File
import java.util.concurrent.Executor

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
            if (captureRequested) {
                captureRequested = false
                saveDiagnosticDump(root, pkg, rides)
            }
            FloatingHudService.onCardsSync(rides)
            maybeScheduleAutoAccept(rides)
            maybeReadTripFromMap(root, rides)
        } catch (e: Exception) {
            Log.e(TAG, "Error while scanning inDrive screen", e)
        } finally {
            root.recycle()
        }
    }

    // =========================================================================
    // TRIP LENGTH FROM THE MAP (OCR)
    //
    // inDrive draws "11 min / 2,7 km" as an image inside a map info-window, exposed to
    // accessibility only as an opaque id. On the Ride-request screen we take one screenshot
    // (AccessibilityService.takeScreenshot, Android 11+), crop the info-window nearest to the
    // destination marker (point B) and read it with on-device ML Kit. The result goes into the
    // trip memory, so both the request screen and that ride's list card show the real length.
    // =========================================================================

    private val workerExecutor = Executor { command -> if (::worker.isInitialized) worker.post(command) }
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    @Volatile private var ocrInFlight = false
    private val ocrAttempts = HashMap<String, Int>() // worker thread only

    private fun maybeReadTripFromMap(root: AccessibilityNodeInfo, rides: List<ParsedRideData>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || ocrInFlight) return
        if (!InDriveAssistantApp.instance.repository.getHudPrefs().readMapDistance) return
        val card = rides.singleOrNull() ?: return
        if (card.tripKnown) return
        val key = rideKey(card) ?: return
        val attempts = ocrAttempts[key] ?: 0
        if (attempts >= MAX_OCR_ATTEMPTS) return

        val markers = findMapMarkers(root)
        val pointB = markers.pointB ?: return
        if (markers.infoWindows.isEmpty()) return
        // The info-window closest to the destination marker carries the trip length.
        val tripWindow = markers.infoWindows.minByOrNull { distanceSq(it, pointB) } ?: return

        ocrAttempts[key] = attempts + 1
        if (ocrAttempts.size > 100) ocrAttempts.clear()
        ocrInFlight = true
        captureAndRead(key, Rect(tripWindow))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureAndRead(key: String, region: Rect) {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, workerExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val crop = try {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        software?.let { cropSafely(it, region) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Screenshot decode failed", e)
                        null
                    }
                    if (crop == null) {
                        ocrInFlight = false
                        return
                    }
                    textRecognizer.process(InputImage.fromBitmap(crop, 0))
                        .addOnSuccessListener(workerExecutor) { visionText ->
                            val raw = visionText.text.replace('\n', ' ')
                            val km = extractTripKm(raw)
                            lastOcrLog = "region=${region.toShortString()} text='$raw' → trip=${km ?: "?"}"
                            if (km != null) {
                                storeTripMemory(key, km)
                                ocrAttempts.remove(key)
                                // Re-parse now so the badge switches from "≈" to the real value.
                                if (!scanScheduled) {
                                    scanScheduled = true
                                    worker.post(scanRunnable)
                                }
                            }
                            ocrInFlight = false
                        }
                        .addOnFailureListener(workerExecutor) { e ->
                            lastOcrLog = "region=${region.toShortString()} OCR failed: ${e.message}"
                            ocrInFlight = false
                        }
                }

                override fun onFailure(errorCode: Int) {
                    // e.g. ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT: the next scan retries.
                    lastOcrLog = "screenshot failed: code=$errorCode"
                    ocrInFlight = false
                }
            })
        } catch (e: Exception) {
            lastOcrLog = "screenshot unavailable: ${e.message}"
            ocrInFlight = false
        }
    }

    private fun cropSafely(bitmap: Bitmap, region: Rect): Bitmap? {
        val r = Rect(region)
        if (!r.intersect(0, 0, bitmap.width, bitmap.height) || r.width() < 8 || r.height() < 8) return null
        return Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
    }

    // =========================================================================
    // DIAGNOSTIC CAPTURE (lets the driver send us the real structure of an inDrive screen)
    // =========================================================================

    private fun saveDiagnosticDump(root: AccessibilityNodeInfo, pkg: String, rides: List<ParsedRideData>) {
        val sb = StringBuilder()
        sb.append("The Driver screen dump | pkg=").append(pkg)
            .append(" | time=").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
            .append('\n')
        sb.append("== PARSED CARDS (").append(rides.size).append(") ==\n")
        rides.forEachIndexed { i, r ->
            sb.append("#").append(i + 1)
                .append(" fare=").append(r.fare)
                .append(" pickup=").append(if (r.pickupKnown) r.pickupDistanceKm.toString() else "?")
                .append(" trip=").append(if (r.tripKnown) r.tripDistanceKm.toString() else "?")
                .append(" name='").append(r.passengerName).append("'")
                .append(" from='").append(r.pickupAddress).append("'")
                .append(" to='").append(r.dropoffAddress).append("'")
                .append(" candidates=").append(r.addressCandidates)
                .append(" bounds=").append(r.screenBounds?.toShortString())
                .append('\n')
        }
        sb.append("== MAP OCR ==\n").append(lastOcrLog).append('\n')
        sb.append("== NODE TREE ==\n")
        val count = intArrayOf(0)
        dumpNode(root, 0, sb, count)
        if (count[0] >= MAX_DUMP_NODES) sb.append("... (truncated at ").append(MAX_DUMP_NODES).append(" nodes)\n")

        val text = sb.toString()
        lastDump = text
        try {
            File(filesDir, DUMP_FILE).writeText(text)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write diagnostic dump", e)
        }
        mainHandler.post {
            Toast.makeText(applicationContext, "تم التقاط الشاشة للتشخيص (${rides.size} كارت). ارجع للتطبيق لنسخها.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int, sb: StringBuilder, count: IntArray) {
        if (count[0] >= MAX_DUMP_NODES) return
        count[0]++
        val r = Rect()
        node.getBoundsInScreen(r)
        sb.append("  ".repeat(depth.coerceAtMost(30)))
            .append(node.className?.toString()?.substringAfterLast('.') ?: "?")
        node.viewIdResourceName?.let { sb.append(" id=").append(it.substringAfterLast('/')) }
        node.text?.let { sb.append(" t='").append(it.toString().replace('\n', '⏎')).append("'") }
        node.contentDescription?.let { sb.append(" d='").append(it.toString().replace('\n', '⏎')).append("'") }
        sb.append(" [").append(r.left).append(',').append(r.top).append(',').append(r.right).append(',').append(r.bottom).append(']')
        if (node.isClickable) sb.append(" click")
        if (node.isScrollable) sb.append(" scroll")
        sb.append('\n')
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNode(child, depth + 1, sb, count)
            child.recycle()
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
            if (clickAcceptButtonInside(root, cardBounds)) {
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
        private const val MAX_DUMP_NODES = 900
        const val DUMP_FILE = "inDrive_screen_dump.txt"

        @Volatile
        private var instance: InDriveAccessibilityService? = null

        /** Set by the app / HUD notification; the next inDrive scan writes a diagnostic dump. */
        @Volatile
        var captureRequested = false

        @Volatile
        private var lastDump: String? = null

        fun readLastDump(context: Context): String? =
            lastDump ?: File(context.filesDir, DUMP_FILE).takeIf { it.exists() }?.readText()

        // ---- Precompiled patterns ----
        private const val CURRENCY = """(?:EGP|E£|ج\.?\s?م|جنيه|جم)"""
        private val CURRENCY_PREFIX_AMOUNT = Regex("""$CURRENCY\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        private val CURRENCY_SUFFIX_AMOUNT = Regex("""(\d+(?:[.,]\d+)?)\s*$CURRENCY""", RegexOption.IGNORE_CASE)
        private val ACCEPT_FOR_FARE = Regex("""(?:Accept for|قبول مقابل|قبول بـ?)\s*(?:$CURRENCY)?\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        private val ACCEPT_BUTTON_REGEX = Regex("""^(?:Accept|قبول)(?:\s|$)|Accept for|قبول مقابل""", RegexOption.IGNORE_CASE)
        private val COUNTER_BID = Regex("""^(?:EGP|ج\.?م)?\s*(\d+)\s*(?:EGP|ج\.?م)?$""", RegexOption.IGNORE_CASE)
        private val RATING = Regex("""^(\d\.\d{1,2})(?:\s*\(\d+\))?$""")
        private val KM_SUFFIX = Regex("""(?:~|≈)?\s*(\d+(?:[.,]\d+)?)\s*(?:km|كم|ك\.?م|كيلو(?:متر)?)""", RegexOption.IGNORE_CASE)
        private val KM_PREFIX = Regex("""(?:km|كم)\s*(?:~|≈)?\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        private val METERS = Regex("""(?:~|≈)?\s*(\d+)\s*(?:متر|م|m)(?:\s|$|[^\p{L}\d])""", RegexOption.IGNORE_CASE)
        private val TIME_ONLY = Regex("""^[~≈]?\s*\d+\s*(?:min|mins|minute|minutes|h|hr|hrs|د|دق|دقيقة|دقائق|ساعة|ساعات)\.?$""", RegexOption.IGNORE_CASE)
        private val LETTER = Regex("""\p{L}""")
        private val PICKUP_HINTS = listOf("~", "≈", "away", "to pickup", "to client", "منك", "إليك", "اليك", "للعميل", "للراكب", "وصول")

        /** UI labels that are never addresses. */
        private val NON_ADDRESS_LABELS = listOf(
            "accept", "skip", "offer", "fair price", "decline", "cash", "card", "comment", "passenger",
            "just now", "now", "ago", "ride request", "close", "online", "offline", "balance", "top it up",
            "performance", "ride requests", "complain", "hide", "choose on map",
            "قبول", "تخطي", "اقترح", "قدم عرض", "تقديم عرض", "سعر مناسب", "رفض", "نقد", "كاش", "بطاقة", "تعليق", "راكب",
            "الآن", "منذ", "طلب رحلة", "إغلاق", "اغلاق", "متصل", "الرصيد"
        )

        // Placeholders used only when a distance cannot be read; flagged via pickupKnown/tripKnown.
        private const val PLACEHOLDER_PICKUP_KM = 1.0
        private const val DEFAULT_PLACEHOLDER_TRIP_KM = 4.0

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
                    '٫' -> sb.append('.')
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun hasFareText(norm: String): Boolean =
            ACCEPT_FOR_FARE.containsMatchIn(norm) ||
                CURRENCY_PREFIX_AMOUNT.containsMatchIn(norm) ||
                CURRENCY_SUFFIX_AMOUNT.containsMatchIn(norm)

        private fun isSkipLabel(s: String) = s.equals("Skip", ignoreCase = true) || s.equals("تخطي", ignoreCase = true)

        /**
         * Splits the screen into ride cards.
         *
         * A feed card is the item of the vertical list (RecyclerView / LazyColumn) that contains a
         * fare text. All fare texts of one card (price, "Accept for N", counter-bid chips) map to
         * the same list item, so a card is always parsed as a whole, never as a lone price node.
         * Screens without such a list (full-screen request, incoming-offer modal) are parsed as a
         * single card.
         */
        fun findRideCards(root: AccessibilityNodeInfo): List<ParsedRideData> {
            val rootBounds = Rect()
            root.getBoundsInScreen(rootBounds)

            val allScreenTexts = mutableListOf<String>()
            val screenIds = mutableMapOf<String, String>()
            collectNodeTexts(root, allScreenTexts, screenIds)
            val normalizedAll = allScreenTexts.map { normalizeArabicDigits(it.trim()) }

            // One-request screens: the full-screen "Ride request" view (has "Accept for EGP22" and
            // a horizontal row of counter-bid chips) and the incoming-offer modal (has "Skip").
            // Feed list cards have neither, so they are split per list item instead.
            val singleRequestScreen = normalizedAll.any {
                isSkipLabel(it) || ACCEPT_FOR_FARE.containsMatchIn(it) ||
                    it.equals("Ride request", ignoreCase = true) || it.contains("طلب رحلة")
            }

            val fareNodes = mutableListOf<AccessibilityNodeInfo>()
            findFareNodes(root, fareNodes)

            val parsedCards = mutableListOf<ParsedRideData>()
            try {
                if (singleRequestScreen) {
                    val card = parseCard(allScreenTexts, null, screenIds)
                    if (card != null) {
                        val top = mainFareNodeTop(fareNodes, card.fare) ?: (rootBounds.height() * 0.45).toInt()
                        val bounds = Rect(0, (top - 24).coerceAtLeast(0), rootBounds.width(), rootBounds.bottom)
                        parsedCards.add(card.copy(screenBounds = bounds, anchorBounds = bounds))
                    }
                } else {
                    val seenItems = mutableSetOf<String>()
                    for (fareNode in fareNodes) {
                        val item = findListItemAncestor(fareNode) ?: continue
                        val itemBounds = Rect()
                        item.getBoundsInScreen(itemBounds)
                        if (!seenItems.add(itemBounds.toShortString())) continue

                        val texts = mutableListOf<String>()
                        val ids = mutableMapOf<String, String>()
                        collectNodeTexts(item, texts, ids)
                        parseCard(texts, itemBounds, ids)?.let { parsedCards.add(it) }
                    }
                    // Unknown layout: fall back to one card made of the whole screen.
                    if (parsedCards.isEmpty()) {
                        parseCard(allScreenTexts, Rect(0, 320, rootBounds.width(), rootBounds.height()))
                            ?.let { parsedCards.add(it) }
                    }
                }
            } finally {
                fareNodes.filter { it !== root }.forEach { it.recycle() }
            }

            // Trip length learned on the "Ride request" screen fills it in on the list card later.
            val withMemory = parsedCards.map { rememberOrRecallTrip(it) }

            // Give identical-looking cards distinct identities so each keeps its own popup.
            val seenFingerprints = mutableMapOf<String, Int>()
            return withMemory.map { card ->
                val n = seenFingerprints.getOrDefault(card.fingerprint, 0)
                seenFingerprints[card.fingerprint] = n + 1
                if (n == 0) card else card.copy(duplicateIndex = n)
            }
        }

        /** Top of the node showing the ride's own price (not "Accept for…", not a bid chip). */
        private fun mainFareNodeTop(fareNodes: List<AccessibilityNodeInfo>, fare: Double): Int? {
            for (node in fareNodes) {
                val norm = normalizeArabicDigits("${node.text ?: ""} ${node.contentDescription ?: ""}".trim())
                if (ACCEPT_FOR_FARE.containsMatchIn(norm)) continue
                val m = CURRENCY_PREFIX_AMOUNT.find(norm) ?: CURRENCY_SUFFIX_AMOUNT.find(norm) ?: continue
                if (m.groupValues[1].replace(',', '.').toDoubleOrNull() == fare) {
                    val r = Rect()
                    node.getBoundsInScreen(r)
                    return r.top
                }
            }
            return null
        }

        // ---- Map markers on the Ride-request screen (content descriptions are opaque ids) ----
        private const val MAX_OCR_ATTEMPTS = 3

        /** Last OCR attempt, included in the diagnostic dump. */
        @Volatile
        var lastOcrLog: String = "no OCR attempt yet"

        private class MapMarkers(val infoWindows: List<Rect>, val pointA: Rect?, val pointB: Rect?)

        private fun findMapMarkers(root: AccessibilityNodeInfo): MapMarkers {
            val windows = mutableListOf<Rect>()
            var a: Rect? = null
            var b: Rect? = null
            fun visit(node: AccessibilityNodeInfo) {
                val desc = node.contentDescription?.toString() ?: ""
                if (desc.isNotEmpty()) {
                    val r = Rect()
                    node.getBoundsInScreen(r)
                    when {
                        desc.contains("_info_window_") -> if (r.width() > 0 && r.height() > 0) windows.add(r)
                        desc.endsWith("_PointA") -> a = r
                        desc.endsWith("_PointB") -> b = r
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    visit(child)
                    child.recycle()
                }
            }
            visit(root)
            return MapMarkers(windows, a, b)
        }

        private fun distanceSq(r1: Rect, r2: Rect): Long {
            val dx = (r1.centerX() - r2.centerX()).toLong()
            val dy = (r1.centerY() - r2.centerY()).toLong()
            return dx * dx + dy * dy
        }

        /** "11 min 2,7 km" → 2.7 ; "966 m" → 1.0 */
        private fun extractTripKm(ocrText: String): Double? =
            extractDistances(normalizeArabicDigits(ocrText)).firstOrNull()

        private fun storeTripMemory(key: String, km: Double) {
            tripMemory.remove(key)
            tripMemory[key] = km to System.currentTimeMillis()
            while (tripMemory.size > 60) tripMemory.remove(tripMemory.keys.first())
        }

        // ---- Trip-length memory (worker thread only) ----
        private const val TRIP_MEMORY_TTL_MS = 30 * 60 * 1000L
        private val tripMemory = LinkedHashMap<String, Pair<Double, Long>>()

        private fun rideKey(card: ParsedRideData): String? {
            if (card.pickupAddress.isBlank() || card.dropoffAddress.isBlank()) return null
            return FareCalculatorEngine.normalizeArabicText(card.pickupAddress) + "|" +
                FareCalculatorEngine.normalizeArabicText(card.dropoffAddress)
        }

        private fun rememberOrRecallTrip(card: ParsedRideData): ParsedRideData {
            val key = rideKey(card) ?: return card
            val now = System.currentTimeMillis()
            if (card.tripKnown) {
                tripMemory.remove(key)
                tripMemory[key] = card.tripDistanceKm to now
                while (tripMemory.size > 60) tripMemory.remove(tripMemory.keys.first())
                return card
            }
            val remembered = tripMemory[key] ?: return card
            if (now - remembered.second > TRIP_MEMORY_TTL_MS) {
                tripMemory.remove(key)
                return card
            }
            return card.copy(tripDistanceKm = remembered.first, tripKnown = true)
        }

        private fun findFareNodes(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
            val norm = normalizeArabicDigits("${node.text ?: ""} ${node.contentDescription ?: ""}".trim())
            if (norm.isNotEmpty() && node.isVisibleToUser && hasFareText(norm)) out.add(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findFareNodes(child, out)
                if (out.none { it === child }) child.recycle()
            }
        }

        /**
         * Climbs from [node] to the child of the nearest *vertical* list container (its item).
         * Horizontal rows (e.g. counter-bid chips) are skipped: their items are much narrower than
         * the row. Returns null when the node is not inside a list.
         */
        private fun findListItemAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var child: AccessibilityNodeInfo = node
            var depth = 0
            while (depth < 40) {
                val parent = child.parent ?: return null
                val pClass = parent.className?.toString() ?: ""
                val isList = parent.isScrollable ||
                    pClass.contains("RecyclerView", ignoreCase = true) ||
                    pClass.contains("ListView", ignoreCase = true)
                if (isList) {
                    val pb = Rect()
                    val cb = Rect()
                    parent.getBoundsInScreen(pb)
                    child.getBoundsInScreen(cb)
                    val spansWidth = pb.width() > 0 && cb.width() >= pb.width() * 0.75
                    val isCardSized = cb.height() in 120..(pb.height().coerceAtLeast(121))
                    if (spansWidth && isCardSized) return child
                }
                child = parent
                depth++
            }
            return null
        }

        /**
         * Collects visible texts in reading order. Off-screen nodes (e.g. inDrive's hidden swipe
         * menu "Complain / Hide / Choose on map", laid out with zero width) are skipped.
         * When [ids] is given, also records `view-id → text` for visible nodes.
         */
        private fun collectNodeTexts(
            node: AccessibilityNodeInfo,
            texts: MutableList<String>,
            ids: MutableMap<String, String>? = null
        ) {
            val r = Rect()
            node.getBoundsInScreen(r)
            val visible = node.isVisibleToUser && r.width() > 0 && r.height() > 0
            if (visible) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) texts.add(text)
                val desc = node.contentDescription?.toString()?.trim()
                if (!desc.isNullOrEmpty() && desc != text) texts.add(desc)
                val id = node.viewIdResourceName?.substringAfterLast('/')
                if (ids != null && id != null && !text.isNullOrEmpty() && id !in ids) ids[id] = text
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectNodeTexts(child, texts, ids)
                child.recycle()
            }
        }

        // inDrive driver app view ids (from a real screen capture, v. 2026-10).
        private const val ID_PRICE = "info_textview_stage_price_view"
        private const val ID_PICKUP_DISTANCE = "order_info_stage_textview_distance"
        private const val ID_FROM_ADDRESS = "order_info_textview_from_address"
        private const val ID_TO_ADDRESS = "order_info_textview_to_addresses"
        // Same fields on the full-screen "Ride request" view use different ids.
        private const val ID_FROM_ADDRESS_REQUEST = "info_textview_pickup"
        private const val ID_TO_ADDRESS_REQUEST = "info_textview_destination"

        /** Map markers / internal ids exposed as content descriptions are never places. */
        private fun isMachineLabel(text: String): Boolean =
            text.contains(":id/") || text.startsWith("sinet.", ignoreCase = true) ||
                text.equals("Google Map", ignoreCase = true) || text.contains("_info_window_")
        private const val ID_NAME = "driver_common_textview_name"
        private const val ID_RATING = "driver_common_textview_rating"

        private fun amountOf(text: String?): Double? {
            if (text == null) return null
            val n = normalizeArabicDigits(text)
            val m = CURRENCY_PREFIX_AMOUNT.find(n) ?: CURRENCY_SUFFIX_AMOUNT.find(n) ?: return null
            return m.groupValues[1].replace(',', '.').toDoubleOrNull()
        }

        /**
         * Parses one card's texts (in reading order). Returns null when no fare is found.
         * [ids] (view-id → text) takes priority when inDrive's known ids are present; the text
         * heuristics remain as a fallback for screens/versions without them.
         */
        fun parseCard(rawTexts: List<String>, bounds: Rect?, ids: Map<String, String> = emptyMap()): ParsedRideData? {
            // Merged semantics (Compose) can put several lines in one node: split them.
            val texts = rawTexts.flatMap { it.split('\n') }.map { it.trim() }.filter { it.isNotEmpty() }
            if (texts.isEmpty()) return null
            val norm = texts.map { normalizeArabicDigits(it) }

            // 1. Fare: prefer the explicit "Accept for N" label, else the first currency amount.
            var fare: Double? = null
            for (t in norm) {
                val v = ACCEPT_FOR_FARE.find(t)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
                if (v != null) { fare = v; break }
            }
            if (fare == null) fare = amountOf(ids[ID_PRICE])
            if (fare == null) {
                for (t in norm) {
                    val m = CURRENCY_PREFIX_AMOUNT.find(t) ?: CURRENCY_SUFFIX_AMOUNT.find(t) ?: continue
                    val v = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (v != null) { fare = v; break }
                }
            }
            if (fare == null || fare <= 0.0) return null

            // 2. Passenger name & rating
            var passengerName = ids[ID_NAME] ?: ""
            var passengerRating = ids[ID_RATING] ?: ""
            val ratingIdx = if (passengerName.isNotEmpty()) -1 else norm.indexOfFirst { RATING.matches(it) }
            if (ratingIdx >= 0) {
                passengerRating = texts[ratingIdx]
                if (ratingIdx > 0) {
                    val candidate = texts[ratingIdx - 1]
                    if (candidate.length in 2..30 && !hasFareText(norm[ratingIdx - 1]) &&
                        extractDistances(norm[ratingIdx - 1]).isEmpty()
                    ) {
                        passengerName = candidate
                    }
                }
            }

            // 3. Distances (currency amounts are stripped first so merged texts still count).
            //    inDrive's distance field on list cards is the distance to the passenger.
            val idPickupKm = ids[ID_PICKUP_DISTANCE]?.let { extractDistances(normalizeArabicDigits(it)).firstOrNull() }
            val resolved = resolveDistances(norm)
            val distances = if (idPickupKm == null) resolved else Distances(
                pickupKm = idPickupKm,
                tripKm = resolved.tripKm?.takeIf { it != idPickupKm }
                    ?: resolved.pickupKm?.takeIf { it != idPickupKm }
            )

            // 4. Counter bids (chips like "EGP 90" / "90")
            val counterBids = norm.mapNotNull { COUNTER_BID.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                .filter { it.toDouble() != fare && it in 5..5000 }
                .distinct()
                .sorted()

            // 5. Addresses
            // inDrive shows the pickup place first and the destination second (with "A" / "B"
            // markers on the request screen); marker-less list cards keep the same order.
            val heuristic = addressCandidates(texts, norm, passengerName)
            val idFrom = ids[ID_FROM_ADDRESS] ?: ids[ID_FROM_ADDRESS_REQUEST]
            val idTo = ids[ID_TO_ADDRESS] ?: ids[ID_TO_ADDRESS_REQUEST]
            val candidates = (listOfNotNull(idFrom, idTo) + heuristic).distinct().take(4)
            val pickupAddress = idFrom
                ?: textAfterMarker(texts, norm, "A", candidates) ?: candidates.getOrNull(0) ?: ""
            val dropoffAddress = idTo
                ?: textAfterMarker(texts, norm, "B", candidates) ?: candidates.firstOrNull { it != pickupAddress } ?: ""

            val assumedTripKm = try {
                InDriveAssistantApp.instance.repository.getDriverSettings().assumedTripKm
            } catch (_: Exception) {
                DEFAULT_PLACEHOLDER_TRIP_KM
            }

            return ParsedRideData(
                passengerName = passengerName,
                passengerRating = passengerRating,
                fare = fare,
                pickupDistanceKm = distances.pickupKm ?: PLACEHOLDER_PICKUP_KM,
                tripDistanceKm = distances.tripKm ?: assumedTripKm,
                pickupKnown = distances.pickupKm != null,
                tripKnown = distances.tripKm != null,
                pickupAddress = pickupAddress,
                dropoffAddress = dropoffAddress,
                addressCandidates = candidates,
                counterBids = counterBids,
                screenBounds = bounds,
                anchorBounds = bounds
            )
        }

        private fun textAfterMarker(texts: List<String>, norm: List<String>, marker: String, candidates: List<String>): String? {
            val idx = norm.indexOfFirst { it.equals(marker, ignoreCase = false) }
            if (idx == -1) return null
            for (j in idx + 1 until texts.size) {
                if (texts[j] in candidates) return texts[j]
            }
            return null
        }

        /** Texts that look like place names: letters, not money, not a bare time/distance, not a UI label. */
        private fun addressCandidates(texts: List<String>, norm: List<String>, passengerName: String): List<String> {
            val result = mutableListOf<String>()
            for (i in texts.indices) {
                val t = texts[i]
                val n = norm[i]
                if (t.length !in 3..90) continue
                if (t == passengerName) continue
                if (isMachineLabel(t)) continue
                if (LETTER.findAll(n).count() < 3) continue
                if (hasFareText(n)) continue
                if (RATING.matches(n) || TIME_ONLY.matches(n)) continue
                if (extractDistances(n).isNotEmpty() && n.length < 20) continue
                val lower = n.lowercase()
                if (NON_ADDRESS_LABELS.any { lower == it || (lower.length < 25 && lower.contains(it)) }) continue
                if (t !in result) result.add(t)
                if (result.size >= 4) break
            }
            return result
        }

        private data class Distances(val pickupKm: Double?, val tripKm: Double?)

        /**
         * Pickup vs trip distance:
         *  1. Texts next to map markers "A" (pickup) / "B" (destination).
         *  2. A distance with a "~"/"≈" or pickup wording ("منك", "away") is the pickup distance.
         *  3. Otherwise distances in reading order: first = pickup, second = trip.
         *  A single unlabeled distance is treated as the pickup distance (what feed cards show);
         *  the trip distance then stays unknown and the HUD marks the profit as approximate.
         */
        private fun resolveDistances(norm: List<String>): Distances {
            var pickupKm: Double? = null
            var tripKm: Double? = null

            // 1. Map markers
            for (i in norm.indices) {
                val t = norm[i]
                if (t == "A" && pickupKm == null) pickupKm = firstDistanceNear(norm, i)
                if (t == "B" && tripKm == null) tripKm = firstDistanceNear(norm, i)
            }

            // 2 & 3. Ordered distances with pickup hints
            val ordered = mutableListOf<Pair<Double, Boolean>>() // value, looksLikePickup
            for (t in norm) {
                val stripped = t.replace(CURRENCY_PREFIX_AMOUNT, " ").replace(CURRENCY_SUFFIX_AMOUNT, " ")
                val hint = PICKUP_HINTS.any { stripped.contains(it, ignoreCase = true) }
                for (d in extractDistances(stripped)) {
                    if (ordered.none { it.first == d }) ordered.add(d to hint)
                }
            }

            if (pickupKm == null) pickupKm = ordered.firstOrNull { it.second }?.first
            val remaining = ordered.map { it.first }.filter { it != pickupKm }
            if (pickupKm == null && tripKm == null) {
                pickupKm = remaining.getOrNull(0)
                tripKm = remaining.getOrNull(1)
            } else if (tripKm == null) {
                tripKm = remaining.firstOrNull { it != pickupKm }
            } else if (pickupKm == null) {
                pickupKm = remaining.firstOrNull { it != tripKm }
            }
            return Distances(pickupKm, tripKm)
        }

        private fun firstDistanceNear(norm: List<String>, i: Int): Double? {
            for (j in listOf(i, i + 1, i - 1, i + 2)) {
                if (j < 0 || j >= norm.size) continue
                extractDistances(norm[j]).firstOrNull()?.let { return it }
            }
            return null
        }

        private fun extractDistances(str: String): List<Double> {
            val list = mutableListOf<Double>()
            if (str.isEmpty()) return list

            for (regex in listOf(KM_SUFFIX, KM_PREFIX)) {
                regex.findAll(str).forEach { match ->
                    val v = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (v != null && v in 0.05..300.0 && !list.contains(v)) list.add(v)
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
