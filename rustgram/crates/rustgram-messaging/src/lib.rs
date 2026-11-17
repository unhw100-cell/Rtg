use rustgram_auth::require_authenticated_uid;
use rustgram_common::{limits, sanitize_id, truncate_chars, ChatMessage, RustDomainError};
use rustgram_crypto::message_integrity_fingerprint;
use rustgram_media::validate_attachment;
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct OutgoingMessagePlan {
    pub message: ChatMessage,
    pub last_message_preview: String,
    pub last_message_sender: String,
    pub integrity_sha256: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ReadReceiptUpdate {
    pub message_id: String,
    pub read_by_uids: Vec<String>,
    pub view_count: i64,
}

#[allow(clippy::too_many_arguments)]
pub fn prepare_send_message(
    uid: &str,
    chat_id: &str,
    message_id: &str,
    text: &str,
    sender_name: &str,
    sender_type: &str,
    member_uids: Vec<String>,
    is_public: bool,
    reply_to_message_id: &str,
    reply_preview_text: &str,
    forwarded_from_name: &str,
    attachment_type: &str,
    attachment_name: &str,
    attachment_data: &str,
    is_silent: bool,
) -> Result<OutgoingMessagePlan, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let clean_chat_id = chat_id.trim();
    if clean_chat_id.is_empty()
        || clean_chat_id.len() > limits::MAX_UID_LEN
        || !clean_chat_id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-')
    {
        return Err(RustDomainError::InvalidIdentifier(
            "chatId must match [a-zA-Z0-9_-]{1,128}".into(),
        ));
    }
    let clean_chat_id = clean_chat_id.to_string();
    let clean_message_id = sanitize_id(message_id, "msg");

    let valid_sender_type = if sender_type.trim() == "ai_bot" {
        "ai_bot".to_string()
    } else {
        "user".to_string()
    };

    let attachment = validate_attachment(attachment_type, attachment_name, attachment_data)?;
    let clean_text = truncate_chars(text.trim(), limits::MAX_MESSAGE_TEXT_LEN);
    if clean_text.is_empty() && attachment.attachment_type == "none" {
        return Err(RustDomainError::ValidationFailed(
            "Message cannot be empty without an attachment.".into(),
        ));
    }

    let clean_sender = {
        let t = sender_name.trim();
        if t.is_empty() {
            "User".to_string()
        } else {
            truncate_chars(t, limits::MAX_DISPLAY_NAME_LEN)
        }
    };

    let mut safe_members: Vec<String> = Vec::new();
    for m in member_uids
        .into_iter()
        .chain(std::iter::once(clean_uid.clone()))
    {
        let trimmed = m.trim().to_string();
        if !trimmed.is_empty() && !safe_members.contains(&trimmed) {
            safe_members.push(trimmed);
            if safe_members.len() >= limits::MAX_MEMBERS {
                break;
            }
        }
    }

    let preview = build_message_preview(
        &clean_text,
        &attachment.attachment_type,
        &attachment.attachment_name,
    );
    let fingerprint = message_integrity_fingerprint(
        &clean_chat_id,
        &clean_uid,
        &clean_text,
        &attachment.attachment_type,
    );

    Ok(OutgoingMessagePlan {
        message: ChatMessage {
            id: clean_message_id,
            chat_id: clean_chat_id,
            author_id: clean_uid.clone(),
            sender_type: valid_sender_type,
            sender_name: clean_sender.clone(),
            member_uids: safe_members,
            is_public,
            text: clean_text,
            reply_to_message_id: truncate_chars(reply_to_message_id.trim(), limits::MAX_UID_LEN),
            reply_preview_text: truncate_chars(
                reply_preview_text.trim(),
                limits::MAX_REPLY_PREVIEW_LEN,
            ),
            forwarded_from_name: truncate_chars(
                forwarded_from_name.trim(),
                limits::MAX_DISPLAY_NAME_LEN,
            ),
            reactions: BTreeMap::new(),
            attachment_type: attachment.attachment_type,
            attachment_name: attachment.attachment_name,
            attachment_data: attachment.attachment_data,
            is_silent,
            is_edited: false,
            view_count: 1,
            read_by_uids: vec![clean_uid],
            created_at_epoch_ms: None,
        },
        last_message_preview: preview,
        last_message_sender: clean_sender,
        integrity_sha256: fingerprint,
    })
}

