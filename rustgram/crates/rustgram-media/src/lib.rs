use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use rustgram_common::{limits, truncate_chars, RustDomainError};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum AttachmentKind {
    None,
    Image,
    Audio,
    Document,
}

impl AttachmentKind {
    pub fn parse(raw: &str) -> Result<Self, RustDomainError> {
        match raw.trim().to_lowercase().as_str() {
            "" | "none" => Ok(Self::None),
            "image" => Ok(Self::Image),
            "audio" => Ok(Self::Audio),
            "document" => Ok(Self::Document),
            other => Err(RustDomainError::ValidationFailed(format!(
                "Unsupported attachment type: {other}"
            ))),
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::None => "none",
            Self::Image => "image",
            Self::Audio => "audio",
            Self::Document => "document",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ValidatedAttachment {
    pub attachment_type: String,
    pub attachment_name: String,
    pub attachment_data: String,
}

pub fn validate_attachment(
    attachment_type: &str,
    attachment_name: &str,
    attachment_data: &str,
) -> Result<ValidatedAttachment, RustDomainError> {
    let kind = AttachmentKind::parse(attachment_type)?;
    if kind == AttachmentKind::None {
        return Ok(ValidatedAttachment {
            attachment_type: "none".to_string(),
            attachment_name: String::new(),
            attachment_data: String::new(),
        });
    }

    let clean_name = {
        let t = attachment_name.trim();
        if t.is_empty() {
            match kind {
                AttachmentKind::Image => "photo.jpg".to_string(),
                AttachmentKind::Audio => "voice.m4a".to_string(),
                AttachmentKind::Document => "document".to_string(),
                AttachmentKind::None => String::new(),
            }
        } else {
            truncate_chars(t, limits::MAX_ATTACHMENT_NAME_LEN)
        }
    };

    if attachment_data.len() > limits::MAX_ATTACHMENT_DATA_LEN {
        return Err(RustDomainError::ValidationFailed(format!(
            "Attachment payload exceeds {} bytes.",
            limits::MAX_ATTACHMENT_DATA_LEN
        )));
    }

    if (kind == AttachmentKind::Image || kind == AttachmentKind::Audio)
        && !attachment_data.is_empty()
        && STANDARD.decode(attachment_data.trim()).is_err()
    {
        return Err(RustDomainError::ValidationFailed(
            "Media attachment must contain valid Base64 data.".to_string(),
        ));
    }

    Ok(ValidatedAttachment {
        attachment_type: kind.as_str().to_string(),
        attachment_name: clean_name,
        attachment_data: attachment_data.trim().to_string(),
    })
}

pub fn format_document_size_kb(size_bytes: u64) -> String {
    let kb = (size_bytes / 1024).max(1);
    format!("{kb} KB")
}

pub fn format_voice_note_filename(duration_secs: u64) -> String {
    format!("voice_{}s.m4a", duration_secs.max(1))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_base64_for_media_and_sizes() {
        let encoded = STANDARD.encode(b"fake-jpeg-bytes");
        let res = validate_attachment("image", "pic.jpg", &encoded).unwrap();
        assert_eq!(res.attachment_type, "image");
        assert_eq!(res.attachment_name, "pic.jpg");

        assert!(validate_attachment("image", "bad.jpg", "not!valid!b64").is_err());
        assert_eq!(format_document_size_kb(512), "1 KB");
        assert_eq!(format_voice_note_filename(12), "voice_12s.m4a");
    }
}
