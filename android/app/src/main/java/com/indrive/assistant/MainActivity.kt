package com.indrive.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.indrive.assistant.data.local.AutomationLog
import com.indrive.assistant.data.local.RequestLog
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
                "pauseScreenReading" -> {
                    // Switches the accessibility service off (e.g. before opening a banking app).
                    result.success(InDriveAccessibilityService.pause())
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
                "testAlert" -> result.success(
                    FloatingHudService.testAlert(
                        call.argument<String>("level") ?: "GOOD",
                        call.argument<Boolean>("voice") ?: false
                    )
                )
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
                "requestScreenCapture" -> {
                    InDriveAccessibilityService.captureRequested = true
                    result.success(true)
                }
                "getLastScreenDump" -> {
                    result.success(InDriveAccessibilityService.readLastDump(this))
                }
                "getAppVersion" -> {
                    val info = packageManager.getPackageInfo(packageName, 0)
                    result.success(info.versionName ?: "")
                }
                // ---- Automation master switch & activity log ----
                "getAutomationPaused" -> result.success(AutomationLog.isPaused(this))
                "setAutomationPaused" -> {
                    AutomationLog.setPaused(this, call.argument<Boolean>("paused") ?: false)
                    FloatingHudService.onScreenReadingChanged() // refresh the notification's switch
                    result.success(true)
                }
                "getAutomationLog" -> result.success(AutomationLog.asJson(this))
                "clearAutomationLog" -> {
                    AutomationLog.clear(this)
                    result.success(true)
                }
                // ---- Request log (analysis & recommendations) ----
                "getRequestLog" -> result.success(RequestLog.asJson(this))
                "rateRequest" -> {
                    val id = call.argument<Number>("id")?.toLong()
                    val stars = call.argument<Int>("stars")
                    if (id == null || stars == null) {
                        result.error("INVALID_ARGUMENT", "id and stars are required", null)
                    } else {
                        val tags = call.argument<List<String>>("tags") ?: emptyList()
                        result.success(RequestLog.rate(this, id, stars, tags, call.argument<String>("note") ?: ""))
                    }
                }
                "clearRequestLog" -> {
                    RequestLog.clear(this)
                    result.success(true)
                }
                "exportRequestLog" -> {
                    try {
                        result.success(RequestLog.export(this, call.argument<Boolean>("csv") ?: true).absolutePath)
                    } catch (e: Exception) {
                        result.error("EXPORT_ERROR", e.message, null)
                    }
                }
                "shareFile" -> {
                    val path = call.argument<String>("path")
                    if (path == null) {
                        result.error("INVALID_ARGUMENT", "path is required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", java.io.File(path))
                        val send = Intent(Intent.ACTION_SEND)
                            .setType(call.argument<String>("mime") ?: "text/csv")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(Intent.EXTRA_SUBJECT, call.argument<String>("title") ?: "")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(Intent.createChooser(send, call.argument<String>("title") ?: "مشاركة"))
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("SHARE_ERROR", e.message, null)
                    }
                }
                "canInstallPackages" -> {
                    result.success(Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls())
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
