use serde::{Deserialize, Serialize};
use thiserror::Error;

pub mod limits {
    pub const MAX_UID_LEN: usize = 128;
    pub const MAX_ID_LEN: usize = 64;
    pub const MAX_DISPLAY_NAME_LEN: usize = 60;
    pub const MIN_USERNAME_LEN: usize = 3;
    pub const MAX_USERNAME_LEN: usize = 32;
    pub const MAX_BIO_LEN: usize = 200;
    pub const MAX_CHAT_TITLE_LEN: usize = 80;
    pub const MAX_CHAT_DESCRIPTION_LEN: usize = 300;
    pub const MAX_MESSAGE_TEXT_LEN: usize = 8000;
    pub const MAX_DRAFT_TEXT_LEN: usize = 4000;
    pub const MAX_REPLY_PREVIEW_LEN: usize = 200;
    pub const MAX_LAST_PREVIEW_LEN: usize = 200;
    pub const MAX_ATTACHMENT_NAME_LEN: usize = 120;
    pub const MAX_ATTACHMENT_DATA_LEN: usize = 400_000;
    pub const MAX_BASE64_MEDIA_LEN: usize = 380_000;
    pub const MAX_BOT_PROMPT_LEN: usize = 1500;
    pub const MAX_MODEL_NAME_LEN: usize = 64;
    pub const MAX_MEMBERS: usize = 50;
    pub const MAX_ADMINS: usize = 20;
    pub const MAX_REACTION_TYPES: usize = 20;
    pub const MAX_REACTORS_PER_EMOJI: usize = 50;
    pub const MAX_CALL_DURATION_SECS: i64 = 86_400;
    pub const MAX_AI_HISTORY_MESSAGES: usize = 24;
}

#[derive(Debug, Error, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum RustDomainError {
    #[error("Authentication required: {0}")]
    Unauthenticated(String),
    #[error("Invalid identifier: {0}")]
    InvalidIdentifier(String),
    #[error("Validation failed: {0}")]
    ValidationFailed(String),
    #[error("Permission denied: {0}")]
    PermissionDenied(String),
    #[error("Blocked user or contact: {0}")]
    Blocked(String),
    #[error("Payload serialization error: {0}")]
    SerializationError(String),
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum OperationType {
    Create,
    Update,
    Delete,
    List,
    Get,
    Write,
}

