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

    // Multi-Ride Queue
    private val activeRidesList = mutableListOf<ParsedRideData>()
    private var selectedRideIndex = 0

    // Anti-Vibration Spam tracking
    private var isVibrationMuted = false
    private var lastVibratedFingerprint = ""
    private var lastVibrateTimestamp = 0L

    // View References
    private var popupHintView: View? = null
    private var expandedSheetView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Popup Hint UI Components
    private var hintStatusTagTv: TextView? = null
    private var hintFairPriceTv: TextView? = null
    private var hintSubtextTv: TextView? = null
    private var hintActionTv: TextView? = null
    private var hintMultiOrdersRow: LinearLayout? = null
    private var hintContainerLayout: LinearLayout? = null

    // Expanded Sheet UI Components
    private var sheetClientHeaderTv: TextView? = null
    private var sheetAddressesTv: TextView? = null
    private var sheetTabsContainer: LinearLayout? = null
    private var sheetTabsScroll: HorizontalScrollView? = null
    private var pickupInputEt: EditText? = null
    private var tripInputEt: EditText? = null
    private var fareInputEt: EditText? = null
    private var sheetProfitTv: TextView? = null
    private var sheetCostTv: TextView? = null
    private var sheetBidsTv: TextView? = null
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
            .setContentTitle("مساعد إن درايف الذكي")
            .setContentText("المحلل التلقائي لتقييم العروض والتسعير العادل يعمل في الخلفية")
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
            x = 32
            y = 380
        }
        layoutParams = params

        val rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun collapseToHint() {
            expandedSheetView?.visibility = View.GONE
            popupHintView?.visibility = View.VISIBLE
            isExpanded = false
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(rootContainer, params)
        }

        // =========================================================================
        // 1. POPUP HINT WIDGET (Sleek Automotive Overlay directly indicating Fair Price)
        // =========================================================================
        val hintLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 16, 22, 16)
            background = createCardBackground("#0B1120", "#10B981", 3.0f, 22f)
            hintContainerLayout = this
        }

        // Multiple orders row inside hint (if 2+ orders exist)
        val multiOrdersLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, 0, 0, 10)
        }
        hintMultiOrdersRow = multiOrdersLayout
        hintLayout.addView(multiOrdersLayout)

        // Main Hint Body
        val hintMainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Left Tag Badge (acceptance level)
        val statusTag = TextView(this).apply {
            text = "جاهز للمسح"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(14, 6, 14, 6)
            background = createBadgeBackground("#10B981", 12f)
        }
        hintStatusTagTv = statusTag
        hintMainRow.addView(statusTag)

        // Center Content: Fair Price & Subtext
        val centerColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 0, 16, 0)
        }

        val fairPriceTv = TextView(this).apply {
            text = "بانتظار طلب رحلة..."
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        hintFairPriceTv = fairPriceTv

        val subtextTv = TextView(this).apply {
            text = "افتح إن درايف لتلقي العروض تلقائياً"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10.5f
            setPadding(0, 2, 0, 0)
        }
        hintSubtextTv = subtextTv

        centerColumn.addView(fairPriceTv)
        centerColumn.addView(subtextTv)
        hintMainRow.addView(centerColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Right Expand / Action Indicator
        val actionTv = TextView(this).apply {
            text = "تفاصيل"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(10, 6, 8, 6)
        }
        hintActionTv = actionTv
        hintMainRow.addView(actionTv)

        hintLayout.addView(hintMainRow)
        popupHintView = hintLayout

        // =========================================================================
        // 2. EXPANDED FINANCIAL AUDIT SHEET (Professional Telematics Breakdown)
        // =========================================================================
        val sheetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(26, 22, 26, 22)
            background = createCardBackground("#090D16", "#334155", 2.0f, 26f)
        }

        // Top Navigation & Close Header
        val sheetHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)

            val sheetTitle = TextView(context).apply {
                text = "تحليل تسعير الرحلة والجدوى المالية"
                setTextColor(Color.WHITE)
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val closeBtn = TextView(context).apply {
                text = "إغلاق"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(Color.parseColor("#1E293B"))
                setPadding(20, 10, 20, 10)
                setOnClickListener { collapseToHint() }
            }

            addView(sheetTitle)
            addView(closeBtn)
        }

        // Accessibility Warning Banner
        val a11yBanner = Button(this).apply {
            text = "تفعيل خدمة قراءة الشاشة (إمكانية الوصول مطلوبة)"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#92400E"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                Toast.makeText(this@FloatingHudService, "ادخل على الخدمات المثبتة وفعل inDrive Assistant", Toast.LENGTH_LONG).show()
            }
            visibility = if (InDriveAccessibilityService.isAccessibilityEnabled(this@FloatingHudService)) View.GONE else View.VISIBLE
        }
        a11yWarningBtn = a11yBanner

        // Customer selection scroll tabs for multiple rides
        val tabsScroll = HorizontalScrollView(this).apply {
            visibility = View.GONE
            setPadding(0, 4, 0, 8)
        }
        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        tabsScroll.addView(tabsLayout)
        sheetTabsScroll = tabsScroll
        sheetTabsContainer = tabsLayout

        // Customer Profile Tag
        val clientHeaderTv = TextView(this).apply {
            text = "العميل: غير محدد"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 2)
        }
        sheetClientHeaderTv = clientHeaderTv

        // Address Details
        val addrTv = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            setPadding(0, 2, 0, 8)
            visibility = View.GONE
        }
        sheetAddressesTv = addrTv

        // Telematics Distance and Fare Inputs
        val pickupInput = createTelemetryInput("مسافة الوصول للراكب (كم):", "2.0")
        val tripInput = createTelemetryInput("مسافة المشوار الصافية (كم):", "5.0")
        val fareInput = createTelemetryInput("عرض الراكب الحالي (ج.م):", "40.0")

        pickupInputEt = pickupInput.second
        tripInputEt = tripInput.second
        fareInputEt = fareInput.second

        // Outcome Metrics
        val netProfitTv = TextView(this).apply {
            text = "صافي الربح المتوقع: --"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 10, 0, 2)
        }
        sheetProfitTv = netProfitTv

        val costTv = TextView(this).apply {
            text = "التكاليف التشغيلية: --"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            setPadding(0, 2, 0, 4)
        }
        sheetCostTv = costTv

        val recommendedBidTv = TextView(this).apply {
            text = "السعر العادل المقترح: --"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 2, 0, 10)
        }
        sheetBidsTv = recommendedBidTv

        // Actions Row
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 10)

            val scanBtn = Button(context).apply {
                text = "قراءة الشاشة"
                setBackgroundColor(Color.parseColor("#1E293B"))
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 11f
                setOnClickListener {
                    val parsedList = InDriveAccessibilityService.scanCurrentScreen()
                    if (parsedList.isNotEmpty()) {
                        Toast.makeText(this@FloatingHudService, "تم مسح ${parsedList.size} طلبات", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@FloatingHudService, "لا توجد طلبات معروضة حالياً", Toast.LENGTH_SHORT).show()
                    }
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                    setMargins(0, 0, 6, 0)
                }
            }

            val calcBtn = Button(context).apply {
                text = "إعادة الحساب"
                setBackgroundColor(Color.parseColor("#059669"))
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setOnClickListener { executeManualRecalculate() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 6, 0)
                }
            }

            val recordBtn = Button(context).apply {
                text = "تسجيل الرحلة"
                setBackgroundColor(Color.parseColor("#334155"))
                setTextColor(Color.WHITE)
                textSize = 11f
                setOnClickListener {
                    saveCurrentRideToDatabase { collapseToHint() }
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 6, 0)
                }
            }

            val muteBtn = Button(context).apply {
                text = "الاهتزاز: مفعل"
                setBackgroundColor(Color.parseColor("#1E293B"))
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10f
                setOnClickListener {
                    isVibrationMuted = !isVibrationMuted
                    text = if (isVibrationMuted) "الاهتزاز: مكتوم" else "الاهتزاز: مفعل"
                    setTextColor(if (isVibrationMuted) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)
            }
            muteVibrateBtn = muteBtn

            addView(scanBtn)
            addView(calcBtn)
            addView(recordBtn)
            addView(muteBtn)
        }

        val minimizeFullBtn = Button(this).apply {
            text = "تصغير وحفظ في الخلفية"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            setOnClickListener { collapseToHint() }
        }

        sheetCard.addView(sheetHeader)
        sheetCard.addView(a11yBanner)
        sheetCard.addView(tabsScroll)
        sheetCard.addView(clientHeaderTv)
        sheetCard.addView(addrTv)
        sheetCard.addView(pickupInput.first)
        sheetCard.addView(tripInput.first)
        sheetCard.addView(fareInput.first)
        sheetCard.addView(netProfitTv)
        sheetCard.addView(costTv)
        sheetCard.addView(recommendedBidTv)
        sheetCard.addView(actionsRow)
        sheetCard.addView(minimizeFullBtn)
        expandedSheetView = sheetCard

        rootContainer.addView(hintLayout)
        rootContainer.addView(sheetCard)

        // Dragging and Tapping for the Popup Hint
        hintLayout.setOnTouchListener(object : View.OnTouchListener {
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
                        windowManager?.updateViewLayout(rootContainer, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            a11yWarningBtn?.visibility = if (InDriveAccessibilityService.isAccessibilityEnabled(this@FloatingHudService)) View.GONE else View.VISIBLE
                            InDriveAccessibilityService.scanCurrentScreen()

                            hintLayout.visibility = View.GONE
                            sheetCard.visibility = View.VISIBLE
                            isExpanded = true
                            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            windowManager?.updateViewLayout(rootContainer, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        containerView = rootContainer
        windowManager?.addView(rootContainer, params)
    }

    private fun createCardBackground(bgColor: String, strokeColor: String, strokeWidth: Float, cornerRadiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            setStroke((strokeWidth * resources.displayMetrics.density).toInt(), Color.parseColor(strokeColor))
            cornerRadius = cornerRadiusDp * resources.displayMetrics.density
        }
    }

    private fun createBadgeBackground(color: String, cornerRadiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = cornerRadiusDp * resources.displayMetrics.density
        }
    }

    private fun createTelemetryInput(label: String, defaultVal: String): Pair<LinearLayout, EditText> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3, 0, 3)
        }

        val tv = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
        }

        val et = EditText(this).apply {
            setText(defaultVal)
            setTextColor(Color.WHITE)
            textSize = 12.5f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(12, 6, 12, 6)
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
        // Color-coding and status strictly based on acceptability & profitability
        val (themeColorHex, tagLabel, statusDesc) = when (analysis.profitabilityLevel) {
            ProfitabilityLevel.EXCELLENT_PROFIT -> Triple("#10B981", "قبول فوري", "عالي الربحية")
            ProfitabilityLevel.GOOD_PROFIT -> Triple("#059669", "عرض رابح", "مجدٍ تشغيلياً")
            ProfitabilityLevel.POOR_MARGIN -> Triple("#D97706", "هامش ضعيف", "ارفع السعر")
            ProfitabilityLevel.LOSS_DANGER -> Triple("#DC2626", "غير مقبول", "مخاطرة خسارة")
        }

        // 1. Update Popup Hint Widget (Live on-screen hint)
        hintContainerLayout?.background = createCardBackground("#0B1120", themeColorHex, 2.5f, 22f)
        hintStatusTagTv?.text = tagLabel
        hintStatusTagTv?.background = createBadgeBackground(themeColorHex, 12f)

        val fairPriceValue = analysis.recommendedFare.toInt()
        val clientName = parsedRide?.passengerName ?: "طلب الرحلة"
        hintFairPriceTv?.text = "السعر العادل: $fairPriceValue ج.م ($clientName)"
        hintFairPriceTv?.setTextColor(Color.WHITE)

        val netProfitVal = String.format("%.1f", analysis.netProfit)
        val profitPerKmVal = String.format("%.1f", analysis.profitPerKm)
        hintSubtextTv?.text = "صافي الربح: $netProfitVal ج.م ($profitPerKmVal ج/كم) • العرض: ${analysis.offeredFare.toInt()} ج.م"

        // Update multi-orders row inside hint
        updateHintMultiOrdersRow()

        // 2. Update Expanded Sheet
        if (parsedRide != null) {
            val ratingStr = if (parsedRide.passengerRating.isNotEmpty()) " (${parsedRide.passengerRating})" else ""
            sheetClientHeaderTv?.text = "العميل: ${parsedRide.passengerName}$ratingStr"
            if (parsedRide.pickupAddress.isNotEmpty() || parsedRide.dropoffAddress.isNotEmpty()) {
                sheetAddressesTv?.visibility = View.VISIBLE
                sheetAddressesTv?.text = "الانطلاق: ${parsedRide.pickupAddress}\nالوجهة: ${parsedRide.dropoffAddress}"
            } else {
                sheetAddressesTv?.visibility = View.GONE
            }
        }

        sheetProfitTv?.setTextColor(Color.parseColor(themeColorHex))
        sheetProfitTv?.text = "صافي الربح الفعلي: $netProfitVal ج.م ($statusDesc) | العائد: $profitPerKmVal ج/كم"

        val totalCost = String.format("%.1f", analysis.fuelCost + analysis.wearAndTearCost + analysis.commissionCost)
        val fuelAndWear = String.format("%.1f", analysis.fuelCost + analysis.wearAndTearCost)
        val comm = String.format("%.1f", analysis.commissionCost)
        sheetCostTv?.text = "إجمالي التكلفة: $totalCost ج.م (وقود وصيانة: $fuelAndWear ج.م | عمولة: $comm ج.م)"

        val bidsInfo = if (parsedRide?.counterBids?.isNotEmpty() == true) {
            "خيارات المزايدة بالتطبيق: ${parsedRide.counterBids.joinToString("، ")} ج.م • السعر الموصى به: $fairPriceValue ج.م"
        } else {
            "الحد الأدنى: ${analysis.breakEvenFare.toInt()} ج.م | السعر العادل: $fairPriceValue ج.م | الذروة: ${analysis.aggressiveFare.toInt()} ج.م"
        }
        sheetBidsTv?.text = bidsInfo
    }

    private fun updateHintMultiOrdersRow() {
        val row = hintMultiOrdersRow ?: return
        row.removeAllViews()

        if (activeRidesList.size <= 1) {
            row.visibility = View.GONE
            return
        }

        row.visibility = View.VISIBLE
        for (i in activeRidesList.indices) {
            val r = activeRidesList[i]
            val isSelected = (i == selectedRideIndex)

            val tabTv = TextView(this).apply {
                text = "${r.passengerName}: ${r.fare.toInt()}ج"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(12, 4, 12, 4)
                if (isSelected) {
                    setTextColor(Color.parseColor("#0F172A"))
                    background = createBadgeBackground("#10B981", 10f)
                } else {
                    setTextColor(Color.parseColor("#94A3B8"))
                    background = createBadgeBackground("#1E293B", 10f)
                }
                setOnClickListener { selectRide(i) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 6, 0)
                }
            }
            row.addView(tabTv)
        }
    }

    private fun refreshSheetRidesTabs() {
        val container = sheetTabsContainer ?: return
        val scroll = sheetTabsScroll ?: return

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
                text = "${ride.passengerName} (${ride.fare.toInt()} ج.م)"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(16, 8, 16, 8)
                if (isSelected) {
                    setTextColor(Color.parseColor("#0F172A"))
                    background = createBadgeBackground("#10B981", 16f)
                } else {
                    setTextColor(Color.parseColor("#94A3B8"))
                    background = createBadgeBackground("#1E293B", 16f)
                }
                setOnClickListener { selectRide(i) }
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

        refreshSheetRidesTabs()
        displayAnalysisInUi(analysis, ride)
    }

    private fun handleNewRideParsed(ride: ParsedRideData) {
        serviceScope.launch(Dispatchers.Main) {
            val existingIdx = activeRidesList.indexOfFirst { it.fingerprint == ride.fingerprint }
            val isGenuinelyNew = (existingIdx == -1)

            if (existingIdx != -1) {
                activeRidesList[existingIdx] = ride
                selectedRideIndex = existingIdx
            } else {
                activeRidesList.add(0, ride)
                if (activeRidesList.size > 5) {
                    activeRidesList.removeAt(activeRidesList.size - 1)
                }
                selectedRideIndex = 0
            }

            selectRide(selectedRideIndex)

            if (isGenuinelyNew) {
                vibrateSingleShotOnce(ride)
            }
        }
    }

    private fun vibrateSingleShotOnce(ride: ParsedRideData) {
        if (isVibrationMuted) return

        val now = System.currentTimeMillis()
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
            Toast.makeText(this@FloatingHudService, "تم حفظ بيانات الرحلة بنجاح", Toast.LENGTH_SHORT).show()
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
