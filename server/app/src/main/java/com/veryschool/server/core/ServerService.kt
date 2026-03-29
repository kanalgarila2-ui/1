package com.veryschool.server.core

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.room.Room
import com.veryschool.server.MainActivity
import com.veryschool.server.data.ServerDatabase
import kotlinx.coroutines.*

class ServerService : LifecycleService() {
    companion object {
        const val CHANNEL_ID = "vs_server_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START_SERVER"
        const val ACTION_STOP = "STOP_SERVER"
        const val ACTION_RESTART = "RESTART_SERVER"
        var instance: ServerService? = null
    }

    private lateinit var db: ServerDatabase
    lateinit var server: VerySchoolServer
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = Room.databaseBuilder(applicationContext, ServerDatabase::class.java, "vs_server.db")
            .fallbackToDestructiveMigration().build()
        server = VerySchoolServer(db, 8080)
        server.setLogDir(filesDir)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { server.stop(); stopSelf(); return START_NOT_STICKY }
            ACTION_RESTART -> scope.launch { server.restart(); updateNotification() }
        }
        startForeground(NOTIFICATION_ID, buildNotification("🟢 Сервер запущен"))
        acquireWakeLock()
        if (!server.isRunning) server.start()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop(); wakeLock?.release(); scope.cancel(); instance = null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VerySchool::ServerLock").apply { if (!isHeld) acquire() }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(if (server.isRunning) "🟢 Сервер запущен" else "🔴 Остановлен"))
    }

    private fun buildNotification(status: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, 1, Intent(this, ServerService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
        val restartPi = PendingIntent.getService(this, 2, Intent(this, ServerService::class.java).apply { action = ACTION_RESTART }, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VerySchool Server").setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_send).setContentIntent(pi)
            .addAction(android.R.drawable.ic_delete, "Стоп", stopPi)
            .addAction(android.R.drawable.ic_popup_sync, "Рестарт", restartPi)
            .setOngoing(true).build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "VerySchool Server", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Серверная служба VerySchool"; setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED)
            context.startForegroundService(Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_START })
    }
}
