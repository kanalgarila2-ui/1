package com.veryschool.client.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isGroup: Boolean,
    val members: String,
    val avatarBase64: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isBot: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val reactions: String = "{}",
    val imageBase64: String = ""
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val avatarBase64: String = "",
    val online: Boolean = false
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<ChatEntity>>
    @Upsert suspend fun upsertChat(chat: ChatEntity)
    @Upsert suspend fun upsertChats(chats: List<ChatEntity>)
    @Query("DELETE FROM chats WHERE id = :chatId") suspend fun deleteChat(chatId: String)
    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId") suspend fun clearUnread(chatId: String)
    @Query("UPDATE chats SET lastMessage = :msg, lastMessageTime = :time, unreadCount = unreadCount + 1 WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, msg: String, time: Long)
    @Query("SELECT * FROM chats WHERE id = :id") suspend fun getById(id: String): ChatEntity?
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<MessageEntity>>
    @Upsert suspend fun upsertMessage(msg: MessageEntity)
    @Upsert suspend fun upsertMessages(msgs: List<MessageEntity>)
    @Query("UPDATE messages SET reactions = :reactions WHERE id = :msgId")
    suspend fun updateReactions(msgId: String, reactions: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id") suspend fun getUser(id: String): UserEntity?
    @Upsert suspend fun upsertUser(user: UserEntity)
    @Upsert suspend fun upsertUsers(users: List<UserEntity>)
    @Query("SELECT * FROM users") fun getAllUsers(): Flow<List<UserEntity>>
    @Query("UPDATE users SET online = :online WHERE id = :id") suspend fun setOnline(id: String, online: Boolean)
    @Query("SELECT COUNT(*) FROM users") suspend fun countAll(): Int
    @Query("UPDATE users SET displayName = :name, avatarBase64 = :avatar WHERE id = :id")
    suspend fun updateProfile(id: String, name: String, avatar: String)
}

@Database(entities = [ChatEntity::class, MessageEntity::class, UserEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
}
