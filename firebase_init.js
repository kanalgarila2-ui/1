/**
 * VerySchool — инициализация Firestore
 * Запустить ОДИН РАЗ через Firebase Console > Firestore > Functions
 * или через Node.js: node firebase_init.js
 *
 * npm install firebase-admin
 * GOOGLE_APPLICATION_CREDENTIALS=serviceAccountKey.json node firebase_init.js
 */

const admin = require('firebase-admin');
admin.initializeApp();
const db = admin.firestore();

async function init() {
    console.log('🚀 Инициализация VerySchool Firestore...');

    // ── global_settings/config ───────────────────────────────────────────────
    const settingsRef = db.collection('global_settings').doc('config');
    const settingsSnap = await settingsRef.get();

    if (!settingsSnap.exists) {
        await settingsRef.set({
            // Регистрация
            registrationEnabled: true,
            maxUsersTotal: 500,
            requirePassphrase: true,
            minPasswordLength: 6,
            minUsernameLength: 3,
            maxUsernameLength: 30,
            minDisplayNameLength: 1,
            maxDisplayNameLength: 60,
            // Сообщения
            maxMessageLength: 4000,
            maxImageSizeKb: 600,
            maxAvatarSizeKb: 200,
            messageCooldownMs: 0,
            maxPollOptions: 10,
            maxPinnedLinks: 10,
            selfDestructEnabled: true,
            pollsEnabled: true,
            voiceMessagesEnabled: true,
            gifEnabled: true,
            imageMessagesEnabled: true,
            editMessageEnabled: true,
            editMessageWindowSec: 300,
            forwardEnabled: true,
            // Чаты
            maxGroupMembers: 200,
            maxGroupsPerUser: 20,
            maxDmChatsPerUser: 100,
            groupCreationEnabled: true,
            dmCreationEnabled: true,
            inviteLinksEnabled: true,
            // Уведомления
            pushNotificationsEnabled: true,
            botMessagesEnabled: true,
            // Модерация
            autoFreezeOnReports: false,
            reportsToAutoFreeze: 5,
            allowUserBlocking: true,
            // Технические
            maintenanceMode: false,
            maintenanceMessage: 'Ведутся технические работы. Скоро вернёмся!',
            appVersion: '2.1.0',
            minAppVersion: '2.0.0',
            forceUpdateMessage: 'Обновите приложение для продолжения работы.',
            announcementText: '',
            announcementEnabled: false,
            messagesHistoryLimit: 200,
            logsRetentionDays: 90,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedBy: 'system'
        });
        console.log('✅ global_settings/config создан');
    } else {
        console.log('ℹ️  global_settings/config уже существует — пропускаем');
    }

    // ── passphrases/active ───────────────────────────────────────────────────
    const phraseRef = db.collection('passphrases').doc('active');
    const phraseSnap = await phraseRef.get();
    if (!phraseSnap.exists) {
        await phraseRef.set({ phrases: ['22sch'] });
        console.log('✅ passphrases/active создан (фраза: 22sch)');
    } else {
        console.log('ℹ️  passphrases/active уже существует');
    }

    // ── counters/user_ids ────────────────────────────────────────────────────
    const counterRef = db.collection('counters').doc('user_ids');
    const counterSnap = await counterRef.get();
    if (!counterSnap.exists) {
        await counterRef.set({ next: 100000 });
        console.log('✅ counters/user_ids создан (начало: 100000)');
    } else {
        console.log('ℹ️  counters/user_ids уже существует');
    }

    console.log('\n✅ Инициализация завершена!');
    console.log('\nДальнейшие шаги:');
    console.log('1. Задеплоить firestore.rules через Firebase Console');
    console.log('2. Создать первого администратора вручную:');
    console.log('   - Зарегистрируйтесь через клиентское приложение');
    console.log('   - В Firebase Console > Firestore > users > {uid}');
    console.log('   - Установить поле isAdmin = true');
    console.log('3. Открыть Admin APK и войти с email/паролем администратора');
}

init().catch(console.error).finally(() => process.exit(0));
