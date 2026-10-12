import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import '../services/native_bridge_service.dart';

class UpdateInfo {
  final bool isAvailable;
  final String currentVersion;
  final String versionName;
  final String downloadUrl;
  final String? sha256Url;
  final String releaseNotes;
  final String publishedAt;

  const UpdateInfo({
    required this.isAvailable,
    required this.currentVersion,
    required this.versionName,
    required this.downloadUrl,
    required this.sha256Url,
    required this.releaseNotes,
    this.publishedAt = "",
  });
}

/// Result of an update check: either [info] or a human-readable [error].
class UpdateCheckResult {
  final UpdateInfo? info;
  final String? error;

  const UpdateCheckResult.success(this.info) : error = null;
  const UpdateCheckResult.failure(this.error) : info = null;
}

class UpdateManager {
  static const String githubLatestReleaseUrl =
      "https://api.github.com/repos/HazZz3M/The-Driver/releases/latest";

  /// Parses "v1.10.0", "1.10.0+12" or "1.10" into comparable numeric parts.
  static List<int> parseVersion(String raw) {
    var s = raw.trim();
    if (s.startsWith('v') || s.startsWith('V')) s = s.substring(1);
    s = s.split('+').first.split('-').first;
    return s.split('.').map((p) => int.tryParse(p) ?? 0).toList();
  }

  /// Negative if [a] < [b], zero if equal, positive if [a] > [b].
  static int compareVersions(String a, String b) {
    final pa = parseVersion(a);
    final pb = parseVersion(b);
    final len = pa.length > pb.length ? pa.length : pb.length;
    for (var i = 0; i < len; i++) {
      final x = i < pa.length ? pa[i] : 0;
      final y = i < pb.length ? pb[i] : 0;
      if (x != y) return x.compareTo(y);
    }
    return 0;
  }

  static Future<String> currentVersion() async => await NativeBridgeService.getAppVersion() ?? "0.0.0";

  static Future<UpdateCheckResult> checkForUpdate() async {
    final current = await currentVersion();
    try {
      final response = await http.get(
        Uri.parse(githubLatestReleaseUrl),
        headers: {
          'Accept': 'application/vnd.github.v3+json',
          'User-Agent': 'The-Driver-Android-App',
        },
      ).timeout(const Duration(seconds: 12));

      if (response.statusCode == 403) {
        return const UpdateCheckResult.failure("تم تجاوز حد الطلبات لدى GitHub، حاول بعد قليل");
      }
      if (response.statusCode != 200) {
        return UpdateCheckResult.failure("تعذر الوصول لخادم التحديثات (${response.statusCode})");
      }

      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final tagName = (json['tag_name'] as String? ?? "").trim();
      final releaseNotes = json['body'] as String? ?? "";
      final publishedAt = json['published_at'] as String? ?? "";

      String downloadUrl = "";
      String? shaUrl;
      for (final asset in (json['assets'] as List<dynamic>? ?? const [])) {
        final name = (asset['name'] as String? ?? "").toLowerCase();
        final url = asset['browser_download_url'] as String? ?? "";
        if (name.endsWith(".apk") && downloadUrl.isEmpty) downloadUrl = url;
        if (name.endsWith(".apk.sha256")) shaUrl = url;
      }

      final isAvailable = tagName.isNotEmpty && downloadUrl.isNotEmpty && compareVersions(tagName, current) > 0;

      return UpdateCheckResult.success(UpdateInfo(
        isAvailable: isAvailable,
        currentVersion: current,
        versionName: tagName,
        downloadUrl: downloadUrl,
        sha256Url: shaUrl,
        releaseNotes: releaseNotes,
        publishedAt: publishedAt,
      ));
    } on SocketException {
      return const UpdateCheckResult.failure("لا يوجد اتصال بالإنترنت");
    } catch (_) {
      return const UpdateCheckResult.failure("تعذر فحص التحديثات، حاول مرة أخرى");
    }
  }

  /// Fetches the published SHA-256 for the APK (null if the release has none).
  static Future<String?> fetchExpectedSha256(String? shaUrl) async {
    if (shaUrl == null) return null;
    try {
      final res = await http.get(Uri.parse(shaUrl)).timeout(const Duration(seconds: 12));
      if (res.statusCode != 200) return null;
      final hash = res.body.trim().split(RegExp(r'\s+')).first.toLowerCase();
      return RegExp(r'^[0-9a-f]{64}$').hasMatch(hash) ? hash : null;
    } catch (_) {
      return null;
    }
  }

  /// Downloads the APK to the app cache with progress reporting.
  static Future<File?> downloadApk(
    String downloadUrl, {
    required void Function(double progress) onProgress,
  }) async {
    final client = http.Client();
    IOSink? sink;
    try {
      final request = http.Request('GET', Uri.parse(downloadUrl));
      request.headers['User-Agent'] = 'The-Driver-Android-App';

      final response = await client.send(request);
      if (response.statusCode != 200) return null;

      final totalBytes = response.contentLength ?? 0;
      final tempDir = await getTemporaryDirectory();
      final targetFile = File('${tempDir.path}/update.apk');
      if (await targetFile.exists()) await targetFile.delete();

      sink = targetFile.openWrite();
      var receivedBytes = 0;
      await for (final chunk in response.stream) {
        sink.add(chunk);
        receivedBytes += chunk.length;
        if (totalBytes > 0) onProgress(receivedBytes / totalBytes);
      }
      await sink.flush();
      await sink.close();
      sink = null;

      if (totalBytes > 0 && receivedBytes != totalBytes) return null;
      return targetFile;
    } catch (_) {
      return null;
    } finally {
      await sink?.close();
      client.close();
    }
  }
}
