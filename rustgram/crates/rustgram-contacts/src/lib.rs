use rustgram_auth::require_authenticated_uid;
use rustgram_common::{
    limits, sanitize_id, truncate_chars, validate_hex_color, ContactEntry, RustDomainError,
};

pub fn normalize_lookup_handle(raw_username: &str) -> Option<String> {
    let trimmed = raw_username.trim().trim_start_matches('@');
    if trimmed.is_empty() {
        return None;
    }
    let handle = sanitize_id(trimmed, "");
    let clipped = truncate_chars(&handle, limits::MAX_USERNAME_LEN);
    if clipped.chars().count() < limits::MIN_USERNAME_LEN {
        None
    } else {
        Some(clipped)
    }
}

#[allow(clippy::too_many_arguments)]
pub fn build_contact_entry(
    owner_uid: &str,
    contact_uid: &str,
    display_name: &str,
    username: &str,
    bio: &str,
    avatar_color_hex: &str,
    is_favorite: bool,
    is_blocked: bool,
) -> Result<ContactEntry, RustDomainError> {
    let clean_owner = require_authenticated_uid(Some(owner_uid))?;
    let clean_contact_uid = contact_uid.trim();
    if clean_contact_uid.is_empty() {
        return Err(RustDomainError::InvalidIdentifier(
            "contactUid cannot be empty".to_string(),
        ));
    }
    let doc_id = sanitize_id(clean_contact_uid, "contact");
    let clean_name = {
        let t = display_name.trim();
        if t.is_empty() {
            "Contact".to_string()
        } else {
            truncate_chars(t, limits::MAX_DISPLAY_NAME_LEN)
        }
    };

    Ok(ContactEntry {
        id: doc_id,
        user_id: clean_owner,
        contact_uid: truncate_chars(clean_contact_uid, limits::MAX_UID_LEN),
        display_name: clean_name,
        username: truncate_chars(
            username.trim().trim_start_matches('@'),
            limits::MAX_USERNAME_LEN,
        ),
        bio: truncate_chars(bio.trim(), limits::MAX_BIO_LEN),
        avatar_color_hex: validate_hex_color(avatar_color_hex, "#1D4E89"),
        is_favorite,
        is_blocked,
    })
}

pub fn sort_contacts(mut contacts: Vec<ContactEntry>) -> Vec<ContactEntry> {
    contacts.sort_by(|a, b| {
        b.is_favorite.cmp(&a.is_favorite).then_with(|| {
            a.display_name
                .to_lowercase()
                .cmp(&b.display_name.to_lowercase())
        })
    });
    contacts
}

pub fn can_message_contact(
    contacts: &[ContactEntry],
    target_uid: &str,
) -> Result<(), RustDomainError> {
    if contacts
        .iter()
        .any(|c| c.contact_uid == target_uid && c.is_blocked)
    {
        return Err(RustDomainError::Blocked(
            "Cannot start chat with a blocked contact.".to_string(),
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lookup_handle_strips_at_and_validates_min_len() {
        assert_eq!(
            normalize_lookup_handle("  @Rust_Dev "),
            Some("rust_dev".into())
        );
        assert_eq!(normalize_lookup_handle("@ab"), None);
    }

    #[test]
    fn blocked_contact_cannot_be_messaged() {
        let contact =
            build_contact_entry("u1", "u2", "Bob", "@bob", "hi", "#1d4e89", false, true).unwrap();
        assert!(matches!(
            can_message_contact(&[contact], "u2"),
            Err(RustDomainError::Blocked(_))
        ));
    }
}
