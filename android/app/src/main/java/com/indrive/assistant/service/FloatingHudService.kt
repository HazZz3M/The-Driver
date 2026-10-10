package com.indrive.assistant.service

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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.indrive.assistant.InDriveAssistantApp
import com.indrive.assistant.MainActivity
import com.indrive.assistant.data.model.ParsedRideData
import com.indrive.assistant.data.model.Trip
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

    // Optional Quick Tool Bubble (Idle Pill)
    private var idleToolBubble: View? = null
    private var isExpandedManualSheet = false
    private var manualSheetView: View? = null

    // Anti-Vibration Spam tracking
    private var isVibrationMuted = false
    private var lastVibratedFingerprint = ""
    private var lastVibrateTimestamp = 0L

    private data class CardPopupViewHolder(
        val ride: ParsedRideData,
        val view: View,
        val params: WindowManager.LayoutParams
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
            .setContentTitle("مساعد إن درايف الذكي")
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
                    try {
                        wm.removeView(holder.view)
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
                    // Update position if screen scrolled
                    val targetY = calculatePopupY(card)
                    if (existing.params.y != targetY) {
                        existing.params.y = targetY
                        try {
                            wm.updateViewLayout(existing.view, existing.params)
                        } catch (_: Exception) {}
                    }
                    updatePopupContent(existing.view, card, analysis)
                } else {
                    // Create and add new small popup attached to this card
                    createAndAttachPopup(card, analysis)
                    vibrateSingleShotOnce(card)
                }
            }
        }
    }

    private fun calculatePopupY(card: ParsedRideData): Int {
        val screenBounds = card.screenBounds
        return if (screenBounds != null) {
            // Position neatly near the top of the card
            (screenBounds.top + 8).coerceAtLeast(30)
        } else {
            // Default top offset
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
            x = 24
            y = targetY
        }

        val popupView = buildSmallPopupView(card, analysis)
        try {
            wm.addView(popupView, params)
            activePopups[card.fingerprint] = CardPopupViewHolder(card, popupView, params)
        } catch (_: Exception) {}
    }

    // =========================================================================
    // SMALL POPUP VIEW BUILDER (OEM Telematics standard - Zero Emojis)
    // =========================================================================
    private fun buildSmallPopupView(card: ParsedRideData, analysis: FareAnalysisResult): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 12)
        }

        updatePopupContent(root, card, analysis)
        return root
    }

    private fun updatePopupContent(view: View, card: ParsedRideData, analysis: FareAnalysisResult) {
        val container = view as? LinearLayout ?: return
        container.removeAllViews()

        // Dynamic Color Logic based on Acceptance, Zones & Profitability
        val (themeColorHex, tagLabel) = when {
            analysis.isBlacklistedZone && analysis.profitabilityLevel != ProfitabilityLevel.GOOD_PROFIT -> {
                Pair("#DC2626", "منطقة غير مرغوبة (${analysis.matchedBlacklistZone ?: ""})")
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
                Pair("#059669", "عرض رابح ومجدٍ")
            }
            analysis.profitabilityLevel == ProfitabilityLevel.POOR_MARGIN -> {
                Pair("#D97706", "هامش ضعيف - اقترح سعراً أعلى")
            }
            else -> {
                Pair("#DC2626", "غير مقبول - مخاطرة خسارة")
            }
        }

        // Sleek Glassmorphism Container with Dynamic Colored Border
        container.background = createCardBackground("#0B1120", themeColorHex, 2.5f, 16f)

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
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(12, 4, 12, 4)
            background = createBadgeBackground(themeColorHex, 8f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(14, 2, 6, 2)
            setOnClickListener {
                windowManager?.let { wm ->
                    try {
                        wm.removeView(container)
                    } catch (_: Exception) {}
                }
                activePopups.remove(card.fingerprint)
            }
        }

        topRow.addView(tagTv)
        topRow.addView(closeBtn)

        // ROW 2: Fair Price (السعر العادل للرحلة) - Bold, prominent telematics font
        val fairPriceVal = analysis.recommendedFare.toInt()
        val offeredVal = card.fare.toInt()
        val fairPriceTv = TextView(this).apply {
            text = "السعر العادل: $fairPriceVal ج.م  (عرض الراكب: $offeredVal ج.م)"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 6, 0, 2)
        }

        // ROW 3: Net Profit Margin & Distance Breakdown
        val netProfitVal = String.format("%.1f", analysis.netProfit)
        val profitPerKmVal = String.format("%.1f", analysis.profitPerKm)
        val distText = "${String.format("%.1f", card.totalDistanceKm)} كم (${card.pickupDistanceKm.toInt()} وصول + ${card.tripDistanceKm.toInt()} مشوار)"

        val profitTv = TextView(this).apply {
            text = "صافي الربح: $netProfitVal ج.م ($profitPerKmVal ج/كم) • $distText"
            setTextColor(Color.parseColor(if (analysis.netProfit >= 0) "#38BDF8" else "#EF4444"))
            textSize = 11.5f
            typeface = Typeface.NORMAL
            setPadding(0, 2, 0, 0)
        }

        container.addView(topRow)
        container.addView(fairPriceTv)
        container.addView(profitTv)
    }

    private fun clearAllCardPopups() {
        val wm = windowManager ?: return
        for ((_, holder) in activePopups) {
            try {
                wm.removeView(holder.view)
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

    // =========================================================================
    // GRAPHICAL DRAWABLE BUILDERS
    // =========================================================================
    private fun createCardBackground(
        backgroundColorHex: String,
        borderColorHex: String,
        strokeWidthDp: Float,
        cornerRadiusDp: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(backgroundColorHex))
            setStroke(
                (strokeWidthDp * resources.displayMetrics.density).toInt(),
                Color.parseColor(borderColorHex)
            )
            cornerRadius = cornerRadiusDp * resources.displayMetrics.density
        }
    }

    private fun createBadgeBackground(color: String, cornerRadiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(color))
            cornerRadius = cornerRadiusDp * resources.displayMetrics.density
        }
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
