package de.stefan.nasbackup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NasBackupApp : Application() {

    companion object {
        const val CHANNEL_ID = "sync"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backup",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Zeigt den Fortschritt beim Hochladen auf den NAS"
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
