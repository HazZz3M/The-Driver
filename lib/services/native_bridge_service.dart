import 'package:flutter/services.dart';

class NativeBridgeService {
  static const MethodChannel _channel = MethodChannel('com.indrive.assistant/hud');

  /// Checks if SYSTEM_ALERT_WINDOW (display over other apps) permission is granted
  static Future<bool> checkOverlayPermission() async {
    try {
      final bool? result = await _channel.invokeMethod<bool>('checkOverlayPermission');
      return result ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Requests the user to allow overlay permission
  static Future<void> requestOverlayPermission() async {
    try {
      await _channel.invokeMethod('requestOverlayPermission');
    } catch (_) {}
  }

  /// Checks if inDrive Accessibility Service is enabled in Android settings
  static Future<bool> checkAccessibilityPermission() async {
    try {
      final bool? result = await _channel.invokeMethod<bool>('checkAccessibilityPermission');
      return result ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Opens Accessibility Settings screen on Android
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } catch (_) {}
  }

  /// Starts the FloatingHudService foreground service
  static Future<bool> startFloatingService() async {
    try {
      final bool? result = await _channel.invokeMethod<bool>('startFloatingService');
      return result ?? true;
    } catch (_) {
      return false;
    }
  }

  /// Stops the FloatingHudService
  static Future<bool> stopFloatingService() async {
    try {
      final bool? result = await _channel.invokeMethod<bool>('stopFloatingService');
      return result ?? true;
    } catch (_) {
      return false;
    }
  }

  /// Checks whether FloatingHudService is currently running in background
  static Future<bool> isFloatingServiceRunning() async {
    try {
      final bool? result = await _channel.invokeMethod<bool>('isFloatingServiceRunning');
      return result ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Triggers the native Android package installer for a downloaded APK file
  static Future<bool> installApk(String filePath) async {
    try {
      final bool? result = await _channel.invokeMethod<bool>('installApk', {'filePath': filePath});
      return result ?? false;
    } catch (_) {
      return false;
    }
  }
}
