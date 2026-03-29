package com.veryschool.client.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "veryschool_prefs")

object PrefKeys {
    val SERVER_URL = stringPreferencesKey("server_url")
    val AUTH_TOKEN = stringPreferencesKey("auth_token")
    val USER_ID = stringPreferencesKey("user_id")
    val USERNAME = stringPreferencesKey("username")
    val DISPLAY_NAME = stringPreferencesKey("display_name")
    val AVATAR_BASE64 = stringPreferencesKey("avatar_base64")
}

class PrefsManager(private val context: Context) {

    val serverUrl: Flow<String> = context.dataStore.data.map { it[PrefKeys.SERVER_URL] ?: "" }
    val authToken: Flow<String> = context.dataStore.data.map { it[PrefKeys.AUTH_TOKEN] ?: "" }
    val userId: Flow<String> = context.dataStore.data.map { it[PrefKeys.USER_ID] ?: "" }
    val username: Flow<String> = context.dataStore.data.map { it[PrefKeys.USERNAME] ?: "" }
    val displayName: Flow<String> = context.dataStore.data.map { it[PrefKeys.DISPLAY_NAME] ?: "" }
    val avatarBase64: Flow<String> = context.dataStore.data.map { it[PrefKeys.AVATAR_BASE64] ?: "" }

    suspend fun saveServerUrl(url: String) = context.dataStore.edit { it[PrefKeys.SERVER_URL] = url }
    suspend fun saveAuth(token: String, userId: String, username: String, displayName: String) {
        context.dataStore.edit {
            it[PrefKeys.AUTH_TOKEN] = token
            it[PrefKeys.USER_ID] = userId
            it[PrefKeys.USERNAME] = username
            it[PrefKeys.DISPLAY_NAME] = displayName
        }
    }
    suspend fun saveAvatar(base64: String) = context.dataStore.edit { it[PrefKeys.AVATAR_BASE64] = base64 }
    suspend fun clearAuth() = context.dataStore.edit {
        it.remove(PrefKeys.AUTH_TOKEN)
        it.remove(PrefKeys.USER_ID)
        it.remove(PrefKeys.USERNAME)
        it.remove(PrefKeys.DISPLAY_NAME)
        it.remove(PrefKeys.AVATAR_BASE64)
    }
}
