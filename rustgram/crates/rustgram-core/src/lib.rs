use rustgram_ai::{
    default_presets, finalize_stream_response, prepare_gemini_request, AVAILABLE_MODELS,
};
use rustgram_auth::{
    direct_pair_chat_id, require_authenticated_uid, saved_messages_chat_id, starter_bot_chat_id,
};
use rustgram_chat::{sort_chats_by_activity, toggle_archive_list, ChatFilter};
use rustgram_common::{
    format_structured_error, sanitize_id, ChatMessage, ChatRoom, ContactEntry, DraftEntry,
    OperationType,
};
use rustgram_contacts::{
    build_contact_entry, can_message_contact, normalize_lookup_handle, sort_contacts,
};
use rustgram_crypto::{generate_invite_code, message_integrity_fingerprint, sha256_hex};
use rustgram_groups::{
    build_chat_room, prepare_add_group_member, prepare_join_public_channel,
    prepare_leave_public_channel,
};
use rustgram_media::{format_document_size_kb, format_voice_note_filename, validate_attachment};
use rustgram_messaging::{
    plan_read_receipts, prepare_edit_message, prepare_send_message, toggle_reaction_map,
};
use rustgram_profile::{build_initial_profile, build_profile_update, normalize_language};
use rustgram_search::{filter_chat_workspace, search_messages_in_chat};
use rustgram_storage::{build_call_log_entry, plan_draft_mutation};
use rustgram_sync::{build_drafts_map, order_and_dedup_messages};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CoreInfo {
    pub engine: String,
    pub version: String,
    pub target_arch: String,
    pub crates_count: usize,
    pub abi_bridge: String,
}

