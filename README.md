# VerySchool 2.0 — Firebase Messenger

## Архитектура
- **Client APK** (`com.veryschool.client`) — мессенджер для пользователей
- **Server APK** (`com.veryschool.server`) — панель администратора

## Firebase
Оба APK используют **Cloud Firestore** как основное хранилище с real-time синхронизацией.

### Настройка Firebase
1. Создать проект в [Firebase Console](https://console.firebase.google.com)
2. Добавить два Android-приложения:
   - `com.veryschool.client`
   - `com.veryschool.server`
3. Скачать `google-services.json` для каждого и положить в соответствующий `app/` директорий
4. Включить **Firebase Authentication** (Email/Password)
5. Включить **Cloud Firestore** (начать в production mode)
6. Применить правила безопасности: `firebase deploy --only firestore:rules`
7. Включить **Firebase Storage** для изображений
8. Включить **Firebase Messaging** для push-уведомлений

### Первый запуск
1. Запустить **Server APK** — войти с email/password первого аккаунта
2. Вручную поставить в Firestore: `users/{uid}.isAdmin = true`
3. Управлять ключевыми фразами входа через вкладку "Фразы"
4. Пользователи регистрируются в **Client APK** с одной из ключевых фраз

## Структура Firestore
```
users/{uid}           — профиль пользователя
chats/{chatId}        — метаданные чата
messages/{chatId}/msgs/{msgId} — сообщения
deleted_messages/{msgId}       — удалённые сообщения (синхронизация)
logs/{logId}          — лог действий
passphrases/active    — ключевые фразы входа
```

## GitHub Actions Secrets
| Secret | Описание |
|--------|----------|
| `GOOGLE_SERVICES_CLIENT` | base64 google-services.json клиента |
| `GOOGLE_SERVICES_SERVER` | base64 google-services.json сервера |
| `KEYSTORE_BASE64` | base64 keystore.jks |
| `KEYSTORE_PASS` | Пароль хранилища |
| `KEY_ALIAS` | Псевдоним ключа |
| `KEY_PASS` | Пароль ключа |

## Основные функции
- ✅ Регистрация/вход с ключевой фразой
- ✅ Личные сообщения и группы
- ✅ Оптимистичная отправка (сообщения появляются мгновенно)
- ✅ Реакции на сообщения
- ✅ Статусы прочтения (✓ / ✓✓)
- ✅ Ответ на сообщение (reply)
- ✅ Закрепление сообщений
- ✅ vs:///id=XXXXXX — ссылки-упоминания в чате
- ✅ Полноэкранный просмотр фото (pinch-to-zoom, double-tap)
- ✅ Выделение сообщений → копировать/удалить
- ✅ VerySchool BOT — системные уведомления каждому пользователю
- ✅ Заморозка аккаунта (❄️ читать можно, писать нет)
- ✅ Блокировка (🚫 полная)
- ✅ Аватарка "УДАЛЁН" для забаненных
- ✅ Тёмная/светлая/авто тема (#8B5CF6)
- ✅ Firebase push-уведомления (FCM)
- ✅ Управление ключевыми фразами входа
- ✅ Логирование всех действий
