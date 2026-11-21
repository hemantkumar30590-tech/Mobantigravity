package com.mobantigravity.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Lightweight foreground keep-alive service to prevent ColorOS / OEM battery savers
 * from killing the local IME bridge (127.0.0.1:8898) during active X11 sessions.
 */
class GemKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "mobantigravity_bridge_channel"
        private const val NOTIF_ID = 8898
        private const val ACTION_STOP = "com.mobantigravity.launcher.STOP_KEEPALIVE"

        fun start(context: Context) {
            val intent = Intent(context, GemKeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Ignore if background start is restricted; AccessibilityService remains active.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, GemKeepAliveService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        val openIntent = Intent(this, LauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val openPi = PendingIntent.getActivity(this, 0, openIntent, pendingFlags)

        val stopIntent = Intent(this, GemKeepAliveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, pendingFlags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("MobAntigravity Bridge Active")
            .setContentText("Keyboard bridge (127.0.0.1:8898) protected from background kill")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop Bridge",
                    stopPi
                ).build()
            )
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "MobAntigravity Session Bridge",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the local keyboard bridge alive during Antigravity X11 sessions"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
