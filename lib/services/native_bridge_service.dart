import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Outcome of handing a downloaded update to the Android installer.
enum InstallApkResult { started, needsPermission, checksumMismatch, fileNotFound, error }

/// Zone lists held by the native HUD, plus how Flutter should merge them
/// ("replace", "union" or "none"; see PreferencesManager.zonesAsJson in Kotlin).
class NativeZones {
  final String mode;
  final List<String> favoriteZones;
  final List<String> blacklistedZones;
  /// Passenger lists edited from the HUD (same "mode"); null from builds before 1.15.
  final List<String>? favoritePassengers;
  final List<String>? blacklistedPassengers;

  const NativeZones(this.mode, this.favoriteZones, this.blacklistedZones,
      [this.favoritePassengers, this.blacklistedPassengers]);
}

class NativeBridgeService {
  static const MethodChannel _channel = MethodChannel('com.indrive.assistant/hud');

  static Future<bool> checkOverlayPermission() async {
    try {
      return await _channel.invokeMethod<bool>('checkOverlayPermission') ?? false;
    } catch (e) {
      debugPrint("checkOverlayPermission failed: $e");
      return false;
    }
  }

  static Future<void> requestOverlayPermission() async {
    try {
      await _channel.invokeMethod('requestOverlayPermission');
    } catch (e) {
      debugPrint("requestOverlayPermission failed: $e");
    }
  }

  static Future<bool> checkAccessibilityPermission() async {
    try {
      return await _channel.invokeMethod<bool>('checkAccessibilityPermission') ?? false;
    } catch (e) {
      debugPrint("checkAccessibilityPermission failed: $e");
      return false;
    }
  }

  /// Switches the screen-reading service off. Only the driver can switch it back on (Settings).
  /// Returns false if it was not running or the call failed.
  static Future<bool> pauseScreenReading() async {
    try {
      return await _channel.invokeMethod<bool>('pauseScreenReading') ?? false;
    } catch (e) {
      debugPrint("pauseScreenReading failed: $e");
      return false;
    }
  }

  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } catch (e) {
      debugPrint("openAccessibilitySettings failed: $e");
    }
  }

  /// Returns null on success, or an Arabic error message to show the driver.
  static Future<String?> startFloatingService() async {
    try {
      await _channel.invokeMethod<bool>('startFloatingService');
      return null;
    } on PlatformException catch (e) {
      if (e.code == "NO_OVERLAY_PERMISSION") {
        return "يجب منح صلاحية العرض فوق التطبيقات أولاً";
      }
      return "تعذر تشغيل المساعد العائم: ${e.message ?? e.code}";
    } catch (e) {
      return "تعذر تشغيل المساعد العائم";
    }
  }

  static Future<bool> stopFloatingService() async {
    try {
      return await _channel.invokeMethod<bool>('stopFloatingService') ?? true;
    } catch (e) {
      debugPrint("stopFloatingService failed: $e");
      return false;
    }
  }

  static Future<bool> isFloatingServiceRunning() async {
    try {
      return await _channel.invokeMethod<bool>('isFloatingServiceRunning') ?? false;
    } catch (e) {
      debugPrint("isFloatingServiceRunning failed: $e");
      return false;
    }
  }

  /// Pushes the full settings snapshot to the native HUD / accessibility layer.
  static Future<void> syncSettings(Map<String, dynamic> snapshot) async {
    try {
      await _channel.invokeMethod('syncSettings', {'json': jsonEncode(snapshot)});
    } catch (e) {
      debugPrint("syncSettings failed: $e");
    }
  }

  /// Zones as edited from the HUD. Null when the native side is unavailable.
  static Future<NativeZones?> getNativeZones() async {
    try {
      final raw = await _channel.invokeMethod<String>('getNativeZones');
      if (raw == null) return null;
      final json = jsonDecode(raw) as Map<String, dynamic>;
      List<String> list(String k) => ((json[k] as List?) ?? const []).map((e) => e.toString()).toList();
      List<String>? optList(String k) => json.containsKey(k) ? list(k) : null;
      return NativeZones(json['mode'] as String? ?? "none", list('favoriteZones'), list('blacklistedZones'),
          optList('favoritePassengers'), optList('blacklistedPassengers'));
    } catch (e) {
      debugPrint("getNativeZones failed: $e");
      return null;
    }
  }

  /// Arms a one-shot diagnostic capture of the next inDrive screen the service reads.
  static Future<bool> requestScreenCapture() async {
    try {
      return await _channel.invokeMethod<bool>('requestScreenCapture') ?? false;
    } catch (e) {
      debugPrint("requestScreenCapture failed: $e");
      return false;
    }
  }

  /// The last diagnostic capture (node tree + parsed cards), or null if none was taken yet.
  static Future<String?> getLastScreenDump() async {
    try {
      return await _channel.invokeMethod<String>('getLastScreenDump');
    } catch (e) {
      debugPrint("getLastScreenDump failed: $e");
      return null;
    }
  }

  /// The installed app's versionName (from the APK, not a hard-coded constant).
  static Future<String?> getAppVersion() async {
    try {
      return await _channel.invokeMethod<String>('getAppVersion');
    } catch (e) {
      debugPrint("getAppVersion failed: $e");
      return null;
    }
  }

  /// Whether Android currently allows this app to install updates ("install unknown apps").
  static Future<bool> canInstallPackages() async {
    try {
      return await _channel.invokeMethod<bool>('canInstallPackages') ?? false;
    } catch (e) {
      debugPrint("canInstallPackages failed: $e");
      return false;
    }
  }

  /// Verifies [expectedSha256] (when given) and starts the system installer.
  static Future<InstallApkResult> installApk(String filePath, {String? expectedSha256}) async {
    try {
      final status = await _channel.invokeMethod<String>(
        'installApk',
        {'filePath': filePath, 'sha256': expectedSha256},
      );
      switch (status) {
        case 'STARTED':
          return InstallApkResult.started;
        case 'NEEDS_PERMISSION':
          return InstallApkResult.needsPermission;
        case 'CHECKSUM_MISMATCH':
          return InstallApkResult.checksumMismatch;
        case 'FILE_NOT_FOUND':
          return InstallApkResult.fileNotFound;
        default:
          return InstallApkResult.error;
      }
    } catch (e) {
      debugPrint("installApk failed: $e");
      return InstallApkResult.error;
    }
  }
}
