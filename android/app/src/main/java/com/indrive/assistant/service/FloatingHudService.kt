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
import kotlinx.coroutines.launch

class FloatingHudService : Service() {

    private var windowManager: WindowManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    // Active Card Popups mapped by ride fingerprint
    private val activePopups = mutableMapOf<String, CardPopupViewHolder>()

    // Anti-Vibration Spam tracking
    private var isVibrationMuted = false
    private var lastVibratedFingerprint = ""
    private var lastVibrateTimestamp = 0L

    private data class CardPopupViewHolder(
        val ride: ParsedRideData,
        var analysis: FareAnalysisResult,
        val container: FrameLayout,
        val params: WindowManager.LayoutParams,
        var isExpanded: Boolean = false,
        var pulseAnimator: ValueAnimator? = null
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, InDriveAssistantApp.CHANNEL_ID)
            .setContentTitle("The Driver")
            .setContentText("المحلل التلقائي للتسعير العادل والمناطق المفضلة يعمل بنشاط")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // =========================================================================
    // MULTI-POPUP HINTS SYNC LOGIC
    // =========================================================================
    private fun handleCardsSync(cards: List<ParsedRideData>) {
        serviceScope.launch(Dispatchers.Main) {
            val wm = windowManager ?: return@launch

            // If 0 cards visible on screen, remove all popups immediately
            if (cards.isEmpty()) {
                clearAllCardPopups()
                return@launch
            }

            val currentFingerprints = cards.map { it.fingerprint }.toSet()

            // 1. Remove popups for cards that disappeared from screen
            val toRemove = activePopups.keys.filter { it !in currentFingerprints }
            for (fp in toRemove) {
                activePopups[fp]?.let { holder ->
                    holder.pulseAnimator?.cancel()
                    try {
                        wm.removeView(holder.container)
                    } catch (_: Exception) {}
                }
                activePopups.remove(fp)
            }

            val settings = InDriveAssistantApp.instance.repository.getDriverSettings()

            // 2. Add or update popups for each currently visible card
            for (card in cards) {
                val analysis = FareCalculatorEngine.analyze(
                    pickupDistanceKm = card.pickupDistanceKm,
                    tripDistanceKm = card.tripDistanceKm,
                    estimatedMinutes = ((card.totalDistanceKm) * 2.3).toInt(),
                    offeredFare = card.fare,
                    settings = settings,
                    pickupAddress = card.pickupAddress,
                    dropoffAddress = card.dropoffAddress
                )

                val existing = activePopups[card.fingerprint]
                if (existing != null) {
                    existing.analysis = analysis
                    val targetY = calculatePopupY(card)
                    if (existing.params.y != targetY && !existing.isExpanded) {
                        existing.params.y = targetY
                        try {
                            wm.updateViewLayout(existing.container, existing.params)
                        } catch (_: Exception) {}
                    }
                    renderPopup(existing)
                } else {
                    createAndAttachPopup(card, analysis)
                    vibrateSingleShotOnce(card)
                }
            }
        }
    }

    private fun calculatePopupY(card: ParsedRideData): Int {
        val screenBounds = card.screenBounds
        return if (screenBounds != null) {
            (screenBounds.top + 6).coerceAtLeast(30)
        } else {
            320
        }
    }

    private fun createAndAttachPopup(card: ParsedRideData, analysis: FareAnalysisResult) {
        val wm = windowManager ?: return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val targetY = calculatePopupY(card)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = targetY
        }

        val container = FrameLayout(this)
        val holder = CardPopupViewHolder(
            ride = card,
            analysis = analysis,
            container = container,
            params = params,
            isExpanded = false
        )

        renderPopup(holder)

