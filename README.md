# VerySchool — Инструкция по сборке и запуску

## Структура проекта

```
VerySchool/
├── client/          ← APK для всех пользователей
├── server/          ← APK для тебя (сервер + админка)
└── .github/
    └── workflows/
        └── build.yml  ← GitHub Actions автосборка
```

---

## 🚀 Способ 1: Сборка через GitHub Actions (рекомендую)

### Шаг 1 — Создай репозиторий на GitHub
1. Зайди на https://github.com → New repository
2. Назови его `VerySchool`
3. Сделай **приватным** (Private)
4. НЕ добавляй README

### Шаг 2 — Залей проект
На своём ПК в терминале:
```bash
cd путь/к/VerySchool
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/ТВО_ЮЗЕРhttps://GITHUB/VerySchool.git
git push -u origin main
```

### Шаг 3 — Получи gradle-wrapper.jar (ВАЖНО!)
Gradle wrapper JAR должен быть в репо. Самый простой способ:

**Вариант А** — Если есть Android Studio на ПК:
```bash
# В папке client/:
cd client
gradle wrapper --gradle-version 8.7
# В папке server/:
cd ../server
gradle wrapper --gradle-version 8.7
```
Потом `git add . && git commit -m "Add gradle wrapper" && git push`

**Вариант Б** — Скачать JAR отдельно:
Скачай файл с https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar
Положи в:
- `client/gradle/wrapper/gradle-wrapper.jar`
- `server/gradle/wrapper/gradle-wrapper.jar`

### Шаг 4 — Дождись сборки
1. Зайди на GitHub → твой репо → вкладка **Actions**
2. Найди workflow "Build VerySchool APKs"
3. Он запустится автоматически при push
4. После завершения (5-10 минут) → нажми на run → раздел **Artifacts**
5. Скачай `VerySchool-Client-APK` и `VerySchool-Server-Admin-APK`

---

## 🔧 Способ 2: Локальная сборка через Android Studio

### Требования
- Android Studio Hedgehog или новее
- JDK 17
- Android SDK 35

### Сборка Client APK
1. Открой Android Studio → Open → выбери папку `client/`
2. Подожди пока синхронизируется Gradle
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK будет в `client/app/build/outputs/apk/debug/app-debug.apk`

### Сборка Server APK
1. Открой **новое окно** Android Studio → Open → папку `server/`
2. Аналогично Build → Build APK(s)
3. APK в `server/app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Установка и запуск

### Установка Server APK (твой телефон — сервер)
1. Включи на телефоне: Настройки → Безопасность → **Неизвестные источники**
2. Скинь `app-debug.apk` (server) на телефон
3. Установи его
4. Открой приложение **"VS Admin"**
5. Сервер запустится автоматически в фоне
6. На главном экране увидишь:
   - **Локальный IP** — для пользователей в той же Wi-Fi сети
   - **Публичный IP** — для пользователей из интернета

### Установка Client APK (все пользователи)
1. Скинь `app-debug.apk` (client) на телефон пользователя
2. Установи
3. Открой **"VerySchool"**
4. Введи IP:PORT (берёшь из админки)
5. Введи ключевую фразу: `22sch`
6. Регистрируйся или входи

---

## 🌐 Как сделать сервер доступным из интернета

### Вариант А — Ngrok (самый простой, бесплатно)
1. Скачай ngrok на телефон или ПК в той же сети
2. ```bash
   ngrok http 8080
   ```
3. Получишь URL типа `https://abc123.ngrok.io`
4. Вводи этот URL в клиенте вместо IP

### Вариант Б — Проброс порта на роутере
1. Зайди в настройки роутера (обычно 192.168.1.1)
2. Найди раздел "Port Forwarding" / "NAT"
3. Добавь правило: внешний порт 8080 → внутренний IP телефона : 8080
4. Узнай свой внешний IP на https://2ip.ru
5. Вводи `ВНЕШНИЙ_IP:8080` в клиенте

### Вариант В — VPN (Hamachi / ZeroTier)
Все устройства подключаются к одной VPN-сети, используют VPN-IP.

---

## ⚙️ Функции Admin панели

| Вкладка | Что делает |
|---------|-----------|
| **Сервер** | Старт/Стоп/Рестарт, показывает IP, ключевую фразу, онлайн |
| **Логи** | Все события в реальном времени (подключения, сообщения, ошибки) |
| **Пользователи** | Список всех зарег. юзеров, удаление |
| **Чаты** | Список всех чатов и групп, удаление |

---

## 🗄️ Где хранятся данные

Все данные сохраняются в SQLite базе на телефоне с сервером:
- **Путь**: `/data/data/com.veryschool.server/databases/vs_server.db`
- Сохраняются: пользователи, чаты, сообщения, токены
- После рестарта телефона сервер **автоматически запускается** (BootReceiver)
- Данные **не теряются** при рестарте приложения/телефона

---

## 🆔 Уникальные ID пользователей

Каждый пользователь получает **уникальный 6-значный ID** (например: `784523`).
Генерируется при регистрации, гарантированно уникален.

---

## 📋 Известные ограничения и советы

- Телефон-сервер должен быть **включён и не в режиме полёта**
- Рекомендуется **не выключать экран** или разрешить фоновую работу для VS Admin
- Если сервер не отвечает — нажми "Рестарт" в админке
- Порт по умолчанию: **8080**
- Ключевая фраза: **22sch** (без кавычек)

---

## 📲 iOS версия (Swift) — в разработке

После тестирования Android версии можно портировать на iOS используя Swift + URLSessionWebSocketTask для WebSocket. API и протокол полностью совместимы.
