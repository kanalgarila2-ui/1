package com.veryschool.server.data.models

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
    val isVerified: Boolean = false,          // ФИЧА #20: верификация ✓
    val banReason: String = "",
    val dmBlocked: Boolean = false,
    val blockedUsers: List<String> = emptyList(), // ФИЧА #8: список заблокированных
    val passphrase: String = "22sch",
    val statusEmoji: String = "",
    val statusText: String = "",
    val msgSentCount: Long = 0L,              // ФИЧА #19: статистика
    val nameHistory: List<String> = emptyList(), // ФИЧА #18: история имён
    val mutedChats: List<String> = emptyList(),  // ФИЧА #17: мьют чата
    val dndEnabled: Boolean = false,          // ФИЧА #16: не беспокоить
    val dndFrom: String = "23:00",
    val dndTo: String = "07:00",
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
    // ФИЧА #10: умный lastSeen
    fun lastSeenText(): String {
        val ts = lastSeen?.toDate()?.time ?: return ""
        val diff = System.currentTimeMillis() - ts
        return when {
            online -> "онлайн"
            diff < 60_000 -> "только что"
            diff < 3_600_000 -> "был(а) ${diff / 60_000} мин назад"
            diff < 86_400_000 -> "был(а) ${diff / 3_600_000} ч назад"
            diff < 172_800_000 -> "был(а) вчера"
            else -> "был(а) давно"
        }
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
    val pinnedMessageId: String = "",
    val pinnedLinks: List<String> = emptyList(),  // ФИЧА #11: прикреплённые ссылки
    val inviteCode: String = "",                  // ФИЧА #12: invite link
    val messageCount: Long = 0L,                  // ФИЧА #14: счётчик сообщений
    val description: String = "",                 // описание группы
    val archivedBy: List<String> = emptyList()   // ФИЧА #18 arch
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
    val voiceBase64: String = "",             // ФИЧА #6: голосовое
    val voiceDurationSec: Int = 0,
    val gifUrl: String = "",                  // ФИЧА #7: GIF
    val replyToId: String = "",
    val replyToText: String = "",
    val reactions: Map<String, List<String>> = emptyMap(),
    val readBy: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    val deletedBy: String = "",
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,            // ФИЧА #1: редактирование
    val editedAt: Timestamp? = null,
    val starredBy: List<String> = emptyList(), // ФИЧА #3: звёздочка/избранное
    val expiresAt: Timestamp? = null,
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, String> = emptyMap(),
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
    val unreadCount: Int = 0, val pinned: Boolean = false, val pinnedMessageId: String = "",
    val description: String = "", val inviteCode: String = "", val messageCount: Long = 0L,
    val isMuted: Boolean = false                // ФИЧА #17
)

data class MessageUiModel(
    val id: String, val chatId: String, val senderId: String, val senderName: String,
    val senderAvatarUrl: String, val text: String, val imageUrl: String, val imageBase64: String,
    val voiceBase64: String = "", val voiceDurationSec: Int = 0,
    val gifUrl: String = "",
    val replyToId: String, val replyToText: String,
    val reactions: Map<String, List<String>>,
    val readBy: List<String>, val isDeleted: Boolean, val isPinned: Boolean,
    val isEdited: Boolean = false,
    val starredBy: List<String> = emptyList(),
    val timestamp: Long, var isPending: Boolean = false,
    val expiresAt: Long? = null,
    val isPoll: Boolean = false,
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, String> = emptyMap(),
    // ФИЧА #23: для sticky date headers
    val dateLabel: String = ""
)

fun MessageModel.toUi() = MessageUiModel(
    id = id, chatId = chatId, senderId = senderId, senderName = senderName,
    senderAvatarUrl = senderAvatarUrl, text = text, imageUrl = imageUrl,
    imageBase64 = imageBase64, voiceBase64 = voiceBase64, voiceDurationSec = voiceDurationSec,
    gifUrl = gifUrl, replyToId = replyToId, replyToText = replyToText,
    reactions = reactions, readBy = readBy, isDeleted = isDeleted, isPinned = isPinned,
    isEdited = isEdited, starredBy = starredBy,
    timestamp = timestamp?.toDate()?.time ?: clientTimestamp,
    expiresAt = expiresAt?.toDate()?.time,
    isPoll = isPoll, pollQuestion = pollQuestion, pollOptions = pollOptions, pollVotes = pollVotes
)

fun ChatModel.toUi(unread: Int = 0, muted: Boolean = false) = ChatUiModel(
    id = id, name = name, isGroup = isGroup, isDm = isDm, isBot = isBot,
    members = members, adminIds = adminIds, avatarUrl = avatarUrl,
    lastMessage = lastMessage, lastMessageTime = lastMessageTime?.toDate()?.time ?: 0L,
    unreadCount = unread, pinned = pinned, pinnedMessageId = pinnedMessageId,
    description = description, inviteCode = inviteCode, messageCount = messageCount,
    isMuted = muted
)

data class AuthResult(val success: Boolean, val user: UserModel? = null, val error: String = "")

// ФИЧА #3: Starred messages коллекция
data class StarredMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val messageId: String = "",
    val chatId: String = "",
    val text: String = "",
    val senderName: String = "",
    @ServerTimestamp val starredAt: Timestamp? = null
)
