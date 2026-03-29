package com.veryschool.server.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val passwordHash: String,
    val displayName: String,
    val avatarBase64: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isAdmin: Boolean = false,
    // Moderation
    val isBanned: Boolean = false,
    val banUntil: Long = 0L,          // 0 = permanent
    val banReason: String = "",
    val dmBlocked: Boolean = false,   // block new DMs
    val dmBlockUntil: Long = 0L       // 0 = permanent
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isGroup: Boolean,
    val members: String,
    val avatarBase64: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val isBot: Boolean = false        // VerySchool BOT chat
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reactions: String = "{}",
    val imageBase64: String = ""
)

@Entity(tableName = "tokens")
data class TokenEntity(
    @PrimaryKey val token: String,
    val userId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ip_log")
data class IpLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ip: String,
    val userId: String = "",
    val action: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface UserDao {
    @Upsert suspend fun upsert(user: UserEntity)
    @Query("SELECT * FROM users WHERE id = :id") suspend fun getById(id: String): UserEntity?
    @Query("SELECT * FROM users WHERE username = :username") suspend fun getByUsername(username: String): UserEntity?
    @Query("SELECT * FROM users") fun getAllFlow(): Flow<List<UserEntity>>
    @Query("SELECT * FROM users") suspend fun getAll(): List<UserEntity>
    @Query("SELECT COUNT(*) FROM users WHERE username = :username") suspend fun countByUsername(username: String): Int
    @Query("UPDATE users SET avatarBase64 = :avatar, displayName = :displayName WHERE id = :id")
    suspend fun updateProfile(id: String, displayName: String, avatar: String)
    @Query("UPDATE users SET passwordHash = :hash WHERE id = :id")
    suspend fun updatePassword(id: String, hash: String)
    @Query("UPDATE users SET isAdmin = :isAdmin WHERE id = :id")
    suspend fun setAdmin(id: String, isAdmin: Boolean)
    @Query("UPDATE users SET isBanned = :banned, banUntil = :until, banReason = :reason WHERE id = :id")
    suspend fun setBan(id: String, banned: Boolean, until: Long, reason: String)
    @Query("UPDATE users SET dmBlocked = :blocked, dmBlockUntil = :until WHERE id = :id")
    suspend fun setDmBlock(id: String, blocked: Boolean, until: Long)
    @Query("DELETE FROM users WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
}

@Dao
interface ChatDao {
    @Upsert suspend fun upsert(chat: ChatEntity)
    @Query("SELECT * FROM chats WHERE id = :id") suspend fun getById(id: String): ChatEntity?
    @Query("SELECT * FROM chats WHERE members LIKE '%' || :userId || '%'") suspend fun getForUser(userId: String): List<ChatEntity>
    @Query("SELECT * FROM chats WHERE members LIKE '%' || :userId || '%' AND isBot = 1") suspend fun getBotChat(userId: String): ChatEntity?
    @Query("SELECT * FROM chats") suspend fun getAll(): List<ChatEntity>
    @Query("SELECT * FROM chats") fun getAllFlow(): Flow<List<ChatEntity>>
    @Query("DELETE FROM chats WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT COUNT(*) FROM chats") suspend fun count(): Int
}

@Dao
interface MessageDao {
    @Upsert suspend fun upsert(msg: MessageEntity)
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC") suspend fun getForChat(chatId: String): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit") suspend fun getRecentForChat(chatId: String, limit: Int): List<MessageEntity>
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit") suspend fun getRecent(limit: Int): List<MessageEntity>
    @Query("UPDATE messages SET reactions = :reactions WHERE id = :id") suspend fun updateReactions(id: String, reactions: String)
    @Query("SELECT * FROM messages WHERE id = :id") suspend fun getById(id: String): MessageEntity?
    @Query("DELETE FROM messages WHERE chatId = :chatId") suspend fun deleteForChat(chatId: String)
    @Query("SELECT COUNT(*) FROM messages") suspend fun count(): Int
}

@Dao
interface TokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(token: TokenEntity)
    @Query("SELECT * FROM tokens WHERE token = :token") suspend fun get(token: String): TokenEntity?
    @Query("DELETE FROM tokens WHERE userId = :userId") suspend fun deleteForUser(userId: String)
    @Query("DELETE FROM tokens WHERE token = :token") suspend fun delete(token: String)
}

@Dao
interface IpLogDao {
    @Insert suspend fun insert(log: IpLogEntity)
    @Query("SELECT * FROM ip_log ORDER BY timestamp DESC LIMIT 500") suspend fun getRecent(): List<IpLogEntity>
    @Query("SELECT * FROM ip_log WHERE userId = :userId ORDER BY timestamp DESC") suspend fun getForUser(userId: String): List<IpLogEntity>
    @Query("DELETE FROM ip_log WHERE timestamp < :before") suspend fun deleteOld(before: Long)
}

@Database(
    entities = [UserEntity::class, ChatEntity::class, MessageEntity::class, TokenEntity::class, IpLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ServerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun tokenDao(): TokenDao
    abstract fun ipLogDao(): IpLogDao
}
