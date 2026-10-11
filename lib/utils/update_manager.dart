import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

class UpdateInfo {
  final bool isAvailable;
  final String versionName;
  final String downloadUrl;
  final String releaseNotes;
  final String publishedAt;

  const UpdateInfo({
    required this.isAvailable,
    required this.versionName,
    required this.downloadUrl,
    required this.releaseNotes,
    this.publishedAt = "",
  });
}

class UpdateManager {
  static const String currentAppVersion = "v1.9.0";

  static const String githubLatestReleaseUrl =
      "https://api.github.com/repos/HazZz3M/The-Driver/releases/latest";

  static Future<UpdateInfo?> checkForUpdate([String? currentVersion]) async {
    final ver = currentVersion ?? currentAppVersion;
    try {
      final response = await http.get(
        Uri.parse(githubLatestReleaseUrl),
        headers: {
          'Accept': 'application/vnd.github.v3+json',
          'User-Agent': 'The-Driver-Android-App',
        },
      ).timeout(const Duration(seconds: 12));

      if (response.statusCode != 200) return null;

      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final tagName = (json['tag_name'] as String? ?? "").trim();
      final releaseNotes = (json['body'] as String? ?? "تحديث وتحسينات شاملة للأداء وقراءة الرحلات");
      final publishedAt = (json['published_at'] as String? ?? "");

      String downloadUrl = "";
      final assets = json['assets'] as List<dynamic>?;
      if (assets != null) {
        for (final asset in assets) {
          final name = (asset['name'] as String? ?? "");
          if (name.toLowerCase().endsWith(".apk")) {
            downloadUrl = asset['browser_download_url'] as String? ?? "";
            break;
          }
        }
      }

      final cleanCurrent = ver.replaceAll('v', '').trim();
      final cleanRemote = tagName.replaceAll('v', '').trim();
      final isAvailable = cleanRemote.isNotEmpty &&
          cleanRemote != cleanCurrent &&
          downloadUrl.isNotEmpty;

      return UpdateInfo(
        isAvailable: isAvailable,
        versionName: tagName,
        downloadUrl: downloadUrl,
        releaseNotes: releaseNotes,
        publishedAt: publishedAt,
      );
    } catch (_) {
      return null;
    }
  }

  /// Downloads the APK file to temporary cache with progress reporting
  static Future<File?> downloadApk(
    String downloadUrl, {
    required void Function(double progress, int receivedBytes, int totalBytes) onProgress,
  }) async {
    try {
      final client = http.Client();
      final request = http.Request('GET', Uri.parse(downloadUrl));
      request.headers['User-Agent'] = 'The-Driver-Android-App';

      final response = await client.send(request);
      if (response.statusCode != 200) {
        return null;
      }

      final totalBytes = response.contentLength ?? 0;
      final tempDir = await getTemporaryDirectory();
      final targetFile = File('${tempDir.path}/update.apk');
      if (await targetFile.exists()) {
        await targetFile.delete();
      }

      final sink = targetFile.openWrite();
      int receivedBytes = 0;

      await for (final chunk in response.stream) {
        sink.add(chunk);
        receivedBytes += chunk.length;
        if (totalBytes > 0) {
          final progress = receivedBytes / totalBytes;
          onProgress(progress, receivedBytes, totalBytes);
        }
      }

      await sink.flush();
      await sink.close();
      client.close();

      return targetFile;
    } catch (_) {
      return null;
    }
  }
}
