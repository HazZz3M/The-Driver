package com.indrive.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
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
    private var containerView: View? = null
    private var isExpanded = false

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    // UI Element References for Live Dynamic Updating
    private var bubbleLabel: TextView? = null
    private var bubbleContainer: LinearLayout? = null
    private var pickupInputEt: EditText? = null
    private var tripInputEt: EditText? = null
    private var fareInputEt: EditText? = null
    private var addressInfoTv: TextView? = null
    private var resultProfitTv: TextView? = null
    private var costBreakdownTv: TextView? = null
    private var suggestedBidsTv: TextView? = null
    private var a11yWarningBtn: Button? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        startForeground(NOTIFICATION_ID, createNotification())
        setupFloatingOverlay()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, InDriveAssistantApp.CHANNEL_ID)
            .setContentTitle("inDrive Assistant HUD Active")
            .setContentText("القراءة التلقائية وحاسبة أرباح إن درايف تعمل الآن فوق التطبيقات")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Collapsed Bubble (Pill that dynamically shows live status and parsed rides)
        val bubbleView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(28, 20, 28, 20)
            gravity = Gravity.CENTER_VERTICAL
            
            background = createPillBackground("#0F172A", "#00EA80")

            val iconText = TextView(context).apply {
                text = "🚖"
                textSize = 17f
                setPadding(0, 0, 10, 0)
            }

            val label = TextView(context).apply {
                text = "inDrive HUD (جاهز)"
                setTextColor(Color.parseColor("#00EA80"))
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            bubbleLabel = label
            bubbleContainer = this

            addView(iconText)
            addView(label)
        }

        // 2. Expanded Panel
        val expandedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1120"))
                setStroke(3, Color.parseColor("#334155"))
                cornerRadius = 28f
            }

            // Header
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                
                val title = TextView(context).apply {
                    text = "⚡ محلل رحلات إن درايف الذكي"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val closeBtn = TextView(context).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 18f
                    setPadding(16, 0, 8, 0)
                    setOnClickListener {
                        visibility = View.GONE
                        bubbleView.visibility = View.VISIBLE
                        isExpanded = false
                        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        windowManager?.updateViewLayout(container, params)
                    }
                }

                addView(title)
                addView(closeBtn)
            }

            // Accessibility Status / Warning Button
            val a11yBtn = Button(context).apply {
                text = "⚠️ فعّل قراءة الشاشة التلقائية (Accessibility)"
                textSize = 11f
                setBackgroundColor(Color.parseColor("#854D0E"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    Toast.makeText(this@FloatingHudService, "اختر inDrive Assistant ثم اضغط تفعيل", Toast.LENGTH_LONG).show()
                }
                visibility = if (InDriveAccessibilityService.isAccessibilityEnabled(this@FloatingHudService)) View.GONE else View.VISIBLE
            }
            a11yWarningBtn = a11yBtn

            // Address summary if read from screen
            val addrTv = TextView(context).apply {
                text = "بانتظار قراءة عرض الرحلة من الشاشة..."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                setPadding(0, 10, 0, 10)
            }
            addressInfoTv = addrTv

            // Input Fields
            val pickupInput = createInputField("مسافة الراكب (كم):", "2.0")
            val tripInput = createInputField("مسافة المشوار (كم):", "5.0")
            val fareInput = createInputField("عرض الراكب (ج.م):", "40.0")

            pickupInputEt = pickupInput.second
            tripInputEt = tripInput.second
            fareInputEt = fareInput.second

            val resultTv = TextView(context).apply {
                text = "صافي الربح: --"
                setTextColor(Color.parseColor("#00EA80"))
                textSize = 15f
                setPadding(0, 12, 0, 4)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            resultProfitTv = resultTv

            val costTv = TextView(context).apply {
                text = "التكاليف: --"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setPadding(0, 0, 0, 4)
            }
            costBreakdownTv = costTv

            val suggestedTv = TextView(context).apply {
                text = "المزايدة المقترحة: --"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                setPadding(0, 0, 0, 12)
            }
            suggestedBidsTv = suggestedTv

            // Actions Row
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                val scanBtn = Button(context).apply {
                    text = "🔄 اقرأ الشاشة"
                    setBackgroundColor(Color.parseColor("#1E293B"))
                    setTextColor(Color.parseColor("#38BDF8"))
                    textSize = 12f
                    setOnClickListener {
                        val parsed = InDriveAccessibilityService.scanCurrentScreen()
                        if (parsed != null) {
                            Toast.makeText(this@FloatingHudService, "تمت قراءة الرحلة بنجاح!", Toast.LENGTH_SHORT).show()
                        } else {
                            if (!InDriveAccessibilityService.isAccessibilityEnabled(this@FloatingHudService)) {
                                Toast.makeText(this@FloatingHudService, "يرجى تفعيل خدمة قراءة الشاشة في إعدادات الهاتف أولاً", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@FloatingHudService, "لم يتم العثور على عرض رحلة مفتوح الآن", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                        setMargins(0, 0, 8, 0)
                    }
                }

                val calcBtn = Button(context).apply {
                    text = "حساب"
                    setBackgroundColor(Color.parseColor("#00EA80"))
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setOnClickListener { executeManualRecalculate() }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f).apply {
                        setMargins(0, 0, 8, 0)
                    }
                }

                val saveBtn = Button(context).apply {
                    text = "💾 حفظ"
                    setBackgroundColor(Color.parseColor("#334155"))
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setOnClickListener {
                        saveCurrentRideToDatabase {
                            visibility = View.GONE
                            bubbleView.visibility = View.VISIBLE
                            isExpanded = false
                            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            windowManager?.updateViewLayout(container, params)
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
                }

                addView(scanBtn)
                addView(calcBtn)
                addView(saveBtn)
            }

            addView(headerRow)
            addView(a11yBtn)
            addView(addrTv)
            addView(pickupInput.first)
            addView(tripInput.first)
            addView(fareInput.first)
            addView(resultTv)
            addView(costTv)
            addView(suggestedTv)
            addView(btnRow)
        }

        container.addView(bubbleView)
        container.addView(expandedCard)

        // Dragging & Tapping
        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(container, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            // Refresh accessibility status button
                            a11yWarningBtn?.visibility = if (InDriveAccessibilityService.isAccessibilityEnabled(this@FloatingHudService)) View.GONE else View.VISIBLE
                            
                            // If user clicks, also attempt an immediate scan in case an offer is active
                            InDriveAccessibilityService.scanCurrentScreen()

                            bubbleView.visibility = View.GONE
                            expandedCard.visibility = View.VISIBLE
                            isExpanded = true
                            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            windowManager?.updateViewLayout(container, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        containerView = container
        windowManager?.addView(container, params)
    }

    private fun createPillBackground(bgColor: String, strokeColor: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            setStroke(4, Color.parseColor(strokeColor))
            cornerRadius = 60f
        }
    }

    private fun createInputField(label: String, defaultVal: String): Pair<LinearLayout, EditText> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }

        val tv = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
        }

        val et = EditText(this).apply {
            setText(defaultVal)
            setTextColor(Color.WHITE)
            textSize = 13f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(14, 10, 14, 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        row.addView(tv)
        row.addView(et)
        return Pair(row, et)
    }

    private fun executeManualRecalculate() {
        val pKm = pickupInputEt?.text?.toString()?.toDoubleOrNull() ?: 0.0
        val tKm = tripInputEt?.text?.toString()?.toDoubleOrNull() ?: 0.0
        val fare = fareInputEt?.text?.toString()?.toDoubleOrNull() ?: 0.0

        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        val analysis = FareCalculatorEngine.analyze(
            pickupDistanceKm = pKm,
            tripDistanceKm = tKm,
            estimatedMinutes = ((pKm + tKm) * 2.5).toInt(),
            offeredFare = fare,
            settings = settings
        )

        displayAnalysisInUi(analysis, null)
    }

    private fun displayAnalysisInUi(analysis: FareAnalysisResult, parsedRide: ParsedRideData?) {
        val (profitColorHex, statusArabic) = when (analysis.profitabilityLevel) {
            ProfitabilityLevel.EXCELLENT_PROFIT -> Pair("#00EA80", "ممتاز جداً 🔥")
            ProfitabilityLevel.GOOD_PROFIT -> Pair("#00EA80", "رابح 🟢")
            ProfitabilityLevel.POOR_MARGIN -> Pair("#F59E0B", "هامش ضعيف 🟡")
            ProfitabilityLevel.LOSS_DANGER -> Pair("#EF4444", "خسارة 🔴")
        }

        // 1. Update Collapsed Bubble text & glow
        val shortLevel = when (analysis.profitabilityLevel) {
            ProfitabilityLevel.EXCELLENT_PROFIT -> "🔥"
            ProfitabilityLevel.GOOD_PROFIT -> "🟢"
            ProfitabilityLevel.POOR_MARGIN -> "🟡"
            ProfitabilityLevel.LOSS_DANGER -> "🔴"
        }
        bubbleLabel?.text = "$shortLevel صافي: ${String.format("%.1f", analysis.netProfit)} ج | ${String.format("%.1f", analysis.tripDistanceKm)} كم | اقترح: ${analysis.recommendedFare.toInt()} ج"
        bubbleLabel?.setTextColor(Color.parseColor(profitColorHex))
        bubbleContainer?.background = createPillBackground("#0F172A", profitColorHex)

        // 2. Update Expanded Card
        resultProfitTv?.setTextColor(Color.parseColor(profitColorHex))
        resultProfitTv?.text = "صافي الربح: ${String.format("%.1f", analysis.netProfit)} ج.م ($statusArabic) | ${String.format("%.1f", analysis.profitPerKm)} ج/كم"

        val totalCost = analysis.fuelCost + analysis.wearAndTearCost + analysis.commissionCost
        costBreakdownTv?.text = "بنزين وزيت: ${String.format("%.1f", analysis.fuelCost + analysis.wearAndTearCost)} ج | عمولة: ${String.format("%.1f", analysis.commissionCost)} ج | الإجمالي: ${String.format("%.1f", totalCost)} ج"

        val bidsInfo = if (parsedRide?.counterBids?.isNotEmpty() == true) {
            "خيارات الشاشة: ${parsedRide.counterBids.joinToString("، ")} ج.م  ⚡ اختر: ${analysis.recommendedFare.toInt()} ج"
        } else {
            "الحد الأدنى: ${analysis.breakEvenFare.toInt()} ج | المقترح: ${analysis.recommendedFare.toInt()} ج | الأعلى: ${analysis.aggressiveFare.toInt()} ج"
        }
        suggestedBidsTv?.text = bidsInfo

        if (parsedRide != null && (parsedRide.pickupAddress.isNotEmpty() || parsedRide.dropoffAddress.isNotEmpty())) {
            addressInfoTv?.text = "📍 من: ${parsedRide.pickupAddress}\n🏁 إلى: ${parsedRide.dropoffAddress}"
        }
    }

    private fun handleNewRideParsed(ride: ParsedRideData) {
        serviceScope.launch(Dispatchers.Main) {
            // Update input textboxes
            pickupInputEt?.setText(String.format("%.1f", ride.pickupDistanceKm))
            tripInputEt?.setText(String.format("%.1f", ride.tripDistanceKm))
            fareInputEt?.setText(String.format("%.1f", ride.fare))

            val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
            val analysis = FareCalculatorEngine.analyze(
                pickupDistanceKm = ride.pickupDistanceKm,
                tripDistanceKm = ride.tripDistanceKm,
                estimatedMinutes = ((ride.pickupDistanceKm + ride.tripDistanceKm) * 2.2).toInt(),
                offeredFare = ride.fare,
                settings = settings
            )

            displayAnalysisInUi(analysis, ride)
            vibrateFeedback()
        }
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    private fun saveCurrentRideToDatabase(onSaved: () -> Unit) {
        val pKm = pickupInputEt?.text?.toString()?.toDoubleOrNull() ?: 0.0
        val tKm = tripInputEt?.text?.toString()?.toDoubleOrNull() ?: 0.0
        val fare = fareInputEt?.text?.toString()?.toDoubleOrNull() ?: 0.0
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        val analysis = FareCalculatorEngine.analyze(pKm, tKm, ((pKm + tKm) * 2.5).toInt(), fare, settings)

        serviceScope.launch {
            InDriveAssistantApp.instance.repository.recordTrip(
                Trip(
                    pickupDistanceKm = pKm,
                    tripDistanceKm = tKm,
                    estimatedMinutes = analysis.estimatedMinutes,
                    offeredFare = fare,
                    fuelCost = analysis.fuelCost,
                    commissionCost = analysis.commissionCost,
                    wearAndTearCost = analysis.wearAndTearCost,
                    netProfit = analysis.netProfit,
                    profitPerKm = analysis.profitPerKm
                )
            )
            Toast.makeText(this@FloatingHudService, "تم تسجيل الرحلة بنجاح في الوردية!", Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        containerView?.let { windowManager?.removeView(it) }
        if (activeInstance == this) {
            activeInstance = null
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private var activeInstance: FloatingHudService? = null

        fun onNewRideParsed(ride: ParsedRideData) {
            activeInstance?.handleNewRideParsed(ride)
        }
    }
}
