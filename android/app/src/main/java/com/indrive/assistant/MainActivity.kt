package com.indrive.assistant

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import com.indrive.assistant.service.FloatingHudService
import com.indrive.assistant.service.InDriveAccessibilityService

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.indrive.assistant/hud"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkOverlayPermission" -> {
                    result.success(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true)
                }
                "requestOverlayPermission" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                    result.success(true)
                }
                "checkAccessibilityPermission" -> {
                    result.success(InDriveAccessibilityService.isAccessibilityEnabled(this))
                }
                "openAccessibilitySettings" -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    result.success(true)
                }
                "startFloatingService" -> {
                    val intent = Intent(this, FloatingHudService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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
                "isFloatingServiceRunning" -> {
                    result.success(FloatingHudService.isRunning())
                }
                "installApk" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath != null) {
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            try {
                                com.indrive.assistant.util.UpdateManager.installApk(this, file)
                                result.success(true)
                            } catch (e: Exception) {
                                result.error("INSTALL_ERROR", e.message, null)
                            }
                        } else {
                            result.error("FILE_NOT_FOUND", "File not found: $filePath", null)
                        }
                    } else {
                        result.error("INVALID_ARGUMENT", "filePath is required", null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }
}
