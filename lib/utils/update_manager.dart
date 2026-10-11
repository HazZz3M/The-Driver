import 'dart:convert';
import 'package:http/http.dart' as http;

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
  static const String githubLatestReleaseUrl =
      "https://api.github.com/repos/HazZz3M/The-Driver/releases/latest";

  static Future<UpdateInfo?> checkForUpdate(String currentVersionName) async {
    try {
      final response = await http.get(
        Uri.parse(githubLatestReleaseUrl),
        headers: {
          'Accept': 'application/vnd.github.v3+json',
          'User-Agent': 'inDrive-Assistant-Flutter',
        },
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode != 200) return null;

      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final tagName = (json['tag_name'] as String? ?? "").trim();
      final releaseNotes = (json['body'] as String? ?? "تحديث تحسينات وتطويرات للأداء العام");
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

      final cleanCurrent = currentVersionName.replaceAll('v', '').trim();
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
}
