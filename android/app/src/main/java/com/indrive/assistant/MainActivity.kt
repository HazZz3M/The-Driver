package com.indrive.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.indrive.assistant.service.FloatingHudService
import com.indrive.assistant.service.InDriveAccessibilityService
import com.indrive.assistant.util.UpdateManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.indrive.assistant/hud"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkOverlayPermission" -> result.success(canDrawOverlays())
                "requestOverlayPermission" -> {
                    if (!canDrawOverlays()) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    }
                    result.success(true)
                }
                "checkAccessibilityPermission" -> {
                    result.success(InDriveAccessibilityService.isAccessibilityEnabled(this))
                }
                "openAccessibilitySettings" -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    result.success(true)
                }
                "startFloatingService" -> {
                    if (!canDrawOverlays()) {
                        result.error("NO_OVERLAY_PERMISSION", "Display-over-other-apps permission is required", null)
                        return@setMethodCallHandler
                    }
                    requestNotificationPermissionIfNeeded()
                    val intent = Intent(this, FloatingHudService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    result.success(true)
                }
                "stopFloatingService" -> {
                    stopService(Intent(this, FloatingHudService::class.java))
                    result.success(true)
                }
                "isFloatingServiceRunning" -> result.success(FloatingHudService.isRunning())
                "syncSettings" -> {
                    val json = call.argument<String>("json")
                    if (json == null) {
                        result.error("INVALID_ARGUMENT", "json is required", null)
                    } else {
                        try {
                            InDriveAssistantApp.instance.repository.applySettingsFromFlutter(JSONObject(json))
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("SYNC_ERROR", e.message, null)
                        }
                    }
                }
                "getNativeZones" -> {
                    result.success(InDriveAssistantApp.instance.repository.zonesAsJson().toString())
                }
                "getAppVersion" -> {
                    val info = packageManager.getPackageInfo(packageName, 0)
                    result.success(info.versionName ?: "")
                }
                "installApk" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath == null) {
                        result.error("INVALID_ARGUMENT", "filePath is required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        val status = UpdateManager.installApk(this, java.io.File(filePath), call.argument<String>("sha256"))
                        result.success(status.name)
                    } catch (e: Exception) {
                        result.error("INSTALL_ERROR", e.message, null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 4001
    }
}
