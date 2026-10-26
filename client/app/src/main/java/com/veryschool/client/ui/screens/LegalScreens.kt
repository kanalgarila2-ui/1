package com.veryschool.client.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Общий шаблон для правовых экранов
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val tc = LocalTC.current
    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Text(title, color = tc.on, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    val tc = LocalTC.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(body, color = tc.muted, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Политика конфиденциальности
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val tc = LocalTC.current
    LegalScreen("Политика конфиденциальности", onBack) {
        Text("Последнее обновление: 1 января 2025 г.", color = tc.muted, fontSize = 12.sp)

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VSPrimary.copy(0.1f))) {
            Text("VerySchool — закрытый мессенджер. Мы серьёзно относимся к защите ваших данных.",
                color = VSPrimary, fontSize = 13.sp, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
        }

        LegalSection("1. Какие данные мы собираем",
            "• Email-адрес — для создания аккаунта Firebase Authentication.\n" +
            "• Имя пользователя (username) и отображаемое имя — для идентификации в чатах.\n" +
            "• Аватар — хранится как base64 непосредственно в Firestore, без внешних сервисов.\n" +
            "• Сообщения — текст и изображения (base64), отправленные в чатах.\n" +
            "• FCM-токен — для доставки push-уведомлений.\n" +
            "• Статус онлайн и время последнего визита — обновляются автоматически при входе и выходе.\n" +
            "• Числовой ID — уникальный идентификатор, генерируется при регистрации.")

        LegalSection("2. Как мы используем данные",
            "• Обеспечение работы мессенджера: отправка и получение сообщений, идентификация пользователей.\n" +
            "• Push-уведомления о новых сообщениях, системных событиях (бан, заморозка).\n" +
            "• Логирование действий администраторов (бан, разбан) для обеспечения безопасности.\n" +
            "• Мы НЕ продаём, НЕ передаём и НЕ монетизируем ваши данные третьим лицам.")

        LegalSection("3. Хранение данных",
            "Все данные хранятся в Google Firebase (Firestore и Authentication), расположенных в дата-центрах Google. " +
            "Firebase соответствует стандартам ISO 27001, SOC 1/2/3 и GDPR. " +
            "Изображения и аватары хранятся как base64-строки в Firestore — Firebase Storage не используется.")

        LegalSection("4. Доступ к данным",
            "• Ваши сообщения видят только участники соответствующего чата.\n" +
            "• Администраторы приложения могут видеть логи действий, но не содержимое личных сообщений.\n" +
            "• Email-адрес не отображается другим пользователям.\n" +
            "• Статус онлайн можно скрыть в настройках приватности.")

        LegalSection("5. Удаление данных",
            "Вы можете запросить удаление аккаунта и всех связанных данных, обратившись к администратору. " +
            "При удалении аккаунта: email удаляется из Firebase Auth, профиль помечается как удалённый, " +
            "сообщения остаются в истории чатов (без привязки к аккаунту).")

        LegalSection("6. Безопасность",
            "• Вход только по ключевой фразе — дополнительный уровень защиты от посторонних.\n" +
            "• Пароли хранятся в Firebase Authentication в хешированном виде (никогда в открытом).\n" +
            "• Правила Firestore ограничивают доступ: каждый пользователь видит только свои данные и чаты, в которых состоит.\n" +
            "• Соединение с Firebase зашифровано по TLS.")

        LegalSection("7. Несовершеннолетние",
            "Приложение предназначено для использования в образовательных учреждениях. " +
            "Регистрация возможна только с действующей ключевой фразой, выданной администратором. " +
            "Мы не собираем данные о возрасте пользователей.")

        LegalSection("8. Изменения политики",
            "Мы можем обновлять настоящую политику конфиденциальности. " +
            "О существенных изменениях пользователи будут уведомлены через BOT-канал в приложении.")

        LegalSection("9. Контакты",
            "По вопросам конфиденциальности обращайтесь к администратору через приложение " +
            "или в BOT-канал VerySchool.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Правила использования
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    val tc = LocalTC.current
    LegalScreen("Правила использования", onBack) {
        Text("Последнее обновление: 1 января 2025 г.", color = tc.muted, fontSize = 12.sp)

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VSSecondary.copy(0.1f))) {
            Text("Используя VerySchool, вы соглашаетесь с настоящими правилами. " +
                "Нарушение правил может повлечь блокировку аккаунта.",
                color = VSSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
        }

        LegalSection("1. Допустимое использование",
            "VerySchool — мессенджер для общения внутри образовательного учреждения. Разрешено:\n" +
            "• Обмен учебными материалами, объявлениями и сообщениями.\n" +
            "• Создание групп для учебных целей.\n" +
            "• Общение между учениками, преподавателями и администрацией.\n" +
            "• Использование функций: голосования, реакций, ответов на сообщения.")

        LegalSection("2. Запрещённый контент",
            "Строго запрещено публиковать, отправлять или распространять:\n" +
            "• Оскорбления, угрозы, травлю (буллинг) в адрес других пользователей.\n" +
            "• Материалы сексуального характера, насилие, экстремистский контент.\n" +
            "• Личные данные других лиц без их согласия (номера телефонов, адреса, фото).\n" +
            "• Спам, рекламу, мошеннические схемы.\n" +
            "• Вредоносные файлы, ссылки на фишинговые сайты.\n" +
            "• Любой контент, нарушающий законодательство РФ.")

        LegalSection("3. Аккаунт и безопасность",
            "• Один человек — один аккаунт. Создание нескольких аккаунтов запрещено.\n" +
            "• Ключевая фраза является конфиденциальной — не передавайте её посторонним.\n" +
            "• Вы несёте ответственность за все действия, совершённые с вашего аккаунта.\n" +
            "• При утрате доступа к аккаунту обращайтесь к администратору.")

        LegalSection("4. Права и обязанности пользователя",
            "Вы имеете право:\n" +
            "• Изменять своё имя, аватар и статус.\n" +
            "• Удалять свои сообщения.\n" +
            "• Запрашивать удаление аккаунта.\n\n" +
            "Вы обязаны:\n" +
            "• Соблюдать настоящие правила.\n" +
            "• Уважать других пользователей.\n" +
            "• Сообщать администратору о нарушениях.")

        LegalSection("5. Санкции за нарушения",
            "В зависимости от тяжести нарушения администратор вправе применить:\n" +
            "• Заморозку аккаунта (запрет отправки сообщений, сохраняется доступ для чтения).\n" +
            "• Полную блокировку (бан) с указанием причины.\n" +
            "• Удаление нарушающих правила сообщений.\n" +
            "Пользователь будет уведомлён через BOT-канал с указанием причины санкции.")

        LegalSection("6. Контент пользователей",
            "Вы сохраняете права на контент, который публикуете. " +
            "Публикуя контент, вы предоставляете другим участникам чата право просматривать его в рамках приложения. " +
            "Администраторы могут удалять контент, нарушающий правила.")

        LegalSection("7. Ограничение ответственности",
            "Приложение предоставляется «как есть». Администрация не несёт ответственности за:\n" +
            "• Временную недоступность сервиса (технические работы, сбои Firebase).\n" +
            "• Контент, публикуемый пользователями.\n" +
            "• Потерю данных вследствие технических сбоев.")

        LegalSection("8. Изменения правил",
            "Администрация вправе изменять правила использования. " +
            "Актуальная версия всегда доступна в разделе «О приложении». " +
            "Продолжение использования приложения после изменений означает согласие с новой редакцией.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Правила сообщества
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CommunityGuidelinesScreen(onBack: () -> Unit) {
    val tc = LocalTC.current
    LegalScreen("Правила сообщества", onBack) {
        Text("Эти правила помогают VerySchool оставаться безопасным и комфортным для всех.", color = tc.muted, fontSize = 13.sp)

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VSGreen.copy(0.1f))) {
            Text("💡 Главный принцип: общайтесь так, как хотели бы, чтобы общались с вами.",
                color = VSGreen, fontSize = 13.sp, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
        }

        LegalSection("🤝 Уважение",
            "• Общайтесь вежливо и конструктивно.\n" +
            "• Не переходите на личности в спорах.\n" +
            "• Принимайте, что люди могут иметь другое мнение.\n" +
            "• Не используйте оскорбительные прозвища, мемы с целью унизить.")

        LegalSection("🛡️ Безопасность",
            "• Не раскрывайте личные данные — свои и чужие (адрес, телефон, расписание).\n" +
            "• Не переходите по подозрительным ссылкам от незнакомых.\n" +
            "• Если чувствуете угрозу или давление — сообщите администратору немедленно.\n" +
            "• Ключевую фразу входа не передавайте никому, кроме администратора.")

        LegalSection("📝 Качество общения",
            "• Пишите по-русски или на другом языке, понятном участникам чата.\n" +
            "• Избегайте чрезмерного использования заглавных букв (это воспринимается как крик).\n" +
            "• Реакции 👍❤️ — отличный способ показать согласие без лишних сообщений.\n" +
            "• Отвечайте на конкретные сообщения через функцию «Ответить» — это удобнее.")

        LegalSection("👥 Групповые чаты",
            "• Соблюдайте тему группы — не засоряйте рабочие чаты посторонними темами.\n" +
            "• Не добавляйте людей в группы без их согласия.\n" +
            "• Уведомления в большой группе? Используйте @упоминание только когда необходимо.\n" +
            "• Администраторы группы несут ответственность за порядок в ней.")

        LegalSection("🚫 Абсолютные запреты",
            "Следующее ведёт к немедленному бану без предупреждения:\n" +
            "• Буллинг, систематическое преследование, угрозы.\n" +
            "• Публикация контента 18+ или сцен насилия.\n" +
            "• Распространение чужих личных фотографий без согласия.\n" +
            "• Попытки взлома аккаунтов или системы.\n" +
            "• Создание поддельных аккаунтов от имени реальных людей.")

        LegalSection("📣 Как сообщить о нарушении",
            "• Нажмите и удерживайте сообщение → выберите действие → «Пожаловаться» (скоро).\n" +
            "• Напишите администратору напрямую.\n" +
            "• Сообщения о нарушениях рассматриваются в течение 24 часов.")

        LegalSection("✅ Поощряем",
            "• Помощь новым участникам с освоением приложения.\n" +
            "• Конструктивные обсуждения учебных тем.\n" +
            "• Использование голосований для коллективных решений.\n" +
            "• Позитивные реакции и поддержку друг друга.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// О приложении
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AboutScreen(onBack: () -> Unit, onPrivacy: () -> Unit, onTerms: () -> Unit, onGuidelines: () -> Unit) {
    val tc = LocalTC.current
    LegalScreen("О приложении", onBack) {

        // Логотип / шапка
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VSPrimary.copy(0.12f))) {
            Column(Modifier.padding(20.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("VS", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = VSPrimary)
                Text("VerySchool", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tc.on)
                Text("Защищённый мессенджер для образования", fontSize = 13.sp, color = tc.muted)
            }
        }

        // Версия и технологии
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutRow("📦", "Версия", "2.1.0 (build 210)")
                HorizontalDivider(color = tc.border)
                AboutRow("🔧", "Платформа", "Android (Jetpack Compose)")
                HorizontalDivider(color = tc.border)
                AboutRow("☁️", "Backend", "Firebase Firestore (real-time)")
                HorizontalDivider(color = tc.border)
                AboutRow("🔐", "Аутентификация", "Firebase Authentication")
                HorizontalDivider(color = tc.border)
                AboutRow("📱", "Мин. Android", "7.0 (API 24)")
                HorizontalDivider(color = tc.border)
                AboutRow("⚡", "Компилятор", "Kotlin 1.9.10 + KSP")
            }
        }

        // Возможности
        Text("Возможности", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "💬" to "Личные чаты и групповые беседы",
                    "🤖" to "BOT-канал с системными уведомлениями",
                    "📊" to "Голосования и опросы в чатах",
                    "⏱" to "Самоудаляющиеся сообщения",
                    "🖼" to "Отправка изображений (без внешних хранилищ)",
                    "🔔" to "Push-уведомления с гибкой настройкой",
                    "🎨" to "Тёмная / светлая тема, кастомизация чата",
                    "🔒" to "Вход только по ключевой фразе",
                    "🔍" to "Поиск по сообщениям с подсветкой",
                    "📌" to "Закреплённые сообщения",
                    "↩️" to "Ответы, реакции, пересылка",
                    "❄️" to "Заморозка аккаунта без полной блокировки"
                ).forEach { (emoji, text) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(emoji, fontSize = 16.sp)
                        Text(text, color = tc.muted, fontSize = 13.sp)
                    }
                }
            }
        }

        // Правовые документы
        Text("Документы", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
            Column(Modifier.padding(8.dp)) {
                LegalLinkRow("🔒", "Политика конфиденциальности", tc, onPrivacy)
                HorizontalDivider(color = tc.border, modifier = Modifier.padding(horizontal = 12.dp))
                LegalLinkRow("📋", "Правила использования", tc, onTerms)
                HorizontalDivider(color = tc.border, modifier = Modifier.padding(horizontal = 12.dp))
                LegalLinkRow("👥", "Правила сообщества", tc, onGuidelines)
            }
        }

        // Благодарности
        Text("Технологии", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Jetpack Compose" to "UI framework",
                    "Firebase Firestore" to "Real-time база данных",
                    "Firebase Authentication" to "Авторизация",
                    "Firebase Cloud Messaging" to "Push-уведомления",
                    "Coil" to "Загрузка изображений",
                    "Navigation Compose" to "Навигация",
                    "DataStore Preferences" to "Локальные настройки",
                    "Kotlin Coroutines + Flow" to "Асинхронность"
                ).forEach { (lib, desc) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(lib, color = VSSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(desc, color = tc.muted, fontSize = 12.sp)
                    }
                }
            }
        }

        Text("© 2025 VerySchool. Все права защищены.", color = tc.muted, fontSize = 11.sp)
    }
}

@Composable
private fun AboutRow(emoji: String, label: String, value: String) {
    val tc = LocalTC.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Text(label, color = tc.muted, fontSize = 13.sp)
        }
        Text(value, color = tc.on, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LegalLinkRow(emoji: String, label: String, tc: TC, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Text(label, color = tc.on, fontSize = 14.sp)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Перейти",
            tint = tc.muted
        )
    }
}