pub fn build_message_preview(text: &str, attachment_type: &str, attachment_name: &str) -> String {
    let raw = if !text.trim().is_empty() {
        text.trim().to_string()
    } else {
        match attachment_type {
            "image" => format!("[Photo] {attachment_name}"),
            "audio" => format!("[Voice] {attachment_name}"),
            "document" => format!("[File] {attachment_name}"),
            _ => String::new(),
        }
    };
    truncate_chars(&raw, limits::MAX_LAST_PREVIEW_LEN)
}

pub fn prepare_edit_message(
    uid: &str,
    author_id: &str,
    new_text: &str,
) -> Result<String, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    if clean_uid != author_id.trim() {
        return Err(RustDomainError::PermissionDenied(
            "Only the author can edit this message.".into(),
        ));
    }
    let cleaned = truncate_chars(new_text.trim(), limits::MAX_MESSAGE_TEXT_LEN);
    if cleaned.is_empty() {
        return Err(RustDomainError::ValidationFailed(
            "Edited message text cannot be blank.".into(),
        ));
    }
    Ok(cleaned)
}

pub fn toggle_reaction_map(
    uid: &str,
    mut reactions: BTreeMap<String, Vec<String>>,
    emoji: &str,
) -> Result<BTreeMap<String, Vec<String>>, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let clean_emoji = emoji.trim();
    if clean_emoji.is_empty() {
        return Err(RustDomainError::ValidationFailed(
            "Emoji cannot be empty".into(),
        ));
    }
    if !reactions.contains_key(clean_emoji) && reactions.len() >= limits::MAX_REACTION_TYPES {
        return Err(RustDomainError::ValidationFailed(
            "Maximum reaction variety per message reached.".into(),
        ));
    }

    let mut reactors = reactions.remove(clean_emoji).unwrap_or_default();
    if reactors.iter().any(|u| u == &clean_uid) {
        reactors.retain(|u| u != &clean_uid);
    } else if reactors.len() < limits::MAX_REACTORS_PER_EMOJI {
        reactors.push(clean_uid);
    }
    if !reactors.is_empty() {
        reactions.insert(clean_emoji.to_string(), reactors);
    }
    Ok(reactions)
}

pub fn plan_read_receipts(
    uid: &str,
    messages: &[ChatMessage],
) -> Result<Vec<ReadReceiptUpdate>, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let mut updates = Vec::new();
    for msg in messages {
        if msg.id.trim().is_empty() {
            continue;
        }
        let already_read = msg.read_by_uids.iter().any(|u| u == &clean_uid);
        if already_read || msg.author_id == clean_uid {
            continue;
        }
        if msg.read_by_uids.len() >= limits::MAX_MEMBERS {
            continue;
        }
        let mut readers = msg.read_by_uids.clone();
        readers.push(clean_uid.clone());
        let next_views = (msg.view_count.max(1) + 1).max(readers.len() as i64);
        updates.push(ReadReceiptUpdate {
            message_id: msg.id.clone(),
            read_by_uids: readers,
            view_count: next_views,
        });
    }
    Ok(updates)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prepare_send_and_toggle_reactions_enforces_rules() {
        let plan = prepare_send_message(
            "u1",
            "chat_1",
            "msg_1",
            "  Hello Rustgram!  ",
            "Alice",
            "user",
            vec!["u2".into()],
            false,
            "",
            "",
            "",
            "none",
            "",
            "",
            true,
        )
        .unwrap();
        assert_eq!(plan.message.text, "Hello Rustgram!");
        assert_eq!(plan.message.member_uids, vec!["u2", "u1"]);
        assert!(plan.message.is_silent);
        assert_eq!(plan.integrity_sha256.len(), 64);

        let r1 = toggle_reaction_map("u1", BTreeMap::new(), "🔥").unwrap();
        assert_eq!(r1.get("🔥").unwrap(), &vec!["u1".to_string()]);
        let r2 = toggle_reaction_map("u1", r1, "🔥").unwrap();
        assert!(!r2.contains_key("🔥"));

        let incoming = ChatMessage {
            author_id: "u2".into(),
            read_by_uids: vec!["u2".into()],
            view_count: 1,
            ..plan.message.clone()
        };
        let receipts = plan_read_receipts("u1", &[plan.message, incoming]).unwrap();
        assert_eq!(receipts.len(), 1);
        assert_eq!(receipts[0].read_by_uids, vec!["u2", "u1"]);
        assert_eq!(receipts[0].view_count, 2);
    }
}