        try {
            wm.addView(container, params)
            activePopups[card.fingerprint] = holder
        } catch (_: Exception) {}
    }

    fun collapseAllPopups() {
        val wm = windowManager ?: return
        for (holder in activePopups.values) {
            if (holder.isExpanded) {
                holder.isExpanded = false
                holder.params.width = WindowManager.LayoutParams.WRAP_CONTENT
                holder.params.height = WindowManager.LayoutParams.WRAP_CONTENT
                holder.params.x = 20
                holder.params.y = calculatePopupY(holder.ride)
                holder.params.gravity = Gravity.TOP or Gravity.START
                renderPopup(holder)
                try {
                    wm.updateViewLayout(holder.container, holder.params)
                } catch (_: Exception) {}
            }
        }
    }

    fun togglePopupExpanded(holder: CardPopupViewHolder) {
        val wm = windowManager ?: return
        if (holder.isExpanded) {
            collapseAllPopups()
        } else {
            // Collapse any other open popups first so only ONE is open!
            collapseAllPopups()

            // Expand this one and cover screen to intercept outside clicks
            holder.isExpanded = true
            holder.params.width = WindowManager.LayoutParams.MATCH_PARENT
            holder.params.height = WindowManager.LayoutParams.MATCH_PARENT
            holder.params.x = 0
            holder.params.y = 0
            holder.params.gravity = Gravity.TOP or Gravity.START
            renderPopup(holder)
            try {
                wm.updateViewLayout(holder.container, holder.params)
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // DYNAMIC POPUP RENDERING (COMPACT VS EXPANDED)
    // =========================================================================
    private fun renderPopup(holder: CardPopupViewHolder) {
        val container = holder.container
        container.removeAllViews()
        holder.pulseAnimator?.cancel()
        holder.pulseAnimator = null

        val analysis = holder.analysis
        val card = holder.ride

        // Determine Theme Color and Label based on Profitability & Zones
        val (themeColorHex, tagLabel) = when {
            analysis.isBlacklistedZone && analysis.profitabilityLevel != ProfitabilityLevel.GOOD_PROFIT -> {
                Pair("#DC2626", "منطقة سوداء (${analysis.matchedBlacklistZone ?: ""})")
            }
            analysis.isBlacklistedZone && analysis.profitabilityLevel == ProfitabilityLevel.GOOD_PROFIT -> {
                Pair("#D97706", "مقبول فقط لارتفاع السعر (${analysis.matchedBlacklistZone ?: ""})")
            }
            analysis.isFavoriteZone -> {
                Pair("#F59E0B", "منطقة مفضلة (${analysis.matchedFavoriteZone ?: ""})")
            }
            analysis.profitabilityLevel == ProfitabilityLevel.EXCELLENT_PROFIT -> {
                Pair("#10B981", "قبول فوري ومجزٍ")
            }
            analysis.profitabilityLevel == ProfitabilityLevel.GOOD_PROFIT -> {
                Pair("#00EA80", "عرض رابح ومجدٍ")
            }
            analysis.profitabilityLevel == ProfitabilityLevel.POOR_MARGIN -> {
                Pair("#EAB308", "هامش ضعيف - اقترح زيادة")
            }
            else -> {
                Pair("#EF4444", "غير مقبول - مخاطرة خسارة")
            }
        }

        if (holder.isExpanded) {
            renderExpandedDetailsCard(holder, themeColorHex, tagLabel)
        } else {
            renderCompactBadge(holder, themeColorHex)
        }
    }

    // =========================================================================
    // 1. ULTRA-COMPACT BADGE BUILDER (6 Distinct OEM Styles)
    // =========================================================================
    private fun renderCompactBadge(holder: CardPopupViewHolder, themeColorHex: String) {
        val density = resources.displayMetrics.density
        val prefs = InDriveAssistantApp.instance.repository.getPreferencesManager()
        val style = prefs.overlayStyleFlow.value.uppercase()
        val sizePref = prefs.overlaySizeFlow.value.uppercase()

        val sizeScale = when (sizePref) {
            "SMALL" -> 0.85f
            "LARGE" -> 1.22f
            else -> 1.0f
        }

        val fairPrice = holder.analysis.recommendedFare.toInt()
        val isFav = holder.analysis.isFavoriteZone

        val badgeView = when (style) {
            "SLIM_PILL" -> buildSlimPill(fairPrice, themeColorHex, sizeScale, density)
            "SPEEDO_GAUGE" -> buildSpeedoGauge(fairPrice, themeColorHex, sizeScale, density)
            "MINI_TAG" -> buildMiniTag(fairPrice, themeColorHex, sizeScale, density)
            "CORNER_RIBBON" -> buildCornerRibbon(fairPrice, themeColorHex, sizeScale, density)
            "GLOW_DOT" -> buildGlowDot(fairPrice, themeColorHex, sizeScale, density)
            else -> buildCompactCircle(fairPrice, themeColorHex, sizeScale, density)
        }

        // Tap to toggle expanded details popover
        badgeView.setOnClickListener {
            togglePopupExpanded(holder)
        }

        // Dragging gesture support for fine-tuning position
        setupDragTouchListener(badgeView, holder)

        // Pulsating Glow Animation for Favorite Zones (Solves gold vs yellow confusion)
        if (isFav) {
            startPulsatingGlowAnimation(badgeView, holder, themeColorHex)
        }

        holder.container.addView(badgeView)

        // Subtle (!) micro-badge to alert driver that zone management is available on tap
        if (holder.ride.pickupAddress.isNotBlank() || holder.ride.dropoffAddress.isNotBlank()) {
            val alertBadge = TextView(this).apply {
                text = "!"
                setTextColor(Color.parseColor("#070B14"))
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val badgeDiameter = (14 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(badgeDiameter, badgeDiameter).apply {
                    gravity = Gravity.TOP or Gravity.START
                    setMargins(0, 0, 0, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isFav) Color.parseColor("#FFB800") else Color.parseColor("#10B981"))
                    setStroke((1 * density).toInt(), Color.parseColor("#070B14"))
                }
            }
            holder.container.addView(alertBadge)
        }
    }

    // Style 1: COMPACT_CIRCLE (قرص دائري مصغر)
    private fun buildCompactCircle(price: Int, colorHex: String, scale: Float, density: Float): View {
        val diameter = (50 * scale * density).toInt()
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

        val priceTv = TextView(this).apply {
            text = "$price ج"
            setTextColor(Color.WHITE)
            textSize = 12f * scale
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
            includeFontPadding = false
        }

        layout.addView(priceTv)
        return layout
    }

    // Style 2: SLIM_PILL (كبسولة أفقية رفيعة)
    private fun buildSlimPill(price: Int, colorHex: String, scale: Float, density: Float): View {
        val h = (28 * scale * density).toInt()
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

        val dot = View(this).apply {
            val dotSize = (8 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                setMargins(0, 0, (6 * density).toInt(), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorHex))
            }
        }

        val textTv = TextView(this).apply {
            text = "$price ج.م"
            setTextColor(Color.WHITE)
            textSize = 11.5f * scale
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
        }

        layout.addView(dot)
        layout.addView(textTv)
        return layout
    }

    // Style 3: SPEEDO_GAUGE (عداد سرعة رقمي)
    private fun buildSpeedoGauge(price: Int, colorHex: String, scale: Float, density: Float): View {
        val w = (54 * scale * density).toInt()
        val h = (44 * scale * density).toInt()
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

        val topBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (3 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
            }
        }

        val priceTv = TextView(this).apply {
            text = "$price ج"
            setTextColor(Color.WHITE)
            textSize = 12f * scale
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(0, (2 * density).toInt(), 0, 0)
        }

        val subTv = TextView(this).apply {
            text = "عادل"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 8.5f * scale
            gravity = Gravity.CENTER
            setSingleLine(true)
        }

        layout.addView(topBar)
        layout.addView(priceTv)
        layout.addView(subTv)
        return layout
    }

    // Style 4: MINI_TAG (شارة تسعير مدمجة)
    private fun buildMiniTag(price: Int, colorHex: String, scale: Float, density: Float): View {
        val h = (28 * scale * density).toInt()
        val padH = (8 * density).toInt()
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

        val textTv = TextView(this).apply {
            text = "$price ج"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 12f * scale
            typeface = Typeface.DEFAULT_BOLD
        }

        layout.addView(textTv)
        return layout
    }

    // Style 5: CORNER_RIBBON (شريط زاوية أنيق)
    private fun buildCornerRibbon(price: Int, colorHex: String, scale: Float, density: Float): View {
        val h = (26 * scale * density).toInt()
        val padH = (9 * density).toInt()
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

        val textTv = TextView(this).apply {
            text = "تسعير $price"
            setTextColor(Color.parseColor(colorHex))
            textSize = 11f * scale
            typeface = Typeface.DEFAULT_BOLD
        }

        layout.addView(textTv)
        return layout
    }

    // Style 6: GLOW_DOT (نقطة إشعاع ذكية)
    private fun buildGlowDot(price: Int, colorHex: String, scale: Float, density: Float): View {
        val diameter = (46 * scale * density).toInt()
        val layout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(diameter, diameter)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0D1B2A"))
                setStroke((3.5f * density).toInt(), Color.parseColor(colorHex))
            }
            elevation = 8 * density
        }

        val priceTv = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            text = "$price"
            setTextColor(Color.WHITE)
            textSize = 14f * scale
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        layout.addView(priceTv)
        return layout
    }

    // =========================================================================
    // 2. EXPANDED DETAILS POPOVER WITH 1-CLICK ZONE MANAGEMENT
    // =========================================================================
    private fun renderExpandedDetailsCard(
        holder: CardPopupViewHolder,
        themeColorHex: String,
        tagLabel: String
    ) {
        val density = resources.displayMetrics.density
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val card = holder.ride
        val analysis = holder.analysis

        // 1. Full-screen transparent backdrop that captures clicks outside the card to dismiss!
        val backdrop = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#33000000")) // Gentle dark scrim
            setOnClickListener {
                collapseAllPopups()
            }
        }

        // 2. The Card itself
        val cardWidth = (screenWidth * 0.92f).toInt().coerceAtMost((380 * density).toInt()).coerceAtLeast((310 * density).toInt())
        val targetY = (calculatePopupY(card)).coerceIn((50 * density).toInt(), screenHeight - (430 * density).toInt())

        val cardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            layoutParams = FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = targetY
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.parseColor("#0B0F19")) // Deep Obsidian
                setStroke((2f * density).toInt(), Color.parseColor(themeColorHex))
            }
            elevation = 16 * density
            // Prevent clicks inside card from bubbling up to backdrop
            setOnClickListener { /* Consume click */ }
        }

        // ROW 1: Status Badge & Close Button
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tagTv = TextView(this).apply {
            text = tagLabel
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(Color.parseColor(themeColorHex))
            }
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(this).apply {
            text = "✕ تصغير"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                collapseAllPopups()
            }
        }

        topRow.addView(tagTv)
        topRow.addView(closeBtn)

        // ROW 2: Side-by-side Fare Comparison Grid
        val fareRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (10 * density).toInt(), 0, 0)
            }
        }

        val fairPriceVal = analysis.recommendedFare.toInt()
        val offeredVal = card.fare.toInt()

        // Box 1: السعر العادل
        val fairBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(Color.parseColor("#131B2E"))
                setStroke((1 * density).toInt(), Color.parseColor("#23324E"))
            }
        }
        val fairLabel = TextView(this).apply {
            text = "السعر العادل"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10f
            gravity = Gravity.CENTER
            setSingleLine(true)
        }
        val fairValue = TextView(this).apply {
            text = "$fairPriceVal ج.م"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
        }
        fairBox.addView(fairLabel)
        fairBox.addView(fairValue)

        // Box 2: عرض الراكب
        val offerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(Color.parseColor("#131B2E"))
                setStroke((1 * density).toInt(), Color.parseColor("#23324E"))
            }
        }
        val offerLabel = TextView(this).apply {
            text = "عرض الراكب"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10f
            gravity = Gravity.CENTER
            setSingleLine(true)
        }
        val offerValue = TextView(this).apply {
            text = "$offeredVal ج.م"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
        }
        offerBox.addView(offerLabel)
        offerBox.addView(offerValue)

        fareRow.addView(fairBox)
        fareRow.addView(offerBox)

        // ROW 3: Net Profit Metric Box
        val netProfitVal = String.format(java.util.Locale.US, "%.1f", analysis.netProfit)
        val profitPerKmVal = String.format(java.util.Locale.US, "%.1f", analysis.profitPerKm)
        val profitContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (6 * density).toInt(), 0, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(Color.parseColor("#131B2E"))
            }
        }
        val profitTv = TextView(this).apply {
            text = "صافي الربح: $netProfitVal ج.م  ($profitPerKmVal ج/كم)"
            setTextColor(Color.parseColor(if (analysis.netProfit >= 0) "#38BDF8" else "#EF4444"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
        }
        profitContainer.addView(profitTv)

        // ROW 4: Distance Breakdown Metric Box
        val pickupFmt = String.format(java.util.Locale.US, "%.1f", card.pickupDistanceKm)
        val tripFmt = String.format(java.util.Locale.US, "%.1f", card.tripDistanceKm)
        val totalFmt = String.format(java.util.Locale.US, "%.1f", card.totalDistanceKm)
        val distContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (6 * density).toInt(), 0, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(Color.parseColor("#131B2E"))
            }
        }
        val distTv = TextView(this).apply {
            text = "المسافة: $totalFmt كم  ($pickupFmt وصول + $tripFmt مشوار)"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT
            setSingleLine(true)
        }
        distContainer.addView(distTv)

        cardRoot.addView(topRow)
        cardRoot.addView(fareRow)
        cardRoot.addView(profitContainer)
        cardRoot.addView(distContainer)

        // ROW 5 & 6: 1-CLICK ZONE MANAGEMENT (Pick-up & Drop-off addresses)
        if (card.pickupAddress.isNotBlank()) {
            cardRoot.addView(buildZoneAddressActionRow(card.pickupAddress, "التحرك", holder, density))
        }

        if (card.dropoffAddress.isNotBlank()) {
            cardRoot.addView(buildZoneAddressActionRow(card.dropoffAddress, "الوصول", holder, density))
        }

        backdrop.addView(cardRoot)
        holder.container.addView(backdrop)
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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(Color.parseColor("#0F1728"))
                setStroke((1.5f * density).toInt(), when {
                    isFav -> Color.parseColor("#FFB800")
                    isBlack -> Color.parseColor("#FF3355")
                    else -> Color.parseColor("#223554")
                })
            }
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
        }

        // Row 1: Alert badge (!) + Clickable Address Title
        val addrRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val alertBadge = TextView(this).apply {
            text = "!"
            setTextColor(Color.parseColor("#070B14"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val badgeDiameter = (18 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(badgeDiameter, badgeDiameter).apply {
                setMargins(0, 0, (8 * density).toInt(), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(when {
                    isFav -> Color.parseColor("#FFB800")
                    isBlack -> Color.parseColor("#FF3355")
                    else -> Color.parseColor("#00F59B")
                })
            }
        }

        val statusTag = when {
            isFav -> " (مفضلة)"
            isBlack -> " (محظورة)"
            else -> ""
        }

        val addrTv = TextView(this).apply {
            text = "$prefix: $address$statusTag"
            setTextColor(Color.parseColor(if (isFav) "#FFB800" else if (isBlack) "#FF3355" else "#F8FAFC"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        addrRow.addView(alertBadge)
        addrRow.addView(addrTv)

        // Row 2: 1-Tap Action Chips
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (6 * density).toInt(), 0, 0)
            }
        }

        val favBtn = TextView(this).apply {
            text = if (isFav) "إلغاء المفضلة" else "+ مفضلة"
            setTextColor(if (isFav) Color.WHITE else Color.parseColor("#070B14"))
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(if (isFav) Color.parseColor("#475569") else Color.parseColor("#FFB800"))
            }
            setOnClickListener {
                if (isFav) {
                    repo.removeFavoriteZone(address)
                    Toast.makeText(this@FloatingHudService, "تمت إزالة '$address' من المفضلة", Toast.LENGTH_SHORT).show()
                } else {
                    repo.addFavoriteZone(address)
                    Toast.makeText(this@FloatingHudService, "تمت إضافة '$address' للمناطق المفضلة", Toast.LENGTH_SHORT).show()
                }
                refreshAnalysis(holder)
            }
        }

        val blackBtn = TextView(this).apply {
            text = if (isBlack) "إلغاء الحظر" else "+ محظورة"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((8 * density).toInt(), 0, 0, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(if (isBlack) Color.parseColor("#475569") else Color.parseColor("#FF3355"))
            }
            setOnClickListener {
                if (isBlack) {
                    repo.removeBlacklistZone(address)
                    Toast.makeText(this@FloatingHudService, "تم إلغاء حظر '$address'", Toast.LENGTH_SHORT).show()
                } else {
                    repo.addBlacklistZone(address)
                    Toast.makeText(this@FloatingHudService, "تمت إضافة '$address' للمناطق المحظورة", Toast.LENGTH_SHORT).show()
                }
                refreshAnalysis(holder)
            }
        }

        btnRow.addView(favBtn)
        btnRow.addView(blackBtn)

        container.addView(addrRow)
        container.addView(btnRow)

        // Make the address row and (!) badge directly clickable to cycle status with 1 tap!
        val rowTapListener = View.OnClickListener {
            if (!isFav && !isBlack) {
                repo.addFavoriteZone(address)
                Toast.makeText(this@FloatingHudService, "تم حفظ '$address' كمفضلة بنجاح", Toast.LENGTH_SHORT).show()
            } else if (isFav) {
                repo.removeFavoriteZone(address)
                repo.addBlacklistZone(address)
                Toast.makeText(this@FloatingHudService, "تم نقل '$address' إلى المناطق المحظورة", Toast.LENGTH_SHORT).show()
            } else {
                repo.removeBlacklistZone(address)
                Toast.makeText(this@FloatingHudService, "تمت إزالة تصنيف '$address'", Toast.LENGTH_SHORT).show()
            }
            refreshAnalysis(holder)
        }

        addrRow.setOnClickListener(rowTapListener)
        alertBadge.setOnClickListener(rowTapListener)
        addrTv.setOnClickListener(rowTapListener)

        return container
    }

    private fun refreshAnalysis(holder: CardPopupViewHolder) {
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        holder.analysis = FareCalculatorEngine.analyze(
            pickupDistanceKm = holder.ride.pickupDistanceKm,
            tripDistanceKm = holder.ride.tripDistanceKm,
            estimatedMinutes = ((holder.ride.totalDistanceKm) * 2.3).toInt(),
            offeredFare = holder.ride.fare,
            settings = settings,
            pickupAddress = holder.ride.pickupAddress,
            dropoffAddress = holder.ride.dropoffAddress
        )
        renderPopup(holder)
        windowManager?.updateViewLayout(holder.container, holder.params)
    }

    // =========================================================================
    // PULSATING GLOW ANIMATION (Brings Favorite Zones to life)
    // =========================================================================
    private fun startPulsatingGlowAnimation(
        view: View,
        holder: CardPopupViewHolder,
        themeColorHex: String
    ) {
        val animator = ValueAnimator.ofFloat(0.35f, 1.0f).apply {
            duration = 850L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { va ->
                val fraction = va.animatedValue as Float
                val baseColor = Color.parseColor(themeColorHex)
                val alpha = (fraction * 255).toInt()
                val animatedColor = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                (view.background as? GradientDrawable)?.setStroke(
                    (3.5f * resources.displayMetrics.density).toInt(),
                    animatedColor
                )
            }
        }
        animator.start()
        holder.pulseAnimator = animator
    }

    // Drag touch listener
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
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > 10) {
                        isDragging = true
                        holder.params.x = initialX + dx.toInt()
                        holder.params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(holder.container, holder.params)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun clearAllCardPopups() {
        val wm = windowManager ?: return
        for ((_, holder) in activePopups) {
            holder.pulseAnimator?.cancel()
            try {
                wm.removeView(holder.container)
            } catch (_: Exception) {}
        }
        activePopups.clear()
    }

    // =========================================================================
    // SINGLE-SHOT HAPTIC ALERT
    // =========================================================================
    private fun vibrateSingleShotOnce(card: ParsedRideData) {
        if (isVibrationMuted) return

        val now = System.currentTimeMillis()
        if (card.fingerprint == lastVibratedFingerprint && (now - lastVibrateTimestamp) < 15000L) {
            return
        }
        if (now - lastVibrateTimestamp < 3500L) {
            return
        }

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
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAllCardPopups()
        if (activeInstance == this) {
            activeInstance = null
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private var activeInstance: FloatingHudService? = null

        fun onCardsSync(cards: List<ParsedRideData>) {
            activeInstance?.handleCardsSync(cards)
        }

        fun onNewRideParsed(ride: ParsedRideData) {
            activeInstance?.handleCardsSync(listOf(ride))
        }

        fun isRunning(): Boolean = activeInstance != null
    }
}
