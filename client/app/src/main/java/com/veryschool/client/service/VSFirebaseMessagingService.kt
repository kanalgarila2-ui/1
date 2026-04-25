package com.veryschool.client.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.veryschool.client.notifications.NotificationHelper

class VSFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        val notif = NotificationHelper(this)
        val data = msg.data
        when (data["type"]) {
            "new_message" -> notif.showMessage(
                data["sender"] ?: "VerySchool",
                data["text"] ?: "Новое сообщение"
            )
            "banned"   -> notif.showBanned(data["reason"] ?: "")
            "frozen"   -> notif.showFrozen(data["frozen"] == "true")
            "unfrozen" -> notif.showFrozen(false)
            else -> msg.notification?.let { notif.showMessage(it.title ?: "VerySchool", it.body ?: "") }
        }
    }

    override fun onNewToken(token: String) {
        // Токен обновляется при следующем логине автоматически
    }
}
