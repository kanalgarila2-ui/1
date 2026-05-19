package com.veryschool.client.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class UserModel(
    @DocumentId val id: String = "",
    val numericId: Long = 0L,
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val online: Boolean = false,
    val isBanned: Boolean = false,
    val isFrozen: Boolean = false,
    val isDeleted: Boolean = false,
    val isAdmin: Boolean = false,
    val banReason: String = "",
    val dmBlocked: Boolean = false,
    val passphrase: String = "22sch",
    // ФИЧА: пользовательский статус
    val statusEmoji: String = "",    // напр. "🎮"
    val statusText: String = "",     // напр. "Играю в CS"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    val fcmToken: String = ""
) {
    fun numericLink() = if (numericId > 0) "vs:///id=$numericId" else "vs:///id=$id"
    fun usernameLink() = if (username.isNotEmpty()) "vs:///u=$username" else numericLink()
    fun statusDisplay() = when {
        statusEmoji.isNotEmpty() && statusText.isNotEmpty() -> "$statusEmoji $statusText"
        statusEmoji.isNotEmpty() -> statusEmoji
        statusText.isNotEmpty()  -> statusText
        else -> ""
    }
}

data class ChatModel(
    @DocumentId val id: String = "",
    val name: String = "",
    val isGroup: Boolean = false,
    val isDm: Boolean = false,
    val isBot: Boolean = false,
    val members: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val avatarUrl: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Timestamp? = null,
    val lastMessageSenderId: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val pinned: Boolean = false,
    val pinnedMessageId: String = ""
)

data class MessageModel(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val imageBase64: String = "",
    val replyToId: String = "",
    val replyToText: String = "",
    val reactions: Map<String, List<String>> = emptyMap(),
    val readBy: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    val deletedBy: String = "",
    val isPinned: Boolean = false,
    // ФИЧА: самоудаляющееся сообщение
    val expiresAt: Timestamp? = null,
    // ФИЧА: голосование
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, String> = emptyMap(), // userId -> optionIndex
    val isPoll: Boolean = false,
    @ServerTimestamp val timestamp: Timestamp? = null,
    val clientTimestamp: Long = System.currentTimeMillis()
)

data class DeletedMessageModel(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val deletedBy: String = "",
    @ServerTimestamp val deletedAt: Timestamp? = null
)

data class LogModel(
    @DocumentId val id: String = "",
    val action: String = "",
    val userId: String = "",
    val targetId: String = "",
    val details: String = "",
    val ip: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
)

data class ChatUiModel(
    val id: String, val name: String, val isGroup: Boolean, val isDm: Boolean,
    val isBot: Boolean, val members: List<String>, val adminIds: List<String>,
    val avatarUrl: String, val lastMessage: String, val lastMessageTime: Long,
    val unreadCount: Int = 0, val pinned: Boolean = false, val pinnedMessageId: String = ""
)

data class MessageUiModel(
    val id: String, val chatId: String, val senderId: String, val senderName: String,
    val senderAvatarUrl: String, val text: String, val imageUrl: String, val imageBase64: String,
    val replyToId: String, val replyToText: String, val reactions: Map<String, List<String>>,
    val readBy: List<String>, val isDeleted: Boolean, val isPinned: Boolean,
    val timestamp: Long, var isPending: Boolean = false,
    val expiresAt: Long? = null,
    val isPoll: Boolean = false,
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, String> = emptyMap()
)

fun MessageModel.toUi() = MessageUiModel(
    id = id, chatId = chatId, senderId = senderId, senderName = senderName,
    senderAvatarUrl = senderAvatarUrl, text = text, imageUrl = imageUrl,
    imageBase64 = imageBase64, replyToId = replyToId, replyToText = replyToText,
    reactions = reactions, readBy = readBy, isDeleted = isDeleted, isPinned = isPinned,
    timestamp = timestamp?.toDate()?.time ?: clientTimestamp,
    expiresAt = expiresAt?.toDate()?.time,
    isPoll = isPoll, pollQuestion = pollQuestion, pollOptions = pollOptions, pollVotes = pollVotes
)

fun ChatModel.toUi(unread: Int = 0) = ChatUiModel(
    id = id, name = name, isGroup = isGroup, isDm = isDm, isBot = isBot,
    members = members, adminIds = adminIds, avatarUrl = avatarUrl,
    lastMessage = lastMessage, lastMessageTime = lastMessageTime?.toDate()?.time ?: 0L,
    unreadCount = unread, pinned = pinned, pinnedMessageId = pinnedMessageId
)

data class AuthResult(val success: Boolean, val user: UserModel? = null, val error: String = "")
