package com.veryschool.client.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.veryschool.client.data.prefs.PrefsManager
import com.veryschool.client.notifications.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class VSFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        val n = NotificationHelper(this)
        val prefs = PrefsManager(this)
        val d = msg.data

        val showPreview = runBlocking { prefs.notifPreview.first() }
        val useSound    = runBlocking { prefs.notifSound.first() }
        val useVib      = runBlocking { prefs.notifVib.first() }
        val notifMsg    = runBlocking { prefs.notifMsg.first() }
        val notifSys    = runBlocking { prefs.notifSys.first() }

        when (d["type"]) {
            "new_message" -> {
                if (notifMsg) n.showMessage(
                    sender = d["sender"] ?: "VerySchool",
                    text = d["text"] ?: "Новое сообщение",
                    showPreview = showPreview,
                    useSound = useSound,
                    useVib = useVib
                )
            }
            "banned"   -> if (notifSys) n.showBanned(d["reason"] ?: "")
            "frozen"   -> if (notifSys) n.showFrozen(true)
            "unfrozen" -> if (notifSys) n.showFrozen(false)
            else       -> msg.notification?.let {
                n.showMessage(it.title ?: "VerySchool", it.body ?: "",
                    showPreview = showPreview, useSound = useSound, useVib = useVib)
            }
        }
    }

    override fun onNewToken(token: String) {}
}
