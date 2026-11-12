package com.indrive.assistant

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.indrive.assistant.service.FloatingHudService
import com.indrive.assistant.service.InDriveAccessibilityService
import com.indrive.assistant.service.RequestBoardView

/**
 * The requests board as a screen of its own: next to inDrive in split screen ([EXTRA_SPLIT]),
 * where it updates live and its buttons act on inDrive, or full screen (the requests read while
 * inDrive was open).
 */
class LiveMapActivity : Activity() {

    private lateinit var board: RequestBoardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        board = RequestBoardView(this, compact = false)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        fun button(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4F46E5"))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B0F17"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(TextView(context).apply {
                text = "لوحة الطلبات"
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(button("إن درايف") { openInDrive(adjacent = false) })
            addView(TextView(context), LinearLayout.LayoutParams(dp(6), 1))
            addView(button("تقسيم الشاشة") { openInDrive(adjacent = true) })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        if (!InDriveAccessibilityService.isRunning()) {
            Toast.makeText(this, "قراءة الشاشة متوقفة: الطلبات مش هتظهر هنا", Toast.LENGTH_LONG).show()
        }
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_SPLIT, false)) openInDrive(adjacent = true)
    }

    // Visible from onStart to onStop: in split screen the board keeps updating (and the floating
    // board stays hidden) while the driver is tapping in inDrive.
    override fun onStart() {
        super.onStart()
        isShowing = true
        board.start()
        FloatingHudService.refreshFloatingMap()
    }

    override fun onStop() {
        super.onStop()
        isShowing = false
        board.stop()
        FloatingHudService.refreshFloatingMap()
    }

    override fun onDestroy() {
        board.destroy()
        super.onDestroy()
    }

    /** Opens inDrive; [adjacent] asks Android to put it next to the board (split screen). */
    private fun openInDrive(adjacent: Boolean) {
        val launch = inDriveLaunchIntent(this)
        if (launch == null) {
            Toast.makeText(this, "إن درايف مش متثبت على الموبايل", Toast.LENGTH_SHORT).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (adjacent) launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            startActivity(launch)
            if (adjacent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInMultiWindowMode) {
                window.decorView.postDelayed({
                    if (!isInMultiWindowMode) {
                        Toast.makeText(
                            this,
                            "لو الشاشة متقسمتش: افتح التطبيقات الأخيرة، اضغط على أيقونة The Driver واختار «تقسيم الشاشة»، وبعدين اختار إن درايف",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }, 1500)
            }
        } catch (e: Exception) {
            Log.w("LiveMapActivity", "Could not open inDrive", e)
            Toast.makeText(this, "تعذر فتح إن درايف", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_SPLIT = "split"

        /** True while the board screen is in front (the floating board steps aside then). */
        @Volatile
        var isShowing = false
            private set

        fun inDriveLaunchIntent(activity: Activity): Intent? {
            val pm = activity.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val pkg = try {
                pm.queryIntentActivities(launcher, 0)
                    .map { it.activityInfo.packageName }
                    .firstOrNull { InDriveAccessibilityService.isTargetPackage(it, activity.packageName) }
            } catch (e: Exception) {
                null
            } ?: return null
            return pm.getLaunchIntentForPackage(pkg)
        }
    }
}
