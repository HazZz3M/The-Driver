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
import com.indrive.assistant.data.local.AutomationLog
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
import android.view.accessibility.AccessibilityWindowInfo
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
        if (!popupsShown && !inDriveShown) return@Runnable
        val root = inDriveRoot()
        root?.recycle()
        val other = if (root == null) otherAppInFront() else false
        // An unreadable screen twice in a row (a lock screen…) counts as leaving.
        missedLeaveChecks = if (other == null) missedLeaveChecks + 1 else 0
        if (root == null && (other == true || missedLeaveChecks >= 2)) {
            hidePopups()
        } else {
            worker.postDelayed(leaveCheckRunnable, LEAVE_CHECK_MS)
        }
    }

    /** inDrive is on screen (alone, or next to another app in split screen). Worker thread. */
    private var inDriveShown = false

    private fun markInDriveShown() {
        if (inDriveShown) return
        inDriveShown = true
        inDriveOnScreen = true
        FloatingHudService.onInDriveVisible(true)
        if (!popupsShown) {
            missedLeaveChecks = 0
            worker.removeCallbacks(leaveCheckRunnable)
            worker.postDelayed(leaveCheckRunnable, LEAVE_CHECK_MS)
        }
    }

    /**
     * inDrive's window, also when another window has the focus (split screen with the live map,
     * the map's own activity…). Null when inDrive isn't on screen. The caller recycles it.
     */
    private fun inDriveRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null) {
            if (isTargetPackage(active.packageName?.toString() ?: "", packageName)) return active
            active.recycle()
        }
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val r = w.root ?: continue
                if (isTargetPackage(r.packageName?.toString() ?: "", packageName)) return r
                r.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not list windows", e)
        }
        return null
    }

    /**
     * With inDrive not on screen: true when another app is (our own map activity included),
     * false for a transient window (keyboard, notification shade, our overlays), null when
     * nothing can be read at all (lock screen, screen off).
     */
    private fun otherAppInFront(): Boolean? {
        val apps = try {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        } catch (e: Exception) {
            null
        }
        if (apps != null) return if (apps.isEmpty()) null else true
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString()
        root.recycle()
        return pkg != null && !isTransientPackage(pkg, packageName)
    }

    /** Runs on the worker thread. */
    private fun showPopups(rides: List<ParsedRideData>) {
        FloatingHudService.onCardsSync(rides)
        val wasShown = popupsShown
        popupsShown = rides.isNotEmpty()
        if (popupsShown && !wasShown && !inDriveShown) {
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
        if (inDriveShown) {
            inDriveShown = false
            inDriveOnScreen = false
            FloatingHudService.onInDriveVisible(false)
            LiveRequests.inDriveGone()
        }
    }

    // =========================================================================
    // REQUESTS BOARD: every request with a snapshot of inDrive's own trip map, and the driver's
    // taps on it (accept, hide, a bid button, or their own price). Actions that need the request's
    // screen open it first and finish there; one action at a time.
    // =========================================================================

    private fun feedLiveMap(rides: List<ParsedRideData>, onRequestScreen: Boolean) {
        if (!InDriveAssistantApp.instance.repository.getHudPrefs().liveMapEnabled) return
        // The driver's position ties inDrive's map to the real one (needs the location permission).
        if (DriverLocation.last == null) mainHandler.post { DriverLocation.start(applicationContext) }
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        LiveRequests.update(rides.map { it to analyze(it, settings) }, onRequestScreen)
    }

    /** The driver's action from the board, being carried out. Worker thread only. */
    private class PendingBoard(val key: String, val action: String, val value: Int, val until: Long) {
        /** Sideways scrolls of the price buttons tried while looking for the chosen price. */
        var stage = 0
        var stageAt = 0L
        var opens = 0
    }

    private var pendingBoard: PendingBoard? = null

    private fun toast(text: String) {
        mainHandler.post { Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show() }
    }

    private fun postMapAction(key: String, action: String, value: Int): Boolean {
        if (!::worker.isInitialized) return false
        worker.post { executeMapAction(key, action, value) }
        return true
    }

    /** The driver tapped Accept / Hide / a bid / their own price on the board. Worker thread. */
    private fun executeMapAction(key: String, action: String, value: Int) {
        pendingBoard = null
        // The driver takes over: an automatic open in progress stops managing the screen.
        if (autoOpenKey != null) {
            autoOpenKey = null
            autoOpenActive = false
            worker.removeCallbacks(autoOpenTimeout)
        }
        val root = inDriveRoot()
        if (root == null) {
            toast("افتح إن درايف الأول (أو قسّم الشاشة معاه)")
            return
        }
        try {
            val rides = resolveTrips(findRideCards(root))
            val onRequestScreen = lastScanWasRequestScreen
            val card = rides.firstOrNull { LiveRequests.keyOf(it) == key }
            if (action == MAP_HIDE) {
                hideFromBoard(root, card, key, onRequestScreen)
                return
            }
            // Accept straight from a list card when it has its own Accept button.
            if (action == MAP_ACCEPT && card != null && !onRequestScreen) {
                val b = card.screenBounds ?: card.anchorBounds
                if (b != null && clickAcceptButtonInside(root, b)) {
                    onBoardAccepted(card)
                    return
                }
            }
            if (card == null && !onRequestScreen) {
                toast("الطلب ده مش ظاهر في إن درايف دلوقتي")
                return
            }
            pendingBoard = PendingBoard(key, action, value, System.currentTimeMillis() + BOARD_ACTION_TIMEOUT_MS)
            LiveRequests.setStatus(key, LiveRequests.STATUS_WORKING, "جاري التنفيذ…")
            continueBoardAction(root, rides, onRequestScreen)
        } catch (e: Exception) {
            Log.e(TAG, "Board action $action failed", e)
        } finally {
            root.recycle()
        }
    }

    private fun hideFromBoard(root: AccessibilityNodeInfo, card: ParsedRideData?, key: String, onRequestScreen: Boolean) {
        if (card == null) {
            // Not in inDrive any more: just take it off the board.
            LiveRequests.setStatus(key, LiveRequests.STATUS_HIDDEN, "اتشال من اللوحة")
            return
        }
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        val done = if (onRequestScreen) clickSkipButton(root) else swipeCard(card, settings.autoDismissDirection)
        if (done) {
            dismissedFingerprints.add(card.fingerprint)
            RequestLog.markDismissed(applicationContext, card.fingerprint, "من لوحة الطلبات")
            LiveRequests.setStatus(key, LiveRequests.STATUS_HIDDEN, "اتخفى")
            AutomationLog.add(applicationContext, AutomationLog.HIDE, card.fare, "من لوحة الطلبات")
        } else {
            toast("معرفتش أخفي الطلب")
        }
    }

    private fun onBoardAccepted(card: ParsedRideData) {
        markAccepted(card)
        LiveRequests.setStatus(LiveRequests.keyOf(card), LiveRequests.STATUS_ACCEPTED, "اتقبل")
        AutomationLog.add(applicationContext, AutomationLog.ACCEPT, card.fare, "من لوحة الطلبات")
        toast("تم قبول الطلب (${card.fare.toInt()} ج.م)")
    }

    private fun onBoardBidSent(card: ParsedRideData, value: Int) {
        RequestLog.recordOutcome(applicationContext, card.fingerprint, RequestLog.STATUS_BID_SENT, value)
        LiveRequests.setStatus(LiveRequests.keyOf(card), LiveRequests.STATUS_BID_SENT, "عرضت $value")
        AutomationLog.add(applicationContext, AutomationLog.BID, value.toDouble(), "من لوحة الطلبات: $value (عرض الراكب ${card.fare.toInt()})")
        toast("تم إرسال عرض $value ج.م")
    }

    private fun failBoardAction(p: PendingBoard, message: String) {
        pendingBoard = null
        LiveRequests.setStatus(p.key, LiveRequests.STATUS_LIVE)
        toast(message)
    }

    /**
     * Moves the pending action one step on the current screen: open the request (going back
     * first if another one is open), then accept / press the price button.
     */
    private fun continueBoardAction(root: AccessibilityNodeInfo, rides: List<ParsedRideData>, onRequestScreen: Boolean) {
        val p = pendingBoard ?: return
        val now = System.currentTimeMillis()
        if (now > p.until) {
            failBoardAction(p, "معرفتش أكمّل: الطلب ممكن يكون اختفى")
            return
        }
        // The own-price sheet is a window of its own (no request on it): carry on typing there.
        if (p.action == MAP_CUSTOM) {
            val sheet = findById(root, ID_PRICE_FIELD)
            if (sheet != null) {
                sheet.recycle()
                continueCustomPrice(root, p, now)
                return
            }
        }
        val card = rides.firstOrNull { LiveRequests.keyOf(it) == p.key }
        if (!onRequestScreen) {
            // On the list: open the request (again if a tap didn't take).
            if (card?.screenBounds == null) return
            if (now - p.stageAt > BOARD_RETRY_MS && p.opens < 3) {
                p.opens++
                p.stageAt = now
                clickCardBody(root, card.screenBounds)
            }
            worker.postDelayed({ requestRescan() }, BOARD_STEP_MS)
            return
        }
        if (card == null) {
            // Another request is open: back to the list first.
            if (now - p.stageAt > BOARD_RETRY_MS) {
                p.stageAt = now
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            worker.postDelayed({ requestRescan() }, BOARD_STEP_MS)
            return
        }
        val screen = Rect().also { root.getBoundsInScreen(it) }
        when (p.action) {
            MAP_OPEN -> {
                pendingBoard = null
                LiveRequests.setStatus(p.key, LiveRequests.STATUS_LIVE)
            }
            MAP_ACCEPT -> {
                pendingBoard = null
                if (clickAcceptButtonInside(root, screen)) onBoardAccepted(card)
                else failBoardAction(p, "مش لاقي زرار القبول في الطلب")
            }
            MAP_BID -> {
                // Read the buttons on screen now (the list kept by the map may include hidden ones).
                val chip = findBidChips(root, card.fare).firstOrNull { it.value == p.value }
                if (chip == null) {
                    // inDrive's price buttons scroll sideways: bring the others into view, then give up.
                    if (now - p.stageAt > BOARD_RETRY_MS) {
                        p.stage++
                        p.stageAt = now
                        if (p.stage > 4 || !scrollPriceButtons(root, forward = p.stage <= 2)) {
                            failBoardAction(p, "السعر ${p.value} مش موجود في أزرار إن درايف")
                            return
                        }
                    }
                    worker.postDelayed({ requestRescan() }, BOARD_STEP_MS)
                    return
                }
                pendingBoard = null
                if (clickNodeAt(root, chip.bounds)) onBoardBidSent(card, p.value)
                else failBoardAction(p, "معرفتش أضغط على السعر")
            }
            MAP_CUSTOM -> continueCustomPrice(root, p, now)
        }
    }

    /**
     * The driver's own price: inDrive's ✎ button (last of the price buttons) opens a sheet with a
     * price field and "Offer". Types the price there and presses Offer.
     */
    private fun continueCustomPrice(root: AccessibilityNodeInfo, p: PendingBoard, now: Long) {
        if (now - p.stageAt < BOARD_STEP_MS) {
            worker.postDelayed({ requestRescan() }, BOARD_STEP_MS)
            return
        }
        p.stageAt = now
        val field = findById(root, ID_PRICE_FIELD)
        if (field != null) {
            val typed = try {
                field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, p.value.toString())
                }
                field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } finally {
                field.recycle()
            }
            if (!typed) return failBoardAction(p, "معرفتش أكتب السعر")
            // Press Offer once the field shows the price.
            worker.postDelayed({ pressOffer(p) }, BOARD_STEP_MS)
            return
        }
        // The ✎ button: a price button without a price; it may be scrolled out of view.
        val pencil = findPencil(root)
        if (pencil != null) {
            pencil.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            pencil.recycle()
        } else if (p.stage < 3) {
            p.stage++
            scrollPriceButtons(root, forward = true)
        } else {
            return failBoardAction(p, "مش لاقي زرار السعر بتاعك في إن درايف")
        }
        worker.postDelayed({ requestRescan() }, BOARD_STEP_MS)
    }

    private fun pressOffer(p: PendingBoard) {
        if (pendingBoard !== p) return
        val root = inDriveRoot() ?: return failBoardAction(p, "إن درايف اختفى")
        try {
            val button = findById(root, ID_SET_OFFER)
            if (button == null) return failBoardAction(p, "مش لاقي زرار Offer")
            val ok = try {
                button.isEnabled && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } finally {
                button.recycle()
            }
            pendingBoard = null
            val card = lastRides.firstOrNull { LiveRequests.keyOf(it) == p.key }
            if (ok && card != null) onBoardBidSent(card, p.value)
            else if (ok) {
                LiveRequests.setStatus(p.key, LiveRequests.STATUS_BID_SENT, "عرضت ${p.value}")
                toast("تم إرسال عرض ${p.value} ج.م")
            } else failBoardAction(p, "معرفتش أضغط Offer")
        } finally {
            root.recycle()
        }
    }

    /** First node with resource id ending in ":id/[id]" (the caller recycles it), or null. */
    private fun findById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val found = root.findAccessibilityNodeInfosByViewId("$INDRIVE_ID_PREFIX$id")
        if (!found.isNullOrEmpty()) {
            found.drop(1).forEach { it.recycle() }
            return found[0]
        }
        // Another inDrive package name: match the id's end.
        var hit: AccessibilityNodeInfo? = null
        fun visit(node: AccessibilityNodeInfo) {
            if (hit != null) return
            if (node.viewIdResourceName?.endsWith(":id/$id") == true) {
                hit = AccessibilityNodeInfo.obtain(node)
                return
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child)
                child.recycle()
            }
        }
        visit(root)
        return hit
    }

    /** inDrive's ✎ (own price) button: a price button with no price on it. */
    private fun findPencil(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = root.findAccessibilityNodeInfosByViewId("$INDRIVE_ID_PREFIX$ID_PRICE_BUTTON") ?: return null
        var pencil: AccessibilityNodeInfo? = null
        for (n in all) {
            if (pencil == null && n.text.isNullOrBlank() && n.isVisibleToUser && n.isClickable) pencil = n else n.recycle()
        }
        return pencil
    }

    /** Scrolls inDrive's row of price buttons sideways. False when there is no such row. */
    private fun scrollPriceButtons(root: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val seen = mutableListOf<AccessibilityNodeInfo>()
        var row: AccessibilityNodeInfo? = null
        fun visit(node: AccessibilityNodeInfo) {
            if (row != null) return
            if (node.viewIdResourceName?.endsWith(":id/common_layout_buttons_scroll_view") == true) {
                row = node
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
            row?.performAction(
                if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            ) ?: false
        } finally {
            seen.forEach { it.recycle() }
        }
    }

    // ---- Snapshot of inDrive's trip map (request screen) ----

    /** Per request: when its markers showed, zoom-outs done, screenshots tried. Worker thread only. */
    private class SnapState(val openedAt: Long) {
        var markersAt = 0L
        var zooms = 0
        var zoomAt = 0L
        var attempts = 0
    }

    private val snapStates = LinkedHashMap<String, SnapState>()

    /**
     * On a Ride-request screen without a snapshot yet: waits for the route to be drawn (markers A
     * and B), zooms the map out when the trip doesn't fit (long trips), then takes one screenshot
     * for the board (and reads the trip length from it when unknown). True when it took over the
     * screenshot for this scan.
     */
    private fun maybeSnapshotRequest(root: AccessibilityNodeInfo, rides: List<ParsedRideData>, onRequestScreen: Boolean): Boolean {
        if (!onRequestScreen || Build.VERSION.SDK_INT < Build.VERSION_CODES.R || ocrInFlight) return false
        val hud = InDriveAssistantApp.instance.repository.getHudPrefs()
        if (!hud.liveMapEnabled) return false
        val card = rides.singleOrNull() ?: return false
        val key = LiveRequests.keyOf(card)
        if (LiveRequests.hasSnapshot(key)) return false
        val now = System.currentTimeMillis()
        val st = snapStates.getOrPut(key) { SnapState(now) }
        while (snapStates.size > 60) snapStates.remove(snapStates.keys.first())
        if (st.attempts >= MAX_SNAP_ATTEMPTS) return false

        val screen = Rect().also { root.getBoundsInScreen(it) }
        val markers = findMapMarkers(root)
        val a = markers.pointA
        val b = markers.pointB
        // The map's visible part ends where the request's sheet starts.
        val mapBottom = markers.mapBottom ?: card.screenBounds?.top ?: (screen.top + screen.height() / 2)
        val map = Rect(screen.left, screen.top, screen.right, mapBottom)
        if (map.height() < 200) return false

        // Give the map a moment to draw the route once its markers are there.
        if (a == null && b == null && now - st.openedAt < SNAP_NO_MARKERS_WAIT_MS) {
            worker.postDelayed({ requestRescan() }, 150)
            return false
        }
        if (st.markersAt == 0L) {
            st.markersAt = now
            // Opened by the app: show inDrive's hidden price buttons too (read while the map settles).
            if (autoOpenKey != null && autoOpenKey == rideKey(card)) scrollPriceButtons(root, forward = true)
        }
        val settleUntil = maxOf(st.markersAt + SNAP_SETTLE_MS, st.zoomAt + SNAP_ZOOM_SETTLE_MS)
        if (now < settleUntil) {
            worker.postDelayed({ requestRescan() }, settleUntil - now + 20)
            return false
        }

        // Long trip: A or B outside the visible map (or under the sheet): zoom out and look again.
        val inner = Rect(map).apply { inset(map.width() / 14, map.height() / 14) }
        val cut = a == null || b == null || listOfNotNull(a, b, markers.car).any { !inner.contains(it.centerX(), it.centerY()) }
        if (cut && st.zooms < MAX_SNAP_ZOOMS && pinchOut(map)) {
            st.zooms++
            st.zoomAt = now
            rideKey(card)?.let { ocrAttempts.remove(it) } // the trip label may be visible now
            worker.postDelayed({ requestRescan() }, SNAP_ZOOM_SETTLE_MS + 20)
            return true
        }

        val region = if (hud.boardShowApproach || a == null || b == null) map else tripRegion(a, b, map)
        // Read the trip length from the same screenshot when it is still unknown.
        val ocrKey = rideKey(card)
        val ocrRegion = if (!card.tripKnown && hud.readMapDistance && ocrKey != null && b != null &&
            (ocrAttempts[ocrKey] ?: 0) < MAX_OCR_ATTEMPTS
        ) {
            markers.infoWindows.minByOrNull { distanceSq(it, b) }
        } else null
        if (ocrRegion != null && ocrKey != null) ocrAttempts[ocrKey] = (ocrAttempts[ocrKey] ?: 0) + 1
        st.attempts++
        ocrInFlight = true
        val job = CaptureJob(
            key, region, map, markers.car, a, b,
            exclude = markers.infoWindows + listOfNotNull(a, b, markers.car),
            pickupKm = card.pickupDistanceKm.takeIf { card.pickupKnown },
            tripKm = card.tripDistanceKm.takeIf { card.tripKnown }
        )
        captureRequest(job, ocrKey, ocrRegion)
        return true
    }

    /** The part of the map around A and B (the trip itself, without the way to the pickup). */
    private fun tripRegion(a: Rect, b: Rect, map: Rect): Rect {
        val r = Rect(a).apply { union(b) }
        val pad = maxOf(r.width(), r.height()) / 4 + 80
        r.inset(-pad, -pad)
        // Not too thin: at least half the map's height and width.
        if (r.height() < map.height() / 2) r.inset(0, -(map.height() / 2 - r.height()) / 2)
        if (r.width() < map.width() / 2) r.inset(-(map.width() / 2 - r.width()) / 2, 0)
        if (!r.intersect(map)) return Rect(map)
        return r
    }

    /** Two fingers pinching towards the centre of [area]: the map zooms out. */
    private fun pinchOut(area: Rect): Boolean {
        val cx = area.centerX().toFloat()
        val cy = area.centerY().toFloat()
        val d = minOf(area.width(), area.height()) * 0.38f
        fun stroke(fromX: Float, fromY: Float, toX: Float, toY: Float) =
            android.accessibilityservice.GestureDescription.StrokeDescription(
                android.graphics.Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }, 0, PINCH_MS
            )
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke(cx - d, cy - d, cx - 24, cy - 24))
            .addStroke(stroke(cx + d, cy + d, cx + 24, cy + 24))
            .build()
        return dispatchGesture(gesture, null, null)
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
        val root = inDriveRoot()
        if (root == null) {
            if (otherAppInFront() == true) hidePopups()
            return // transient (keyboard, notification shade…): keep current popups
        }
        try {
            val pkg = root.packageName?.toString() ?: return
            val rides = resolveTrips(findRideCards(root))
            val onRequestScreen = lastScanWasRequestScreen
            if (captureRequested) {
                captureRequested = false
                saveDiagnosticDump(root, pkg, rides)
            }
            markInDriveShown()
            showPopups(rides)
            feedLiveMap(rides, onRequestScreen)
            continueBoardAction(root, rides, onRequestScreen)
            logRequests(rides, onRequestScreen)
            maybePromptRating(rides, onRequestScreen)
            maybeScheduleAutoAccept(rides)
            if (!maybeSnapshotRequest(root, rides, onRequestScreen)) maybeReadTripFromMap(root, rides)
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

    /** The driver's master switch (app or notification): no automated tap or swipe while paused. */
    private fun automationPaused(): Boolean = AutomationLog.isPaused(applicationContext)

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
        // Requests board: open every new request quickly to snapshot its trip map.
        val boardOpen = hud.liveMapEnabled && hud.boardAutoOpen
        // Driver's timings: how long a request stays open at most, and the pause before the next one.
        val stayMs = if (boardOpen) BOARD_OPEN_STAY_MS else (hud.autoOpenStaySeconds * 1000).toLong().coerceIn(1000L, 30_000L)
        val gapMs = if (boardOpen) BOARD_OPEN_GAP_MS else (hud.autoOpenGapSeconds * 1000).toLong().coerceIn(1000L, 120_000L)
        val active = autoOpenKey
        if (active != null) {
            val elapsed = now - autoOpenStartedAt
            if (onRequestScreen) {
                autoOpenSawRequest = true
                val card = rides.singleOrNull()
                if (card == null || rideKey(card) != active) {
                    finishAutoOpen(goBack = false) // the driver opened something else: hands off
                } else if (autoOpenDone(card, active, hud, boardOpen) || elapsed > stayMs) {
                    finishAutoOpen(goBack = true)
                }
            } else if (autoOpenSawRequest || elapsed > AUTO_OPEN_TAP_TIMEOUT_MS) {
                finishAutoOpen(goBack = false) // back on the list (driver pressed Back, or the tap did nothing)
            }
            return
        }

        val tripOpen = hud.tripAutoOpen && hud.readMapDistance
        if ((!tripOpen && !boardOpen) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R || automationPaused()) return
        if (onRequestScreen || pendingAutoAcceptFingerprint != null || pendingDismiss != null || pendingBoard != null) return
        // The pause is counted from the moment the previous request was closed.
        if (now - lastAutoOpenAt < gapMs) return

        val candidate = rides.firstOrNull { card ->
            val k = rideKey(card)
            card.screenBounds != null && k != null && k !in autoOpenTried &&
                ((tripOpen && !card.tripKnown) || (boardOpen && !LiveRequests.hasSnapshot(k)))
        } ?: return
        val key = rideKey(candidate) ?: return
        autoOpenTried.add(key)
        while (autoOpenTried.size > 100) autoOpenTried.remove(autoOpenTried.first())

        if (clickCardBody(root, candidate.screenBounds!!)) {
            autoOpenKey = key
            autoOpenActive = true
            autoOpenStartedAt = now
            autoOpenSawRequest = false
            lastAutoOpenAt = now
            worker.postDelayed(autoOpenTimeout, stayMs + 200)
            Log.i(TAG, "Auto-open: opened request to read its trip length")
            AutomationLog.add(applicationContext, AutomationLog.OPEN, candidate.fare, "فتح الطلب لقراءة طول المشوار")
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
        if (automationPaused()) return
        if (System.currentTimeMillis() - lastDismissAt < DISMISS_COOLDOWN_MS) return
        val candidate = rides.firstOrNull { it.fingerprint !in dismissedFingerprints && dismissReason(it, settings) != null } ?: return
        pendingDismiss = candidate.fingerprint
        val delayMs = (settings.autoDismissDelaySeconds * 1000).toLong().coerceIn(300L, 10_000L)
        worker.postDelayed({ executeDismiss(candidate.fingerprint) }, delayMs)
    }

    private fun executeDismiss(fingerprint: String) {
        pendingDismiss = null
        if (automationPaused()) return
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        val root = inDriveRoot() ?: return
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
                AutomationLog.add(
                    applicationContext, AutomationLog.HIDE, card.fare,
                    "طلب $reason" + (if (card.dropoffAddress.isNotBlank()) " إلى ${card.dropoffAddress}" else "")
                )
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
        if (InDriveAssistantApp.instance.repository.getHudPrefs().bidChipsMode != "AUTO_TAP" || automationPaused()) return
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
        if (InDriveAssistantApp.instance.repository.getHudPrefs().bidChipsMode != "AUTO_TAP" || automationPaused()) return
        val root = inDriveRoot() ?: return
        try {
            if (!isTargetPackage(root.packageName?.toString() ?: "", packageName)) return
            val card = findRideCards(root).singleOrNull() ?: return
            if ((rideKey(card) ?: card.fingerprint) != key) return // another request is open now
            val chip = card.bidChips.firstOrNull { it.value == value } ?: return
            if (clickNodeAt(root, chip.bounds)) {
                mainHandler.post {
                    Toast.makeText(applicationContext, "تم اختيار أفضل مزايدة: $value ج.م", Toast.LENGTH_SHORT).show()
                }
                AutomationLog.add(applicationContext, AutomationLog.BID, value.toDouble(), "ضغط على أفضل مزايدة (عرض الراكب ${card.fare.toInt()})")
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

    /** The opened request gave what it was opened for: its trip length and/or its board snapshot. */
    private fun autoOpenDone(card: ParsedRideData, key: String, hud: com.indrive.assistant.data.model.HudPrefs, boardOpen: Boolean): Boolean {
        if (!boardOpen) return card.tripKnown && hud.autoOpenBackWhenRead
        // The snapshot's screenshot also reads the trip length when it can: wait for that reading.
        val snapDone = LiveRequests.hasSnapshot(key) || (snapStates[key]?.attempts ?: 0) >= MAX_SNAP_ATTEMPTS
        return snapDone && !ocrInFlight
    }

    private fun finishAutoOpen(goBack: Boolean) {
        autoOpenKey = null
        autoOpenActive = false
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
    private fun captureAndRead(key: String, region: Rect) = captureRequest(null, key, region)

    /**
     * What one Ride-request screenshot is used for on the live map: the picture of inDrive's map
     * ([snapRegion]) and placing the request on the real map (markers, route colours, distances).
     */
    private class CaptureJob(
        val key: String,
        val snapRegion: Rect,
        val map: Rect,
        val car: Rect?,
        val a: Rect?,
        val b: Rect?,
        val exclude: List<Rect>,
        val pickupKm: Double?,
        val tripKm: Double?
    )

    /**
     * One screenshot for the live map [job] and/or the trip-length OCR ([ocrRegion], learned for
     * [ocrKey]). Either may be null.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureRequest(job: CaptureJob?, ocrKey: String?, ocrRegion: Rect?) {
        // Our own windows (the floating map, badges) would cover inDrive's map in the picture:
        // hide them for a moment, take the shot once they're gone from the screen, show them again.
        if (FloatingHudService.isRunning()) {
            FloatingHudService.setOverlaysHidden(true)
            worker.postDelayed({ takeRequestShot(job, ocrKey, ocrRegion) }, OVERLAY_HIDE_MS)
        } else {
            takeRequestShot(job, ocrKey, ocrRegion)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeRequestShot(job: CaptureJob?, ocrKey: String?, ocrRegion: Rect?) {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, workerExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    FloatingHudService.setOverlaysHidden(false)
                    val full = try {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        software
                    } catch (e: Exception) {
                        Log.w(TAG, "Screenshot decode failed", e)
                        null
                    }
                    if (full == null) {
                        ocrInFlight = false
                        return
                    }
                    if (job != null) cropSafely(full, job.snapRegion)?.let { LiveRequests.setSnapshot(job.key, scaleForBoard(it)) }
                    val crop = if (ocrKey != null && ocrRegion != null) cropSafely(full, ocrRegion) else null
                    if (crop == null || ocrKey == null || ocrRegion == null) {
                        if (job != null) placeOnMap(job, full, job.tripKm)
                        ocrInFlight = false
                        requestRescan() // auto-open can go back now
                        return
                    }
                    // The trip length read now also gives the map's scale.
                    readTripLength(ocrKey, ocrRegion, crop) { km -> if (job != null) placeOnMap(job, full, km ?: job.tripKm) }
                }

                override fun onFailure(errorCode: Int) {
                    // e.g. ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT: the next scan retries.
                    FloatingHudService.setOverlaysHidden(false)
                    lastOcrLog = "screenshot failed: code=$errorCode"
                    ocrInFlight = false
                    if (job != null) worker.postDelayed({ requestRescan() }, 400)
                }
            })
        } catch (e: Exception) {
            FloatingHudService.setOverlaysHidden(false)
            lastOcrLog = "screenshot unavailable: ${e.message}"
            ocrInFlight = false
        }
    }

    /** Pickup, destination and routes in real coordinates (needs the driver's GPS position). */
    private fun placeOnMap(job: CaptureJob, full: Bitmap, tripKm: Double?) {
        val me = DriverLocation.recent()
        if (me == null || job.car == null || job.a == null || job.b == null) {
            lastGeoLog = "not placed: gps=${me != null} car=${job.car != null} A=${job.a != null} B=${job.b != null}"
            return
        }
        try {
            val geo = RouteLocator.locate(full, job.map, job.car, job.a, job.b, job.exclude, me, job.pickupKm, tripKm)
            if (geo != null) LiveRequests.setGeo(job.key, geo)
            lastGeoLog = if (geo == null) "not placed: no scale" else
                "placed: ${"%.2f".format(java.util.Locale.US, geo.metersPerPixel)} m/px, trip pts=${geo.trip.size}, approach pts=${geo.approach.size}"
            Log.i(TAG, "Live map: $lastGeoLog")
        } catch (e: Exception) {
            Log.w(TAG, "Placing the request failed", e)
        }
    }

    private fun scaleForBoard(src: Bitmap): Bitmap {
        if (src.width <= SNAP_WIDTH) return src
        return Bitmap.createScaledBitmap(src, SNAP_WIDTH, (src.height.toLong() * SNAP_WIDTH / src.width).toInt().coerceAtLeast(1), true)
    }

    private fun readTripLength(key: String, region: Rect, crop: Bitmap, then: ((Double?) -> Unit)? = null) {
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
                }
                then?.invoke(km)
                ocrInFlight = false
                // Re-parse now so the badge switches from "≈" to the real value (and auto-open can go back).
                if (!scanScheduled) {
                    scanScheduled = true
                    worker.post(scanRunnable)
                }
            }
            .addOnFailureListener(workerExecutor) { e ->
                lastOcrLog = "region=${region.toShortString()} OCR failed: ${e.message}"
                then?.invoke(null)
                ocrInFlight = false
                requestRescan()
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
        sb.append("== LIVE MAP ==\n").append(lastGeoLog).append('\n')
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
        if (!settings.autoAcceptEnabled || automationPaused()) return
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
        if (!settings.autoAcceptEnabled || automationPaused()) return

        val root = inDriveRoot() ?: return
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
                AutomationLog.add(
                    applicationContext, AutomationLog.ACCEPT, card.fare,
                    "صافي ${analysis.netProfit.toInt()} ج.م • ${String.format(java.util.Locale.US, "%.1f", analysis.profitPerKm)} ج.م/كم"
                )
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
        autoOpenActive = false
        if (inDriveOnScreen) {
            inDriveOnScreen = false
            FloatingHudService.onInDriveVisible(false)
        }
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
        /** True while auto-open has a request open (the HUD stays quiet about screens it opened). */
        @Volatile var autoOpenActive = false
            private set
        /** inDrive is on screen (alone or in split screen): the floating map shows only then. */
        @Volatile var inDriveOnScreen = false
            private set
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

        const val MAP_ACCEPT = "ACCEPT"
        const val MAP_HIDE = "HIDE"
        const val MAP_BID = "BID"
        /** Just open the request in inDrive (its price buttons then show on the map). */
        const val MAP_OPEN = "OPEN"
        /** The driver's own price, typed into inDrive's ✎ price sheet. */
        const val MAP_CUSTOM = "CUSTOM"
        private const val INDRIVE_ID_PREFIX = "sinet.startup.inDriver:id/"
        private const val ID_PRICE_BUTTON = "bid_btn_price_highlighted"
        private const val ID_PRICE_FIELD = "set_price_edittext"
        private const val ID_SET_OFFER = "set_offer_button"
        /** A board action gives up after this long (request gone, unknown screen). */
        private const val BOARD_ACTION_TIMEOUT_MS = 9000L
        /** Pause between two steps of a board action (the screen needs to react). */
        private const val BOARD_STEP_MS = 280L
        /** A tap that didn't open / close a screen is retried after this long. */
        private const val BOARD_RETRY_MS = 900L

        // Board snapshots: wait for the route to draw, zoom out for long trips (a few times at most).
        private const val SNAP_SETTLE_MS = 350L
        private const val SNAP_ZOOM_SETTLE_MS = 650L
        private const val SNAP_NO_MARKERS_WAIT_MS = 1500L
        private const val MAX_SNAP_ZOOMS = 3
        private const val MAX_SNAP_ATTEMPTS = 3
        private const val PINCH_MS = 260L
        /** Board snapshots are kept this wide (enough for a card, light on memory). */
        private const val SNAP_WIDTH = 480
        /** Our overlays are hidden this long before a screenshot (a couple of frames). */
        private const val OVERLAY_HIDE_MS = 90L
        // Board auto-open: as fast as the map allows (it goes back as soon as the snapshot is taken).
        private const val BOARD_OPEN_STAY_MS = 5000L
        private const val BOARD_OPEN_GAP_MS = 400L

        /**
         * The driver's tap on the live map ([MAP_ACCEPT], [MAP_HIDE], [MAP_OPEN], [MAP_BID] or [MAP_CUSTOM] with
         * [value]) for the request [key] (LiveRequests.keyOf). False when screen reading is off.
         */
        fun mapAction(key: String, action: String, value: Int = 0): Boolean {
            return instance?.postMapAction(key, action, value) ?: false
        }

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

        /** Last attempt to place a request on the live map, included in the diagnostic dump. */
        @Volatile
        var lastGeoLog: String = "no request placed yet"

        /**
         * [car] is the driver's car marker; [mapBottom] the top of the sheet covering the map's
         * lower part (null when not found).
         */
        private class MapMarkers(
            val infoWindows: List<Rect>,
            val pointA: Rect?,
            val pointB: Rect?,
            val car: Rect? = null,
            val mapBottom: Int? = null
        )

        private fun findMapMarkers(root: AccessibilityNodeInfo): MapMarkers {
            val windows = mutableListOf<Rect>()
            var a: Rect? = null
            var b: Rect? = null
            var car: Rect? = null
            var sheetTop: Int? = null
            fun visit(node: AccessibilityNodeInfo) {
                val desc = node.contentDescription?.toString() ?: ""
                if (desc.isNotEmpty()) {
                    val r = Rect()
                    node.getBoundsInScreen(r)
                    when {
                        desc.contains("_info_window_") -> if (r.width() > 0 && r.height() > 0) windows.add(r)
                        desc.endsWith("_PointA") -> a = r
                        desc.endsWith("_PointB") -> b = r
                        desc.endsWith("_CarIcon") -> car = r
                    }
                }
                if (sheetTop == null && node.viewIdResourceName?.endsWith(":id/order_container_info") == true) {
                    sheetTop = Rect().also { node.getBoundsInScreen(it) }.top
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    visit(child)
                    child.recycle()
                }
            }
            visit(root)
            return MapMarkers(windows, a, b, car, sheetTop)
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
