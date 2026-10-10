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
import android.widget.HorizontalScrollView
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

    // Multiple Rides Storage & Selection
    private val activeRidesList = mutableListOf<ParsedRideData>()
    private var selectedRideIndex = 0

    // Anti-Vibration Spam tracking
    private var isVibrationMuted = false
    private var lastVibratedFingerprint = ""
    private var lastVibrateTimestamp = 0L

    // UI Element References
    private var bubbleView: View? = null
    private var expandedCard: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var bubbleLabel: TextView? = null
    private var bubbleContainer: LinearLayout? = null
    private var ridesTabsContainer: LinearLayout? = null
    private var ridesTabsScroll: HorizontalScrollView? = null
    private var currentClientTv: TextView? = null
    private var addressInfoTv: TextView? = null
    private var pickupInputEt: EditText? = null
    private var tripInputEt: EditText? = null
    private var fareInputEt: EditText? = null
    private var resultProfitTv: TextView? = null
    private var costBreakdownTv: TextView? = null
    private var suggestedBidsTv: TextView? = null
    private var a11yWarningBtn: Button? = null
    private var muteVibrateBtn: Button? = null

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
            .setContentText("محلل ومساعد عروض رحلات إن درايف يعمل الآن فوق التطبيقات")
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
        layoutParams = params

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Collapse / Close logic helper
        fun collapsePanel() {
            expandedCard?.visibility = View.GONE
            bubbleView?.visibility = View.VISIBLE
            isExpanded = false
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(container, params)
        }

        // 1. Collapsed Bubble (Pill that dynamically displays latest customer & profit)
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(26, 18, 26, 18)
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
        bubbleView = bubble

        // 2. Expanded Panel
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(28, 24, 28, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1120"))
                setStroke(3, Color.parseColor("#334155"))
                cornerRadius = 28f
            }

            // Top Header with clear clickable Close button
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 8)

                val title = TextView(context).apply {
                    text = "⚡ محلل رحلات إن درايف"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { collapsePanel() }
                }

                // Close Button (Fixed: closes the card explicitly!)
                val closeBtn = TextView(context).apply {
                    text = "✕ إغلاق"
                    setTextColor(Color.parseColor("#F87171"))
                    textSize = 13f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setBackgroundColor(Color.parseColor("#1E293B"))
                    setPadding(24, 12, 24, 12)
                    setOnClickListener {
                        collapsePanel()
                    }
                }

                addView(title)
                addView(closeBtn)
            }

            // Accessibility Warning button if disabled
            val a11yBtn = Button(context).apply {
                text = "⚠️ اضغط هنا لتفعيل قراءة الشاشة (Accessibility)"
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

            // Horizontal Scroll for Multiple Client Rides Tabs
            val tabsScroll = HorizontalScrollView(context).apply {
                visibility = View.GONE
                setPadding(0, 6, 0, 8)
            }
            val tabsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            tabsScroll.addView(tabsLayout)
            ridesTabsScroll = tabsScroll
            ridesTabsContainer = tabsLayout

            // Customer Name & Rating Badge
            val clientTv = TextView(context).apply {
                text = "بانتظار قراءة عرض الرحلة..."
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 4, 0, 4)
            }
            currentClientTv = clientTv

            // Pickup & Destination Address Info
            val addrTv = TextView(context).apply {
                text = ""
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                setPadding(0, 2, 0, 8)
                visibility = View.GONE
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
                setPadding(0, 10, 0, 2)
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
                setPadding(0, 0, 0, 10)
            }
            suggestedBidsTv = suggestedTv

            // Actions Row 1 (Scan, Calc, Save, Mute Vibrate)
            val btnRow1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 8)

                val scanBtn = Button(context).apply {
                    text = "🔄 اقرأ"
                    setBackgroundColor(Color.parseColor("#1E293B"))
                    setTextColor(Color.parseColor("#38BDF8"))
                    textSize = 11f
                    setOnClickListener {
                        val parsedList = InDriveAccessibilityService.scanCurrentScreen()
                        if (parsedList.isNotEmpty()) {
                            Toast.makeText(this@FloatingHudService, "تمت قراءة ${parsedList.size} عروض بنجاح!", Toast.LENGTH_SHORT).show()
                        } else {
                            if (!InDriveAccessibilityService.isAccessibilityEnabled(this@FloatingHudService)) {
                                Toast.makeText(this@FloatingHudService, "يرجى تفعيل خدمة قراءة الشاشة في إعدادات الهاتف أولاً", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@FloatingHudService, "لم يتم العثور على عرض رحلة على الشاشة حالياً", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f).apply {
                        setMargins(0, 0, 6, 0)
                    }
                }

                val calcBtn = Button(context).apply {
                    text = "حساب"
                    setBackgroundColor(Color.parseColor("#00EA80"))
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 11f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setOnClickListener { executeManualRecalculate() }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f).apply {
                        setMargins(0, 0, 6, 0)
                    }
                }

                val saveBtn = Button(context).apply {
                    text = "💾 حفظ"
                    setBackgroundColor(Color.parseColor("#334155"))
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    setOnClickListener {
                        saveCurrentRideToDatabase {
                            collapsePanel()
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f).apply {
                        setMargins(0, 0, 6, 0)
                    }
                }

                val vibrateBtn = Button(context).apply {
                    text = "📳 هزاز"
                    setBackgroundColor(Color.parseColor("#1E293B"))
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11f
                    setOnClickListener {
                        isVibrationMuted = !isVibrationMuted
                        text = if (isVibrationMuted) "🔕 صامت" else "📳 هزاز"
                        setTextColor(if (isVibrationMuted) Color.parseColor("#EF4444") else Color.parseColor("#00EA80"))
                        Toast.makeText(this@FloatingHudService, if (isVibrationMuted) "تم كتم اهتزاز العروض" else "تم تفعيل الاهتزاز", Toast.LENGTH_SHORT).show()
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                muteVibrateBtn = vibrateBtn

                addView(scanBtn)
                addView(calcBtn)
                addView(saveBtn)
                addView(vibrateBtn)
            }

            // Big, Dedicated Close / Minimize Button at bottom (Guaranteed to close easily!)
            val bottomCloseBtn = Button(context).apply {
                text = "✕ تصغير وإغلاق النافذة"
                setBackgroundColor(Color.parseColor("#1E293B"))
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 16, 0, 16)
                setOnClickListener {
                    collapsePanel()
                }
            }

            addView(headerRow)
            addView(a11yBtn)
            addView(tabsScroll)
            addView(clientTv)
            addView(addrTv)
            addView(pickupInput.first)
            addView(tripInput.first)
            addView(fareInput.first)
            addView(resultTv)
            addView(costTv)
            addView(suggestedTv)
            addView(btnRow1)
            addView(bottomCloseBtn)
        }
        expandedCard = card

        container.addView(bubble)
        container.addView(card)

        // Dragging & Tapping the Bubble
        bubble.setOnTouchListener(object : View.OnTouchListener {
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
                            a11yWarningBtn?.visibility = if (InDriveAccessibilityService.isAccessibilityEnabled(this@FloatingHudService)) View.GONE else View.VISIBLE
                            
                            // On click, scan in case a ride is open
                            InDriveAccessibilityService.scanCurrentScreen()

                            bubble.visibility = View.GONE
                            card.visibility = View.VISIBLE
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
            setPadding(0, 4, 0, 4)
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
            setPadding(14, 8, 14, 8)
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

        val currentRide = if (activeRidesList.isNotEmpty() && selectedRideIndex in activeRidesList.indices) {
            activeRidesList[selectedRideIndex]
        } else null

        displayAnalysisInUi(analysis, currentRide)
    }

    private fun displayAnalysisInUi(analysis: FareAnalysisResult, parsedRide: ParsedRideData?) {
        val (profitColorHex, statusArabic) = when (analysis.profitabilityLevel) {
            ProfitabilityLevel.EXCELLENT_PROFIT -> Pair("#00EA80", "ممتاز جداً 🔥")
            ProfitabilityLevel.GOOD_PROFIT -> Pair("#00EA80", "رابح 🟢")
            ProfitabilityLevel.POOR_MARGIN -> Pair("#F59E0B", "هامش ضعيف 🟡")
            ProfitabilityLevel.LOSS_DANGER -> Pair("#EF4444", "خسارة 🔴")
        }

        val shortIcon = when (analysis.profitabilityLevel) {
            ProfitabilityLevel.EXCELLENT_PROFIT -> "🔥"
            ProfitabilityLevel.GOOD_PROFIT -> "🟢"
            ProfitabilityLevel.POOR_MARGIN -> "🟡"
            ProfitabilityLevel.LOSS_DANGER -> "🔴"
        }

        // 1. Update Collapsed Bubble text
        if (activeRidesList.size > 1) {
            val r1 = activeRidesList[0]
            val r2 = activeRidesList[1]
            bubbleLabel?.text = "$shortIcon (${activeRidesList.size}) [${r1.passengerName}]: ${r1.fare.toInt()}ج ⮂ [${r2.passengerName}]: ${r2.fare.toInt()}ج"
        } else if (parsedRide != null) {
            bubbleLabel?.text = "$shortIcon [${parsedRide.passengerName}]: ${analysis.offeredFare.toInt()} ج | صافي: ${String.format("%.1f", analysis.netProfit)} ج | اقترح: ${analysis.recommendedFare.toInt()} ج"
        } else {
            bubbleLabel?.text = "$shortIcon صافي: ${String.format("%.1f", analysis.netProfit)} ج | اقترح: ${analysis.recommendedFare.toInt()} ج"
        }
        bubbleLabel?.setTextColor(Color.parseColor(profitColorHex))
        bubbleContainer?.background = createPillBackground("#0F172A", profitColorHex)

        // 2. Update Customer Name & Rating
        if (parsedRide != null) {
            val ratingTxt = if (parsedRide.passengerRating.isNotEmpty()) " (${parsedRide.passengerRating})" else ""
            currentClientTv?.text = "👤 عميل: ${parsedRide.passengerName}$ratingTxt"
            if (parsedRide.pickupAddress.isNotEmpty() || parsedRide.dropoffAddress.isNotEmpty()) {
                addressInfoTv?.visibility = View.VISIBLE
                addressInfoTv?.text = "📍 من: ${parsedRide.pickupAddress}\n🏁 إلى: ${parsedRide.dropoffAddress}"
            } else {
                addressInfoTv?.visibility = View.GONE
            }
        }

        // 3. Update Result texts
        resultProfitTv?.setTextColor(Color.parseColor(profitColorHex))
        resultProfitTv?.text = "صافي الربح: ${String.format("%.1f", analysis.netProfit)} ج.م ($statusArabic) | ${String.format("%.1f", analysis.profitPerKm)} ج/كم"

        val totalCost = analysis.fuelCost + analysis.wearAndTearCost + analysis.commissionCost
        costBreakdownTv?.text = "بنزين وزيت: ${String.format("%.1f", analysis.fuelCost + analysis.wearAndTearCost)} ج | عمولة: ${String.format("%.1f", analysis.commissionCost)} ج | التكلفة: ${String.format("%.1f", totalCost)} ج"

        val bidsInfo = if (parsedRide?.counterBids?.isNotEmpty() == true) {
            "خيارات الشاشة: ${parsedRide.counterBids.joinToString("، ")} ج.م  ⚡ اختر: ${analysis.recommendedFare.toInt()} ج"
        } else {
            "الحد الأدنى: ${analysis.breakEvenFare.toInt()} ج | المقترح: ${analysis.recommendedFare.toInt()} ج | الأعلى: ${analysis.aggressiveFare.toInt()} ج"
        }
        suggestedBidsTv?.text = bidsInfo
    }

    private fun refreshRidesTabs() {
        val container = ridesTabsContainer ?: return
        val scroll = ridesTabsScroll ?: return

        container.removeAllViews()
        if (activeRidesList.size <= 1) {
            scroll.visibility = View.GONE
            return
        }

        scroll.visibility = View.VISIBLE
        for (i in activeRidesList.indices) {
            val ride = activeRidesList[i]
            val isSelected = (i == selectedRideIndex)

            val chip = TextView(this).apply {
                text = "${if (isSelected) "👉 " else ""}${ride.passengerName} (${ride.fare.toInt()} ج)"
                textSize = 11f
                setPadding(18, 10, 18, 10)
                if (isSelected) {
                    setTextColor(Color.parseColor("#0F172A"))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00EA80"))
                        cornerRadius = 20f
                    }
                } else {
                    setTextColor(Color.parseColor("#94A3B8"))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E293B"))
                        cornerRadius = 20f
                    }
                }
                setOnClickListener {
                    selectRide(i)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
            }
            container.addView(chip)
        }
    }

    private fun selectRide(index: Int) {
        if (index !in activeRidesList.indices) return
        selectedRideIndex = index
        val ride = activeRidesList[index]

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

        refreshRidesTabs()
        displayAnalysisInUi(analysis, ride)
    }

    private fun handleNewRideParsed(ride: ParsedRideData) {
        serviceScope.launch(Dispatchers.Main) {
            // Check if this exact ride fingerprint is already in the list
            val existingIdx = activeRidesList.indexOfFirst { it.fingerprint == ride.fingerprint }
            val isGenuinelyNew = (existingIdx == -1)

            if (existingIdx != -1) {
                // Update existing item
                activeRidesList[existingIdx] = ride
                selectedRideIndex = existingIdx
            } else {
                // Prepend new ride so it's first
                activeRidesList.add(0, ride)
                // Limit queue to 6 recent rides
                if (activeRidesList.size > 6) {
                    activeRidesList.removeAt(activeRidesList.size - 1)
                }
                selectedRideIndex = 0
            }

            selectRide(selectedRideIndex)

            // Vibrate ONLY ONCE when a genuinely new ride arrives (never in a loop!)
            if (isGenuinelyNew) {
                vibrateSingleShotOnce(ride)
            }
        }
    }

    private fun vibrateSingleShotOnce(ride: ParsedRideData) {
        if (isVibrationMuted) return

        val now = System.currentTimeMillis()
        // Deduplicate & enforce 4-second minimum interval between vibrations
        if (ride.fingerprint == lastVibratedFingerprint && (now - lastVibrateTimestamp) < 15000L) {
            return
        }
        if (now - lastVibrateTimestamp < 4000L) {
            return
        }

        lastVibratedFingerprint = ride.fingerprint
        lastVibrateTimestamp = now

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
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
            Toast.makeText(this@FloatingHudService, "تم تسجيل الرحلة في الوردية بنجاح!", Toast.LENGTH_SHORT).show()
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
