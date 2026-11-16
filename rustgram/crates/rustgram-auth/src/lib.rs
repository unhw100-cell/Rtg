use rustgram_common::{limits, sanitize_id, RustDomainError};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AuthSessionState {
    pub uid: String,
    pub display_name: String,
    pub is_authenticated: bool,
    pub provider: String,
}

pub fn require_authenticated_uid(uid: Option<&str>) -> Result<String, RustDomainError> {
    let raw = uid.unwrap_or("").trim();
    if raw.is_empty() {
        return Err(RustDomainError::Unauthenticated(
            "User must be signed in with Google before accessing Firestore.".to_string(),
        ));
    }
    if raw.len() > limits::MAX_UID_LEN {
        return Err(RustDomainError::InvalidIdentifier(
            "UID exceeds maximum allowed length.".to_string(),
        ));
    }
    Ok(raw.to_string())
}

pub fn saved_messages_chat_id(uid: &str) -> Result<String, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let prefix: String = clean_uid.chars().take(24).collect();
    Ok(sanitize_id(&format!("saved_{prefix}"), "saved_messages"))
}

pub fn direct_pair_chat_id(uid_a: &str, uid_b: &str) -> Result<String, RustDomainError> {
    let a = require_authenticated_uid(Some(uid_a))?;
    let b = require_authenticated_uid(Some(uid_b))?;
    let mut pair = [a, b];
    pair.sort();
    let p0: String = pair[0].chars().take(14).collect();
    let p1: String = pair[1].chars().take(14).collect();
    Ok(sanitize_id(&format!("dm_{p0}_{p1}"), "dm_chat"))
}

pub fn starter_bot_chat_id(role_id: &str, uid: &str) -> Result<String, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let uid_part: String = clean_uid.chars().take(20).collect();
    let role_clean = sanitize_id(role_id, "rust_architect");
    Ok(sanitize_id(
        &format!("bot_{role_clean}_{uid_part}"),
        "bot_chat",
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn require_authenticated_uid_rejects_empty() {
        assert!(matches!(
            require_authenticated_uid(Some("  ")),
            Err(RustDomainError::Unauthenticated(_))
        ));
        assert_eq!(
            require_authenticated_uid(Some("uid_123")).unwrap(),
            "uid_123"
        );
    }

    #[test]
    fn deterministic_chat_ids_are_order_independent_for_dm() {
        let ab = direct_pair_chat_id("uid_b", "uid_a").unwrap();
        let ba = direct_pair_chat_id("uid_a", "uid_b").unwrap();
        assert_eq!(ab, ba);
        assert_eq!(ab, "dm_uid_a_uid_b");
        assert_eq!(
            saved_messages_chat_id("alice123").unwrap(),
            "saved_alice123"
        );
    }
}
