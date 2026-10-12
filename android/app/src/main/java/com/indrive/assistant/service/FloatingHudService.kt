package com.indrive.assistant.service

import android.animation.ValueAnimator
import android.app.Notification
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
import com.indrive.assistant.data.model.ParsedRideData
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
        var renderKey: String = ""
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

        return NotificationCompat.Builder(this, InDriveAssistantApp.CHANNEL_ID)
            .setContentTitle("The Driver")
            .setContentText("المساعد العائم يعمل ويحلل عروض إن درايف")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopIntent)
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

            val currentFingerprints = cards.map { it.fingerprint }.toSet()

            // 1. Remove popups for cards that disappeared from screen
            activePopups.keys.filter { it !in currentFingerprints }.forEach { fp ->
                activePopups.remove(fp)?.let { removePopupWindow(wm, it) }
            }

            // 2. Add or update popups for each currently visible card
            for (card in cards) {
                val analysis = analyze(card)
                val existing = activePopups[card.fingerprint]
                if (existing != null) {
                    existing.ride = card
                    existing.analysis = analysis
                    if (!existing.isExpanded) {
                        val targetX = BADGE_MARGIN_X + existing.dragOffsetX
                        val targetY = calculatePopupY(card) + existing.dragOffsetY
                        if (existing.params.x != targetX || existing.params.y != targetY) {
                            existing.params.x = targetX
                            existing.params.y = targetY
                            safeUpdateLayout(existing)
                        }
                    }
                    renderPopupIfChanged(existing)
                } else {
                    createAndAttachPopup(card, analysis)
                    vibrateSingleShotOnce(card)
                }
            }
        }
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
            dropoffAddress = card.dropoffAddress
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
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun createAndAttachPopup(card: ParsedRideData, analysis: FareAnalysisResult) {
        val wm = windowManager ?: return

        val params = baseLayoutParams().apply {
            x = BADGE_MARGIN_X
            y = calculatePopupY(card)
        }

        val container = FrameLayout(this)
        val holder = CardPopupViewHolder(ride = card, analysis = analysis, container = container, params = params)

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
        holder.isExpanded = false
        holder.params.width = WindowManager.LayoutParams.WRAP_CONTENT
        holder.params.x = BADGE_MARGIN_X + holder.dragOffsetX
        holder.params.y = calculatePopupY(holder.ride) + holder.dragOffsetY
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
        holder.params.x = (screenWidth - cardWidth) / 2
        holder.params.y = calculatePopupY(holder.ride)
            .coerceIn((50 * density).toInt(), (screenHeight - (430 * density).toInt()).coerceAtLeast((50 * density).toInt()))
        renderPopupIfChanged(holder)
        safeUpdateLayout(holder)
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    private data class BadgeTheme(val colorHex: String, val label: String)

    /** Profitability decides the colour; zones only add a marker and a label. */
    private fun themeFor(ride: ParsedRideData, analysis: FareAnalysisResult): BadgeTheme {
        if (ride.distanceEstimated) return BadgeTheme(COLOR_UNKNOWN, "المسافة غير مؤكدة - راجع الطلب")
        val zoneSuffix = when {
            analysis.matchedBlacklistZone != null -> " • منطقة محظورة (${analysis.matchedBlacklistZone})"
            analysis.matchedFavoriteZone != null -> " • ★ منطقة مفضلة (${analysis.matchedFavoriteZone})"
            else -> ""
        }
        return when (analysis.profitabilityLevel) {
            ProfitabilityLevel.EXCELLENT_PROFIT -> BadgeTheme(COLOR_EXCELLENT, "ربح ممتاز$zoneSuffix")
            ProfitabilityLevel.GOOD_PROFIT -> BadgeTheme(COLOR_GOOD, "عرض رابح$zoneSuffix")
            ProfitabilityLevel.POOR_MARGIN -> BadgeTheme(COLOR_POOR, "هامش ضعيف - اقترح زيادة$zoneSuffix")
            ProfitabilityLevel.LOSS_DANGER -> BadgeTheme(COLOR_LOSS, "${analysis.statusMessage}$zoneSuffix")
        }
    }

    private fun renderPopupIfChanged(holder: CardPopupViewHolder) {
        val prefs = InDriveAssistantApp.instance.repository.getPreferencesManager()
        val a = holder.analysis
        val r = holder.ride
        val key = listOf(
            holder.isExpanded, r.distanceEstimated, r.fare.toInt(), a.netProfit.roundToInt(),
            a.recommendedFare.toInt(), a.profitabilityLevel, a.matchedFavoriteZone, a.matchedBlacklistZone,
            r.pickupAddress, r.dropoffAddress, String.format(Locale.US, "%.1f/%.1f", r.pickupDistanceKm, r.tripDistanceKm),
            prefs.overlayStyleFlow.value, prefs.overlaySizeFlow.value,
            prefs.isFavoriteZone(r.pickupAddress), prefs.isBlacklistedZone(r.pickupAddress),
            prefs.isFavoriteZone(r.dropoffAddress), prefs.isBlacklistedZone(r.dropoffAddress)
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
            renderExpandedDetailsCard(holder, theme)
        } else {
            renderCompactBadge(holder, theme)
        }
    }

    /** Compact badge text: the ride's net profit, or "?" when the distance is a guess. */
    private fun badgeValue(holder: CardPopupViewHolder): String {
        if (holder.ride.distanceEstimated) return "؟"
        val p = holder.analysis.netProfit.roundToInt()
        return if (p >= 0) "+$p" else "−${abs(p)}"
    }

    private fun renderCompactBadge(holder: CardPopupViewHolder, theme: BadgeTheme) {
        val density = resources.displayMetrics.density
        val prefs = InDriveAssistantApp.instance.repository.getPreferencesManager()
        val style = prefs.overlayStyleFlow.value.uppercase()
        val sizeScale = when (prefs.overlaySizeFlow.value.uppercase()) {
            "SMALL" -> 0.85f
            "LARGE" -> 1.22f
            else -> 1.0f
        }

        val value = badgeValue(holder)
        val isFav = holder.analysis.isFavoriteZone && !holder.ride.distanceEstimated

        val badgeView = when (style) {
            "SLIM_PILL" -> buildSlimPill(value, theme.colorHex, sizeScale, density)
            "SPEEDO_GAUGE" -> buildSpeedoGauge(value, theme.colorHex, sizeScale, density)
            "MINI_TAG" -> buildMiniTag(value, theme.colorHex, sizeScale, density)
            "CORNER_RIBBON" -> buildCornerRibbon(value, theme.colorHex, sizeScale, density)
            "GLOW_DOT" -> buildGlowDot(value, theme.colorHex, sizeScale, density)
            else -> buildCompactCircle(value, theme.colorHex, sizeScale, density)
        }
        badgeView.contentDescription = "${theme.label}: صافي الربح $value جنيه"

        badgeView.setOnClickListener { togglePopupExpanded(holder) }
        setupDragTouchListener(badgeView, holder)

        if (isFav) startPulsatingGlowAnimation(badgeView, holder, COLOR_FAVORITE)

        holder.container.addView(badgeView)

        // Zone marker: gold star for favourites, red bar for blacklisted zones.
        val marker = when {
            holder.analysis.isBlacklistedZone -> "⛔" to "#FF3355"
            isFav -> "★" to COLOR_FAVORITE
            else -> null
        }
        if (marker != null) {
            holder.container.addView(TextView(this).apply {
                text = marker.first
                setTextColor(Color.parseColor("#070B14"))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val d = (16 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(d, d).apply { gravity = Gravity.TOP or Gravity.START }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(marker.second))
                    setStroke((1 * density).toInt(), Color.parseColor("#070B14"))
                }
            })
        }
    }

    // Style 1: COMPACT_CIRCLE
    private fun buildCompactCircle(value: String, colorHex: String, scale: Float, density: Float): View {
        val diameter = (52 * scale * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(diameter, diameter)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0B0F19"))
                setStroke((2.5f * density).toInt(), Color.parseColor(colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            textSize = 13f * scale
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
            includeFontPadding = false
        })
        return layout
    }

    // Style 2: SLIM_PILL
    private fun buildSlimPill(value: String, colorHex: String, scale: Float, density: Float): View {
        val h = (30 * scale * density).toInt()
        val padH = (10 * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, 0, padH, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(Color.parseColor("#0B0F19"))
                setStroke((2.2f * density).toInt(), Color.parseColor(colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(View(this).apply {
            val dotSize = (8 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { marginEnd = (6 * density).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorHex))
            }
        })
        layout.addView(TextView(this).apply {
            text = "$value ج"
            setTextColor(Color.WHITE)
            textSize = 12.5f * scale
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
        })
        return layout
    }

    // Style 3: SPEEDO_GAUGE
    private fun buildSpeedoGauge(value: String, colorHex: String, scale: Float, density: Float): View {
        val w = (58 * scale * density).toInt()
        val h = (46 * scale * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(w, h)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(Color.parseColor("#0F172A"))
                setStroke((2.5f * density).toInt(), Color.parseColor(colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (3 * density).toInt())
            background = GradientDrawable().apply { setColor(Color.parseColor(colorHex)) }
        })
        layout.addView(TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            textSize = 13f * scale
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(0, (2 * density).toInt(), 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = "صافي"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 9f * scale
            gravity = Gravity.CENTER
            setSingleLine(true)
        })
        return layout
    }

    // Style 4: MINI_TAG
    private fun buildMiniTag(value: String, colorHex: String, scale: Float, density: Float): View {
        val h = (30 * scale * density).toInt()
        val padH = (9 * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, 0, padH, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * density
                setColor(Color.parseColor(colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(TextView(this).apply {
            text = "$value ج"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 13f * scale
            typeface = Typeface.DEFAULT_BOLD
        })
        return layout
    }

    // Style 5: CORNER_RIBBON
    private fun buildCornerRibbon(value: String, colorHex: String, scale: Float, density: Float): View {
        val h = (28 * scale * density).toInt()
        val padH = (10 * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, 0, padH, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor(Color.parseColor("#0B132B"))
                setStroke((1.8f * density).toInt(), Color.parseColor(colorHex))
            }
            elevation = 6 * density
        }
        layout.addView(TextView(this).apply {
            text = "صافي $value"
            setTextColor(Color.parseColor(colorHex))
            textSize = 12f * scale
            typeface = Typeface.DEFAULT_BOLD
        })
        return layout
    }

    // Style 6: GLOW_DOT
    private fun buildGlowDot(value: String, colorHex: String, scale: Float, density: Float): View {
        val diameter = (48 * scale * density).toInt()
        val layout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(diameter, diameter)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0D1B2A"))
                setStroke((3.5f * density).toInt(), Color.parseColor(colorHex))
            }
            elevation = 8 * density
        }
        layout.addView(TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            text = value
            setTextColor(Color.WHITE)
            textSize = 13.5f * scale
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
            setTextColor(if (theme.colorHex == COLOR_POOR || theme.colorHex == COLOR_GOOD) Color.parseColor("#070B14") else Color.WHITE)
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
            "صافي الربح: $netProfitVal ج.م  ($profitPerKmVal ج/كم)",
            if (analysis.netProfit >= 0) "#38BDF8" else "#EF4444",
            density,
            bold = true
        )

        // ROW 4: Distances
        val distText = if (card.distanceEstimated) {
            "المسافة: لم تُقرأ بدقة (الأرقام تقديرية) - راجع الطلب في إن درايف"
        } else {
            String.format(
                Locale.US, "المسافة: %.1f كم  (%.1f وصول + %.1f مشوار)",
                card.totalDistanceKm, card.pickupDistanceKm, card.tripDistanceKm
            )
        }
        val distRow = infoRow(distText, if (card.distanceEstimated) "#FBBF24" else "#F8FAFC", density, bold = false)

        cardRoot.addView(topRow)
        cardRoot.addView(fareRow)
        cardRoot.addView(profitRow)
        cardRoot.addView(distRow)

        if (card.pickupAddress.isNotBlank()) {
            cardRoot.addView(buildZoneAddressActionRow(card.pickupAddress, "التحرك", holder, density))
        }
        if (card.dropoffAddress.isNotBlank()) {
            cardRoot.addView(buildZoneAddressActionRow(card.dropoffAddress, "الوصول", holder, density))
        }

        holder.container.addView(cardRoot)
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
                setStroke(dp(1.5f), Color.parseColor(if (isFav) COLOR_FAVORITE else if (isBlack) "#FF3355" else "#223554"))
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
            setTextColor(Color.parseColor(if (isFav) COLOR_FAVORITE else if (isBlack) "#FF3355" else "#F8FAFC"))
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
            textColor = Color.WHITE,
            bgColor = if (isBlack) "#475569" else "#FF3355",
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

    // =========================================================================
    // PULSATING GLOW (favourite zones)
    // =========================================================================
    private fun startPulsatingGlowAnimation(view: View, holder: CardPopupViewHolder, colorHex: String) {
        val baseColor = Color.parseColor(colorHex)
        val strokeWidth = (3.5f * resources.displayMetrics.density).toInt()
        val animator = ValueAnimator.ofFloat(0.35f, 1.0f).apply {
            duration = 850L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { va ->
                val alpha = ((va.animatedValue as Float) * 255).toInt()
                (view.background as? GradientDrawable)?.setStroke(
                    strokeWidth,
                    Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                )
            }
        }
        animator.start()
        holder.pulseAnimator = animator
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
                        holder.params.x = initialX + dx.toInt()
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
    }

    // =========================================================================
    // SINGLE-SHOT HAPTIC ALERT
    // =========================================================================
    private fun vibrateSingleShotOnce(card: ParsedRideData) {
        val now = System.currentTimeMillis()
        if (card.fingerprint == lastVibratedFingerprint && (now - lastVibrateTimestamp) < 15000L) return
        if (now - lastVibrateTimestamp < 3500L) return

        lastVibratedFingerprint = card.fingerprint
        lastVibrateTimestamp = now

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(70)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    override fun onDestroy() {
        if (activeInstance == this) activeInstance = null
        clearAllCardPopups()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingHud"
        private const val NOTIFICATION_ID = 1001
        private const val BADGE_MARGIN_X = 20
        const val ACTION_STOP = "com.indrive.assistant.action.STOP_HUD"

        private const val COLOR_EXCELLENT = "#10B981"
        private const val COLOR_GOOD = "#00EA80"
        private const val COLOR_POOR = "#FACC15"
        private const val COLOR_LOSS = "#EF4444"
        private const val COLOR_UNKNOWN = "#64748B"
        private const val COLOR_FAVORITE = "#FFB800"

        @Volatile
        private var activeInstance: FloatingHudService? = null

        /** May be called from any thread; work is posted to the main thread. */
        fun onCardsSync(cards: List<ParsedRideData>) {
            activeInstance?.handleCardsSync(cards)
        }

        fun isRunning(): Boolean = activeInstance != null
    }
}
