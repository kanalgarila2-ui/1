package com.veryschool.client.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vs_prefs_v2")
enum class AppTheme { DARK, LIGHT, SYSTEM }

object PK {
    val USER_ID = stringPreferencesKey("user_id"); val USERNAME = stringPreferencesKey("username")
    val DISPLAY_NAME = stringPreferencesKey("display_name"); val AVATAR_URL = stringPreferencesKey("avatar_url")
    val IS_ADMIN = booleanPreferencesKey("is_admin"); val THEME = stringPreferencesKey("theme")
    val NOTIF_MSG = booleanPreferencesKey("notif_msg"); val NOTIF_SYS = booleanPreferencesKey("notif_sys")
    val NOTIF_ERR = booleanPreferencesKey("notif_err"); val NOTIF_SOUND = booleanPreferencesKey("notif_sound")
    val NOTIF_VIB = booleanPreferencesKey("notif_vib")
}

class PrefsManager(private val context: Context) {
    private val ds = context.dataStore
    val userId: Flow<String>   = ds.data.map { it[PK.USER_ID] ?: "" }
    val username: Flow<String> = ds.data.map { it[PK.USERNAME] ?: "" }
    val displayName: Flow<String> = ds.data.map { it[PK.DISPLAY_NAME] ?: "" }
    val avatarUrl: Flow<String> = ds.data.map { it[PK.AVATAR_URL] ?: "" }
    val isAdmin: Flow<Boolean> = ds.data.map { it[PK.IS_ADMIN] ?: false }
    val theme: Flow<AppTheme>  = ds.data.map { runCatching { AppTheme.valueOf(it[PK.THEME] ?: "DARK") }.getOrDefault(AppTheme.DARK) }
    val notifMsg: Flow<Boolean>   = ds.data.map { it[PK.NOTIF_MSG] ?: true }
    val notifSys: Flow<Boolean>   = ds.data.map { it[PK.NOTIF_SYS] ?: true }
    val notifErr: Flow<Boolean>   = ds.data.map { it[PK.NOTIF_ERR] ?: true }
    val notifSound: Flow<Boolean> = ds.data.map { it[PK.NOTIF_SOUND] ?: true }
    val notifVib: Flow<Boolean>   = ds.data.map { it[PK.NOTIF_VIB] ?: true }

    suspend fun saveUser(uid: String, uname: String, dn: String, url: String, admin: Boolean) = ds.edit {
        it[PK.USER_ID] = uid; it[PK.USERNAME] = uname; it[PK.DISPLAY_NAME] = dn; it[PK.AVATAR_URL] = url; it[PK.IS_ADMIN] = admin
    }
    suspend fun saveTheme(v: AppTheme)     = ds.edit { it[PK.THEME]       = v.name }
    suspend fun saveNotifMsg(v: Boolean)   = ds.edit { it[PK.NOTIF_MSG]   = v }
    suspend fun saveNotifSys(v: Boolean)   = ds.edit { it[PK.NOTIF_SYS]   = v }
    suspend fun saveNotifErr(v: Boolean)   = ds.edit { it[PK.NOTIF_ERR]   = v }
    suspend fun saveNotifSound(v: Boolean) = ds.edit { it[PK.NOTIF_SOUND] = v }
    suspend fun saveNotifVib(v: Boolean)   = ds.edit { it[PK.NOTIF_VIB]   = v }
    suspend fun clear() = ds.edit { it.clear() }
}
