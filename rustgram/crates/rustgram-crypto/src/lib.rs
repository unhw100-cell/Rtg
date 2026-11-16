use hmac::{Hmac, Mac};
use sha2::{Digest, Sha256};

type HmacSha256 = Hmac<Sha256>;

pub fn sha256_hex(input: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(input);
    hex::encode(hasher.finalize())
}

pub fn hmac_sha256_hex(key: &[u8], data: &[u8]) -> Result<String, String> {
    let mut mac =
        HmacSha256::new_from_slice(key).map_err(|e| format!("Invalid HMAC key length: {e}"))?;
    mac.update(data);
    Ok(hex::encode(mac.finalize().into_bytes()))
}

pub fn generate_invite_code(chat_id: &str) -> String {
    let alnum: String = chat_id
        .chars()
        .filter(|c| c.is_ascii_alphanumeric())
        .take(8)
        .collect();
    if alnum.len() >= 4 {
        alnum.to_uppercase()
    } else {
        sha256_hex(chat_id.as_bytes())[..8].to_uppercase()
    }
}

pub fn message_integrity_fingerprint(
    chat_id: &str,
    author_id: &str,
    text: &str,
    attachment_type: &str,
) -> String {
    let canonical = format!("{chat_id}|{author_id}|{attachment_type}|{}", text.trim());
    sha256_hex(canonical.as_bytes())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn invite_code_uses_uppercase_prefix_or_sha_fallback() {
        assert_eq!(generate_invite_code("abc123def456"), "ABC123DE");
        assert_eq!(generate_invite_code("---").len(), 8);
    }

    #[test]
    fn hmac_and_sha256_are_deterministic() {
        let h1 = sha256_hex(b"rustgram");
        let h2 = sha256_hex(b"rustgram");
        assert_eq!(h1, h2);
        assert_eq!(h1.len(), 64);

        let sig = hmac_sha256_hex(b"secret", b"payload").expect("hmac works");
        assert_eq!(sig.len(), 64);
    }
}
