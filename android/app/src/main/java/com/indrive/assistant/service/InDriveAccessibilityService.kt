package com.indrive.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.indrive.assistant.MainActivity
import com.indrive.assistant.data.local.RequestLog
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
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
import com.indrive.assistant.engine.BidAdvisor
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
        restrictEventsToInDrive()
        onRunningStateChanged(this)
        Log.d(TAG, "InDriveAccessibilityService connected")
    }

    /**
     * Ask the system for events from the installed inDrive packages only, instead of every app.
     * Leaving inDrive then produces no event, so [leaveCheckRunnable] notices it instead.
     */
    private fun restrictEventsToInDrive() {
        val info = serviceInfo ?: return
        val installed = try {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName }
                .filter { isTargetPackage(it, packageName) }
                .distinct()
        } catch (e: Exception) {
            Log.w(TAG, "Could not list installed inDrive packages", e)
            emptyList()
        }
        info.packageNames = (installed.ifEmpty { listOf(DEFAULT_INDRIVE_PACKAGE) }).toTypedArray()
        serviceInfo = info
        Log.d(TAG, "Listening to: ${info.packageNames.joinToString()}")
    }

    // Accessed only on the worker thread.
    private var popupsShown = false
    private var missedLeaveChecks = 0

    /**
     * While badges are on screen, checks every second that inDrive is still in front and hides
     * them once it is not (events from other apps no longer reach this service).
     */
    private val leaveCheckRunnable: Runnable = Runnable {
        if (!popupsShown) return@Runnable
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        root?.recycle()
        val inDriveInFront = pkg != null && isTargetPackage(pkg, packageName)
        val transient = pkg != null && isTransientPackage(pkg, packageName)
        // An unreadable window twice in a row (another app, a lock screen) counts as leaving.
        missedLeaveChecks = if (pkg == null) missedLeaveChecks + 1 else 0
        if (!inDriveInFront && !transient && (pkg != null || missedLeaveChecks >= 2)) {
            hidePopups()
        } else {
            worker.postDelayed(leaveCheckRunnable, LEAVE_CHECK_MS)
        }
    }

    /** Runs on the worker thread. */
    private fun showPopups(rides: List<ParsedRideData>) {
        FloatingHudService.onCardsSync(rides)
        val wasShown = popupsShown
        popupsShown = rides.isNotEmpty()
        if (popupsShown && !wasShown) {
            missedLeaveChecks = 0
            worker.postDelayed(leaveCheckRunnable, LEAVE_CHECK_MS)
        }
    }

    /** Runs on the worker thread. */
    private fun hidePopups() {
        worker.removeCallbacks(leaveCheckRunnable)
        popupsShown = false
        pendingAutoAcceptFingerprint = null
        FloatingHudService.onCardsSync(emptyList())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::worker.isInitialized) return
        val pkg = event.packageName?.toString() ?: return

        when {
            isTargetPackage(pkg, packageName) && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // The driver tapped something in inDrive (Accept, a bid button…): log the outcome.
                val label = (event.text?.joinToString(" ") ?: "") + " " + (event.contentDescription ?: "")
                val bounds = event.source?.let { src -> Rect().also { src.getBoundsInScreen(it); src.recycle() } }
                worker.post { handleInDriveClick(label.trim(), bounds) }
            }
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
                // The driver switched to a different app (only seen if the package filter is not
                // applied): hide all ride popups.
                worker.removeCallbacks(scanRunnable)
                scanScheduled = false
                worker.post { hidePopups() }
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
                if (!isTransientPackage(pkg, packageName)) hidePopups()
                return
            }
            val rides = resolveTrips(findRideCards(root))
            val onRequestScreen = lastScanWasRequestScreen
            if (captureRequested) {
                captureRequested = false
                saveDiagnosticDump(root, pkg, rides)
            }
            showPopups(rides)
            logRequests(rides, onRequestScreen)
            maybePromptRating(rides, onRequestScreen)
            maybeScheduleAutoAccept(rides)
            maybeReadTripFromMap(root, rides)
            maybeAutoOpen(root, rides, onRequestScreen)
            maybeAutoTapBestBid(rides, onRequestScreen)
            maybeAutoDismiss(rides)
        } catch (e: Exception) {
            Log.e(TAG, "Error while scanning inDrive screen", e)
        } finally {
            root.recycle()
        }
    }

    // =========================================================================
    // REQUEST LOG (for the analysis & recommendations tab) and RATING PROMPT
    // =========================================================================

    // Worker thread only.
    private var lastRides: List<ParsedRideData> = emptyList()
    private var ratingPendingFingerprint: String? = null
    private var ratingAcceptedAt = 0L

    private fun logRequests(rides: List<ParsedRideData>, onRequestScreen: Boolean) {
        lastRides = rides
        if (rides.isEmpty()) return
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        val analyses = rides.map { analyze(it, settings) }
        RequestLog.recordSeen(applicationContext, rides, analyses, onRequestScreen, if (onRequestScreen) lastScreenTexts else emptyList())
    }

    /** A click inside inDrive: Accept → accepted; a bid button → bid sent. Worker thread. */
    private fun handleInDriveClick(label: String, bounds: Rect?) {
        val rides = lastRides
        val card = rides.singleOrNull()
            ?: rides.firstOrNull { c -> bounds != null && c.screenBounds?.contains(bounds.centerX(), bounds.centerY()) == true }
            ?: return
        val norm = normalizeArabicDigits(label)
        when {
            ACCEPT_BUTTON_REGEX.containsMatchIn(norm) -> markAccepted(card)
            else -> {
                val v = COUNTER_BID.find(norm)?.groupValues?.get(1)?.toIntOrNull() ?: return
                if (card.bidChips.any { it.value == v } || card.counterBids.contains(v)) {
                    RequestLog.recordOutcome(applicationContext, card.fingerprint, RequestLog.STATUS_BID_SENT, v)
                }
            }
        }
    }

    private fun markAccepted(card: ParsedRideData) {
        RequestLog.recordOutcome(applicationContext, card.fingerprint, RequestLog.STATUS_ACCEPTED)
        ratingPendingFingerprint = card.fingerprint
        ratingAcceptedAt = System.currentTimeMillis()
    }

    /**
     * After an accepted ride, once the driver is back on the requests list (the ride is over),
     * posts one notification asking to rate it. The app shows the rating sheet when opened.
     */
    private fun maybePromptRating(rides: List<ParsedRideData>, onRequestScreen: Boolean) {
        if (ratingPendingFingerprint == null || onRequestScreen || rides.isEmpty()) return
        if (System.currentTimeMillis() - ratingAcceptedAt < RATING_PROMPT_AFTER_MS) return
        ratingPendingFingerprint = null
        try {
            val open = PendingIntent.getActivity(
                this, 7, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(this, InDriveAssistantApp.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.btn_star_big_on)
                .setContentTitle("قيّم رحلتك الأخيرة")
                .setContentText("تقييمك بيحسّن التحليلات والتوصيات. اضغط هنا.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(RATING_NOTIFICATION_ID, n)
        } catch (e: Exception) {
            Log.w(TAG, "Rating prompt failed", e)
        }
    }

    /** Fills unknown trip lengths from history (B) and/or the addresses (A), per the driver's settings. */
    private fun resolveTrips(rides: List<ParsedRideData>): List<ParsedRideData> {
        val hud = InDriveAssistantApp.instance.repository.getHudPrefs()
        if (!hud.tripFromHistory && !hud.tripFromAddresses) return rides
        return rides.map {
            TripDistanceResolver.resolve(applicationContext, it, hud.tripFromHistory, hud.tripFromAddresses) { requestRescan() }
        }
    }

    /** Re-parses the screen soon (e.g. when a geocoding lookup or OCR finished). Any thread. */
    private fun requestRescan() {
        if (!::worker.isInitialized) return
        worker.post {
            if (!scanScheduled) {
                scanScheduled = true
                worker.post(scanRunnable)
            }
        }
    }

    // =========================================================================
    // C: OPEN UNREAD REQUESTS AUTOMATICALLY (opt-in)
    //
    // For a list card whose trip length is still unknown, tap the card body (never a button),
    // let the map OCR read the trip length on the Ride-request screen, then press Back. One card
    // at a time, with a cooldown; stops managing the screen as soon as the driver navigates.
    // =========================================================================

    // Worker thread only.
    private var autoOpenKey: String? = null
    private var autoOpenStartedAt = 0L
    private var autoOpenSawRequest = false
    private var lastAutoOpenAt = 0L
    private val autoOpenTried = LinkedHashSet<String>()
    private val autoOpenTimeout = Runnable { if (autoOpenKey != null) requestRescan() }

    private fun maybeAutoOpen(root: AccessibilityNodeInfo, rides: List<ParsedRideData>, onRequestScreen: Boolean) {
        val now = System.currentTimeMillis()
        val hud = InDriveAssistantApp.instance.repository.getHudPrefs()
        // Driver's timings: how long a request stays open at most, and the pause before the next one.
        val stayMs = (hud.autoOpenStaySeconds * 1000).toLong().coerceIn(1000L, 30_000L)
        val gapMs = (hud.autoOpenGapSeconds * 1000).toLong().coerceIn(1000L, 120_000L)
        val active = autoOpenKey
        if (active != null) {
            val elapsed = now - autoOpenStartedAt
            if (onRequestScreen) {
                autoOpenSawRequest = true
                val card = rides.singleOrNull()
                if (card == null || rideKey(card) != active) {
                    finishAutoOpen(goBack = false) // the driver opened something else: hands off
                } else if ((card.tripKnown && hud.autoOpenBackWhenRead) || elapsed > stayMs) {
                    finishAutoOpen(goBack = true)
                }
            } else if (autoOpenSawRequest || elapsed > AUTO_OPEN_TAP_TIMEOUT_MS) {
                finishAutoOpen(goBack = false) // back on the list (driver pressed Back, or the tap did nothing)
            }
            return
        }

        if (!hud.tripAutoOpen || !hud.readMapDistance || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (onRequestScreen || pendingAutoAcceptFingerprint != null || pendingDismiss != null) return
        // The pause is counted from the moment the previous request was closed.
        if (now - lastAutoOpenAt < gapMs) return

        val candidate = rides.firstOrNull { card ->
            !card.tripKnown && card.screenBounds != null && rideKey(card)?.let { it !in autoOpenTried } == true
        } ?: return
        val key = rideKey(candidate) ?: return
        autoOpenTried.add(key)
        while (autoOpenTried.size > 100) autoOpenTried.remove(autoOpenTried.first())

        if (clickCardBody(root, candidate.screenBounds!!)) {
            autoOpenKey = key
            autoOpenStartedAt = now
            autoOpenSawRequest = false
            lastAutoOpenAt = now
            worker.postDelayed(autoOpenTimeout, stayMs + 200)
            Log.i(TAG, "Auto-open: opened request to read its trip length")
        }
    }

    // =========================================================================
    // AUTO-HIDE LOSING / BLOCKED REQUESTS (opt-in)
    // After the driver's delay, re-read the screen; if the card still qualifies, swipe it away in
    // the chosen direction (list card), or press Skip / Close (full-screen request). One at a time.
    // =========================================================================

    // Worker thread only.
    private val dismissedFingerprints = LinkedHashSet<String>()
    private var pendingDismiss: String? = null
    private var lastDismissAt = 0L

    /** Why [card] should be hidden under the driver's settings, or null to keep it. */
    private fun dismissReason(card: ParsedRideData, settings: DriverSettings): String? {
        if (!settings.autoDismissLoss && !settings.autoDismissBlocked) return null
        val a = analyze(card, settings)
        // A blacklisted zone with an exceptionally high price is rated good by the engine: keep it.
        val blocked = a.isBlacklistedPassenger ||
            (a.isBlacklistedZone && a.profitabilityLevel == com.indrive.assistant.engine.ProfitabilityLevel.LOSS_DANGER)
        if (settings.autoDismissBlocked && blocked) return "محظور"
        // A real money loss, with both distances read (an assumed trip length could be wrong).
        if (settings.autoDismissLoss && !card.distanceEstimated && a.netProfit < 0) return "خاسر"
        return null
    }

    private fun maybeAutoDismiss(rides: List<ParsedRideData>) {
        if (rides.isEmpty() || pendingDismiss != null || autoOpenKey != null || pendingAutoAcceptFingerprint != null) return
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        if (!settings.autoDismissLoss && !settings.autoDismissBlocked) return
        if (System.currentTimeMillis() - lastDismissAt < DISMISS_COOLDOWN_MS) return
        val candidate = rides.firstOrNull { it.fingerprint !in dismissedFingerprints && dismissReason(it, settings) != null } ?: return
        pendingDismiss = candidate.fingerprint
        val delayMs = (settings.autoDismissDelaySeconds * 1000).toLong().coerceIn(300L, 10_000L)
        worker.postDelayed({ executeDismiss(candidate.fingerprint) }, delayMs)
    }

    private fun executeDismiss(fingerprint: String) {
        pendingDismiss = null
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        val root = rootInActiveWindow ?: return
        try {
            if (!isTargetPackage(root.packageName?.toString() ?: "", packageName)) return
            val card = resolveTrips(findRideCards(root)).firstOrNull { it.fingerprint == fingerprint } ?: return
            val reason = dismissReason(card, settings) ?: return
            dismissedFingerprints.add(fingerprint)
            while (dismissedFingerprints.size > 200) dismissedFingerprints.remove(dismissedFingerprints.first())
            lastDismissAt = System.currentTimeMillis()
            val done = if (lastScanWasRequestScreen) clickSkipButton(root) else swipeCard(card, settings.autoDismissDirection)
            if (done) {
                RequestLog.markDismissed(applicationContext, fingerprint, reason)
                mainHandler.post {
                    Toast.makeText(applicationContext, "تم إخفاء طلب $reason (${card.fare.toInt()} ج.م)", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-hide failed", e)
        } finally {
            root.recycle()
        }
    }

    /** Swipes the card horizontally across most of its width, like the driver would. */
    private fun swipeCard(card: ParsedRideData, direction: String): Boolean {
        val b = card.screenBounds ?: return false
        if (b.width() < 50 || b.height() < 20) return false
        val y = b.centerY().toFloat()
        val (fromX, toX) = if (direction == "LEFT") {
            b.right - b.width() * 0.15f to b.left + b.width() * 0.05f
        } else {
            b.left + b.width() * 0.15f to b.right - b.width() * 0.05f
        }
        val path = android.graphics.Path().apply { moveTo(fromX, y); lineTo(toX, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, DISMISS_SWIPE_MS))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Presses Skip / Skip offer / Close on a full-screen request. */
    private fun clickSkipButton(root: AccessibilityNodeInfo): Boolean {
        val seen = mutableListOf<AccessibilityNodeInfo>()
        var target: AccessibilityNodeInfo? = null
        fun visit(node: AccessibilityNodeInfo) {
            if (target != null) return
            val label = "${node.text ?: node.contentDescription ?: ""}".trim()
            if (label.isNotEmpty() && SKIP_LABELS.any { label.equals(it, ignoreCase = true) }) {
                target = node
                return
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                seen.add(child)
                visit(child)
            }
        }
        return try {
            visit(root)
            var t = target
            var depth = 0
            while (t != null && !t.isClickable && depth < 4) {
                t = t.parent?.also { seen.add(it) }
                depth++
            }
            t != null && t.isClickable && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            seen.forEach { it.recycle() }
        }
    }

    // =========================================================================
    // AUTO-TAP THE BEST COUNTER-BID (opt-in, bidChipsMode == AUTO_TAP)
    // On a Ride-request screen, once per ride: wait, re-read the screen, and click the button of
    // the best bid (BidAdvisor) if it is still there with the same value.
    // =========================================================================

    // Worker thread only.
    private val bidTappedKeys = LinkedHashSet<String>()
    private var pendingBidTap: String? = null

    private fun maybeAutoTapBestBid(rides: List<ParsedRideData>, onRequestScreen: Boolean) {
        if (!onRequestScreen || pendingBidTap != null || autoOpenKey != null) return
        if (InDriveAssistantApp.instance.repository.getHudPrefs().bidChipsMode != "AUTO_TAP") return
        val card = rides.singleOrNull() ?: return
        if (card.bidChips.isEmpty()) return
        val key = rideKey(card) ?: card.fingerprint
        if (key in bidTappedKeys) return
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        val best = BidAdvisor.best(BidAdvisor.rate(card, settings)) ?: return

        bidTappedKeys.add(key)
        while (bidTappedKeys.size > 100) bidTappedKeys.remove(bidTappedKeys.first())
        pendingBidTap = key
        val delayMs = (settings.autoAcceptDelaySeconds * 1000).toLong().coerceIn(500L, 8000L)
        worker.postDelayed({ executeBidTap(key, best.chip.value) }, delayMs)
    }

    private fun executeBidTap(key: String, value: Int) {
        pendingBidTap = null
        if (InDriveAssistantApp.instance.repository.getHudPrefs().bidChipsMode != "AUTO_TAP") return
        val root = rootInActiveWindow ?: return
        try {
            if (!isTargetPackage(root.packageName?.toString() ?: "", packageName)) return
            val card = findRideCards(root).singleOrNull() ?: return
            if ((rideKey(card) ?: card.fingerprint) != key) return // another request is open now
            val chip = card.bidChips.firstOrNull { it.value == value } ?: return
            if (clickNodeAt(root, chip.bounds)) {
                mainHandler.post {
                    Toast.makeText(applicationContext, "تم اختيار أفضل مزايدة: $value ج.م", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bid auto-tap failed", e)
        } finally {
            root.recycle()
        }
    }

    /** Clicks the clickable node at [bounds] (the node itself or its nearest clickable ancestor). */
    private fun clickNodeAt(root: AccessibilityNodeInfo, bounds: Rect): Boolean {
        val seen = mutableListOf<AccessibilityNodeInfo>()
        var match: AccessibilityNodeInfo? = null
        fun visit(node: AccessibilityNodeInfo) {
            if (match != null) return
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r == bounds) {
                match = node
                return
            }
            if (!Rect.intersects(r, bounds) && node !== root) return
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                seen.add(child)
                visit(child)
            }
        }
        return try {
            visit(root)
            var target = match
            var depth = 0
            while (target != null && !target.isClickable && depth < 4) {
                target = target.parent?.also { seen.add(it) }
                depth++
            }
            target != null && target.isClickable && target.isEnabled && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            seen.forEach { it.recycle() }
        }
    }

    private fun finishAutoOpen(goBack: Boolean) {
        autoOpenKey = null
        worker.removeCallbacks(autoOpenTimeout)
        lastAutoOpenAt = System.currentTimeMillis() // the pause before the next open starts now
        if (goBack) performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Clicks the card itself (the largest clickable node inside [cardBounds] that is not an
     * Accept / Offer / Skip button). Returns false when no such node exists.
     */
    private fun clickCardBody(root: AccessibilityNodeInfo, cardBounds: Rect): Boolean {
        val minArea = cardBounds.width().toLong() * cardBounds.height() * 4 / 10
        val seen = mutableListOf<AccessibilityNodeInfo>() // recycled once, at the end
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        fun visit(node: AccessibilityNodeInfo) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (!Rect.intersects(r, cardBounds)) return
            val area = r.width().toLong() * r.height()
            val label = normalizeArabicDigits("${node.text ?: ""} ${node.contentDescription ?: ""}".trim())
            val isButton = ACCEPT_BUTTON_REGEX.containsMatchIn(label) || isSkipLabel(label) ||
                NON_CARD_BUTTONS.any { label.contains(it, ignoreCase = true) }
            if (node.isClickable && node.isEnabled && !isButton && cardBounds.contains(r) && area >= minArea && area > bestArea) {
                best = node
                bestArea = area
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                seen.add(child)
                visit(child)
            }
        }
        return try {
            visit(root)
            best?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } finally {
            seen.forEach { it.recycle() }
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
                                // B: remember this pickup → destination for later list cards.
                                if (InDriveAssistantApp.instance.repository.getHudPrefs().tripFromHistory) {
                                    TripDistanceResolver.learn(applicationContext, key, km)
                                }
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

        val qualifying = rides.filter { it.fingerprint != lastAutoAcceptedFingerprint && qualifiesForAutoAccept(it, settings) }
        // Either the first qualifying card on screen, or (driver's option) the best one by their ranking.
        val candidate = (if (settings.autoAcceptPickBest) {
            qualifying.maxByOrNull { rankScore(it, settings) }
        } else {
            qualifying.firstOrNull()
        }) ?: return

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
            val card = resolveTrips(findRideCards(root)).firstOrNull { it.fingerprint == fingerprint }
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
                markAccepted(card)
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

    /** Higher is better: profit per km (default), net profit, or margin %. */
    private fun rankScore(card: ParsedRideData, settings: DriverSettings): Double {
        val a = analyze(card, settings)
        return when (settings.autoAcceptRankBy) {
            "NET_PROFIT" -> a.netProfit
            "MARGIN" -> a.profitMarginPercent
            else -> a.profitPerKm
        }
    }

    private fun qualifiesForAutoAccept(card: ParsedRideData, settings: DriverSettings): Boolean {
        if (card.distanceEstimated) return false
        if (settings.isBlacklistedPassenger(card.passengerName)) return false
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
        dropoffAddress = card.dropoffAddress,
        passengerName = card.passengerName
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
        onRunningStateChanged(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "InDriveA11y"
        private const val SCAN_DELAY_MS = 200L
        private const val AUTO_ACCEPT_COOLDOWN_MS = 5000L
        private const val MAX_DUMP_NODES = 900
        const val DUMP_FILE = "inDrive_screen_dump.txt"
        private const val LEAVE_CHECK_MS = 1000L
        // C (auto-open): give the map time to load, then press Back; at most one card per cooldown.
        // Auto-hide: at most one card per cooldown; the swipe takes this long.
        private const val DISMISS_COOLDOWN_MS = 1500L
        private const val DISMISS_SWIPE_MS = 300L
        private val SKIP_LABELS = listOf("Skip", "Skip offer", "Close", "تخطي", "تخطي العرض", "إغلاق", "اغلاق")
        // (How long a request stays open and the pause between two are the driver's HudPrefs settings.)
        private const val AUTO_OPEN_TAP_TIMEOUT_MS = 3000L
        /** Labels of buttons on a list card that the auto-open must never press. */
        private val NON_CARD_BUTTONS = listOf("offer", "اقترح", "قدم عرض", "تقديم عرض", "decline", "رفض", "complain", "hide")

        /** Whether the last findRideCards call saw the full-screen "Ride request" view (worker thread). */
        @Volatile
        var lastScanWasRequestScreen = false

        /** All texts of the last Ride-request screen read (its "text snapshot" for the request log). */
        @Volatile
        var lastScreenTexts: List<String> = emptyList()

        private const val RATING_PROMPT_AFTER_MS = 3 * 60 * 1000L
        private const val RATING_NOTIFICATION_ID = 2201
        /** Used as the event filter when no inDrive app can be found on the phone. */
        private const val DEFAULT_INDRIVE_PACKAGE = "sinet.startup.inDriver"

        @Volatile
        private var instance: InDriveAccessibilityService? = null

        /** True while the service is switched on in Android's accessibility settings and connected. */
        fun isRunning(): Boolean = instance != null

        /**
         * Switches the service off in Android's accessibility settings (as if the driver turned it
         * off), e.g. so banking apps that refuse to run next to a screen reader can be opened.
         * Only the driver can switch it back on, from Settings. Returns false if it was not running.
         */
        fun pause(): Boolean {
            val service = instance ?: return false
            service.disableSelf()
            return true
        }

        /** Refreshes the Quick Settings tile and the HUD notification after the service starts / stops. */
        private fun onRunningStateChanged(context: Context) {
            try {
                TileService.requestListeningState(context, ComponentName(context, ScreenReadingTileService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Tile refresh failed", e)
            }
            FloatingHudService.onScreenReadingChanged()
        }

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
        private val TIME_ONLY = Regex("""^[~≈]?\s*\d+\s*(?:sec|secs|second|seconds|min|mins|minute|minutes|h|hr|hrs|ث|ثانية|ثواني|د|دق|دقيقة|دقائق|ساعة|ساعات)\.?$""", RegexOption.IGNORE_CASE)

        /**
         * Texts that only appear on inDrive screens that are not ride requests: the ride in
         * progress, cancel dialogs, rating, balance / income / tier, the "waiting for the
         * passenger's reply" countdown, notifications, going offline. Found in real request logs.
         */
        private val NON_REQUEST_PHRASES = listOf(
            "wait for the reply", "cancel ride", "cancel this ride", "why do you want to cancel", "waiting time",
            "buckle up", "today's income", "service payment", "your balance is low", "top up", "your tier",
            "is the ride completed", "what happened", "describe the issue", "please describe what happened",
            "do you want to go offline", "reach your daily goal", "new notification",
            "في انتظار الرد", "انتظر الرد", "إلغاء الرحلة", "الغاء الرحلة", "دخل اليوم", "اشحن", "رصيدك منخفض", "هل انتهت الرحلة"
        )
        /** Short labels of those screens, matched as the whole text only. */
        private val NON_REQUEST_EXACT = setOf("navigate", "vat", "goal:", "excellent", "platinum", "day", "week")

        /** A screen label or a countdown ("31 sec."), never a place: used for addresses and to clean the log. */
        fun isNotAPlace(text: String): Boolean =
            hasNonRequestMarker(listOf(text)) || TIME_ONLY.matches(normalizeArabicDigits(text.trim()))

        fun hasNonRequestMarker(texts: List<String>): Boolean = texts.any { t ->
            val n = t.trim().lowercase()
            n in NON_REQUEST_EXACT || NON_REQUEST_PHRASES.any { n.contains(it) }
        }

        /** inDrive's own request ids, or an Accept / Offer button: the screen really shows a request. */
        private fun hasRequestEvidence(texts: List<String>, ids: Map<String, String>): Boolean =
            REQUEST_IDS.any { it in ids } || texts.any { t ->
                val n = normalizeArabicDigits(t)
                ACCEPT_BUTTON_REGEX.containsMatchIn(n) || n.contains("Offer your fare", ignoreCase = true) || n.contains("اقترح سعرك")
            }
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
            lastScanWasRequestScreen = singleRequestScreen
            // Map markers and internal ids are noise in the snapshot: keep what the driver can read.
            lastScreenTexts = if (singleRequestScreen) allScreenTexts.filterNot { isMachineLabel(it) } else emptyList()

            val fareNodes = mutableListOf<AccessibilityNodeInfo>()
            findFareNodes(root, fareNodes)

            val parsedCards = mutableListOf<ParsedRideData>()
            try {
                if (singleRequestScreen) {
                    // A request screen never shows in-ride / balance / cancel texts; if it does, it isn't one.
                    val card = if (hasNonRequestMarker(allScreenTexts)) null else parseCard(allScreenTexts, null, screenIds)
                    if (card != null) {
                        val top = mainFareNodeTop(fareNodes, card.fare) ?: (rootBounds.height() * 0.45).toInt()
                        val bounds = Rect(0, (top - 24).coerceAtLeast(0), rootBounds.width(), rootBounds.bottom)
                        parsedCards.add(card.copy(screenBounds = bounds, anchorBounds = bounds, bidChips = findBidChips(root, card.fare)))
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
                        // List items that are banners (balance, notifications…) are not requests.
                        if (hasNonRequestMarker(texts)) continue
                        val card = parseCard(texts, itemBounds, ids) ?: continue
                        if (hasRequestEvidence(texts, ids) || card.pickupKnown) parsedCards.add(card)
                    }
                    // Unknown layout: one card made of the whole screen, but only when it really shows
                    // a request (inDrive's request ids or an Accept / Offer button) and no in-ride,
                    // balance or cancel screen texts. Without this, any screen with an amount in
                    // EGP (income, VAT, top-up, waiting for the passenger's reply…) became a "ride".
                    if (parsedCards.isEmpty() && hasRequestEvidence(allScreenTexts, screenIds) && !hasNonRequestMarker(allScreenTexts)) {
                        parseCard(allScreenTexts, Rect(0, 320, rootBounds.width(), rootBounds.height()), screenIds)
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

        /**
         * The counter-bid buttons of the Ride-request screen: price-only texts ("EGP 90", "90")
         * laid out side by side in one row. Returns the row with the most such buttons (2+),
         * left to right; the ride's own fare is excluded.
         */
        fun findBidChips(root: AccessibilityNodeInfo, fare: Double): List<ParsedRideData.BidChip> {
            val found = mutableListOf<ParsedRideData.BidChip>()
            fun visit(node: AccessibilityNodeInfo) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (node.isVisibleToUser && r.width() > 0 && r.height() > 0) {
                    val norm = normalizeArabicDigits("${node.text ?: node.contentDescription ?: ""}".trim())
                    val v = COUNTER_BID.find(norm)?.groupValues?.get(1)?.toIntOrNull()
                    if (v != null && v in 5..5000 && v.toDouble() != fare && found.none { it.bounds == r }) {
                        found.add(ParsedRideData.BidChip(v, Rect(r)))
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    visit(child)
                    child.recycle()
                }
            }
            visit(root)
            // Group by row (similar vertical centre) and keep the fullest row.
            val rows = found.groupBy { it.bounds.centerY() / 40 }
            val row = rows.values.maxByOrNull { it.size } ?: return emptyList()
            if (row.size < 2) return emptyList()
            return row.distinctBy { it.value }.sortedBy { it.bounds.left }
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
        private val REQUEST_IDS = listOf(
            ID_PRICE, ID_PICKUP_DISTANCE, ID_FROM_ADDRESS, ID_TO_ADDRESS, ID_FROM_ADDRESS_REQUEST, ID_TO_ADDRESS_REQUEST
        )

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
                if (RATING.matches(n) || TIME_ONLY.matches(n) || hasNonRequestMarker(listOf(t))) continue
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
