package com.indrive.assistant.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.indrive.assistant.InDriveAssistantApp
import com.indrive.assistant.MainActivity
import com.indrive.assistant.data.local.AutomationLog
import com.indrive.assistant.data.model.HudPrefs
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.engine.BidAdvisor
import com.indrive.assistant.engine.FareAnalysisResult
import com.indrive.assistant.engine.FareCalculatorEngine
import com.indrive.assistant.engine.ProfitabilityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class FloatingHudService : Service() {

    private var windowManager: WindowManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Active Card Popups mapped by ride fingerprint (main thread only)
    private val activePopups = mutableMapOf<String, CardPopupViewHolder>()

    // Anti-Vibration Spam tracking
    private var lastVibratedFingerprint = ""
    private var lastVibrateTimestamp = 0L

    private class CardPopupViewHolder(
        var ride: ParsedRideData,
        var analysis: FareAnalysisResult,
        val container: FrameLayout,
        val params: WindowManager.LayoutParams,
        var isExpanded: Boolean = false,
        var pulseAnimator: ValueAnimator? = null,
        /** Driver's manual drag, kept across re-scans so the badge doesn't snap back. */
        var dragOffsetX: Int = 0,
        var dragOffsetY: Int = 0,
        /** Content signature of the last render; views are only rebuilt when it changes. */
        var renderKey: String = "",
        /** Screen edge the drag offset was measured from ("LEFT" / "RIGHT"). */
        var side: String = "",
        /** Pending auto-collapse of the expanded card, if enabled. */
        var autoCollapse: Runnable? = null
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
        activeInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE_READING) {
            if (InDriveAccessibilityService.pause()) {
                toast("تم إيقاف قراءة الشاشة. يمكنك فتح تطبيقات البنوك الآن، وشغّلها من نفس الإشعار عند العودة لإن درايف")
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_TOGGLE_AUTOMATION) {
            val paused = !AutomationLog.isPaused(this)
            AutomationLog.setPaused(this, paused)
            toast(if (paused) "تم إيقاف كل الأتمتة (قبول، إخفاء، فتح، مزايدة)" else "تم تشغيل الأتمتة حسب إعداداتك")
            onScreenReadingChanged()
            return START_STICKY
        }
        if (intent?.action == ACTION_CAPTURE) {
            InDriveAccessibilityService.captureRequested = true
            toast("أغلق الإشعارات وافتح شاشة الطلبات في إن درايف: سيتم الالتقاط تلقائياً")
            return START_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; stopping HUD service")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingHudService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Screen reading on: pause it here. Off: only the driver can switch it on, in Settings.
        val reading = InDriveAccessibilityService.isRunning()
        val automationPaused = AutomationLog.isPaused(this)
        val readingAction = if (reading) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "إيقاف قراءة الشاشة",
                PendingIntent.getService(
                    this,
                    3,
                    Intent(this, FloatingHudService::class.java).setAction(ACTION_PAUSE_READING),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "تشغيل قراءة الشاشة",
                ScreenReadingTileService.settingsPendingIntent(this, 4)
            )
        }

        return NotificationCompat.Builder(this, InDriveAssistantApp.CHANNEL_ID)
            .setContentTitle("The Driver")
            .setContentText(
                when {
                    !reading -> "قراءة الشاشة متوقفة: لن تظهر العلامات حتى تشغّلها"
                    automationPaused -> "المساعد يعمل • الأتمتة متوقفة"
                    else -> "المساعد العائم يعمل ويحلل عروض إن درايف"
                }
            )
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopIntent)
            .addAction(readingAction)
            // Master switch for every automated tap / swipe (diagnostic capture is in the app).
            .addAction(
                if (automationPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (automationPaused) "تشغيل الأتمتة" else "إيقاف الأتمتة",
                PendingIntent.getService(
                    this,
                    2,
                    Intent(this, FloatingHudService::class.java).setAction(ACTION_TOGGLE_AUTOMATION),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()
    }

    // =========================================================================
    // MULTI-POPUP SYNC
    // =========================================================================
    private fun handleCardsSync(cards: List<ParsedRideData>) {
        serviceScope.launch {
            val wm = windowManager ?: return@launch

            if (cards.isEmpty()) {
                clearAllCardPopups()
                return@launch
            }

            val hud = hudPrefs()

            // Cards with their analysis; losing/unknown rides are hidden when the driver chose so.
            val visible = cards.map { it to analyze(it) }.filter { (card, analysis) ->
                hud.showLossBadges ||
                    (!card.distanceUnknown && analysis.profitabilityLevel != ProfitabilityLevel.LOSS_DANGER)
            }
            val currentFingerprints = visible.map { it.first.fingerprint }.toSet()

            // 1. Remove popups for cards that disappeared from screen (or are now hidden)
            activePopups.keys.filter { it !in currentFingerprints }.forEach { fp ->
                activePopups.remove(fp)?.let { removePopupWindow(wm, it) }
            }

            // 2. Add or update popups for each currently visible card
            for ((card, analysis) in visible) {
                val existing = activePopups[card.fingerprint]
                if (existing != null) {
                    existing.ride = card
                    existing.analysis = analysis
                    if (!existing.isExpanded && applyCompactPosition(existing)) safeUpdateLayout(existing)
                    renderPopupIfChanged(existing)
                } else {
                    createAndAttachPopup(card, analysis)
                    alertNewRequest(card, analysis)
                }
            }

            // 3. Circles over the counter-bid buttons (Ride-request screen only)
            syncBidChips(cards)
        }
    }

    private fun hudPrefs(): HudPrefs = InDriveAssistantApp.instance.repository.getHudPrefs()

    /**
     * Places a compact badge next to its card on the configured screen edge, keeping the
     * driver's drag offset. Returns true when the window position changed.
     */
    private fun applyCompactPosition(holder: CardPopupViewHolder): Boolean {
        val side = hudPrefs().side
        if (holder.side != side) {
            // Switching edges: the old drag offset was measured from the other side.
            holder.side = side
            holder.dragOffsetX = 0
        }
        val gravity = Gravity.TOP or (if (side == "RIGHT") Gravity.RIGHT else Gravity.LEFT)
        val x = BADGE_MARGIN_X + holder.dragOffsetX
        val y = calculatePopupY(holder.ride) + holder.dragOffsetY
        if (holder.params.gravity == gravity && holder.params.x == x && holder.params.y == y) return false
        holder.params.gravity = gravity
        holder.params.x = x
        holder.params.y = y
        return true
    }

    private fun analyze(card: ParsedRideData): FareAnalysisResult {
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        return FareCalculatorEngine.analyze(
            pickupDistanceKm = card.pickupDistanceKm,
            tripDistanceKm = card.tripDistanceKm,
            estimatedMinutes = FareCalculatorEngine.estimateMinutes(card.totalDistanceKm),
            offeredFare = card.fare,
            settings = settings,
            pickupAddress = card.pickupAddress,
            dropoffAddress = card.dropoffAddress,
            passengerName = card.passengerName
        )
    }

    /** Screen-space Y of the card's top edge (windows use FLAG_LAYOUT_IN_SCREEN). */
    private fun calculatePopupY(card: ParsedRideData): Int {
        return (card.screenBounds?.top?.plus(6) ?: 320).coerceAtLeast(30)
    }

    private fun baseLayoutParams(): WindowManager.LayoutParams {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
        }
    }

    private fun createAndAttachPopup(card: ParsedRideData, analysis: FareAnalysisResult) {
        val wm = windowManager ?: return

        val params = baseLayoutParams()
        val container = FrameLayout(this)
        val holder = CardPopupViewHolder(ride = card, analysis = analysis, container = container, params = params)
        applyCompactPosition(holder)

        // Touches outside an expanded card collapse it, while still reaching inDrive underneath.
        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE && holder.isExpanded) {
                collapsePopup(holder)
            }
            false
        }

        renderPopupIfChanged(holder)

        try {
            wm.addView(container, params)
            activePopups[card.fingerprint] = holder
        } catch (e: Exception) {
            Log.e(TAG, "Could not attach HUD popup (overlay permission?)", e)
        }
    }

    private fun removePopupWindow(wm: WindowManager, holder: CardPopupViewHolder) {
        holder.pulseAnimator?.cancel()
        holder.autoCollapse?.let { holder.container.removeCallbacks(it) }
        try {
            wm.removeView(holder.container)
        } catch (_: IllegalArgumentException) {
            // Already detached.
        }
    }

    private fun safeUpdateLayout(holder: CardPopupViewHolder) {
        try {
            windowManager?.updateViewLayout(holder.container, holder.params)
        } catch (_: IllegalArgumentException) {
            // The popup was removed (card vanished) between the tap and this update.
        }
    }

    private fun collapsePopup(holder: CardPopupViewHolder) {
        if (!holder.isExpanded) return
        holder.autoCollapse?.let { holder.container.removeCallbacks(it) }
        holder.autoCollapse = null
        holder.isExpanded = false
        holder.params.width = WindowManager.LayoutParams.WRAP_CONTENT
        applyCompactPosition(holder)
        renderPopupIfChanged(holder)
        safeUpdateLayout(holder)
    }

    private fun collapseAllPopups() {
        activePopups.values.forEach { collapsePopup(it) }
    }

    private fun togglePopupExpanded(holder: CardPopupViewHolder) {
        if (holder.isExpanded) {
            collapsePopup(holder)
            return
        }
        // Only one card is expanded at a time.
        collapseAllPopups()

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val cardWidth = (screenWidth * 0.92f).toInt()
            .coerceAtMost((380 * density).toInt())
            .coerceAtMost(screenWidth - (16 * density).toInt())

        holder.isExpanded = true
        holder.params.width = cardWidth
        holder.params.gravity = Gravity.TOP or Gravity.LEFT
        holder.params.x = (screenWidth - cardWidth) / 2
        // Prefer opening ABOVE the request card so its price and "Accept" button stay visible
        // and tappable; fall back to the card's own position when there isn't room above.
        val minY = (50 * density).toInt()
        val cardTop = calculatePopupY(holder.ride)
        val above = cardTop - (EXPANDED_HEIGHT_ESTIMATE_DP * density).toInt() - (8 * density).toInt()
        holder.params.y = if (above >= minY) {
            above
        } else {
            cardTop.coerceIn(minY, (screenHeight - (430 * density).toInt()).coerceAtLeast(minY))
        }
        renderPopupIfChanged(holder)
        safeUpdateLayout(holder)

        val seconds = hudPrefs().autoCollapseSeconds
        if (seconds > 0) {
            val runnable = Runnable { collapsePopup(holder) }
            holder.autoCollapse = runnable
            holder.container.postDelayed(runnable, seconds * 1000L)
        }
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    /**
     * [filled] = solid background (only "excellent", so it stands out more than "good");
     * [glyph] = status symbol shown next to the colour so the state never relies on colour alone.
     */
    private data class BadgeTheme(val colorHex: String, val label: String, val filled: Boolean = false, val glyph: String? = null)

    /** Profitability decides the colour; zones only add a marker and a label. */
    private fun themeFor(ride: ParsedRideData, analysis: FareAnalysisResult): BadgeTheme {
        if (ride.distanceUnknown) return BadgeTheme(COLOR_UNKNOWN, "لم تُقرأ المسافة - راجع الطلب")
        val zoneSuffix = when {
            analysis.matchedBlacklistZone != null -> " • منطقة محظورة (${analysis.matchedBlacklistZone})"
            analysis.matchedFavoriteZone != null -> " • ★ منطقة مفضلة (${analysis.matchedFavoriteZone})"
            else -> ""
        } + if (ride.distanceEstimated) " • ربح تقريبي" else ""
        return when (analysis.profitabilityLevel) {
            ProfitabilityLevel.EXCELLENT_PROFIT -> BadgeTheme(COLOR_EXCELLENT, "ربح ممتاز$zoneSuffix", filled = true, glyph = "✓")
            ProfitabilityLevel.GOOD_PROFIT -> BadgeTheme(COLOR_GOOD, "عرض رابح$zoneSuffix", glyph = "✓")
            ProfitabilityLevel.POOR_MARGIN -> BadgeTheme(COLOR_POOR, "هامش ضعيف - اقترح زيادة$zoneSuffix", glyph = "!")
            ProfitabilityLevel.LOSS_DANGER -> BadgeTheme(COLOR_LOSS, "${analysis.statusMessage}$zoneSuffix", glyph = "✕")
        }
    }

    private fun renderPopupIfChanged(holder: CardPopupViewHolder) {
        val repo = InDriveAssistantApp.instance.repository
        val a = holder.analysis
        val r = holder.ride
        val key = listOf(
            holder.isExpanded, r.pickupKnown, r.tripKnown, r.addressCandidates, r.fare.toInt(), a.netProfit.roundToInt(),
            a.recommendedFare.toInt(), a.profitabilityLevel, a.matchedFavoriteZone, a.matchedBlacklistZone,
            r.pickupAddress, r.dropoffAddress, String.format(Locale.US, "%.1f/%.1f", r.pickupDistanceKm, r.tripDistanceKm),
            hudPrefs(),
            repo.isFavoriteZone(r.pickupAddress), repo.isBlacklistedZone(r.pickupAddress),
            repo.isFavoriteZone(r.dropoffAddress), repo.isBlacklistedZone(r.dropoffAddress),
            r.passengerName, repo.isFavoritePassenger(r.passengerName), repo.isBlacklistedPassenger(r.passengerName)
        ).joinToString("|")
        if (key == holder.renderKey) return
        holder.renderKey = key
        renderPopup(holder)
    }

    private fun renderPopup(holder: CardPopupViewHolder) {
        holder.container.removeAllViews()
        holder.pulseAnimator?.cancel()
        holder.pulseAnimator = null

        val theme = themeFor(holder.ride, holder.analysis)
        if (holder.isExpanded) {
            holder.container.alpha = 1f
            renderExpandedDetailsCard(holder, theme)
        } else {
            holder.container.alpha = hudPrefs().opacity.toFloat()
            renderCompactBadge(holder, theme)
        }
    }

    private fun signedInt(v: Double): String {
        val p = v.roundToInt()
        return if (p >= 0) "+$p" else "−${abs(p)}"
    }

    /**
     * Compact badge text, per the driver's choice of primary value. Profit-based values are
     * prefixed with "≈" when one distance had to be assumed, and show "؟" when none was read.
     */
    private fun badgeValue(holder: CardPopupViewHolder): String {
        val a = holder.analysis
        val r = holder.ride
        return when (hudPrefs().primaryValue) {
            "OFFER" -> "${r.fare.roundToInt()}"
            "RECOMMENDED" -> if (r.distanceUnknown) "؟" else "${a.recommendedFare.roundToInt()}"
            "PROFIT_PER_KM" -> when {
                r.distanceUnknown -> "؟"
                r.distanceEstimated -> "≈" + String.format(Locale.US, "%.1f", a.profitPerKm)
                else -> String.format(Locale.US, "%.1f", a.profitPerKm)
            }
            else -> when {
                r.distanceUnknown -> "؟"
                r.distanceEstimated -> "≈" + signedInt(a.netProfit)
                else -> signedInt(a.netProfit)
            }
        }
    }

    /** Short caption under/next to the value, explaining what the number is. */
    private fun badgeCaption(): String = when (hudPrefs().primaryValue) {
        "OFFER" -> "العرض"
        "RECOMMENDED" -> "اقترح"
        "PROFIT_PER_KM" -> "ج/كم"
        else -> "صافي"
    }

    private fun renderCompactBadge(holder: CardPopupViewHolder, theme: BadgeTheme) {
        val density = resources.displayMetrics.density
        val hud = hudPrefs()
        val style = hud.style.uppercase()
        val sizeScale = when (hud.size.uppercase()) {
            "SMALL" -> 0.85f
            "LARGE" -> 1.22f
            else -> 1.0f
        }

        val value = badgeValue(holder)
        val isFav = (holder.analysis.isFavoriteZone || holder.analysis.isFavoritePassenger) && !holder.ride.distanceUnknown

        val badgeView = when (style) {
            "SLIM_PILL" -> buildSlimPill(value, theme, sizeScale, density)
            "SPEEDO_GAUGE" -> buildSpeedoGauge(value, theme, sizeScale, density)
            "MINI_TAG" -> buildMiniTag(value, theme, sizeScale, density)
            "CORNER_RIBBON" -> buildCornerRibbon(value, theme, sizeScale, density)
            "GLOW_DOT" -> buildGlowDot(value, theme, sizeScale, density)
            else -> buildCompactCircle(value, theme, sizeScale, density)
        }
        badgeView.contentDescription = "${theme.label}: ${badgeCaption()} $value"

        badgeView.setOnClickListener { togglePopupExpanded(holder) }
        setupDragTouchListener(badgeView, holder)

        holder.container.addView(badgeView)

        // Status symbol (top-end corner), so the verdict never relies on colour alone.
        theme.glyph?.let { holder.container.addView(cornerChip(it, theme.colorHex, Gravity.TOP or Gravity.END, density)) }

        // Zone marker (top-start corner): brand star for favourites, red sign for blacklisted zones.
        val marker = when {
            holder.analysis.isBlacklistedZone || holder.analysis.isBlacklistedPassenger -> "⛔" to COLOR_LOSS
            isFav -> "★" to COLOR_FAVORITE
            else -> null
        }
        if (marker != null) {
            holder.container.addView(cornerChip(marker.first, marker.second, Gravity.TOP or Gravity.START, density))
        }
    }

    /** Small round chip pinned to a corner of the compact badge. */
    private fun cornerChip(symbol: String, colorHex: String, corner: Int, density: Float): View = TextView(this).apply {
        text = symbol
        setTextColor(Color.parseColor(COLOR_INK))
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        val d = (18 * density).toInt()
        layoutParams = FrameLayout.LayoutParams(d, d).apply { gravity = corner }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(colorHex))
            setStroke((1 * density).toInt(), Color.parseColor(COLOR_INK))
        }
        elevation = 9 * density
    }

    /** Badge text size: scaled with the chosen size, but never below 11sp. */
    private fun sp(base: Float, scale: Float): Float = max(11f, base * scale)

    /** Background of a badge: solid status colour when [BadgeTheme.filled], otherwise the dark default. */
    private fun badgeFill(theme: BadgeTheme, darkDefault: String): Int =
        Color.parseColor(if (theme.filled) theme.colorHex else darkDefault)

    /** Text colour that stays readable on [badgeFill]. */
    private fun badgeInk(theme: BadgeTheme): Int = if (theme.filled) Color.parseColor(COLOR_INK) else Color.WHITE

    // Style 1: COMPACT_CIRCLE
    private fun buildCompactCircle(value: String, theme: BadgeTheme, scale: Float, density: Float): View {
        val diameter = (56 * scale * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(diameter, diameter)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(badgeFill(theme, "#0B0F19"))
                setStroke((2.5f * density).toInt(), Color.parseColor(theme.colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(TextView(this).apply {
            text = value
            setTextColor(badgeInk(theme))
            textSize = sp(14f, scale)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
            includeFontPadding = false
        })
        return layout
    }

    // Style 2: SLIM_PILL
    private fun buildSlimPill(value: String, theme: BadgeTheme, scale: Float, density: Float): View {
        val h = (32 * scale * density).toInt()
        val padH = (10 * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, 0, padH, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(badgeFill(theme, "#0B0F19"))
                setStroke((2.2f * density).toInt(), Color.parseColor(theme.colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(View(this).apply {
            val dotSize = (8 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { marginEnd = (6 * density).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (theme.filled) Color.parseColor(COLOR_INK) else Color.parseColor(theme.colorHex))
            }
        })
        layout.addView(TextView(this).apply {
            text = "$value ${badgeCaption()}"
            setTextColor(badgeInk(theme))
            textSize = sp(13.5f, scale)
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
        })
        return layout
    }

    // Style 3: SPEEDO_GAUGE
    private fun buildSpeedoGauge(value: String, theme: BadgeTheme, scale: Float, density: Float): View {
        val w = (62 * scale * density).toInt()
        val h = (50 * scale * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(w, h)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(badgeFill(theme, "#0F172A"))
                setStroke((2.5f * density).toInt(), Color.parseColor(theme.colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (3 * density).toInt())
            background = GradientDrawable().apply { setColor(Color.parseColor(theme.colorHex)) }
        })
        layout.addView(TextView(this).apply {
            text = value
            setTextColor(badgeInk(theme))
            textSize = sp(14f, scale)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
            includeFontPadding = false
            setPadding(0, (2 * density).toInt(), 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = badgeCaption()
            setTextColor(if (theme.filled) Color.parseColor(COLOR_INK) else Color.parseColor("#A3AEC2"))
            textSize = 11f
            gravity = Gravity.CENTER
            setSingleLine(true)
            includeFontPadding = false
        })
        return layout
    }

    // Style 4: MINI_TAG (always a solid tag; the corner symbol tells the states apart)
    private fun buildMiniTag(value: String, theme: BadgeTheme, scale: Float, density: Float): View {
        val h = (32 * scale * density).toInt()
        val padH = (9 * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, 0, padH, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * density
                setColor(Color.parseColor(theme.colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(TextView(this).apply {
            text = "$value ${badgeCaption()}"
            setTextColor(Color.parseColor(COLOR_INK))
            textSize = sp(13.5f, scale)
            typeface = Typeface.DEFAULT_BOLD
        })
        return layout
    }

    // Style 5: CORNER_RIBBON
    private fun buildCornerRibbon(value: String, theme: BadgeTheme, scale: Float, density: Float): View {
        val h = (30 * scale * density).toInt()
        val padH = (10 * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, 0, padH, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor(badgeFill(theme, "#0B132B"))
                setStroke((1.8f * density).toInt(), Color.parseColor(theme.colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(TextView(this).apply {
            text = "${badgeCaption()} $value"
            setTextColor(if (theme.filled) Color.parseColor(COLOR_INK) else Color.parseColor(theme.colorHex))
            textSize = sp(13f, scale)
            typeface = Typeface.DEFAULT_BOLD
        })
        return layout
    }

    // Style 6: GLOW_DOT
    private fun buildGlowDot(value: String, theme: BadgeTheme, scale: Float, density: Float): View {
        val diameter = (52 * scale * density).toInt()
        val layout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(diameter, diameter)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(badgeFill(theme, "#0D1B2A"))
                setStroke((3.5f * density).toInt(), Color.parseColor(theme.colorHex))
            }
            elevation = 8 * density
        }
        layout.addView(TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            text = value
            setTextColor(badgeInk(theme))
            textSize = sp(14f, scale)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        return layout
    }

    // =========================================================================
    // EXPANDED DETAILS CARD WITH 1-CLICK ZONE MANAGEMENT
    // =========================================================================
    private fun renderExpandedDetailsCard(holder: CardPopupViewHolder, theme: BadgeTheme) {
        val density = resources.displayMetrics.density
        val card = holder.ride
        val analysis = holder.analysis
        fun dp(v: Float) = (v * density).toInt()

        val cardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.parseColor("#0B0F19"))
                setStroke(dp(2f), Color.parseColor(theme.colorHex))
            }
            elevation = 16 * density
        }

        // ROW 1: Status label & close button
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(TextView(this).apply {
            text = theme.label
            setTextColor(Color.parseColor(COLOR_INK))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(Color.parseColor(theme.colorHex))
            }
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = "✕ تصغير"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(44f)
            minWidth = dp(64f)
            setPadding(dp(12f), dp(8f), dp(4f), dp(8f))
            setOnClickListener { collapsePopup(holder) }
        })

        // ROW 2: Side-by-side fare comparison
        val fareRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10f) }
        }
        fareRow.addView(metricBox("عرض الراكب", "${card.fare.toInt()} ج.م", "#FFFFFF", density, 0))
        fareRow.addView(metricBox("المزايدة المقترحة", "${analysis.recommendedFare.toInt()} ج.م", "#10B981", density, dp(8f)))

        // ROW 3: Net profit
        val netProfitVal = String.format(Locale.US, "%.1f", analysis.netProfit)
        val profitPerKmVal = String.format(Locale.US, "%.1f", analysis.profitPerKm)
        val profitRow = infoRow(
            "صافي الربح: $netProfitVal ج.م  ($profitPerKmVal ج.م/كم)",
            if (analysis.netProfit >= 0) "#38BDF8" else "#EF4444",
            density,
            bold = true
        )

        // ROW 4: Distances
        fun km(v: Double, known: Boolean) =
            if (known) String.format(Locale.US, "%.1f", v) else String.format(Locale.US, "~%.0f؟", v)
        val distText = if (card.distanceUnknown) {
            "المسافة: لم تُقرأ من الكارت - راجع الطلب في إن درايف"
        } else {
            "المسافة: ${km(card.pickupDistanceKm, card.pickupKnown)} وصول + ${km(card.tripDistanceKm, card.tripKnown)} مشوار (كم)" +
                if (card.distanceEstimated) "\nالرقم الذي عليه ؟ غير ظاهر في الكارت وتم افتراضه" else ""
        }
        val distRow = infoRow(distText, if (card.distanceEstimated) "#FBBF24" else "#F8FAFC", density, bold = false)

        // ROW 5 (optional): operating cost breakdown
        val costRow = infoRow(
            String.format(
                Locale.US,
                "التكاليف: بنزين %.1f + عمولة %.1f + استهلاك %.1f = %.1f ج.م",
                analysis.fuelCost, analysis.commissionCost, analysis.wearAndTearCost, analysis.totalOperatingCost
            ),
            "#CBD5E1", density, bold = false
        )

        val hud = hudPrefs()
        cardRoot.addView(topRow)
        // "ليه؟": the reason behind the colour, in one line.
        cardRoot.addView(infoRow("ليه؟ ${whyText(card, analysis)}", "#E2E8F0", density, bold = false))
        if (hud.showFareComparison) cardRoot.addView(fareRow)
        if (hud.showProfit) cardRoot.addView(profitRow)
        if (hud.showDistance) cardRoot.addView(distRow)
        if (hud.showCostBreakdown) cardRoot.addView(costRow)

        // Passenger lists: always offered when the card shows a name.
        if (card.passengerName.isNotBlank()) {
            cardRoot.addView(buildPassengerActionRow(card.passengerName, holder, density))
        }

        if (!hud.showZones) {
            holder.container.addView(cardRoot)
            return
        }

        // Zone tagging: pickup / drop-off if recognised, plus any other place names on the card.
        val shown = mutableListOf<String>()
        if (card.pickupAddress.isNotBlank()) {
            cardRoot.addView(buildZoneAddressActionRow(card.pickupAddress, "التحرك", holder, density))
            shown.add(card.pickupAddress)
        }
        if (card.dropoffAddress.isNotBlank() && card.dropoffAddress !in shown) {
            cardRoot.addView(buildZoneAddressActionRow(card.dropoffAddress, "الوصول", holder, density))
            shown.add(card.dropoffAddress)
        }
        card.addressCandidates.filter { it !in shown }.take(2).forEach {
            cardRoot.addView(buildZoneAddressActionRow(it, "مكان", holder, density))
            shown.add(it)
        }
        if (shown.isEmpty()) {
            cardRoot.addView(infoRow(
                "لم يُعثر على اسم منطقة في هذا الكارت. استخدم \"التقاط للتشخيص\" من الإشعار وأرسل الملف للمطور.",
                "#94A3B8", density, bold = false
            ))
        }

        holder.container.addView(cardRoot)
    }

    /** Plain-language reason for the verdict, e.g. "التكلفة 31 أكبر من السعر 25". */
    private fun whyText(card: ParsedRideData, a: FareAnalysisResult): String {
        fun n(v: Double) = String.format(Locale.US, "%.1f", v)
        val main = when {
            card.distanceUnknown -> "المسافة مش مقروءة من الكارت، فالربح مش محسوب"
            a.isBlacklistedPassenger -> "العميل ده في قائمتك المحظورة"
            a.isBlacklistedZone && a.profitabilityLevel == ProfitabilityLevel.LOSS_DANGER ->
                "منطقة محظورة (${a.matchedBlacklistZone}) والسعر مش عالي كفاية"
            a.netProfit < 0 -> "التكلفة ${a.totalOperatingCost.roundToInt()} ج.م (بنزين + عمولة + استهلاك) أكبر من السعر ${card.fare.roundToInt()}"
            a.profitabilityLevel == ProfitabilityLevel.POOR_MARGIN ->
                "ربحك ${n(a.profitPerKm)} ج.م/كم أقل من هدفك ${n(a.targetProfitPerKm)}"
            a.profitabilityLevel == ProfitabilityLevel.EXCELLENT_PROFIT ->
                "ربحك ${n(a.profitPerKm)} ج.م/كم، أكتر من مرة ونص هدفك ${n(a.targetProfitPerKm)}"
            else -> "ربحك ${n(a.profitPerKm)} ج.م/كم، وهدفك ${n(a.targetProfitPerKm)}"
        }
        val notes = buildList {
            if (card.pickupKnown && card.tripKnown && card.pickupDistanceKm > card.tripDistanceKm) add("الوصول أطول من المشوار")
            if (card.distanceEstimated && !card.distanceUnknown) add("طول المشوار مفترض (≈)")
            if (a.isFavoritePassenger) add("عميل مميز ★")
            else if (a.isFavoriteZone) add("منطقة مفضلة ★")
        }
        return if (notes.isEmpty()) main else main + " • " + notes.joinToString(" • ")
    }

    private fun metricBox(label: String, value: String, valueColor: String, density: Float, marginStart: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { this.marginStart = marginStart }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(Color.parseColor("#131B2E"))
                setStroke((1 * density).toInt(), Color.parseColor("#23324E"))
            }
        }
        box.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            gravity = Gravity.CENTER
            setSingleLine(true)
        })
        box.addView(TextView(this).apply {
            text = value
            setTextColor(Color.parseColor(valueColor))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
        })
        return box
    }

    private fun infoRow(text: String, colorHex: String, density: Float, bold: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (6 * density).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(Color.parseColor("#131B2E"))
            }
        }
        row.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(colorHex))
            textSize = 12.5f
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        })
        return row
    }

    private fun buildZoneAddressActionRow(
        address: String,
        prefix: String,
        holder: CardPopupViewHolder,
        density: Float
    ): View {
        val repo = InDriveAssistantApp.instance.repository
        val isFav = repo.isFavoriteZone(address)
        val isBlack = repo.isBlacklistedZone(address)
        fun dp(v: Float) = (v * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(4f), 0, dp(4f)) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(Color.parseColor("#0F1728"))
                setStroke(dp(1.5f), Color.parseColor(if (isFav) COLOR_FAVORITE else if (isBlack) COLOR_LOSS else "#223554"))
            }
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }

        val statusTag = when {
            isFav -> " (مفضلة ★)"
            isBlack -> " (محظورة)"
            else -> ""
        }
        container.addView(TextView(this).apply {
            text = "$prefix: $address$statusTag"
            setTextColor(Color.parseColor(if (isFav) COLOR_FAVORITE else if (isBlack) COLOR_LOSS else "#F8FAFC"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6f) }
        }

        btnRow.addView(actionChip(
            label = if (isFav) "إلغاء المفضلة" else "+ مفضلة",
            textColor = if (isFav) Color.WHITE else Color.parseColor("#070B14"),
            bgColor = if (isFav) "#475569" else COLOR_FAVORITE,
            density = density,
            marginStart = 0
        ) {
            if (isFav) {
                repo.removeFavoriteZone(address)
                toast("تمت إزالة '$address' من المفضلة")
            } else {
                repo.addFavoriteZone(address)
                toast("تمت إضافة '$address' للمناطق المفضلة")
            }
            refreshAnalysis(holder)
        })

        btnRow.addView(actionChip(
            label = if (isBlack) "إلغاء الحظر" else "+ محظورة",
            textColor = if (isBlack) Color.WHITE else Color.parseColor(COLOR_INK),
            bgColor = if (isBlack) "#475569" else COLOR_LOSS,
            density = density,
            marginStart = dp(8f)
        ) {
            if (isBlack) {
                repo.removeBlacklistZone(address)
                toast("تم إلغاء حظر '$address'")
            } else {
                repo.addBlacklistZone(address)
                toast("تمت إضافة '$address' للمناطق المحظورة")
            }
            refreshAnalysis(holder)
        })

        container.addView(btnRow)
        return container
    }

    /** "العميل: name" with 1-tap buttons to mark the passenger as favourite or blocked. */
    private fun buildPassengerActionRow(name: String, holder: CardPopupViewHolder, density: Float): View {
        val repo = InDriveAssistantApp.instance.repository
        val isFav = repo.isFavoritePassenger(name)
        val isBlack = repo.isBlacklistedPassenger(name)
        fun dp(v: Float) = (v * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(6f), 0, dp(4f)) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(Color.parseColor("#0F1728"))
                setStroke(dp(1.5f), Color.parseColor(if (isFav) COLOR_FAVORITE else if (isBlack) COLOR_LOSS else "#223554"))
            }
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }
        container.addView(TextView(this).apply {
            text = "العميل: $name" + when {
                isFav -> " (مميز ★)"
                isBlack -> " (محظور)"
                else -> ""
            }
            setTextColor(Color.parseColor(if (isFav) COLOR_FAVORITE else if (isBlack) COLOR_LOSS else "#F8FAFC"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6f) }
        }
        btnRow.addView(actionChip(
            label = if (isFav) "إلغاء التمييز" else "+ عميل مميز",
            textColor = if (isFav) Color.WHITE else Color.parseColor(COLOR_INK),
            bgColor = if (isFav) "#475569" else COLOR_FAVORITE,
            density = density,
            marginStart = 0
        ) {
            repo.setPassengerList(name, if (isFav) null else true)
            toast(if (isFav) "تمت إزالة '$name' من العملاء المميزين" else "تمت إضافة '$name' للعملاء المميزين")
            refreshAnalysis(holder)
        })
        btnRow.addView(actionChip(
            label = if (isBlack) "إلغاء الحظر" else "+ عميل محظور",
            textColor = if (isBlack) Color.WHITE else Color.parseColor(COLOR_INK),
            bgColor = if (isBlack) "#475569" else COLOR_LOSS,
            density = density,
            marginStart = dp(8f)
        ) {
            repo.setPassengerList(name, if (isBlack) null else false)
            toast(if (isBlack) "تم إلغاء حظر '$name'" else "تمت إضافة '$name' للعملاء المحظورين")
            refreshAnalysis(holder)
        })
        container.addView(btnRow)
        return container
    }

    private fun actionChip(
        label: String,
        textColor: Int,
        bgColor: String,
        density: Float,
        marginStart: Int,
        onClick: () -> Unit
    ): View = TextView(this).apply {
        text = label
        setTextColor(textColor)
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minHeight = (40 * density).toInt()
        setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { this.marginStart = marginStart }
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            setColor(Color.parseColor(bgColor))
        }
        setOnClickListener { onClick() }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun refreshAnalysis(holder: CardPopupViewHolder) {
        holder.analysis = analyze(holder.ride)
        renderPopupIfChanged(holder)
        safeUpdateLayout(holder)
    }

    // Drag touch listener: the offset is remembered per card.
    private fun setupDragTouchListener(view: View, holder: CardPopupViewHolder) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = holder.params.x
                    initialY = holder.params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (isDragging || Math.hypot(dx.toDouble(), dy.toDouble()) > 10) {
                        isDragging = true
                        // With RIGHT gravity, x is measured from the right edge.
                        val dirX = if ((holder.params.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) -1 else 1
                        holder.params.x = initialX + dirX * dx.toInt()
                        holder.params.y = initialY + dy.toInt()
                        holder.dragOffsetX = holder.params.x - BADGE_MARGIN_X
                        holder.dragOffsetY = holder.params.y - calculatePopupY(holder.ride)
                        safeUpdateLayout(holder)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun clearAllCardPopups() {
        val wm = windowManager ?: return
        activePopups.values.forEach { removePopupWindow(wm, it) }
        activePopups.clear()
        clearBidChips()
    }

    // =========================================================================
    // COUNTER-BID CIRCLES (Ride-request screen)
    // A small circle above each of inDrive's bid buttons with the net profit of that bid; the
    // best bid (BidAdvisor) is filled and starred. Touches pass through to inDrive.
    // =========================================================================
    private val chipViews = mutableListOf<View>()
    private var chipKey = ""

    private fun syncBidChips(cards: List<ParsedRideData>) {
        val wm = windowManager ?: return
        val card = cards.singleOrNull()?.takeIf { it.bidChips.isNotEmpty() && hudPrefs().bidChipsMode != "OFF" }
        if (card == null) {
            clearBidChips()
            return
        }
        val rated = BidAdvisor.rate(card, InDriveAssistantApp.instance.repository.getDriverSettings())
        val best = BidAdvisor.best(rated)
        val key = rated.joinToString("|") {
            "${it.chip.value}@${it.chip.bounds.toShortString()}:${it.analysis.netProfit.roundToInt()}:${it.analysis.profitabilityLevel}"
        } + "#" + best?.chip?.value
        if (key == chipKey) return
        clearBidChips()
        chipKey = key

        val density = resources.displayMetrics.density
        val size = (46 * density).toInt()
        for (rb in rated) {
            val view = buildBidCircle(rb, rb === best, density)
            val params = baseLayoutParams().apply {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                width = size
                height = size
                gravity = Gravity.TOP or Gravity.LEFT
                x = rb.chip.bounds.centerX() - size / 2
                val above = rb.chip.bounds.top - size - (2 * density).toInt()
                y = if (above > 30) above else rb.chip.bounds.bottom + (2 * density).toInt()
            }
            try {
                wm.addView(view, params)
                chipViews.add(view)
            } catch (e: Exception) {
                Log.w(TAG, "Could not show bid circle", e)
            }
        }
    }

    private fun clearBidChips() {
        val wm = windowManager
        chipViews.forEach { v -> try { wm?.removeView(v) } catch (_: Exception) {} }
        chipViews.clear()
        chipKey = ""
    }

    private fun levelColor(level: ProfitabilityLevel): String = when (level) {
        ProfitabilityLevel.EXCELLENT_PROFIT -> COLOR_EXCELLENT
        ProfitabilityLevel.GOOD_PROFIT -> COLOR_GOOD
        ProfitabilityLevel.POOR_MARGIN -> COLOR_POOR
        ProfitabilityLevel.LOSS_DANGER -> COLOR_LOSS
    }

    private fun buildBidCircle(rb: BidAdvisor.RatedBid, isBest: Boolean, density: Float): View {
        val color = levelColor(rb.analysis.profitabilityLevel)
        val frame = FrameLayout(this)
        val d = (40 * density).toInt()
        frame.addView(TextView(this).apply {
            text = signedInt(rb.analysis.netProfit)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (isBest) Color.parseColor(COLOR_INK) else Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(d, d).apply { gravity = Gravity.CENTER }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isBest) Color.parseColor(color) else Color.parseColor("#0B0F19"))
                setStroke(((if (isBest) 3f else 2f) * density).toInt(), Color.parseColor(if (isBest) COLOR_INK else color))
            }
            elevation = 6 * density
            contentDescription = "مزايدة ${rb.chip.value}: صافي ${rb.analysis.netProfit.roundToInt()}" + if (isBest) "، أفضل عرض" else ""
        })
        if (isBest) frame.addView(cornerChip("★", COLOR_FAVORITE, Gravity.TOP or Gravity.END, density))
        return frame
    }

    // =========================================================================
    // NEW-REQUEST ALERT: a vibration per verdict, and (optionally) the verdict spoken aloud,
    // so the driver doesn't have to look at the screen.
    // =========================================================================
    private fun alertNewRequest(card: ParsedRideData, analysis: FareAnalysisResult) {
        val hud = hudPrefs()
        if (!hud.vibrate && !hud.speakVerdict) return
        if (hud.vibrateProfitableOnly) {
            val profitable = !card.distanceEstimated &&
                (analysis.profitabilityLevel == ProfitabilityLevel.GOOD_PROFIT ||
                    analysis.profitabilityLevel == ProfitabilityLevel.EXCELLENT_PROFIT)
            if (!profitable) return
        }
        val now = System.currentTimeMillis()
        if (card.fingerprint == lastVibratedFingerprint && (now - lastVibrateTimestamp) < 15000L) return
        if (now - lastVibrateTimestamp < 3500L) return

        lastVibratedFingerprint = card.fingerprint
        lastVibrateTimestamp = now

        if (hud.vibrate) vibrateFor(card, analysis)
        if (hud.speakVerdict) speak(verdictSpeech(card, analysis))
    }

    /** One short buzz = profitable, two = thin margin, one long = loss, a tap = distance not read. */
    private fun vibrateFor(card: ParsedRideData, analysis: FareAnalysisResult) {
        val pattern = when {
            card.distanceUnknown -> longArrayOf(0, 35)
            analysis.profitabilityLevel == ProfitabilityLevel.LOSS_DANGER -> longArrayOf(0, 450)
            analysis.profitabilityLevel == ProfitabilityLevel.POOR_MARGIN -> longArrayOf(0, 60, 120, 60)
            else -> longArrayOf(0, 70)
        }
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    /** Short Arabic phrase for the verdict, e.g. "رابح، صافي 42". */
    private fun verdictSpeech(card: ParsedRideData, a: FareAnalysisResult): String {
        val net = a.netProfit.roundToInt()
        val approx = if (card.distanceEstimated) "تقريباً " else ""
        return when {
            card.distanceUnknown -> "طلب جديد، المسافة مش مقروءة"
            a.isBlacklistedPassenger -> "عميل محظور"
            a.profitabilityLevel == ProfitabilityLevel.LOSS_DANGER && a.isBlacklistedZone -> "منطقة محظورة"
            a.profitabilityLevel == ProfitabilityLevel.LOSS_DANGER -> "خسارة ${approx}${abs(net)}"
            a.profitabilityLevel == ProfitabilityLevel.POOR_MARGIN -> "هامش ضعيف، صافي ${approx}$net"
            a.profitabilityLevel == ProfitabilityLevel.EXCELLENT_PROFIT -> "ممتاز، صافي ${approx}$net"
            else -> "رابح، صافي ${approx}$net"
        }
    }

    // Text-to-speech, created on first use (main thread).
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            pendingSpeech = text
            tts = android.speech.tts.TextToSpeech(applicationContext) { status ->
                ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale("ar")
                    pendingSpeech?.let { tts?.speak(it, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "verdict") }
                } else {
                    Log.w(TAG, "Text-to-speech unavailable (status $status)")
                }
                pendingSpeech = null
            }
            return
        }
        if (ttsReady) engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "verdict")
    }

    override fun onDestroy() {
        if (activeInstance == this) activeInstance = null
        clearAllCardPopups()
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingHud"
        private const val NOTIFICATION_ID = 1001
        private const val BADGE_MARGIN_X = 20
        private const val EXPANDED_HEIGHT_ESTIMATE_DP = 300
        const val ACTION_STOP = "com.indrive.assistant.action.STOP_HUD"
        const val ACTION_CAPTURE = "com.indrive.assistant.action.CAPTURE_SCREEN"
        const val ACTION_PAUSE_READING = "com.indrive.assistant.action.PAUSE_SCREEN_READING"
        const val ACTION_TOGGLE_AUTOMATION = "com.indrive.assistant.action.TOGGLE_AUTOMATION"

        // Money colours only mean money; "excellent" is the strongest (solid fill), favourites use
        // the brand indigo so they can't be confused with the amber "thin margin".
        private const val COLOR_EXCELLENT = "#22C55E"
        private const val COLOR_GOOD = "#4ADE80"
        private const val COLOR_POOR = "#F59E0B"
        private const val COLOR_LOSS = "#F87171"
        private const val COLOR_UNKNOWN = "#94A3B8"
        private const val COLOR_FAVORITE = "#7C8CFF"
        private const val COLOR_INK = "#0B0F17"

        @Volatile
        private var activeInstance: FloatingHudService? = null

        /** May be called from any thread; work is posted to the main thread. */
        fun onCardsSync(cards: List<ParsedRideData>) {
            activeInstance?.handleCardsSync(cards)
        }

        fun isRunning(): Boolean = activeInstance != null

        /** Rebuilds the notification so its screen-reading action matches the service state. */
        fun onScreenReadingChanged() {
            val service = activeInstance ?: return
            service.serviceScope.launch {
                try {
                    val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, service.createNotification())
                } catch (e: Exception) {
                    Log.w(TAG, "Notification refresh failed", e)
                }
            }
        }
    }
}
