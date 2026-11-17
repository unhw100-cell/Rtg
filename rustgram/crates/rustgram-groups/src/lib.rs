use rustgram_auth::require_authenticated_uid;
use rustgram_common::{
    limits, sanitize_id, truncate_chars, validate_hex_color, ChatRoom, RustDomainError,
};
use rustgram_crypto::generate_invite_code;

#[allow(clippy::too_many_arguments)]
pub fn build_chat_room(
    uid: &str,
    chat_type: &str,
    title: &str,
    description: &str,
    avatar_color_hex: &str,
    extra_member_uids: Vec<String>,
    is_public: bool,
    bot_role_id: &str,
    bot_model_name: &str,
    bot_system_prompt: &str,
    initial_preview: &str,
    initial_sender: &str,
    chat_id: &str,
) -> Result<ChatRoom, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    let normalized_type = match chat_type.trim() {
        "direct" | "group" | "channel" | "ai_bot" => chat_type.trim().to_string(),
        other => {
            return Err(RustDomainError::ValidationFailed(format!(
                "Unsupported chatType: {other}"
            )))
        }
    };
    let valid_public = is_public && normalized_type == "channel";
    let clean_id = sanitize_id(chat_id, "room");
    let clean_title = {
        let t = title.trim();
        if t.is_empty() {
            "Chat".to_string()
        } else {
            truncate_chars(t, limits::MAX_CHAT_TITLE_LEN)
        }
    };

    let mut members = vec![clean_uid.clone()];
    for m in extra_member_uids {
        let t = m.trim().to_string();
        if !t.is_empty() && !members.contains(&t) {
            members.push(t);
            if members.len() >= limits::MAX_MEMBERS {
                break;
            }
        }
    }

    Ok(ChatRoom {
        id: clean_id.clone(),
        owner_id: clean_uid.clone(),
        chat_type: normalized_type,
        title: clean_title,
        description: truncate_chars(description.trim(), limits::MAX_CHAT_DESCRIPTION_LEN),
        avatar_color_hex: validate_hex_color(avatar_color_hex, "#1D4E89"),
        member_uids: members,
        admin_uids: vec![clean_uid],
        is_public: valid_public,
        invite_code: generate_invite_code(&clean_id),
        pinned_message_id: String::new(),
        pinned_preview: String::new(),
        archived_by_uids: Vec::new(),
        bot_role_id: truncate_chars(bot_role_id.trim(), limits::MAX_ID_LEN),
        bot_model_name: truncate_chars(bot_model_name.trim(), limits::MAX_MODEL_NAME_LEN),
        bot_system_prompt: truncate_chars(bot_system_prompt.trim(), limits::MAX_BOT_PROMPT_LEN),
        last_message_preview: truncate_chars(initial_preview.trim(), limits::MAX_LAST_PREVIEW_LEN),
        last_message_sender: truncate_chars(initial_sender.trim(), limits::MAX_DISPLAY_NAME_LEN),
        last_message_at_epoch_ms: None,
        created_at_epoch_ms: None,
    })
}

pub fn prepare_join_public_channel(
    uid: &str,
    chat: &ChatRoom,
) -> Result<Vec<String>, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    if !chat.is_public {
        return Err(RustDomainError::PermissionDenied(
            "Cannot self-join a private room.".into(),
        ));
    }
    if chat.member_uids.iter().any(|u| u == &clean_uid) {
        return Ok(chat.member_uids.clone());
    }
    if chat.member_uids.len() >= limits::MAX_MEMBERS {
        return Err(RustDomainError::ValidationFailed(
            "Channel member limit reached.".into(),
        ));
    }
    let mut updated = chat.member_uids.clone();
    updated.push(clean_uid);
    Ok(updated)
}

pub fn prepare_add_group_member(
    uid: &str,
    chat: &ChatRoom,
    new_member_uid: &str,
) -> Result<Vec<String>, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    if chat.owner_id != clean_uid && !chat.admin_uids.iter().any(|a| a == &clean_uid) {
        return Err(RustDomainError::PermissionDenied(
            "Only admins can add members.".into(),
        ));
    }
    let target = new_member_uid.trim();
    if target.is_empty() {
        return Err(RustDomainError::InvalidIdentifier(
            "newMemberUid empty".into(),
        ));
    }
    let mut updated = chat.member_uids.clone();
    if !updated.iter().any(|m| m == target) && updated.len() < limits::MAX_MEMBERS {
        updated.push(target.to_string());
    }
    Ok(updated)
}

pub fn prepare_leave_public_channel(
    uid: &str,
    chat: &ChatRoom,
) -> Result<Vec<String>, RustDomainError> {
    let clean_uid = require_authenticated_uid(Some(uid))?;
    if !chat.is_public {
        return Err(RustDomainError::PermissionDenied(
            "Only public channels support self-unsubscribe.".into(),
        ));
    }
    if chat.owner_id == clean_uid {
        return Err(RustDomainError::PermissionDenied(
            "Channel owner cannot unsubscribe from own channel.".into(),
        ));
    }
    if !chat.member_uids.iter().any(|u| u == &clean_uid) {
        return Ok(chat.member_uids.clone());
    }
    Ok(chat
        .member_uids
        .iter()
        .filter(|u| *u != &clean_uid)
        .cloned()
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn public_flag_restricted_to_channels_and_join_works() {
        let group = build_chat_room(
            "u1",
            "group",
            "Dev Group",
            "desc",
            "#D96B27",
            vec![],
            true,
            "",
            "",
            "",
            "",
            "",
            "grp-001",
        )
        .unwrap();
        assert!(!group.is_public);
        assert!(prepare_join_public_channel("u2", &group).is_err());

        let mut channel = build_chat_room(
            "u1",
            "channel",
            "Rust News",
            "desc",
            "#0EA5E9",
            vec![],
            true,
            "",
            "",
            "",
            "",
            "",
            "chan-001",
        )
        .unwrap();
        assert!(channel.is_public);
        let joined = prepare_join_public_channel("u2", &channel).unwrap();
        assert_eq!(joined, vec!["u1", "u2"]);
        channel.member_uids = joined;
        assert!(prepare_leave_public_channel("u1", &channel).is_err());
        assert_eq!(
            prepare_leave_public_channel("u2", &channel).unwrap(),
            vec!["u1"]
        );
    }
}
