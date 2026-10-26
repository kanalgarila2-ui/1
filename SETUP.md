# VerySchool — Инструкция по настройке

## 1. Firebase — первый запуск

### Задеплоить правила Firestore
```
firebase deploy --only firestore:rules
```
Или вручную: Firebase Console → Firestore Database → Rules → вставить содержимое `firestore.rules`.

### Инициализировать коллекции
```bash
npm install firebase-admin
GOOGLE_APPLICATION_CREDENTIALS=serviceAccountKey.json node firebase_init.js
```
Это создаст:
- `global_settings/config` — все настройки приложения
- `passphrases/active` — ключевые фразы входа (по умолчанию: `22sch`)
- `counters/user_ids` — счётчик числовых ID пользователей

### Создать первого администратора
1. Зарегистрируйтесь через Client APK
2. Firebase Console → Firestore → `users` → найти свой документ
3. Добавить поле: `isAdmin = true` (тип Boolean)

## 2. Admin APK

Открыть Admin APK и войти email/паролем администратора.

### Вкладки Admin Panel:

| Вкладка | Что делает |
|---------|-----------|
| 📊 Обзор | Статистика пользователей, чатов, активные лимиты и функции |
| 👥 Юзеры | Бан/разбан, заморозка, верификация, редактирование, удаление |
| 💬 Чаты | Просмотр, удаление чатов и сообщений |
| 📋 Логи | Все действия с фильтрацией по типу |
| 🤖 БОТ | Отправить сообщение одному пользователю или всем |
| 🔑 Фразы | Управление ключевыми фразами входа |
| ⚙️ Настройки | **Глобальные настройки** (сохраняются в `global_settings/config`) |

## 3. Global Settings — описание полей

### Регистрация
- `registrationEnabled` — открыть/закрыть регистрацию
- `maxUsersTotal` — максимальное кол-во пользователей
- `requirePassphrase` — требовать ключевую фразу

### Сообщения
- `maxMessageLength` — макс. символов (по умолч. 4000)
- `maxImageSizeKb` — макс. размер фото KB (по умолч. 600)
- `messageCooldownMs` — задержка между сообщениями в ms (0 = без задержки)
- `editMessageWindowSec` — сколько секунд можно редактировать (по умолч. 300 = 5 мин)

### Переключатели функций
- `imageMessagesEnabled`, `voiceMessagesEnabled`, `gifEnabled`
- `pollsEnabled`, `selfDestructEnabled`, `editMessageEnabled`, `forwardEnabled`
- `groupCreationEnabled`, `dmCreationEnabled`, `inviteLinksEnabled`

### Технические
- `maintenanceMode` — режим обслуживания (пользователи видят заглушку)
- `announcementEnabled` + `announcementText` — глобальное объявление
- `appVersion` / `minAppVersion` — управление force update
- `messagesHistoryLimit` — сколько сообщений подгружать за раз
- `logsRetentionDays` — хранить логи N дней

## 4. Сборка APK

```bash
# Windows
python3 cr.py

# GitHub Actions — пуш в main
git push origin main
```

Artifacts: `client-debug.apk` и `server-debug.apk`.
