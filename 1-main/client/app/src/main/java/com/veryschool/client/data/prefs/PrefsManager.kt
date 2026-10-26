package com.veryschool.client.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vs_prefs_v3")

enum class AppTheme   { DARK, LIGHT, SYSTEM }
enum class BubbleStyle { ROUND, SHARP, RECT }
enum class FontSize    { SMALL, MEDIUM, LARGE }
enum class ChatBg      { NONE, DOTS, GRID, WAVES, STARS, GRADIENT }
enum class TimeFormat  { H24, H12 }

object PK {
    val USER_ID         = stringPreferencesKey("user_id")
    val USERNAME        = stringPreferencesKey("username")
    val DISPLAY_NAME    = stringPreferencesKey("display_name")
    val AVATAR_URL      = stringPreferencesKey("avatar_url")
    val IS_ADMIN        = booleanPreferencesKey("is_admin")
    // Внешний вид
    val THEME           = stringPreferencesKey("theme")
    val BUBBLE_STYLE    = stringPreferencesKey("bubble_style")
    val FONT_SIZE       = stringPreferencesKey("font_size")
    val CHAT_BG         = stringPreferencesKey("chat_bg")
    val TIME_FORMAT     = stringPreferencesKey("time_format")
    val COMPACT_MODE    = booleanPreferencesKey("compact_mode")
    // Уведомления
    val NOTIF_MSG       = booleanPreferencesKey("notif_msg")
    val NOTIF_SYS       = booleanPreferencesKey("notif_sys")
    val NOTIF_ERR       = booleanPreferencesKey("notif_err")
    val NOTIF_SOUND     = booleanPreferencesKey("notif_sound")
    val NOTIF_VIB       = booleanPreferencesKey("notif_vib")
    val NOTIF_PREVIEW   = booleanPreferencesKey("notif_preview")   // показывать текст в уведомлении
    val NOTIF_GROUPS    = booleanPreferencesKey("notif_groups")     // уведомления из групп
    // Приватность
    val HIDE_ONLINE     = booleanPreferencesKey("hide_online")      // скрыть статус онлайн
    val HIDE_READ       = booleanPreferencesKey("hide_read")        // скрыть ✓✓
    val HIDE_STATUS     = booleanPreferencesKey("hide_status")      // скрыть текстовый статус
    // Медиа
    val AUTO_DOWNLOAD   = booleanPreferencesKey("auto_download")    // автозагрузка фото
    val SEND_QUALITY    = stringPreferencesKey("send_quality")      // LOW / MEDIUM / HIGH
}

class PrefsManager(private val context: Context) {
    private val ds = context.dataStore

    val userId       : Flow<String>      = ds.data.map { it[PK.USER_ID]       ?: "" }
    val username     : Flow<String>      = ds.data.map { it[PK.USERNAME]      ?: "" }
    val displayName  : Flow<String>      = ds.data.map { it[PK.DISPLAY_NAME]  ?: "" }
    val avatarUrl    : Flow<String>      = ds.data.map { it[PK.AVATAR_URL]    ?: "" }
    val isAdmin      : Flow<Boolean>     = ds.data.map { it[PK.IS_ADMIN]      ?: false }
    val theme        : Flow<AppTheme>    = ds.data.map { runCatching { AppTheme.valueOf(it[PK.THEME] ?: "DARK") }.getOrDefault(AppTheme.DARK) }
    val bubbleStyle  : Flow<BubbleStyle> = ds.data.map { runCatching { BubbleStyle.valueOf(it[PK.BUBBLE_STYLE] ?: "ROUND") }.getOrDefault(BubbleStyle.ROUND) }
    val fontSize     : Flow<FontSize>    = ds.data.map { runCatching { FontSize.valueOf(it[PK.FONT_SIZE] ?: "MEDIUM") }.getOrDefault(FontSize.MEDIUM) }
    val chatBg       : Flow<ChatBg>      = ds.data.map { runCatching { ChatBg.valueOf(it[PK.CHAT_BG] ?: "NONE") }.getOrDefault(ChatBg.NONE) }
    val timeFormat   : Flow<TimeFormat>  = ds.data.map { runCatching { TimeFormat.valueOf(it[PK.TIME_FORMAT] ?: "H24") }.getOrDefault(TimeFormat.H24) }
    val compactMode  : Flow<Boolean>     = ds.data.map { it[PK.COMPACT_MODE]  ?: false }
    val notifMsg     : Flow<Boolean>     = ds.data.map { it[PK.NOTIF_MSG]     ?: true }
    val notifSys     : Flow<Boolean>     = ds.data.map { it[PK.NOTIF_SYS]     ?: true }
    val notifErr     : Flow<Boolean>     = ds.data.map { it[PK.NOTIF_ERR]     ?: true }
    val notifSound   : Flow<Boolean>     = ds.data.map { it[PK.NOTIF_SOUND]   ?: true }
    val notifVib     : Flow<Boolean>     = ds.data.map { it[PK.NOTIF_VIB]     ?: true }
    val notifPreview : Flow<Boolean>     = ds.data.map { it[PK.NOTIF_PREVIEW] ?: true }
    val notifGroups  : Flow<Boolean>     = ds.data.map { it[PK.NOTIF_GROUPS]  ?: true }
    val hideOnline   : Flow<Boolean>     = ds.data.map { it[PK.HIDE_ONLINE]   ?: false }
    val hideRead     : Flow<Boolean>     = ds.data.map { it[PK.HIDE_READ]     ?: false }
    val hideStatus   : Flow<Boolean>     = ds.data.map { it[PK.HIDE_STATUS]   ?: false }
    val autoDownload : Flow<Boolean>     = ds.data.map { it[PK.AUTO_DOWNLOAD] ?: true }
    val sendQuality  : Flow<String>      = ds.data.map { it[PK.SEND_QUALITY]  ?: "MEDIUM" }

