package com.veryschool.client.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.veryschool.client.notifications.NotificationHelper

class VSFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        val n = NotificationHelper(this)
        val d = msg.data
        when (d["type"]) {
            "new_message" -> n.showMessage(d["sender"] ?: "VerySchool", d["text"] ?: "Новое сообщение")
            "banned"      -> n.showBanned(d["reason"] ?: "")
            "frozen"      -> n.showFrozen(true)
            "unfrozen"    -> n.showFrozen(false)
            else          -> msg.notification?.let { n.showMessage(it.title ?: "VerySchool", it.body ?: "") }
        }
    }
    override fun onNewToken(token: String) {}
}
