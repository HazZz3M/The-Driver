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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.indrive.assistant.InDriveAssistantApp
import com.indrive.assistant.MainActivity
import com.indrive.assistant.R
import com.indrive.assistant.data.model.Trip
import com.indrive.assistant.engine.FareCalculatorEngine
import com.indrive.assistant.engine.ProfitabilityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingHudService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isExpanded = false

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
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
            .setContentText("Floating quick fare analyzer is running over other apps")
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
            x = 50
            y = 200
        }

        // Create Container View
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Collapsed Bubble (Small draggable pill)
        val bubbleView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(28, 20, 28, 20)
            gravity = Gravity.CENTER_VERTICAL
            
            // Stylized background (inDrive Neon Green border & dark background)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                setStroke(4, Color.parseColor("#00EA80"))
                cornerRadius = 60f
            }

            val iconText = TextView(context).apply {
                text = "🚖"
                textSize = 18f
                setPadding(0, 0, 12, 0)
            }

            val label = TextView(context).apply {
                text = "inDrive HUD"
                setTextColor(Color.parseColor("#00EA80"))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            addView(iconText)
            addView(label)
        }

        // 2. Expanded Panel (Quick Fare Calculator directly over inDrive)
        val expandedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1120"))
                setStroke(3, Color.parseColor("#334155"))
                cornerRadius = 32f
            }

            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                
                val title = TextView(context).apply {
                    text = "⚡ Quick inDrive Bid Calculator"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val closeBtn = TextView(context).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 16f
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

            // Input Fields
            val pickupInput = createInput("Pickup Km (إلى الراكب):", "2.0")
            val tripInput = createInput("Trip Km (مسافة الرحلة):", "8.5")
            val fareInput = createInput("Offered Fare (عرض الراكب):", "75.0")

            val resultProfitText = TextView(context).apply {
                text = "Net Profit: --"
                setTextColor(Color.parseColor("#00EA80"))
                textSize = 15f
                setPadding(0, 16, 0, 8)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val suggestedBidsText = TextView(context).apply {
                text = "Recommended Counter: --"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 13f
                setPadding(0, 0, 0, 16)
            }

            // Calculate & Analyze Action
            fun recalculate() {
                val pKm = pickupInput.second.text.toString().toDoubleOrNull() ?: 0.0
                val tKm = tripInput.second.text.toString().toDoubleOrNull() ?: 0.0
                val fare = fareInput.second.text.toString().toDoubleOrNull() ?: 0.0

                val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
                val analysis = FareCalculatorEngine.analyze(
                    pickupDistanceKm = pKm,
                    tripDistanceKm = tKm,
                    estimatedMinutes = ((pKm + tKm) * 2.5).toInt(),
                    offeredFare = fare,
                    settings = settings
                )

                val profitColor = when (analysis.profitabilityLevel) {
                    ProfitabilityLevel.LOSS_DANGER -> Color.parseColor("#EF4444")
                    ProfitabilityLevel.POOR_MARGIN -> Color.parseColor("#F59E0B")
                    else -> Color.parseColor("#00EA80")
                }

                resultProfitText.setTextColor(profitColor)
                resultProfitText.text = "Net Profit: ${String.format("%.1f", analysis.netProfit)} ${settings.currency} (${analysis.statusMessage})"
                suggestedBidsText.text = "Floor: ${analysis.breakEvenFare.toInt()} | Recommended: ${analysis.recommendedFare.toInt()} | High: ${analysis.aggressiveFare.toInt()} ${settings.currency}"
            }

            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                val calcBtn = Button(context).apply {
                    text = "Calculate"
                    setBackgroundColor(Color.parseColor("#00EA80"))
                    setTextColor(Color.parseColor("#0F172A"))
                    setOnClickListener { recalculate() }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val acceptLogBtn = Button(context).apply {
                    text = "Save Ride"
                    setBackgroundColor(Color.parseColor("#1E293B"))
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        val pKm = pickupInput.second.text.toString().toDoubleOrNull() ?: 0.0
                        val tKm = tripInput.second.text.toString().toDoubleOrNull() ?: 0.0
                        val fare = fareInput.second.text.toString().toDoubleOrNull() ?: 0.0
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
                        }
                        // Collapse back
                        visibility = View.GONE
                        bubbleView.visibility = View.VISIBLE
                        isExpanded = false
                        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        windowManager?.updateViewLayout(container, params)
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                addView(calcBtn)
                addView(acceptLogBtn)
            }

            addView(headerRow)
            addView(pickupInput.first)
            addView(tripInput.first)
            addView(fareInput.first)
            addView(resultProfitText)
            addView(suggestedBidsText)
            addView(btnRow)
        }

        container.addView(bubbleView)
        container.addView(expandedCard)

        // Drag and touch handling for the floating HUD
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
                            // Expand the panel
                            bubbleView.visibility = View.GONE
                            expandedCard.visibility = View.VISIBLE
                            isExpanded = true
                            // Allow keyboard focus when expanded
                            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            windowManager?.updateViewLayout(container, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingView = container
        windowManager?.addView(container, params)
    }

    private fun createInput(label: String, defaultVal: String): Pair<LinearLayout, EditText> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
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
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        row.addView(tv)
        row.addView(et)
        return Pair(row, et)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
