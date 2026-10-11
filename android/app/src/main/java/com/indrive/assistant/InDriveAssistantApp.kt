package com.indrive.assistant

import io.flutter.app.FlutterApplication
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.indrive.assistant.data.local.PreferencesManager
import com.indrive.assistant.data.repository.AssistantRepository

class InDriveAssistantApp : FlutterApplication() {

    lateinit var repository: AssistantRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val preferencesManager = PreferencesManager(this)
        repository = AssistantRepository(preferencesManager)

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "inDrive Assistant HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification for driver floating HUD overlay"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "indrive_hud_service_channel"
        lateinit var instance: InDriveAssistantApp
            private set
    }
}