pub fn core_info() -> CoreInfo {
    CoreInfo {
        engine: "rustgram-core".to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
        target_arch: std::env::consts::ARCH.to_string(),
        crates_count: 15,
        abi_bridge: "direct-jni-cdylib".to_string(),
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct NotificationDecision {
    pub should_notify: bool,
    pub suppress_reason: Option<String>,
    pub title: String,
    pub preview: String,
}

pub fn evaluate_notification_decision(
    current_uid: &str,
    author_id: &str,
    sender_name: &str,
    chat_title: &str,
    message_text: &str,
    is_silent: bool,
    blocked_uids: &[String],
) -> NotificationDecision {
    if author_id == current_uid {
        return NotificationDecision {
            should_notify: false,
            suppress_reason: Some("own_message".into()),
            title: chat_title.to_string(),
            preview: message_text.to_string(),
        };
    }
    if is_silent {
        return NotificationDecision {
            should_notify: false,
            suppress_reason: Some("silent_mode".into()),
            title: chat_title.to_string(),
            preview: message_text.to_string(),
        };
    }
    if blocked_uids.iter().any(|b| b == author_id) {
        return NotificationDecision {
            should_notify: false,
            suppress_reason: Some("blocked_sender".into()),
            title: chat_title.to_string(),
            preview: message_text.to_string(),
        };
    }
    let preview: String = message_text.trim().chars().take(120).collect();
    let title = if chat_title.trim().is_empty() {
        sender_name.to_string()
    } else {
        chat_title.to_string()
    };
    NotificationDecision {
        should_notify: true,
        suppress_reason: None,
        title,
        preview,
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalizedStrings {
    pub app_name: String,
    pub tagline: String,
    pub sign_in_subtitle: String,
    pub sign_in_with_google: String,
    pub checking_session: String,
    pub tab_chats: String,
    pub tab_contacts: String,
    pub tab_ai_bots: String,
    pub tab_calls: String,
    pub tab_settings: String,
    pub filter_all: String,
    pub filter_direct: String,
    pub filter_groups: String,
    pub filter_channels: String,
    pub filter_bots: String,
    pub filter_archived: String,
    pub search_hint: String,
    pub new_chat: String,
    pub new_group: String,
    pub new_channel: String,
    pub new_ai_bot: String,
    pub empty_chats_title: String,
    pub empty_chats_body: String,
    pub message_placeholder: String,
    pub reply: String,
    pub react: String,
    pub forward: String,
    pub pin: String,
    pub unpin: String,
    pub edit: String,
    pub delete: String,
    pub copy: String,
    pub pinned_label: String,
    pub draft_prefix: String,
    pub silent_mode: String,
    pub edited_label: String,
    pub ai_streaming: String,
    pub model_label: String,
    pub system_instruction_label: String,
    pub save: String,
    pub cancel: String,
    pub create: String,
    pub title_label: String,
    pub description_label: String,
    pub public_channel_label: String,
    pub contacts_title: String,
    pub add_contact: String,
    pub lookup_handle_hint: String,
    pub find_by_handle: String,
    pub handle_not_found: String,
    pub start_chat: String,
    pub block: String,
    pub unblock: String,
    pub blocked_badge: String,
    pub calls_title: String,
    pub audio_call: String,
    pub video_call: String,
    pub local_preview_notice: String,
    pub end_call: String,
    pub mic_muted: String,
    pub mic_active: String,
    pub speaker_on: String,
    pub settings_title: String,
    pub profile_section: String,
    pub display_name_label: String,
    pub username_label: String,
    pub bio_label: String,
    pub appearance_section: String,
    pub dark_mode_label: String,
    pub language_label: String,
    pub sessions_section: String,
    pub current_device_label: String,
    pub sign_out: String,
    pub archive: String,
    pub unarchive: String,
    pub photo_attachment: String,
    pub file_attachment: String,
    pub voice_note: String,
    pub recording_voice: String,
    pub stop_recording: String,
    pub public_channels_discover: String,
    pub join_channel: String,
    pub joined: String,
    pub members_count: String,
    pub invite_code_label: String,
    pub saved_messages_title: String,
    pub retry: String,
}

pub fn localized_strings(lang: &str) -> LocalizedStrings {
    if normalize_language(lang) == "en" {
        LocalizedStrings {
      app_name: "Rustgram".into(),
      tagline: "Fast cloud messenger with Rust Core & Gemini AI".into(),
      sign_in_subtitle: "Real-time chats, groups, broadcast channels, and multi-turn Gemini AI bots secured by your Google account.".into(),
      sign_in_with_google: "Sign in with Google".into(),
      checking_session: "Checking session...".into(),
      tab_chats: "Chats".into(),
      tab_contacts: "Contacts".into(),
      tab_ai_bots: "AI Bots".into(),
      tab_calls: "Calls".into(),
      tab_settings: "Settings".into(),
      filter_all: "All".into(),
      filter_direct: "Direct".into(),
      filter_groups: "Groups".into(),
      filter_channels: "Channels".into(),
      filter_bots: "AI Bots".into(),
      filter_archived: "Archived".into(),
      search_hint: "Search chats and messages...".into(),
      new_chat: "New Chat".into(),
      new_group: "Create Group".into(),
      new_channel: "Create Channel".into(),
      new_ai_bot: "New AI Bot".into(),
      empty_chats_title: "No conversations here yet".into(),
      empty_chats_body: "Create a group, channel, direct chat, or launch a Gemini AI assistant.".into(),
      message_placeholder: "Message...".into(),
      reply: "Reply".into(),
      react: "React".into(),
      forward: "Forward".into(),
      pin: "Pin".into(),
      unpin: "Unpin".into(),
      edit: "Edit".into(),
      delete: "Delete".into(),
      copy: "Copy".into(),
      pinned_label: "Pinned".into(),
      draft_prefix: "Draft:".into(),
      silent_mode: "Silent".into(),
      edited_label: "edited".into(),
      ai_streaming: "Gemini is thinking...".into(),
      model_label: "Gemini Model".into(),
      system_instruction_label: "System Instruction (Bot Role)".into(),
      save: "Save".into(),
      cancel: "Cancel".into(),
      create: "Create".into(),
      title_label: "Title".into(),
      description_label: "Description".into(),
      public_channel_label: "Public channel (discoverable by other users)".into(),
      contacts_title: "Contacts".into(),
      add_contact: "Add Contact".into(),
      lookup_handle_hint: "Lookup by @username".into(),
      find_by_handle: "Lookup".into(),
      handle_not_found: "No user found with that @username".into(),
      start_chat: "Message".into(),
      block: "Block".into(),
      unblock: "Unblock".into(),
      blocked_badge: "Blocked".into(),
      calls_title: "Calls & Diagnostics".into(),
      audio_call: "Mic Audio Check".into(),
      video_call: "Session Preview".into(),
      local_preview_notice: "Local microphone & session check mode: live P2P WebRTC calls require signaling/TURN servers, and end-to-end encryption requires X3DH/Double Ratchet key exchange.".into(),
      end_call: "End Call".into(),
      mic_muted: "Mic Muted".into(),
      mic_active: "Mic Active".into(),
      speaker_on: "Speaker On".into(),
      settings_title: "Settings".into(),
      profile_section: "Profile".into(),
      display_name_label: "Display Name".into(),
      username_label: "Username (@handle)".into(),
      bio_label: "Bio".into(),
      appearance_section: "Appearance & Language".into(),
      dark_mode_label: "Dark Theme".into(),
      language_label: "Interface Language".into(),
      sessions_section: "Active Sessions".into(),
      current_device_label: "Current Android Device • Rust Core + Firebase".into(),
      sign_out: "Sign Out".into(),
      archive: "Archive".into(),
      unarchive: "Unarchive".into(),
      photo_attachment: "Photo".into(),
      file_attachment: "File".into(),
      voice_note: "Voice".into(),
      recording_voice: "Recording audio...".into(),
      stop_recording: "Send".into(),
      public_channels_discover: "Public Channels".into(),
      join_channel: "Subscribe".into(),
      joined: "Joined".into(),
      members_count: "members".into(),
      invite_code_label: "Invite Code".into(),
      saved_messages_title: "Saved Messages".into(),
      retry: "Retry".into(),
    }
    } else {
        LocalizedStrings {
      app_name: "Rustgram".into(),
      tagline: "Облачный мессенджер на Rust Core и Gemini AI".into(),
      sign_in_subtitle: "Синхронизация чатов, групп, каналов и многоходовых диалогов с Gemini через защищённый профиль Google.".into(),
      sign_in_with_google: "Войти через Google".into(),
      checking_session: "Проверка сессии...".into(),
      tab_chats: "Чаты".into(),
      tab_contacts: "Контакты".into(),
      tab_ai_bots: "AI Боты".into(),
      tab_calls: "Звонки".into(),
      tab_settings: "Настройки".into(),
      filter_all: "Все".into(),
      filter_direct: "Личные".into(),
      filter_groups: "Группы".into(),
      filter_channels: "Каналы".into(),
      filter_bots: "Боты".into(),
      filter_archived: "Архив".into(),
      search_hint: "Поиск чатов и сообщений...".into(),
      new_chat: "Новый чат".into(),
      new_group: "Создать группу".into(),
      new_channel: "Создать канал".into(),
      new_ai_bot: "Новый AI-бот".into(),
      empty_chats_title: "Нет чатов в этой категории".into(),
      empty_chats_body: "Создайте группу, канал, личный чат или запустите диалог с Gemini AI.".into(),
      message_placeholder: "Сообщение...".into(),
      reply: "Ответить".into(),
      react: "Реакция".into(),
      forward: "Переслать".into(),
      pin: "Закрепить".into(),
      unpin: "Открепить".into(),
      edit: "Редактировать".into(),
      delete: "Удалить".into(),
      copy: "Копировать".into(),
      pinned_label: "Закреплено".into(),
      draft_prefix: "Черновик:".into(),
      silent_mode: "Без звука".into(),
      edited_label: "изм.".into(),
      ai_streaming: "Gemini печатает...".into(),
      model_label: "Модель Gemini".into(),
      system_instruction_label: "Системная роль (System Instruction)".into(),
      save: "Сохранить".into(),
      cancel: "Отмена".into(),
      create: "Создать".into(),
      title_label: "Название".into(),
      description_label: "Описание".into(),
      public_channel_label: "Публичный канал (виден другим пользователям)".into(),
      contacts_title: "Контакты".into(),
      add_contact: "Добавить контакт".into(),
      lookup_handle_hint: "Найти по @username".into(),
      find_by_handle: "Найти".into(),
      handle_not_found: "Пользователь с таким @username не найден".into(),
      start_chat: "Написать".into(),
      block: "Заблокировать".into(),
      unblock: "Разблокировать".into(),
      blocked_badge: "В чёрном списке".into(),
      calls_title: "Звонки и диагностика".into(),
      audio_call: "Проверка микрофона".into(),
      video_call: "Тест сессии".into(),
      local_preview_notice: "Локальная диагностика микрофона и журнала: полноценные P2P WebRTC-звонки требуют сервер сигналинга/TURN, а E2EE — обмена ключами X3DH/Double Ratchet.".into(),
      end_call: "Завершить".into(),
      mic_muted: "Микрофон выкл.".into(),
      mic_active: "Микрофон вкл.".into(),
      speaker_on: "Громкая связь".into(),
      settings_title: "Настройки".into(),
      profile_section: "Профиль".into(),
      display_name_label: "Имя".into(),
      username_label: "Имя пользователя (@username)".into(),
      bio_label: "О себе".into(),
      appearance_section: "Оформление и язык".into(),
      dark_mode_label: "Тёмная тема".into(),
      language_label: "Язык интерфейса".into(),
      sessions_section: "Активные сессии".into(),
      current_device_label: "Текущее Android-устройство • Rust Core + Firebase".into(),
      sign_out: "Выйти из аккаунта".into(),
      archive: "В архив".into(),
      unarchive: "Из архива".into(),
      photo_attachment: "Фото".into(),
      file_attachment: "Файл".into(),
      voice_note: "Голосовое".into(),
      recording_voice: "Запись аудио...".into(),
      stop_recording: "Отправить".into(),
      public_channels_discover: "Публичные каналы".into(),
      join_channel: "Подписаться".into(),
      joined: "Вы подписаны".into(),
      members_count: "участн.".into(),
      invite_code_label: "Код приглашения".into(),
      saved_messages_title: "Избранное".into(),
      retry: "Повторить".into(),
    }
    }
}

#[derive(Debug, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case")]
pub enum CoreCommand {
    CoreInfo,
    SanitizeId {
        raw: String,
        fallback: String,
    },
    FormatError {
        raw_message: String,
        op_type: OperationType,
        path: Option<String>,
        user_id: Option<String>,
    },
    BuildInitialProfile {
        uid: String,
        default_display_name: String,
        default_language: String,
    },
    BuildProfileUpdate {
        uid: String,
        display_name: String,
        username: String,
        bio: String,
        avatar_color_hex: String,
        language_pref: String,
        dark_mode_pref: bool,
        blocked_uids: Vec<String>,
    },
    NormalizeLookupHandle {
        raw_username: String,
    },
    BuildContactEntry {
        owner_uid: String,
        contact_uid: String,
        display_name: String,
        username: String,
        bio: String,
        avatar_color_hex: String,
        is_favorite: bool,
        is_blocked: bool,
    },
    SortContacts {
        contacts: Vec<ContactEntry>,
    },
    CanMessageContact {
        contacts: Vec<ContactEntry>,
        target_uid: String,
    },
    PrepareSendMessage {
        uid: String,
        chat_id: String,
        message_id: String,
        text: String,
        sender_name: String,
        sender_type: String,
        member_uids: Vec<String>,
        is_public: bool,
        reply_to_message_id: String,
        reply_preview_text: String,
        forwarded_from_name: String,
        attachment_type: String,
        attachment_name: String,
        attachment_data: String,
        is_silent: bool,
    },
    PrepareEditMessage {
        uid: String,
        author_id: String,
        new_text: String,
    },
    ToggleReaction {
        uid: String,
        reactions: BTreeMap<String, Vec<String>>,
        emoji: String,
    },
    BuildChatRoom {
        uid: String,
        chat_type: String,
        title: String,
        description: String,
        avatar_color_hex: String,
        extra_member_uids: Vec<String>,
        is_public: bool,
        bot_role_id: String,
        bot_model_name: String,
        bot_system_prompt: String,
        initial_preview: String,
        initial_sender: String,
        chat_id: String,
    },
    JoinPublicChannel {
        uid: String,
        chat: ChatRoom,
    },
    LeavePublicChannel {
        uid: String,
        chat: ChatRoom,
    },
    AddGroupMember {
        uid: String,
        chat: ChatRoom,
        new_member_uid: String,
    },
    PlanReadReceipts {
        uid: String,
        messages: Vec<ChatMessage>,
    },
    SortChats {
        chats: Vec<ChatRoom>,
    },
    ToggleArchiveChat {
        uid: String,
        archived_by_uids: Vec<String>,
        archive: bool,
    },
    FilterChatWorkspace {
        chats: Vec<ChatRoom>,
        public_channels: Vec<ChatRoom>,
        current_uid: String,
        filter: String,
        query: String,
    },
    SearchMessages {
        messages: Vec<ChatMessage>,
        query: String,
    },
    PlanDraftMutation {
        uid: String,
        chat_id: String,
        text: String,
    },
    OrderMessages {
        messages: Vec<ChatMessage>,
    },
    BuildDraftsMap {
        entries: Vec<DraftEntry>,
    },
    BuildCallLog {
        uid: String,
        call_id: String,
        peer_name: String,
        call_type: String,
        status: String,
        duration_seconds: i64,
    },
    ValidateAttachment {
        attachment_type: String,
        attachment_name: String,
        attachment_data: String,
    },
    FormatDocumentSize {
        size_bytes: u64,
    },
    FormatVoiceFilename {
        duration_secs: u64,
    },
    PrepareGeminiRequest {
        requested_model: String,
        system_prompt: String,
        history_messages: Vec<ChatMessage>,
        user_prompt: String,
        has_image: bool,
    },
    FinalizeGeminiStream {
        accumulated: String,
    },
    GetGeminiPresets,
    GetGeminiModels,
    GetLocalizedStrings {
        lang: String,
    },
    DeterministicIds {
        uid: String,
        peer_uid: Option<String>,
        role_id: Option<String>,
    },
    EvaluateNotification {
        current_uid: String,
        author_id: String,
        sender_name: String,
        chat_title: String,
        message_text: String,
        is_silent: bool,
        blocked_uids: Vec<String>,
    },
    CryptoDigest {
        payload: String,
    },
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DispatchEnvelope<T: Serialize> {
    ok: bool,
    data: Option<T>,
    error: Option<String>,
}

fn ok_json<T: Serialize>(value: T) -> String {
    serde_json::to_string(&DispatchEnvelope {
        ok: true,
        data: Some(value),
        error: None,
    })
    .unwrap_or_else(|e| err_json(&e.to_string()))
}

fn err_json(msg: &str) -> String {
    serde_json::to_string(&DispatchEnvelope::<serde_json::Value> {
        ok: false,
        data: None,
        error: Some(msg.to_string()),
    })
    .unwrap_or_else(|_| r#"{"ok":false,"data":null,"error":"serialization failed"}"#.to_string())
}

pub fn execute_command_json(raw_json: &str) -> String {
    let cmd: CoreCommand = match serde_json::from_str(raw_json) {
        Ok(c) => c,
        Err(e) => return err_json(&format!("Invalid command JSON: {e}")),
    };

    match cmd {
        CoreCommand::CoreInfo => ok_json(core_info()),
        CoreCommand::SanitizeId { raw, fallback } => ok_json(sanitize_id(&raw, &fallback)),
        CoreCommand::FormatError {
            raw_message,
            op_type,
            path,
            user_id,
        } => ok_json(format_structured_error(
            &raw_message,
            op_type,
            path.as_deref(),
            user_id.as_deref(),
        )),
        CoreCommand::BuildInitialProfile {
            uid,
            default_display_name,
            default_language,
        } => match build_initial_profile(&uid, &default_display_name, &default_language) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::BuildProfileUpdate {
            uid,
            display_name,
            username,
            bio,
            avatar_color_hex,
            language_pref,
            dark_mode_pref,
            blocked_uids,
        } => match build_profile_update(
            &uid,
            &display_name,
            &username,
            &bio,
            &avatar_color_hex,
            &language_pref,
            dark_mode_pref,
            blocked_uids,
        ) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::NormalizeLookupHandle { raw_username } => {
            ok_json(normalize_lookup_handle(&raw_username))
        }
        CoreCommand::BuildContactEntry {
            owner_uid,
            contact_uid,
            display_name,
            username,
            bio,
            avatar_color_hex,
            is_favorite,
            is_blocked,
        } => match build_contact_entry(
            &owner_uid,
            &contact_uid,
            &display_name,
            &username,
            &bio,
            &avatar_color_hex,
            is_favorite,
            is_blocked,
        ) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::SortContacts { contacts } => ok_json(sort_contacts(contacts)),
        CoreCommand::CanMessageContact {
            contacts,
            target_uid,
        } => match can_message_contact(&contacts, &target_uid) {
            Ok(()) => ok_json(true),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::PrepareSendMessage {
            uid,
            chat_id,
            message_id,
            text,
            sender_name,
            sender_type,
            member_uids,
            is_public,
            reply_to_message_id,
            reply_preview_text,
            forwarded_from_name,
            attachment_type,
            attachment_name,
            attachment_data,
            is_silent,
        } => match prepare_send_message(
            &uid,
            &chat_id,
            &message_id,
            &text,
            &sender_name,
            &sender_type,
            member_uids,
            is_public,
            &reply_to_message_id,
            &reply_preview_text,
            &forwarded_from_name,
            &attachment_type,
            &attachment_name,
            &attachment_data,
            is_silent,
        ) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::PrepareEditMessage {
            uid,
            author_id,
            new_text,
        } => match prepare_edit_message(&uid, &author_id, &new_text) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::ToggleReaction {
            uid,
            reactions,
            emoji,
        } => match toggle_reaction_map(&uid, reactions, &emoji) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::BuildChatRoom {
            uid,
            chat_type,
            title,
            description,
            avatar_color_hex,
            extra_member_uids,
            is_public,
            bot_role_id,
            bot_model_name,
            bot_system_prompt,
            initial_preview,
            initial_sender,
            chat_id,
        } => match build_chat_room(
            &uid,
            &chat_type,
            &title,
            &description,
            &avatar_color_hex,
            extra_member_uids,
            is_public,
            &bot_role_id,
            &bot_model_name,
            &bot_system_prompt,
            &initial_preview,
            &initial_sender,
            &chat_id,
        ) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::JoinPublicChannel { uid, chat } => {
            match prepare_join_public_channel(&uid, &chat) {
                Ok(v) => ok_json(v),
                Err(e) => err_json(&e.to_string()),
            }
        }
        CoreCommand::LeavePublicChannel { uid, chat } => {
            match prepare_leave_public_channel(&uid, &chat) {
                Ok(v) => ok_json(v),
                Err(e) => err_json(&e.to_string()),
            }
        }
        CoreCommand::AddGroupMember {
            uid,
            chat,
            new_member_uid,
        } => match prepare_add_group_member(&uid, &chat, &new_member_uid) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::PlanReadReceipts { uid, messages } => {
            match plan_read_receipts(&uid, &messages) {
                Ok(v) => ok_json(v),
                Err(e) => err_json(&e.to_string()),
            }
        }
        CoreCommand::SortChats { chats } => ok_json(sort_chats_by_activity(chats)),
        CoreCommand::ToggleArchiveChat {
            uid,
            archived_by_uids,
            archive,
        } => match toggle_archive_list(&uid, &archived_by_uids, archive) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::FilterChatWorkspace {
            chats,
            public_channels,
            current_uid,
            filter,
            query,
        } => ok_json(filter_chat_workspace(
            chats,
            public_channels,
            &current_uid,
            ChatFilter::parse(&filter),
            &query,
        )),
        CoreCommand::SearchMessages { messages, query } => {
            ok_json(search_messages_in_chat(messages, &query))
        }
        CoreCommand::PlanDraftMutation { uid, chat_id, text } => {
            match plan_draft_mutation(&uid, &chat_id, &text) {
                Ok(v) => ok_json(v),
                Err(e) => err_json(&e.to_string()),
            }
        }
        CoreCommand::OrderMessages { messages } => ok_json(order_and_dedup_messages(messages)),
        CoreCommand::BuildDraftsMap { entries } => ok_json(build_drafts_map(entries)),
        CoreCommand::BuildCallLog {
            uid,
            call_id,
            peer_name,
            call_type,
            status,
            duration_seconds,
        } => match build_call_log_entry(
            &uid,
            &call_id,
            &peer_name,
            &call_type,
            &status,
            duration_seconds,
        ) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::ValidateAttachment {
            attachment_type,
            attachment_name,
            attachment_data,
        } => match validate_attachment(&attachment_type, &attachment_name, &attachment_data) {
            Ok(v) => ok_json(v),
            Err(e) => err_json(&e.to_string()),
        },
        CoreCommand::FormatDocumentSize { size_bytes } => {
            ok_json(format_document_size_kb(size_bytes))
        }
        CoreCommand::FormatVoiceFilename { duration_secs } => {
            ok_json(format_voice_note_filename(duration_secs))
        }
        CoreCommand::PrepareGeminiRequest {
            requested_model,
            system_prompt,
            history_messages,
            user_prompt,
            has_image,
        } => ok_json(prepare_gemini_request(
            &requested_model,
            &system_prompt,
            &history_messages,
            &user_prompt,
            has_image,
        )),
        CoreCommand::FinalizeGeminiStream { accumulated } => {
            match finalize_stream_response(&accumulated) {
                Ok(v) => ok_json(v),
                Err(e) => err_json(&e),
            }
        }
        CoreCommand::GetGeminiPresets => ok_json(default_presets()),
        CoreCommand::GetGeminiModels => ok_json(AVAILABLE_MODELS),
        CoreCommand::GetLocalizedStrings { lang } => ok_json(localized_strings(&lang)),
        CoreCommand::DeterministicIds {
            uid,
            peer_uid,
            role_id,
        } => {
            let clean_uid = match require_authenticated_uid(Some(&uid)) {
                Ok(u) => u,
                Err(e) => return err_json(&e.to_string()),
            };
            let saved = saved_messages_chat_id(&clean_uid).ok();
            let dm = peer_uid
                .as_deref()
                .and_then(|p| direct_pair_chat_id(&clean_uid, p).ok());
            let bot = role_id
                .as_deref()
                .and_then(|r| starter_bot_chat_id(r, &clean_uid).ok());
            let invite = generate_invite_code(&saved.clone().unwrap_or_default());
            ok_json(serde_json::json!({
              "savedChatId": saved,
              "directChatId": dm,
              "botChatId": bot,
              "inviteSample": invite,
            }))
        }
        CoreCommand::EvaluateNotification {
            current_uid,
            author_id,
            sender_name,
            chat_title,
            message_text,
            is_silent,
            blocked_uids,
        } => ok_json(evaluate_notification_decision(
            &current_uid,
            &author_id,
            &sender_name,
            &chat_title,
            &message_text,
            is_silent,
            &blocked_uids,
        )),
        CoreCommand::CryptoDigest { payload } => ok_json(serde_json::json!({
          "sha256": sha256_hex(payload.as_bytes()),
          "integrity": message_integrity_fingerprint("sys", "core", &payload, "none"),
        })),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dispatch_roundtrip_works_for_core_domains() {
        let info_res: serde_json::Value =
            serde_json::from_str(&execute_command_json(r#"{"op":"core_info"}"#)).unwrap();
        assert_eq!(info_res["ok"], true);
        assert_eq!(info_res["data"]["cratesCount"], 15);

        let notif =
            evaluate_notification_decision("u1", "u2", "Bob", "Rust Team", "Deployed!", true, &[]);
        assert!(!notif.should_notify);
        assert_eq!(notif.suppress_reason.as_deref(), Some("silent_mode"));
    }
}
