use rustgram_auth::require_authenticated_uid;
use rustgram_common::{
    limits, sanitize_id, truncate_chars, CallLogEntry, DraftEntry, RustDomainError,
};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(tag = "action", rename_all = "snake_case")]
pub enum DraftMutation {
    Delete { user_id: String, chat_id: String },
    Upsert { entry: DraftEntry },
}

pub fn plan_draft_mutation(
    uid: &str,
    chat_id: &str,
    text: &str,
) -> Result<DraftMutation, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let safe_chat_id = sanitize_id(chat_id, "draft");
    if text.trim().is_empty() {
        Ok(DraftMutation::Delete {
            user_id: clean_uid,
            chat_id: safe_chat_id,
        })
    } else {
        Ok(DraftMutation::Upsert {
            entry: DraftEntry {
                id: safe_chat_id.clone(),
                user_id: clean_uid,
                chat_id: safe_chat_id,
                text: truncate_chars(text, limits::MAX_DRAFT_TEXT_LEN),
                updated_at_epoch_ms: None,
            },
        })
    }
}

pub fn build_call_log_entry(
    uid: &str,
    call_id: &str,
    peer_name: &str,
    call_type: &str,
    status: &str,
    duration_seconds: i64,
) -> Result<CallLogEntry, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let valid_type = match call_type.trim() {
        "audio" | "video" | "group" => call_type.trim().to_string(),
        _ => "audio".to_string(),
    };
    let valid_status = match status.trim() {
        "completed" | "missed" | "preview" => status.trim().to_string(),
        _ => "preview".to_string(),
    };
    let clean_peer = {
        let t = peer_name.trim();
        if t.is_empty() {
            "Rustgram Peer".to_string()
        } else {
            truncate_chars(t, limits::MAX_DISPLAY_NAME_LEN)
        }
    };
    Ok(CallLogEntry {
        id: sanitize_id(call_id, "call"),
        user_id: clean_uid,
        peer_name: clean_peer,
        call_type: valid_type,
        status: valid_status,
        duration_seconds: duration_seconds.clamp(0, limits::MAX_CALL_DURATION_SECS),
        created_at_epoch_ms: None,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn draft_plan_deletes_blank_and_clamps_call_duration() {
        assert!(matches!(
            plan_draft_mutation("u1", "c1", "   ").unwrap(),
            DraftMutation::Delete { .. }
        ));
        let call = build_call_log_entry("u1", "id1", "", "video", "preview", 999_999).unwrap();
        assert_eq!(call.peer_name, "Rustgram Peer");
        assert_eq!(call.duration_seconds, limits::MAX_CALL_DURATION_SECS);
    }
}
