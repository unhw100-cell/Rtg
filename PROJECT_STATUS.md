# RUSTGRAM — PROJECT_STATUS.md

## Архитектурный режим
- **Режим**: **Independent Messenger (Android Kotlin + Jetpack Compose + Firebase)**
- **Важное уточнение**: Данная среда сборки предназначена исключительно для Android-приложений (Gradle / Kotlin / Jetpack Compose) и не содержит кросс-компилятора Rust/Cargo, NDK-пайплайна или внешнего PostgreSQL/WebRTC сервера. В соответствии с правилом о честной совместимости, приложение реализовано как полноценный самостоятельный облачный мессенджер на Android (без имитации MTProto или поддельных серверов Telegram).

## Статусы этапов и модулей
- [x] **Этап 1–2. Архитектура и схема данных**: `firebase-blueprint.json`, `firestore.rules`, модель данных `RustgramModels.kt`.
- [x] **Этап 3–4. Облачный Backend и безопасность**: Firebase Firestore (custom DB ID `firestore_database_id`), Zero-Trust правила доступа, App Check (`configureAppCheck` в `MainActivity.onCreate`), `FirestoreErrorHandler`.
- [x] **Этап 5–6. Авторизация и профиль**: Google Sign-In через Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + тихий авто-вход `GetGoogleIdOption`), изоляция данных по `uid`, редактирование профиля и `@username`, переключение RU/EN и светлой/тёмной темы.
- [x] **Этап 7. Сообщения, группы и каналы**: Личные чаты, группы, публичные и приватные каналы, ответы, цитирование, пересылка, реакции, закрепление, редактирование, удаление, отправка без звука, архивация и синхронизация черновиков.
- [x] **Этап 8. Мультимедиа**: Выбор фото через Android Photo Picker (`PickVisualMedia`), просмотр изображений с масштабированием, прикрепление документов (`OpenDocument`), запись и воспроизведение голосовых сообщений (`MediaRecorder` / `MediaPlayer`).
- [x] **Этап 9. Gemini AI-боты**: Многоходовые диалоги (`startChat` + `sendMessageStream`) через Firebase AI Logic (`Firebase.ai`), выбор моделей (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`), редактируемые системные инструкции (System Instructions) и поддержка мультимодального анализа изображений.
- [x] **Этап 10. Контакты и журнал звонков**: Поиск по `@username` через `/handles`, чёрный список, журнал аудио/видео-сеансов и локальная проверка микрофона (с явным указанием необходимости внешнего WebRTC TURN-сервера для P2P-звонков).
- [x] **Этап 11–12. Тестирование и финальная сборка**: Выполнены `firestore.test.js` (6/6 passed), Kotlin-тесты `MessagingRepositoryRuleTest`, `ExampleUnitTest` и `ExampleRobolectricTest` (6/6 passed, `BUILD SUCCESSFUL`), правила развёрнуты в Firebase (`DeployRules`), приложение скомпилировано (`compile_applet`).