impl OperationType {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Create => "create",
            Self::Update => "update",
            Self::Delete => "delete",
            Self::List => "list",
            Self::Get => "get",
            Self::Write => "write",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct UserProfile {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub uid: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default)]
    pub username: String,
    #[serde(default)]
    pub bio: String,
    #[serde(default = "default_avatar_color")]
    pub avatar_color_hex: String,
    #[serde(default = "default_lang")]
    pub language_pref: String,
    #[serde(default = "default_true")]
    pub dark_mode_pref: bool,
    #[serde(default)]
    pub blocked_uids: Vec<String>,
    #[serde(default)]
    pub last_seen_epoch_ms: Option<i64>,
    #[serde(default)]
    pub created_at_epoch_ms: Option<i64>,
    #[serde(default)]
    pub updated_at_epoch_ms: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct HandleEntry {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub owner_uid: String,
    #[serde(default)]
    pub username: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default)]
    pub bio: String,
    #[serde(default = "default_blue_color")]
    pub avatar_color_hex: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ContactEntry {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub user_id: String,
    #[serde(default)]
    pub contact_uid: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default)]
    pub username: String,
    #[serde(default)]
    pub bio: String,
    #[serde(default = "default_blue_color")]
    pub avatar_color_hex: String,
    #[serde(default)]
    pub is_favorite: bool,
    #[serde(default)]
    pub is_blocked: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct DraftEntry {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub user_id: String,
    #[serde(default)]
    pub chat_id: String,
    #[serde(default)]
    pub text: String,
    #[serde(default)]
    pub updated_at_epoch_ms: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct CallLogEntry {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub user_id: String,
    #[serde(default)]
    pub peer_name: String,
    #[serde(default = "default_audio")]
    pub call_type: String,
    #[serde(default = "default_completed")]
    pub status: String,
    #[serde(default)]
    pub duration_seconds: i64,
    #[serde(default)]
    pub created_at_epoch_ms: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ChatRoom {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub owner_id: String,
    #[serde(default = "default_direct")]
    pub chat_type: String,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub description: String,
    #[serde(default = "default_blue_color")]
    pub avatar_color_hex: String,
    #[serde(default)]
    pub member_uids: Vec<String>,
    #[serde(default)]
    pub admin_uids: Vec<String>,
    #[serde(default)]
    pub is_public: bool,
    #[serde(default)]
    pub invite_code: String,
    #[serde(default)]
    pub pinned_message_id: String,
    #[serde(default)]
    pub pinned_preview: String,
    #[serde(default)]
    pub archived_by_uids: Vec<String>,
    #[serde(default)]
    pub bot_role_id: String,
    #[serde(default)]
    pub bot_model_name: String,
    #[serde(default)]
    pub bot_system_prompt: String,
    #[serde(default)]
    pub last_message_preview: String,
    #[serde(default)]
    pub last_message_sender: String,
    #[serde(default)]
    pub last_message_at_epoch_ms: Option<i64>,
    #[serde(default)]
    pub created_at_epoch_ms: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ChatMessage {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub chat_id: String,
    #[serde(default)]
    pub author_id: String,
    #[serde(default = "default_sender_user")]
    pub sender_type: String,
    #[serde(default)]
    pub sender_name: String,
    #[serde(default)]
    pub member_uids: Vec<String>,
    #[serde(default)]
    pub is_public: bool,
    #[serde(default)]
    pub text: String,
    #[serde(default)]
    pub reply_to_message_id: String,
    #[serde(default)]
    pub reply_preview_text: String,
    #[serde(default)]
    pub forwarded_from_name: String,
    #[serde(default)]
    pub reactions: std::collections::BTreeMap<String, Vec<String>>,
    #[serde(default = "default_attachment_none")]
    pub attachment_type: String,
    #[serde(default)]
    pub attachment_name: String,
    #[serde(default)]
    pub attachment_data: String,
    #[serde(default)]
    pub is_silent: bool,
    #[serde(default)]
    pub is_edited: bool,
    #[serde(default = "default_one")]
    pub view_count: i64,
    #[serde(default)]
    pub read_by_uids: Vec<String>,
    #[serde(default)]
    pub created_at_epoch_ms: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct StructuredErrorReport {
    pub error: String,
    pub operation_type: String,
    pub path: Option<String>,
    pub user_id: Option<String>,
    pub is_permission_denied: bool,
    pub is_network_error: bool,
}

pub fn format_structured_error(
    raw_message: &str,
    op: OperationType,
    path: Option<&str>,
    user_id: Option<&str>,
) -> StructuredErrorReport {
    let lower = raw_message.to_lowercase();
    StructuredErrorReport {
        error: raw_message.trim().chars().take(500).collect(),
        operation_type: op.as_str().to_string(),
        path: path.map(|s| s.to_string()),
        user_id: user_id.map(|s| s.to_string()),
        is_permission_denied: lower.contains("permission_denied")
            || lower.contains("permission denied"),
        is_network_error: lower.contains("unavailable")
            || lower.contains("network")
            || lower.contains("timeout"),
    }
}

pub fn sanitize_id(raw: &str, fallback_id: &str) -> String {
    let cleaned: String = raw
        .trim()
        .to_lowercase()
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '_' || c == '-' {
                c
            } else {
                '_'
            }
        })
        .take(limits::MAX_ID_LEN)
        .collect();
    if cleaned.is_empty() {
        fallback_id.chars().take(limits::MAX_ID_LEN).collect()
    } else {
        cleaned
    }
}

pub fn validate_hex_color(hex: &str, fallback: &str) -> String {
    let trimmed = hex.trim();
    if trimmed.starts_with('#')
        && (trimmed.len() == 4 || trimmed.len() == 7 || trimmed.len() == 9)
        && trimmed[1..].chars().all(|c| c.is_ascii_hexdigit())
    {
        trimmed.to_uppercase()
    } else {
        fallback.to_string()
    }
}

pub fn truncate_chars(input: &str, max_chars: usize) -> String {
    input.chars().take(max_chars).collect()
}

fn default_avatar_color() -> String {
    "#D96B27".to_string()
}
fn default_blue_color() -> String {
    "#1D4E89".to_string()
}
fn default_lang() -> String {
    "ru".to_string()
}
fn default_true() -> bool {
    true
}
fn default_audio() -> String {
    "audio".to_string()
}
fn default_completed() -> String {
    "completed".to_string()
}
fn default_direct() -> String {
    "direct".to_string()
}
fn default_sender_user() -> String {
    "user".to_string()
}
fn default_attachment_none() -> String {
    "none".to_string()
}
fn default_one() -> i64 {
    1
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sanitize_id_replaces_invalid_chars_and_truncates() {
        assert_eq!(
            sanitize_id("@Alice.Rust-01!", "fallback"),
            "_alice_rust-01_"
        );
        assert_eq!(sanitize_id("   ", "uuid_1"), "uuid_1");
        let long = "a".repeat(100);
        assert_eq!(sanitize_id(&long, "x").len(), limits::MAX_ID_LEN);
    }

    #[test]
    fn validate_hex_color_accepts_valid_and_falls_back() {
        assert_eq!(validate_hex_color("#d96b27", "#000000"), "#D96B27");
        assert_eq!(validate_hex_color("invalid", "#1D4E89"), "#1D4E89");
    }
}
