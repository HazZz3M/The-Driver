package com.indrive.assistant.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isAvailable: Boolean,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val publishedAt: String = ""
)

object UpdateManager {

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/HazZz3M/The-Driver/releases/latest"

    suspend fun checkForUpdate(currentVersionName: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "inDrive-Assistant-App")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("استجابة الخادم: ${connection.responseCode} ${connection.responseMessage}")
                )
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "").trim()
            val releaseNotes = json.optString("body", "تحديث تحسينات وتطويرات للأداء العام")
            val publishedAt = json.optString("published_at", "")

            var downloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val cleanCurrent = currentVersionName.removePrefix("v").trim()
            val cleanRemote = tagName.removePrefix("v").trim()
            val isAvailable = cleanRemote.isNotEmpty() && cleanRemote != cleanCurrent && downloadUrl.isNotEmpty()

            Result.success(
                UpdateInfo(
                    isAvailable = isAvailable,
                    versionName = tagName,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    publishedAt = publishedAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destinationFile = File(context.cacheDir, "inDrive-Assistant-Update.apk")
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "inDrive-Assistant-App")
            }

            // Handle redirect if needed
            val responseCode = connection.responseCode
            val finalConnection = if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 307 || responseCode == 308) {
                val newUrl = connection.getHeaderField("Location")
                (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "inDrive-Assistant-App")
                }
            } else connection

            val totalBytes = finalConnection.contentLength
            val inputStream = finalConnection.inputStream
            val outputStream = FileOutputStream(destinationFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalBytes > 0) {
                    val progress = ((totalRead * 100) / totalBytes).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+, check if app has permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val manageIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(manageIntent)
                return
            }
        }

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}
