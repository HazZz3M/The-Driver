package com.indrive.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.indrive.assistant.service.FloatingHudService
import com.indrive.assistant.service.InDriveAccessibilityService
import com.indrive.assistant.service.LiveMapView

/**
 * The live requests map as a screen of its own: full screen (requests read while inDrive was
 * open), or next to inDrive in split screen ([EXTRA_SPLIT]), where it updates live and its
 * buttons act on inDrive.
 */
class LiveMapActivity : Activity() {

    private lateinit var mapView: LiveMapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapView = LiveMapView(this, compact = false)
        val root = FrameLayout(this)
        root.addView(mapView)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        fun button(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E64F46E5"))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(48), dp(8), 0)
            addView(button("فتح إن درايف") { openInDrive(adjacent = false) })
            addView(TextView(context), LinearLayout.LayoutParams(dp(6), 1))
            addView(button("تقسيم الشاشة") { openInDrive(adjacent = true) })
        }
        root.addView(actions, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START
        ))
        setContentView(root)

        askForLocation()
        if (!InDriveAccessibilityService.isRunning()) {
            Toast.makeText(this, "قراءة الشاشة متوقفة: الطلبات مش هتظهر على الخريطة", Toast.LENGTH_LONG).show()
        }
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_SPLIT, false)) openInDrive(adjacent = true)
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
        mapView.start()
        FloatingHudService.refreshFloatingMap()
    }

    override fun onPause() {
        super.onPause()
        isShowing = false
        mapView.stop()
        FloatingHudService.refreshFloatingMap()
    }

    override fun onDestroy() {
        mapView.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            mapView.stop()
            mapView.start() // picks up the permission
        }
    }

    private fun askForLocation() {
        if (!InDriveAssistantApp.instance.repository.getHudPrefs().liveMapShowMe) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION
            )
        }
    }

    /** Opens inDrive; [adjacent] asks Android to put it next to the map (split screen). */
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
        private const val REQUEST_LOCATION = 41

        /** True while the map screen is in front (the floating map steps aside then). */
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
