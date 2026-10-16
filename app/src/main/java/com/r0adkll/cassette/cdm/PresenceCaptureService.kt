package com.r0adkll.cassette.cdm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.r0adkll.cassette.CassetteApp
import com.r0adkll.cassette.ridetest.PresenceReadPath
import com.r0adkll.cassette.ridetest.RideEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CDM spike: a short `connectedDevice` foreground service for the hub read after a presence "appeared", for
 * comparison with reading straight from the bound [HubPresenceService]. Stops itself once its reads finish.
 */
class PresenceCaptureService : Service() {
  private val app
    get() = application as CassetteApp

  private var running = 0

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val started = runCatching { startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }
    val associationId = intent?.getIntExtra(EXTRA_ASSOCIATION_ID, -1) ?: -1
    val address = intent?.getStringExtra(EXTRA_ADDRESS)
    val appearedAt = intent?.getLongExtra(EXTRA_APPEARED_AT, 0L) ?: 0L
    if (started.isFailure || address == null) {
      app.scope.launch {
        app.rideLog.append(RideEvent.Cdm(System.currentTimeMillis(), "fgs_failed", associationId, started.exceptionOrNull()?.toString() ?: "no address"))
      }
      if (running == 0) stopSelf()
      return START_NOT_STICKY
    }
    running++
    app.scope.launch {
      try {
        app.readHubFromPresence(associationId, address, appearedAt, PresenceReadPath.FOREGROUND_SERVICE)
      } finally {
        withContext(Dispatchers.Main) {
          running--
          if (running == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
          }
        }
      }
    }
    return START_NOT_STICKY
  }

  private fun notification(): Notification {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Capture", NotificationManager.IMPORTANCE_MIN))
    return Notification.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
      .setContentTitle("Reading bike batteries")
      .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }

  companion object {
    /** In the notification ledger in CLAUDE.md. */
    const val NOTIFICATION_ID = 1
    const val CHANNEL_ID = "capture"
    private const val EXTRA_ASSOCIATION_ID = "association_id"
    private const val EXTRA_ADDRESS = "address"
    private const val EXTRA_APPEARED_AT = "appeared_at"

    /** Throws if the system refuses a foreground service start from the background. */
    fun start(context: Context, associationId: Int, address: String, appearedAt: Long) {
      context.startForegroundService(
        Intent(context, PresenceCaptureService::class.java)
          .putExtra(EXTRA_ASSOCIATION_ID, associationId)
          .putExtra(EXTRA_ADDRESS, address)
          .putExtra(EXTRA_APPEARED_AT, appearedAt)
      )
    }
  }
}
