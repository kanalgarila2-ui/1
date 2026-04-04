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
```bash
cd путь/к/VerySchool
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/ТВО_ЮЗЕРhttps://GITHUB/VerySchool.git
git push -u origin main
```

### Шаг 3 — Дождись сборки
1. GitHub → твой репо → вкладка **Actions**
2. Workflow "Build VerySchool APKs" запустится автоматически
3. После завершения (5-10 минут) → нажми на run → **Artifacts**
4. Скачай `VerySchool-Client-APK` и `VerySchool-Server-Admin-APK`

---

## 📱 Установка и запуск

### Server APK (твой телефон — сервер)
1. Настройки → Безопасность → **Неизвестные источники**
2. Установи server APK → открой **"VS Admin"**
3. Сервер запустится автоматически
4. Увидишь **Локальный IP** и **Публичный IP**

### Client APK (все пользователи)
1. Установи client APK → открой **"VerySchool"**
2. Введи IP:PORT из админки
3. Ключевая фраза: `22sch`
4. Регистрируйся или входи

---

## ⚙️ Функции

| Вкладка | Что делает |
|---------|-----------|
| **Сервер** | Старт/Стоп/Рестарт, IP, ключевая фраза |
| **Логи** | Все события в реальном времени |
| **Юзеры** | Список, бан, блок DM, выдача админки |
| **Чаты** | Список всех чатов, удаление |
| **Бот** | Рассылка сообщений от VerySchool BOT |

---

## 🔧 Что было исправлено (v2)

1. **`composeOptions`** — версия `1.5.14` → `1.5.3` (совместима с Kotlin 1.9.24)
2. **`WsClient`** — Channel больше не закрывается при реконнекте (критичный баг — пользователи не отображались)
3. **`MainViewModel`** — `openChat()` теперь отменяет предыдущую подписку на сообщения
4. **`WsConnectionService`** — новый foreground service держит WS живым в фоне
