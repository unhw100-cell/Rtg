# RUST_MIGRATION_PLAN — Карта и стратегия миграции Rustgram на Rust Core

## 1. Целевая архитектура: Rust Core + Thin Android Kotlin UI/Adapters

- **Rust Core Workspace (`rustgram/`)**: Единый источник доменной логики, моделей, валидации, поиска, синхронизации черновиков, правил вложений, AI-оркестрации контекста, криптографических утилит и локализации.
- **Native JNI Bridge (`crates/rust-bridge`)**: Библиотека типа `cdylib` (`librustgram_bridge.so`), экспортирующая прямые JNI-символы `Java_com_example_rust_RustBridge_*` через крейт `jni` (нулевые лишние зависимости вроде JNA).
- **Android App (корень проекта `app/`)**: Kotlin + Jetpack Compose + Material Design 3 + тонкие платформенные адаптеры для Google Sign-In (`CredentialManager`), Cloud Firestore SDK, App Check, Firebase AI Logic (`Firebase.ai`), Photo Picker, `MediaRecorder`/`MediaPlayer` и локальных уведомлений.

## 2. Матрица переноса 18 бизнес-доменов в Rust-крейты

| # | Бизнес-домен | Целевой Rust Crate | Ключевые переносимые функции |
|---|---|---|---|
| 1 | Общие модели данных | `rustgram-common` | `UserProfile`, `HandleEntry`, `ContactEntry`, `DraftEntry`, `CallLogEntry`, `ChatRoom`, `ChatMessage`, `GeminiBotPreset`, `DomainLimits` |
| 2 | Обработка ошибок | `rustgram-common` | `RustDomainError`, `OperationType`, структурированный JSON-отчёт об ошибке |
| 3 | Общий фасад и локализация | `rustgram-core` | Диспетчеризация доменных команд, словарь `AppStrings` (RU/EN), версия ядра, healthcheck |
| 4 | Авторизация и сессии | `rustgram-auth` | Проверка валидности UID/сессии, построение детерминированных приватных ID чатов (`saved_`, `dm_`) |
| 5 | Управление профилями | `rustgram-profile` | Санитизация `displayName`/`@username`/`bio`, валидация HEX-цвета, правила обновления настроек |
| 6 | Контакты и чёрный список | `rustgram-contacts` | Подготовка `HandleEntry` / `ContactEntry`, проверка блокировки и права начала диалога |
| 7 | Обработка сообщений | `rustgram-messaging` | Подготовка отправки, редактирования, пересылки, цитирования, реакций (toggle + лимит 50), превью |
| 8 | Управление чатами | `rustgram-chat` | Фильтрация (`ALL`, `DIRECT`, `GROUPS`, `CHANNELS`, `BOTS`, `ARCHIVED`), архивация, сортировка |
| 9 | Бизнес-логика групп и каналов | `rustgram-groups` | Создание комнат/каналов, роли `ownerId`/`adminUids`/`memberUids`, публичность, вступление/выход, закрепление |
| 10 | Логика поиска | `rustgram-search` | Регистронезависимый UTF-8 поиск (включая кириллицу) по чатам, сообщениям и публичным каналам |
| 11 | Локальное/черновое хранение | `rustgram-storage` | Машина состояний черновиков (нормализация, удаление пустых, лимит 4000), валидация логов звонков |
| 12 | Синхронизация и слияние | `rustgram-sync` | Сортировка и дедупликация снимков сообщений/чатов, разрешение конфликтов черновиков |
| 13 | Обработка вложений (медиа) | `rustgram-media` | Валидация типов (`image`/`audio`/`document`), проверка лимитов Base64 (<= 400KB), форматирование и проверка медиа |
| 14 | Криптография и целостность | `rustgram-crypto` | Детерминированные SHA-256 хеши, генерация `inviteCode`, подпись/верификация целостности сообщений |
| 15 | AI-боты Gemini | `rustgram-ai` | Каталог моделей (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`), пресеты, сборка контекста истории (до 24 сообщений), очистка стриминга и классификация ошибок |
| 16 | Уведомления и маршрутизация | `rustgram-core` | Политика уведомлений (`NotificationDecision`: suppress при `isSilent`, блок-листе или собственном сообщении) |
| 17 | Формирование запросов к backend | `rustgram-messaging`, `rustgram-chat`, `rustgram-profile` | Генерация JSON-payload с точной проверкой всех полей под `firestore.rules` |
| 18 | JNI FFI Мост | `rust-bridge` | Экспорт `cdylib` (`librustgram_bridge.so`), безопасный обмен UTF-8 строками через `jni` crate |

## 3. Сохранение Firebase и Gemini
- **Firebase Auth, Firestore, App Check**: остаются без изменений на стороне облака и `firestore.rules`. Kotlin-адаптер запрашивает `snapshots()` и применяет записи/обновления, сформированные и провалидированные Rust Core.
- **Gemini AI Logic**: пресеты, валидация моделей, сборка истории (`history`), подготовка системных инструкций и постобработка потоковых фрагментов выполняются в `rustgram-ai`; сам вызов `Firebase.ai.generativeModel(...).startChat(history).sendMessageStream(...)` выполняется тонким Kotlin-адаптером, что исключает встраивание API-ключей и сохраняет App Check.
