# VerySchool 2.1 — Firebase Messenger

## Архитектура
- **Client APK** (`com.veryschool.client`) — мессенджер
- **Server APK** (`com.veryschool.server`) — панель администратора

## Исправленные баги v2.1
- Дублирование сообщений (openChat не отменял старый job)
- `msg.senderId == msg.senderId` всегда true — исправлено на `== currentUserId`
- `startListeners()` вызывался дважды при логине
- `ViewModel.onCleared()` не отменял Firebase scope
- Firebase Storage импортирован но не используется — убран
- `LocalContext` в AvatarImage — неиспользуемый import убран
- `showMenu` не сбрасывался при прокрутке
- URI передавался напрямую вместо конвертации в base64
- AdminViewModel.openChatMessages без отмены предыдущего collect

## Новые фичи v2.1
1. 🔍 Поиск в чате с подсветкой найденного текста
2. 💬 Счётчики непрочитанных сообщений
3. ↪️ Пересылка сообщений
4. 📝 Черновики сообщений (сохраняются при выходе из чата)
5. 🔒 Смена пароля с реаутентификацией
6. 🖼️ Изображения через base64 (без Firebase Storage)
7. 🔄 Индикатор загрузки на кнопке входа/регистрации
8. 📌 Ссылка-упоминание `vs:///id=...` в профиле
9. 🚫 Кнопка "Переслать" при выделении сообщений
10. ✅ markAllRead при открытии чата

## Firebase Indexes (добавить в Firestore)
```
chats: members (array) + lastMessageTime (desc)
messages/{chatId}/msgs: timestamp (asc)
logs: timestamp (desc)
```

## Структура Firestore
```
users/{uid}           passphrases/active
chats/{chatId}        logs/{logId}
messages/{chatId}/msgs/{msgId}
deleted_messages/{msgId}
```

## Первый запуск
1. Firebase Console → Auth → Add user (email/password)
2. Firestore → users/{uid} → isAdmin: true (boolean)
3. Firestore → passphrases/active → phrases: ["22sch"] (array)
4. Войти в Server APK с этим email/password
