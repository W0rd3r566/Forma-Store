package dev.formastore.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

internal class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads", "Загрузки APK", NotificationManager.IMPORTANCE_LOW))
        startForeground(31, notification(this, 0))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    companion object {
        private fun notification(context: Context, percent: Int): android.app.Notification {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationCompat.Builder(context, "downloads").setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Forma Store").setContentText("Загрузка APK · $percent%")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(100, percent, percent == 0).build()
        }
        fun progress(context: Context, percent: Int) {
            try { context.getSystemService(NotificationManager::class.java).notify(31, notification(context, percent)) } catch (_: SecurityException) { }
        }
    }
}
