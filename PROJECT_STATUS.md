# RUSTGRAM — PROJECT_STATUS.md

## Архитектурный режим
- **Режим**: **Rust Core + Android Jetpack Compose UI + Firebase Adapters**
- **Стек**:
  - **Rust Workspace (`/rustgram`, 15 крейтов)**: вся доменная и бизнес-логика перенесена в Rust (`rustgram-common`, `rustgram-crypto`, `rustgram-auth`, `rustgram-profile`, `rustgram-contacts`, `rustgram-media`, `rustgram-messaging`, `rustgram-chat`, `rustgram-groups`, `rustgram-search`, `rustgram-storage`, `rustgram-sync`, `rustgram-ai`, `rustgram-core`, `rust-bridge`).
  - **Direct JNI Bridge (`cdylib` `librustgram_bridge.so`)**: прямой мост `jni` v0.21 без сторонних FFI-рантаймов; собран для всех 4 Android ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` с выравниванием 16 KB ELF) и для host JVM тестов (`app/build/rust-host-jni/librustgram_bridge.so`).
  - **Android UI & Transport Adapters (Kotlin + Jetpack Compose + Material Design 3)**: тонкий слой UI и транспортов Firebase (`CredentialManager` Google Sign-In, Cloud Firestore с Zero-Trust `firestore.rules`, Firebase App Check `configureAppCheck`, Firebase AI Logic `Firebase.ai`, Android Photo Picker и аудио/медиа API).

## Статусы этапов и модулей
- [x] **Этап 1–2. Аудит, схема данных и Rust Workspace**: `docs/CURRENT_ARCHITECTURE.md`, `docs/RUST_MIGRATION_PLAN.md`, `docs/PROGRAMMING_LANGUAGES.md`, `firebase-blueprint.json`, `firestore.rules`, `rustgram/` (15 крейтов).
- [x] **Этап 3–4. Direct JNI Bridge и Облачный Backend**: `RustBridge.kt` + `RustJson.kt`, `FirestoreErrorHandler.kt` через Rust, App Check (`configureAppCheck` в `MainActivity.onCreate`), Zero-Trust правила Firestore.
- [x] **Этап 5–6. Авторизация, профиль и локализация**: Google Sign-In через `CredentialManager`, изоляция по `uid`, уникальность `@username` и очистка старых `/handles/{id}`, синхронизация `blockedUids`, безопасное зондирование `RustNativeStatus`, RU/EN-строки из Rust (`RustgramStrings.kt`).
- [x] **Этап 7. Сообщения, чаты, группы и каналы**: личные чаты, группы, публичные и приватные каналы, ответы, цитирование, пересылка, реакции, read receipts и `viewCount`, редактирование, удаление, добавление участников по `@username`, выход из публичных каналов, удаление комнат, уведомления через `evaluateNotification`, черновики и сортировка через Rust Core.
- [x] **Этап 8. Мультимедиа**: Photo Picker (`PickVisualMedia`), документы (`OpenDocument`), голосовые сообщения (`MediaRecorder` / `MediaPlayer`); валидация типов, размеров, имён файлов и SHA-256/HMAC целостности в `rustgram-media` / `rustgram-crypto`.
- [x] **Этап 9. Gemini AI-боты**: выбор моделей, пресеты ботов, 24-сообщенное окно истории, системные инструкции и финализация стрима выполняются в `rustgram-ai` (`GeminiBotRepository.kt`), транспорт стриминга — `Firebase.ai`.
- [x] **Этап 10. Контакты и диагностика звонков**: поиск по `@username`, чёрный список, проверка права отправки сообщений, реальная диагностика микрофона через `AudioRecord` (RMS-уровень при разрешении `RECORD_AUDIO`) и журнал сессий в Firestore.
- [x] **Этап 11–12. Тестирование (после финального аудита)**:
  - Rust workspace (`cargo fmt --check`, `cargo check`, `cargo clippy -- -D warnings`, `cargo test --workspace`): **20/20 passed**.
  - Firestore Security Rules (`node --test firestore.test.js` с Firebase Emulators): **6/6 passed**.
  - Android/JVM/JNI/Firestore Emulator (`gradle :app:testDebugUnitTest`): **13/13 passed**.
- [ ] **WebRTC P2P и E2EE (не реализовано — честный архитектурный статус)**: живые P2P-звонки (требуется `libwebrtc` + signaling + TURN/STUN) и сквозное шифрование (X3DH + Double Ratchet + AEAD в `rustgram-crypto`) задокументированы в `docs/CALLS_WEBRTC_E2EE_PLAN.md`.