    suspend fun saveUser(uid: String, uname: String, dn: String, url: String, admin: Boolean) = ds.edit {
        it[PK.USER_ID] = uid; it[PK.USERNAME] = uname; it[PK.DISPLAY_NAME] = dn
        it[PK.AVATAR_URL] = url; it[PK.IS_ADMIN] = admin
    }
    suspend fun saveTheme(v: AppTheme)       = ds.edit { it[PK.THEME]         = v.name }
    suspend fun saveBubbleStyle(v: BubbleStyle) = ds.edit { it[PK.BUBBLE_STYLE] = v.name }
    suspend fun saveFontSize(v: FontSize)    = ds.edit { it[PK.FONT_SIZE]     = v.name }
    suspend fun saveChatBg(v: ChatBg)        = ds.edit { it[PK.CHAT_BG]       = v.name }
    suspend fun saveTimeFormat(v: TimeFormat) = ds.edit { it[PK.TIME_FORMAT]  = v.name }
    suspend fun saveCompactMode(v: Boolean)  = ds.edit { it[PK.COMPACT_MODE]  = v }
    suspend fun saveNotifMsg(v: Boolean)     = ds.edit { it[PK.NOTIF_MSG]     = v }
    suspend fun saveNotifSys(v: Boolean)     = ds.edit { it[PK.NOTIF_SYS]     = v }
    suspend fun saveNotifErr(v: Boolean)     = ds.edit { it[PK.NOTIF_ERR]     = v }
    suspend fun saveNotifSound(v: Boolean)   = ds.edit { it[PK.NOTIF_SOUND]   = v }
    suspend fun saveNotifVib(v: Boolean)     = ds.edit { it[PK.NOTIF_VIB]     = v }
    suspend fun saveNotifPreview(v: Boolean) = ds.edit { it[PK.NOTIF_PREVIEW] = v }
    suspend fun saveNotifGroups(v: Boolean)  = ds.edit { it[PK.NOTIF_GROUPS]  = v }
    suspend fun saveHideOnline(v: Boolean)   = ds.edit { it[PK.HIDE_ONLINE]   = v }
    suspend fun saveHideRead(v: Boolean)     = ds.edit { it[PK.HIDE_READ]     = v }
    suspend fun saveHideStatus(v: Boolean)   = ds.edit { it[PK.HIDE_STATUS]   = v }
    suspend fun saveAutoDownload(v: Boolean) = ds.edit { it[PK.AUTO_DOWNLOAD] = v }
    suspend fun saveSendQuality(v: String)   = ds.edit { it[PK.SEND_QUALITY]  = v }
    suspend fun clear() = ds.edit { it.clear() }
}
