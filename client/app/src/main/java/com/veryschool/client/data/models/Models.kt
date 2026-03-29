package com.veryschool.client.data.models

import kotlinx.serialization.Serializable

@Serializable data class WsMessage(val type: String, val payload: String = "")
@Serializable data class UserDto(val id: String, val username: String, val displayName: String, val avatarBase64: String = "", val online: Boolean = false, val isBanned: Boolean = false, val dmBlocked: Boolean = false)
@Serializable data class MessageDto(val id: String, val chatId: String, val senderId: String, val senderName: String, val text: String, val timestamp: Long, val reactions: Map<String, List<String>> = emptyMap(), val imageBase64: String = "")
@Serializable data class ChatDto(val id: String, val name: String, val isGroup: Boolean, val members: List<String>, val avatarBase64: String = "", val lastMessage: String = "", val lastMessageTime: Long = 0L, val isBot: Boolean = false)
@Serializable data class AuthRequest(val username: String, val password: String, val passphrase: String)
@Serializable data class RegisterRequest(val username: String, val password: String, val displayName: String, val passphrase: String)
@Serializable data class AuthResponse(val success: Boolean, val token: String = "", val user: UserDto? = null, val error: String = "")
@Serializable data class SendMessageRequest(val chatId: String, val text: String, val imageBase64: String = "")
@Serializable data class ReactionRequest(val messageId: String, val chatId: String, val emoji: String)
@Serializable data class CreateGroupRequest(val name: String, val memberIds: List<String>, val isDm: Boolean = false)
@Serializable data class UpdateProfileRequest(val displayName: String, val avatarBase64: String = "")
@Serializable data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)
@Serializable data class CheckUsernameResponse(val exists: Boolean)

object WsTypes {
    const val AUTH = "auth"; const val AUTH_OK = "auth_ok"; const val AUTH_FAIL = "auth_fail"
    const val NEW_MESSAGE = "new_message"; const val SEND_MESSAGE = "send_message"
    const val REACTION = "reaction"; const val REACTION_UPDATE = "reaction_update"
    const val CHAT_LIST = "chat_list"; const val USER_LIST = "user_list"
    const val MESSAGE_HISTORY = "message_history"
    const val USER_ONLINE = "user_online"; const val USER_OFFLINE = "user_offline"
    const val CREATE_GROUP = "create_group"; const val GROUP_CREATED = "group_created"
    const val PROFILE_UPDATED = "profile_updated"
    const val ERROR = "error"; const val PING = "ping"; const val PONG = "pong"
    const val TYPING = "typing"; const val TYPING_STOP = "typing_stop"
    const val BANNED = "banned"; const val USER_UPDATED = "user_updated"
}
