package com.veryschool.server.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Документ: global_settings/config
 * Все настройки приложения, управляемые из Admin APK.
 */
data class GlobalSettings(
    @DocumentId val id: String = "config",

    // ── Регистрация и аутентификация ──────────────────────────────────────────
    val registrationEnabled: Boolean = true,          // можно ли регистрироваться
    val maxUsersTotal: Int = 500,                     // максимум пользователей
    val requirePassphrase: Boolean = true,            // требовать фразу при регистрации
    val minPasswordLength: Int = 6,                   // минимальная длина пароля
    val minUsernameLength: Int = 3,                   // минимальная длина username
    val maxUsernameLength: Int = 30,                  // максимальная длина username
    val minDisplayNameLength: Int = 1,                // минимальная длина имени
    val maxDisplayNameLength: Int = 60,               // максимальная длина имени

    // ── Сообщения ─────────────────────────────────────────────────────────────
    val maxMessageLength: Int = 4000,                 // макс. символов в сообщении
    val maxImageSizeKb: Int = 600,                    // макс. размер фото (KB)
    val maxAvatarSizeKb: Int = 200,                   // макс. размер аватара (KB)
    val messageCooldownMs: Long = 0L,                 // задержка между сообщениями (ms)
    val maxPollOptions: Int = 10,                     // макс. вариантов в опросе
    val maxPinnedLinks: Int = 10,                     // макс. прикреплённых ссылок
    val selfDestructEnabled: Boolean = true,          // разрешить самоудаляющиеся
    val pollsEnabled: Boolean = true,                 // разрешить голосования
    val voiceMessagesEnabled: Boolean = true,         // разрешить голосовые
    val gifEnabled: Boolean = true,                   // разрешить GIF
    val imageMessagesEnabled: Boolean = true,         // разрешить изображения
    val editMessageEnabled: Boolean = true,           // разрешить редактировать
    val editMessageWindowSec: Int = 300,              // окно редактирования (сек)
    val forwardEnabled: Boolean = true,               // разрешить пересылку

    // ── Чаты ─────────────────────────────────────────────────────────────────
    val maxGroupMembers: Int = 200,                   // макс. участников в группе
    val maxGroupsPerUser: Int = 20,                   // макс. групп на пользователя
    val maxDmChatsPerUser: Int = 100,                 // макс. DM чатов
    val groupCreationEnabled: Boolean = true,         // разрешить создание групп
    val dmCreationEnabled: Boolean = true,            // разрешить личные чаты
    val inviteLinksEnabled: Boolean = true,           // разрешить invite ссылки

    // ── Уведомления ──────────────────────────────────────────────────────────
    val pushNotificationsEnabled: Boolean = true,     // включить push
    val botMessagesEnabled: Boolean = true,           // включить BOT сообщения

    // ── Модерация ─────────────────────────────────────────────────────────────
    val autoFreezeOnReports: Boolean = false,         // авто-заморозка при жалобах
    val reportsToAutoFreeze: Int = 5,                 // кол-во жалоб до заморозки
    val allowUserBlocking: Boolean = true,            // разрешить блокировку юзеров

    // ── Технические ───────────────────────────────────────────────────────────
    val maintenanceMode: Boolean = false,             // режим обслуживания
    val maintenanceMessage: String = "Ведутся технические работы. Скоро вернёмся!",
    val appVersion: String = "2.1.0",                 // текущая версия
    val minAppVersion: String = "2.0.0",              // минимальная поддерживаемая
    val forceUpdateMessage: String = "Обновите приложение для продолжения работы.",
    val announcementText: String = "",                // объявление (показывается всем)
    val announcementEnabled: Boolean = false,

    // ── Лимиты хранения ───────────────────────────────────────────────────────
    val messagesHistoryLimit: Int = 200,              // сколько сообщений подгружать
    val logsRetentionDays: Int = 90,                  // хранить логи N дней

    @ServerTimestamp val updatedAt: Timestamp? = null,
    val updatedBy: String = ""
)
