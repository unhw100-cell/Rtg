# MIGRATION_STATUS — Статус миграции Rustgram на Rust Core

## Среда сборки и инструментарий
- **Rust Toolchain**: `rustc 1.100.0`, `cargo 1.100.0`, `rustfmt`, `clippy` (установлены и проверены в контейнере сборки).
- **Host JNI (для JVM / Robolectric / Gradle-тестов)**: `app/build/rust-host-jni/librustgram_bridge.so` (1010K, release).
- **Android NDK / ABI `jniLibs` (16 KB ELF page alignment, `ndk;27.2.12479018` + `cargo-ndk 4.1.2`)**:
  - `app/src/main/jniLibs/arm64-v8a/librustgram_bridge.so` (906K)
  - `app/src/main/jniLibs/armeabi-v7a/librustgram_bridge.so` (618K)
  - `app/src/main/jniLibs/x86_64/librustgram_bridge.so` (1.1M)
  - `app/src/main/jniLibs/x86/librustgram_bridge.so` (1.1M)
  - `app/build/rust-host-jni/librustgram_bridge.so` (1.1M)

## Этапы миграции

- [x] **Этап 1. Полный аудит существующего Kotlin-проекта** (`docs/CURRENT_ARCHITECTURE.md`, `docs/RUST_MIGRATION_PLAN.md`, `docs/PROGRAMMING_LANGUAGES.md`).
- [x] **Этап 2. Создание Rust Workspace** (`rustgram/Cargo.toml`, `rustgram/rust-toolchain.toml`, 14 доменных крейтов + `rust-bridge`).
- [x] **Этап 3. Настройка Android Rust Bridge** (Direct JNI `cdylib` в `rust-bridge`, `RustBridge.kt` + `RustJson.kt`, автоматическая проверка свежести `buildRustJni` до `preBuild` и изоляция `forkEvery = 1` в `app/build.gradle.kts`).
- [x] **Этап 4. Перенос общих моделей данных** (`rustgram-common`, `rustgram-core` — единая точка маршрутизации команд и общие контракты, `@get:PropertyName` в `RustgramModels.kt`).
- [x] **Этап 5. Перенос бизнес-логики сообщений** (`rustgram-messaging` — отправка, редактирование, реакции, read receipts / viewCount, детерминированные ID, целостность).
- [x] **Этап 6. Перенос чатов и групп** (`rustgram-chat`, `rustgram-groups` — создание личных/групповых чатов, публичных и приватных каналов, инвайты, архивация, выход из канала, удаление комнат, добавление участников по `@username`).
- [x] **Этап 7. Перенос профилей и контактов** (`rustgram-profile`, `rustgram-contacts`, `rustgram-auth` — нормализация и уникальность `@username`, освобождение старого handle, контакты, синхронизация `blockedUids`, проверка прав доступа).
- [x] **Этап 8. Перенос синхронизации и поиска** (`rustgram-sync`, `rustgram-search` — дедупликация и хронологическая сортировка сообщений/чатов, фильтрация рабочего пространства и регистронезависимый поиск с поддержкой кириллицы).
- [x] **Этап 9. Перенос медиа и файловой логики** (`rustgram-media`, `rustgram-crypto` — валидация фото/документов/голосовых и лимитов размеров, SHA-256/HMAC-хэширование и инвайт-коды).
- [x] **Этап 10. Перенос AI-логики** (`rustgram-ai` — реестр моделей Gemini и пресетов, окно контекста из 24 сообщений, формирование системных инструкций и финализация стрима поверх `Firebase.ai`).
- [x] **Этап 11. Перенос хранения, уведомлений и звонков** (`rustgram-storage` — мутации черновиков, журнал звонков с реальной диагностикой микрофона `AudioRecord` RMS, оценка уведомлений, локализация RU/EN, структурирование ошибок Firestore).
- [x] **Этап 12. Интеграционное тестирование (после финального аудита)**:
  - `cargo fmt --all -- --check`, `cargo check --workspace`, `cargo clippy --workspace --all-targets -- -D warnings`, `cargo test --workspace` — **20/20 тестов** (включая `tests/end_to_end_core.rs`) пройдены.
  - `node --test firestore.test.js` с Firebase Emulators — **6/6 тестов** правил безопасности Firestore пройдены.
  - `gradle :app:testDebugUnitTest` с Firebase Emulators — **13/13 тестов** (`ExampleUnitTest` 2/2, `ExampleRobolectricTest` 2/2, `RustBridgeIntegrationTest` 4/4, `MessagingRepositoryRuleTest` 5/5) пройдены.
- [ ] **Внешняя инфраструктура (за пределами автономного клиента, см. `docs/CALLS_WEBRTC_E2EE_PLAN.md`)**: P2P WebRTC-звонки (требуется `libwebrtc` + сервер сигналинга + STUN/TURN `coturn`) и сквозное шифрование E2EE (X3DH + Double Ratchet + AEAD в `rustgram-crypto`).
