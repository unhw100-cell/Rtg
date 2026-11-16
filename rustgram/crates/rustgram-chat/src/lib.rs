use rustgram_auth::require_authenticated_uid;
use rustgram_common::{limits, ChatRoom, RustDomainError};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ChatFilter {
    All,
    Direct,
    Groups,
    Channels,
    Bots,
    Archived,
}

impl ChatFilter {
    pub fn parse(raw: &str) -> Self {
        match raw.trim().to_uppercase().as_str() {
            "DIRECT" => Self::Direct,
            "GROUPS" => Self::Groups,
            "CHANNELS" => Self::Channels,
            "BOTS" => Self::Bots,
            "ARCHIVED" => Self::Archived,
            _ => Self::All,
        }
    }
}

pub fn toggle_archive_list(
    uid: &str,
    archived_by_uids: &[String],
    archive: bool,
) -> Result<Vec<String>, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let mut out: Vec<String> = archived_by_uids
        .iter()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty() && s != &clean_uid)
        .collect();
    if archive && out.len() < limits::MAX_MEMBERS {
        out.push(clean_uid);
    }
    Ok(out)
}

pub fn matches_chat_filter(chat: &ChatRoom, current_uid: &str, filter: ChatFilter) -> bool {
    let is_archived = chat.archived_by_uids.iter().any(|u| u == current_uid);
    match filter {
        ChatFilter::All => !is_archived,
        ChatFilter::Direct => !is_archived && chat.chat_type == "direct",
        ChatFilter::Groups => !is_archived && chat.chat_type == "group",
        ChatFilter::Channels => !is_archived && chat.chat_type == "channel",
        ChatFilter::Bots => !is_archived && chat.chat_type == "ai_bot",
        ChatFilter::Archived => is_archived,
    }
}

pub fn sort_chats_by_activity(mut chats: Vec<ChatRoom>) -> Vec<ChatRoom> {
    chats.sort_by(|a, b| {
        let ta = a
            .last_message_at_epoch_ms
            .or(a.created_at_epoch_ms)
            .unwrap_or(0);
        let tb = b
            .last_message_at_epoch_ms
            .or(b.created_at_epoch_ms)
            .unwrap_or(0);
        tb.cmp(&ta)
    });
    chats
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn archive_toggle_and_filter_work_consistently() {
        let archived = toggle_archive_list("u1", &[], true).unwrap();
        assert_eq!(archived, vec!["u1".to_string()]);
        let chat = ChatRoom {
            id: "c1".into(),
            owner_id: "u1".into(),
            chat_type: "group".into(),
            title: "Rust Team".into(),
            description: String::new(),
            avatar_color_hex: "#D96B27".into(),
            member_uids: vec!["u1".into()],
            admin_uids: vec!["u1".into()],
            is_public: false,
            invite_code: "C1".into(),
            pinned_message_id: String::new(),
            pinned_preview: String::new(),
            archived_by_uids: archived,
            bot_role_id: String::new(),
            bot_model_name: String::new(),
            bot_system_prompt: String::new(),
            last_message_preview: String::new(),
            last_message_sender: String::new(),
            last_message_at_epoch_ms: Some(100),
            created_at_epoch_ms: Some(100),
        };
        assert!(!matches_chat_filter(&chat, "u1", ChatFilter::All));
        assert!(matches_chat_filter(&chat, "u1", ChatFilter::Archived));
    }
}
