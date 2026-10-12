package com.indrive.assistant.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** Hands a downloaded update APK to the system installer. Downloading happens in Dart. */
object UpdateManager {

    enum class InstallResult { STARTED, NEEDS_PERMISSION, CHECKSUM_MISMATCH, FILE_NOT_FOUND }

    fun installApk(context: Context, apkFile: File, expectedSha256: String?): InstallResult {
        if (!apkFile.exists()) return InstallResult.FILE_NOT_FOUND

        if (!expectedSha256.isNullOrBlank()) {
            val actual = sha256(apkFile)
            if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                apkFile.delete()
                return InstallResult.CHECKSUM_MISMATCH
            }
        }

        // On Android 8.0+, the user must allow this app to install packages.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallResult.NEEDS_PERMISSION
        }

        val apkUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
        return InstallResult.STARTED
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
