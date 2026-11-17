# Архитектурный план: Полноценные WebRTC-звонки и E2EE-шифрование (X3DH + Double Ratchet)

## 1. Честный статус текущей реализации

### Звонки (Audio / Video Calls)
- **Что реализовано сейчас**:
  - Вкладка `CallsTabScreen` предоставляет реальную диагностику микрофона через `android.media.AudioRecord` (`16 kHz PCM_16BIT`) с расчётом RMS-уровня сигнала в реальном времени (`Mic Audio Check`) после явного запроса разрешения `RECORD_AUDIO`.
  - Сохранение истории сессий в Cloud Firestore (`users/{uid}/call_logs/{callId}`) через Rust-валидатор `rustgram_storage::build_call_log_entry`.
  - Валидация типа (`audio`, `video`, `group`) и статуса (`completed`, `missed`, `preview`) выполняется в Rust Core и дублируется в `firestore.rules`.
- **Что НЕ реализовано и почему**:
  - Настоящие P2P-аудио/видеозвонки между двумя устройствами не реализованы, так как отсутствуют:
    1. Стек `WebRTC` (`org.webrtc` / `libwebrtc` или Rust `webrtc-rs` / `str0m` + Android audio/video capture sink).
    2. Сервер сигналинга (обмен SDP Offer/Answer и ICE-кандидатами).
    3. Серверы STUN/TURN (например, `coturn`) для обхода NAT/CGNAT в реальных мобильных сетях.

### Криптография и E2EE (End-to-End Encryption)
- **Что реализовано сейчас**:
  - Креейт `rustgram-crypto` реализует SHA-256 (`sha2`), HMAC-SHA256 (`hmac`) для контрольных отпечатков целостности сообщений (`integritySha256`) и генерацию криптостойких инвайт-кодов (`rand`).
  - Вся облачная транспортная и серверная защита обеспечивается HTTPS/TLS 1.3 (Firebase SDK), Google Sign-In (`CredentialManager`), Firebase App Check и Zero-Trust `firestore.rules`.
- **Что НЕ реализовано и почему**:
  - Полноценный E2EE для Secret Chats (протокол Signal: X3DH + Double Ratchet / либо Telegram MTProto 2.0 E2EE Diffie-Hellman) и AEAD-шифрование полезной нагрузки не реализованы, так как требуют серверного/клиентского хранилища PreKey Bundles (Signed PreKeys, One-Time PreKeys), верификации ключей безопасности (Safety Numbers) и клиентского KeyStore для приватных ключей.

---

## 2. План внедрения P2P WebRTC-звонков

1. **Сигналинг поверх Firestore (`calls/{callId}`)**:
   - Документ звонка: `callerUid`, `calleeUid`, `offerSdp`, `answerSdp`, `state` (`ringing | active | ended`), `createdAt`, `expiresAt`.
   - Подколлекции: `callerCandidates/{id}`, `calleeCandidates/{id}` для ICE-кандидатов.
   - Новые правила в `firestore.rules`: читать/обновлять звонок могут только `callerUid` и `calleeUid`; неаутентифицированный доступ запрещён.
2. **STUN / TURN инфраструктура**:
   - Развернуть `coturn` с эфемерными HMAC REST-credentials (`turn-auth-secret` через Firebase Functions / Cloud Run, не в клиенте).
   - Конфигурация `PeerConnection.IceServer` на клиенте через `IceServerDto`, валидируемый в новом модуле `rustgram-calls`.
3. **Android WebRTC + Rust Call State Machine**:
   - Добавить проверенный артефакт `io.github.webrtc-sdk:android` (после проверки лицензии и размера APK через `dependency-advisor`).
   - Перенести конечный автомат звонка (`Idle -> Offering -> Ringing -> Connected -> Ended`) и генерацию SDP/ICE-событий в новый крейт `rustgram-calls` (или расширение `rustgram-media`).
   - Добавить `CameraX` (`PERMISSION_CAMERA`) + `ForegroundService` (`microphone|camera` для Android 14+) строго при переходе к настоящим звонкам в соответствии с Play Policy.

---

## 3. План внедрения E2EE-шифрования в `rustgram-crypto`

1. **Криптопримитивы в Rust Core (`rustgram-crypto`)**:
   - Подключить аудит-проверенные крейты RustCrypto: `x25519-dalek` (ECDH), `ed25519-dalek` (подписи pre-key), `hkdf` (KDF-цепочки), `chacha20poly1305` или `aes-gcm` (AEAD), `zeroize` (затирание ключевого материала в памяти).
2. **Обмен ключами (X3DH) и хранение ключей**:
   - При инициализации устройства генерировать Identity Key Pair (`Ed25519`/`X25519`) в `rustgram-crypto` и сохранять приватный ключ в `EncryptedSharedPreferences` / Android Keystore (привязанном к устройству, без выгрузки в Firestore).
   - Публиковать только публичный PreKey Bundle в Firestore `users/{uid}/key_bundles/{deviceId}` (подписанный pre-key + пул one-time pre-keys).
3. **Double Ratchet для Secret Chats**:
   - Для секретных чатов (`chatType == "secret"`) выносить хранение состояния ratchet-сессии локально, а в Firestore передавать только AEAD-ciphertext (`ciphertextB64`, `nonce`, `headerDhPub`, `msgIndex`).
   - Отображать Safety Number / emoji-отпечаток (`sha256(IK_A || IK_B)`) в диалоге информации о чате для очной верификации ключей.
4. **Интеграция с Search / Gemini AI**:
   - Исключить серверное индексирование текста в secret-чатах; локальный поиск в `rustgram-search` работает только по расшифрованному кэшу на устройстве.
