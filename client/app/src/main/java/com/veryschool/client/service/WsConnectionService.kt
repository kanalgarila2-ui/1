package com.veryschool.client.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.veryschool.client.MainActivity

/**
 * Foreground service чтобы WS соединение не умирало в фоне.
 * Показывает маленькое уведомление "VerySchool — на связи".
 */
class WsConnectionService : Service() {

    companion object {
        const val CHANNEL_ID = "vs_ws_channel"
        const val NOTIFICATION_ID = 42
        const val ACTION_START = "START_WS_SERVICE"
        const val ACTION_STOP = "STOP_WS_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, WsConnectionService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WsConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i("WsService", "WS foreground service started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("WsService", "WS foreground service destroyed")
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VerySchool")
            .setContentText("На связи")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "VerySchool Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Поддерживает соединение с сервером"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
