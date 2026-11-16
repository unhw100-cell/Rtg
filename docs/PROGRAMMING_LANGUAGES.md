# PROGRAMMING_LANGUAGES — Языки программирования в проекте Rustgram

## Распределение ответственности по языкам

| Язык | Доля логики / Назначение | Каталоги и модули |
|---|---|---|
| **Rust (Edition 2021)** | **Основной язык бизнес-логики**: доменные модели, валидация под Firestore Rules, обработка сообщений и реакций, чаты/группы/каналы, контакты и профили, поиск UTF-8, черновики, синхронизация, медиа-политики, криптография (SHA-256 / HMAC / invite codes), AI-контекст Gemini, локализация, JNI FFI-мост (`cdylib`) | `rustgram/Cargo.toml`, `rustgram/crates/rustgram-*`, `rustgram/crates/rust-bridge` |
| **Kotlin (JVM 11)** | **Android UI и тонкие платформенные адаптеры**: Jetpack Compose UI, Material Design 3, Navigation/BackHandler, Google Sign-In (`CredentialManager`), адаптеры Firestore SDK и `Firebase.ai`, Android App Check, Photo Picker, `MediaRecorder`/`MediaPlayer`, вызовы `RustBridge` через `external fun` + Robolectric/unit тесты | `app/src/main/java/com/example/`, `app/src/test/java/com/example/` |
| **Gradle Kotlin DSL** | Конфигурация Android-сборки, зависимостей, `jniLibs` ABI и условный хук сборки Rust | `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts` |
| **Firestore Security Rules DSL** | Облачная авторизация, проверка схем и защита коллекций на стороне Firebase | `firestore.rules`, `firebase-blueprint.json` |
| **JavaScript (Node.js Test Runner)** | Автоматизированное тестирование правил безопасности Firestore против эмулятора | `firestore.test.js` |
| **POSIX Shell / XML** | Скрипты сборки `cargo-ndk`, проверка качества Rust, Android-ресурсы и манифест | `rustgram/scripts/*.sh`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/` |

## Граница между Kotlin и Rust
1. Вся детерминированная бизнес-логика (валидация, санитизация, фильтрация, сортировка, мутации состояний, формирование запросов, контекст AI, правила уведомлений, расчёт криптографических идентификаторов) принадлежит **Rust**.
2. Вся асинхронная работа с ОС Android и Google Play Services / Firebase SDK (`snapshots()`, `Task.await()`, `CredentialManager`, `Firebase.ai`, UI Compose) остаётся тонкой прослойкой на **Kotlin**.
