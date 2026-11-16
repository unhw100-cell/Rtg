use rustgram_auth::require_authenticated_uid;
use rustgram_common::{
    limits, sanitize_id, truncate_chars, validate_hex_color, HandleEntry, RustDomainError,
    UserProfile,
};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ProfileUpdatePlan {
    pub profile: UserProfile,
    pub handle: HandleEntry,
}

pub fn normalize_language(lang: &str) -> String {
    if lang.trim().eq_ignore_ascii_case("en") {
        "en".to_string()
    } else {
        "ru".to_string()
    }
}

pub fn normalize_username(raw: &str, uid: &str) -> String {
    let stripped = raw.trim().trim_start_matches('@');
    let sanitized = sanitize_id(stripped, "");
    let effective_len = sanitized.trim_matches('_').chars().count();
    let uid_suffix: String = sanitize_id(uid, "user").chars().take(6).collect();
    let candidate = if effective_len < limits::MIN_USERNAME_LEN {
        format!("user_{uid_suffix}")
    } else {
        sanitized
    };
    let truncated = truncate_chars(&candidate, limits::MAX_USERNAME_LEN);
    if truncated.chars().count() < limits::MIN_USERNAME_LEN {
        "rust_user".to_string()
    } else {
        truncated
    }
}

pub fn normalize_display_name(raw: &str) -> String {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        "Rustgram User".to_string()
    } else {
        truncate_chars(trimmed, limits::MAX_DISPLAY_NAME_LEN)
    }
}

pub fn build_initial_profile(
    uid: &str,
    default_display_name: &str,
    default_language: &str,
) -> Result<ProfileUpdatePlan, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let uid_short: String = clean_uid.chars().take(8).collect();
    let username = normalize_username(&format!("user_{uid_short}"), &clean_uid);
    let display_name = normalize_display_name(default_display_name);
    let bio = "Rustgram • Modern Messenger".to_string();
    let avatar_color = "#D96B27".to_string();
    let lang = normalize_language(default_language);

    Ok(ProfileUpdatePlan {
        profile: UserProfile {
            id: clean_uid.clone(),
            uid: clean_uid.clone(),
            display_name: display_name.clone(),
            username: username.clone(),
            bio: bio.clone(),
            avatar_color_hex: avatar_color.clone(),
            language_pref: lang,
            dark_mode_pref: true,
            blocked_uids: Vec::new(),
            last_seen_epoch_ms: None,
            created_at_epoch_ms: None,
            updated_at_epoch_ms: None,
        },
        handle: HandleEntry {
            id: username.clone(),
            owner_uid: clean_uid,
            username,
            display_name,
            bio,
            avatar_color_hex: avatar_color,
        },
    })
}

#[allow(clippy::too_many_arguments)]
pub fn build_profile_update(
    uid: &str,
    display_name: &str,
    username: &str,
    bio: &str,
    avatar_color_hex: &str,
    language_pref: &str,
    dark_mode_pref: bool,
    blocked_uids: Vec<String>,
) -> Result<ProfileUpdatePlan, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let clean_username = normalize_username(username, &clean_uid);
    let clean_name = normalize_display_name(display_name);
    let clean_bio = truncate_chars(bio.trim(), limits::MAX_BIO_LEN);
    let clean_color = validate_hex_color(avatar_color_hex, "#D96B27");
    let clean_lang = normalize_language(language_pref);

    let clean_blocked: Vec<String> = blocked_uids
        .into_iter()
        .map(|u| u.trim().to_string())
        .filter(|u| !u.is_empty() && u != &clean_uid)
        .take(50)
        .collect();

    Ok(ProfileUpdatePlan {
        profile: UserProfile {
            id: clean_uid.clone(),
            uid: clean_uid.clone(),
            display_name: clean_name.clone(),
            username: clean_username.clone(),
            bio: clean_bio.clone(),
            avatar_color_hex: clean_color.clone(),
            language_pref: clean_lang,
            dark_mode_pref,
            blocked_uids: clean_blocked,
            last_seen_epoch_ms: None,
            created_at_epoch_ms: None,
            updated_at_epoch_ms: None,
        },
        handle: HandleEntry {
            id: clean_username.clone(),
            owner_uid: clean_uid,
            username: clean_username,
            display_name: clean_name,
            bio: clean_bio,
            avatar_color_hex: clean_color,
        },
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn profile_update_enforces_username_and_bounds() {
        let plan = build_profile_update(
            "uid_alice_123",
            "  Alice Rust  ",
            "@Al!",
            "System programmer",
            "#1d4e89",
            "en",
            false,
            vec!["uid_alice_123".into(), "uid_bob".into()],
        )
        .unwrap();
        assert_eq!(plan.profile.display_name, "Alice Rust");
        assert!(plan.profile.username.starts_with("user_"));
        assert_eq!(plan.profile.avatar_color_hex, "#1D4E89");
        assert_eq!(plan.profile.language_pref, "en");
        assert_eq!(plan.profile.blocked_uids, vec!["uid_bob".to_string()]);
        assert_eq!(plan.handle.username, plan.profile.username);
    }
}
