# MIGRATION_STATUS — Статус миграции Rustgram на Rust Core

## Среда сборки и инструментарий
- **Проверка контейнера**: выполняется проверка / установка `rustup`, `rustc`, `cargo`, `cargo-ndk`, Android NDK и таргетов (`aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`, `i686-linux-android`). Статус будет обновлён по фактическому результату.

## Этапы миграции

- [x] **Этап 1. Полный аудит существующего Kotlin-проекта** (`docs/CURRENT_ARCHITECTURE.md`, `docs/RUST_MIGRATION_PLAN.md`, `docs/PROGRAMMING_LANGUAGES.md`).
- [ ] **Этап 2. Создание Rust Workspace** (`rustgram/Cargo.toml`, `rustgram/rust-toolchain.toml`, 14 доменных крейтов + `rust-bridge`).
- [ ] **Этап 3. Настройка Android Rust Bridge** (JNI `cdylib`, Kotlin `RustBridge`, интеграция в Gradle).
- [ ] **Этап 4. Перенос общих моделей данных** (`rustgram-common`, `rustgram-core`).
- [ ] **Этап 5. Перенос бизнес-логики сообщений** (`rustgram-messaging`).
- [ ] **Этап 6. Перенос чатов и групп** (`rustgram-chat`, `rustgram-groups`).
- [ ] **Этап 7. Перенос профилей и контактов** (`rustgram-profile`, `rustgram-contacts`, `rustgram-auth`).
- [ ] **Этап 8. Перенос синхронизации и поиска** (`rustgram-sync`, `rustgram-search`).
- [ ] **Этап 9. Перенос медиа и файловой логики** (`rustgram-media`, `rustgram-crypto`).
- [ ] **Этап 10. Перенос AI-логики** (`rustgram-ai`).
- [ ] **Этап 11. Перенос хранения и обработки данных** (`rustgram-storage`).
- [ ] **Этап 12. Интеграционное тестирование** (`cargo test`, `cargo clippy`, `cargo fmt`, Kotlin <-> JNI тесты, Gradle `testDebugUnitTest`).
- [ ] **Этап 13. Оптимизация и сборка APK** (`compile_applet`, проверка вызова реальных Rust-функций из Kotlin).
