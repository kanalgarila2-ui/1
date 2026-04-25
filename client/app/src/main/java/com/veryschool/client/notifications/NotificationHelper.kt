package com.veryschool.client.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.veryschool.client.MainActivity

object Ch {
    const val MESSAGES = "vs_messages"
    const val SYSTEM   = "vs_system"
    const val ERRORS   = "vs_errors"
}

class NotificationHelper(private val ctx: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(listOf(
            NotificationChannel(Ch.MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
                enableLights(true); lightColor = Color.rgb(139, 92, 246); enableVibration(true)
            },
            NotificationChannel(Ch.SYSTEM, "Системные", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(Ch.ERRORS, "Ошибки", NotificationManager.IMPORTANCE_HIGH).apply {
                enableLights(true); lightColor = Color.RED
            }
        ))
    }

    private fun pendingIntent() = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notifId() = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    fun showMessage(sender: String, text: String) {
        val n = NotificationCompat.Builder(ctx, Ch.MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .setContentIntent(pendingIntent()).setColor(Color.rgb(139, 92, 246)).build()
        try { NotificationManagerCompat.from(ctx).notify(notifId(), n) } catch (_: SecurityException) {}
    }

    fun showBanned(reason: String = "") {
        val n = NotificationCompat.Builder(ctx, Ch.SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚫 Аккаунт заблокирован")
            .setContentText(reason.ifEmpty { "Обратитесь к администратору" })
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .setContentIntent(pendingIntent()).build()
        try { NotificationManagerCompat.from(ctx).notify(999, n) } catch (_: SecurityException) {}
    }

    fun showFrozen(frozen: Boolean) {
        val n = NotificationCompat.Builder(ctx, Ch.SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (frozen) "❄️ Аккаунт заморожен" else "✅ Аккаунт разморожен")
            .setContentText(if (frozen) "Отправка сообщений недоступна" else "Все функции восстановлены")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true)
            .setContentIntent(pendingIntent()).build()
        try { NotificationManagerCompat.from(ctx).notify(998, n) } catch (_: SecurityException) {}
    }

    fun showSyncError(msg: String) {
        val n = NotificationCompat.Builder(ctx, Ch.ERRORS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Ошибка синхронизации").setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true)
            .setContentIntent(pendingIntent()).build()
        try { NotificationManagerCompat.from(ctx).notify(notifId(), n) } catch (_: SecurityException) {}
    }
}
