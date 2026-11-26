package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Lightweight dataSync Foreground Service keeping continuous 15-minute camera recording active
 * without interruption.
 */
class CameraRecordingService : Service() {

  companion object {
    const val EXTRA_ACTIVE_CAMERA_COUNT = "active_camera_count"
    private const val CHANNEL_ID = "campanel_recording_channel"
    private const val NOTIFICATION_ID = 4401
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_CAMERA_COUNT, 1) ?: 1
    if (activeCount <= 0) {
      stopSelf()
      return START_NOT_STICKY
    }

    val notification = buildNotification(activeCount)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    return START_STICKY
  }

  private fun buildNotification(activeCount: Int): Notification {
    val openAppIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingFlags =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags)

    val text =
      if (activeCount == 1) "Nahrávání videa (1 kamera · MP4)"
      else "Nahrávání videa ($activeCount kamery · MP4)"

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("CamPanel")
      .setContentText(text)
      .setOngoing(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(pendingIntent)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(NotificationManager::class.java) ?: return
      val existing = mgr.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            "Kontinuální nahrávání kamer",
            NotificationManager.IMPORTANCE_LOW,
          ).apply {
            description = "Průběžný záznam vybraných kamer do úsporných 15min MP4 souborů."
            setShowBadge(false)
          }
        mgr.createNotificationChannel(channel)
      }
    }
  }
}
