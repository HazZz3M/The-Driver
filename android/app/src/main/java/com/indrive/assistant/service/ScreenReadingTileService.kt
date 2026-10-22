package com.indrive.assistant.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

/**
 * Quick Settings tile for the screen-reading (accessibility) service.
 *
 * On: tapping it switches the service off ([InDriveAccessibilityService.pause]), so apps that
 * refuse to run next to an accessibility service (banking apps such as InstaPay) can be opened.
 * Off: Android only lets the driver switch an accessibility service on, so tapping it opens
 * the accessibility settings page.
 */
class ScreenReadingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (InDriveAccessibilityService.pause()) {
            Toast.makeText(this, "تم إيقاف قراءة الشاشة. يمكنك فتح تطبيقات البنوك الآن", Toast.LENGTH_LONG).show()
            refresh(active = false)
        } else {
            openAccessibilitySettings()
        }
    }

    private fun refresh(active: Boolean = InDriveAccessibilityService.isRunning()) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "قراءة الشاشة"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "تعمل • اضغط للإيقاف" else "متوقفة • اضغط للتشغيل"
        }
        tile.updateTile()
    }

    private fun openAccessibilitySettings() {
        val intent = accessibilitySettingsIntent()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ only accepts a PendingIntent here for apps targeting 34+.
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open accessibility settings", e)
        }
    }

    companion object {
        private const val TAG = "ScreenReadingTile"

        /** Android's accessibility settings, where the driver switches the service back on. */
        fun accessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens the accessibility settings from a non-activity context (notification, service). */
        fun settingsPendingIntent(context: Context, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(context, requestCode, accessibilitySettingsIntent(), PendingIntent.FLAG_IMMUTABLE)
    }
}
