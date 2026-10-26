package com.veryschool.client.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.veryschool.client.MainActivity

object Ch {
    const val MESSAGES = "vs_messages_v3"  // v3 — новый канал со звуком
    const val SYSTEM   = "vs_system_v3"
    const val ERRORS   = "vs_errors_v3"
}

class NotificationHelper(private val ctx: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // FIX: Правильный звук — системный рингтон уведомлений
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        nm.createNotificationChannels(listOf(
            NotificationChannel(Ch.MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Новые сообщения в VerySchool"
                enableLights(true)
                lightColor = 0xFF8B5CF6.toInt()
                // FIX: правильная вибрация
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                // FIX: звук
                setSound(soundUri, audioAttrs)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(Ch.SYSTEM, "Системные", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Бан, заморозка, системные события"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200)
                setSound(soundUri, audioAttrs)
            },
            NotificationChannel(Ch.ERRORS, "Ошибки синхронизации", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ошибки подключения к серверу"
                enableLights(true)
                lightColor = 0xFFEF4444.toInt()
                enableVibration(false)
            }
        ))
    }

    private fun pi() = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun nid() = (System.currentTimeMillis() % Int.MAX_VALUE).toInt().and(Int.MAX_VALUE)

    fun showMessage(sender: String, text: String, showPreview: Boolean = true, useSound: Boolean = true, useVib: Boolean = true) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val body = if (showPreview) text else "Новое сообщение"
        val n = NotificationCompat.Builder(ctx, Ch.MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi())
            .setColor(0xFF8B5CF6.toInt())
            .apply {
                // FIX: явно задаём звук и вибрацию в уведомлении (для надёжности)
                if (useSound) setSound(soundUri)
                if (useVib) setVibrate(longArrayOf(0, 250, 150, 250))
                else setVibrate(longArrayOf(0))
                if (!useSound) setSilent(true)
            }
            .build()
        try { NotificationManagerCompat.from(ctx).notify(nid(), n) } catch (_: SecurityException) {}
    }

    fun showBanned(reason: String = "") {
        val n = NotificationCompat.Builder(ctx, Ch.SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚫 Аккаунт заблокирован")
            .setContentText(reason.ifEmpty { "Обратитесь к администратору" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi())
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        try { NotificationManagerCompat.from(ctx).notify(999, n) } catch (_: SecurityException) {}
    }

    fun showFrozen(frozen: Boolean) {
        val n = NotificationCompat.Builder(ctx, Ch.SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (frozen) "❄️ Аккаунт заморожен" else "✅ Аккаунт разморожен")
            .setContentText(if (frozen) "Отправка сообщений недоступна" else "Все функции восстановлены")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi())
            .build()
        try { NotificationManagerCompat.from(ctx).notify(998, n) } catch (_: SecurityException) {}
    }

    fun showSyncError(msg: String) {
        val n = NotificationCompat.Builder(ctx, Ch.ERRORS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Ошибка синхронизации")
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi())
            .build()
        try { NotificationManagerCompat.from(ctx).notify(nid(), n) } catch (_: SecurityException) {}
    }

    // FIX: принудительная вибрация через Vibrator API (работает даже без уведомления)
    fun vibrate(pattern: LongArray = longArrayOf(0, 250, 150, 250)) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}
    }
}
