use rustgram_common::{ChatMessage, DraftEntry};
use std::collections::{BTreeMap, HashSet};

pub fn order_and_dedup_messages(messages: Vec<ChatMessage>) -> Vec<ChatMessage> {
    let mut seen = HashSet::new();
    let mut deduped: Vec<ChatMessage> = messages
        .into_iter()
        .filter(|m| seen.insert(m.id.clone()))
        .collect();
    deduped.sort_by(|a, b| {
        let ta = a.created_at_epoch_ms.unwrap_or(0);
        let tb = b.created_at_epoch_ms.unwrap_or(0);
        ta.cmp(&tb).then_with(|| a.id.cmp(&b.id))
    });
    deduped
}

pub fn build_drafts_map(entries: Vec<DraftEntry>) -> BTreeMap<String, String> {
    let mut map = BTreeMap::new();
    for entry in entries {
        if !entry.text.trim().is_empty() && !entry.chat_id.is_empty() {
            map.insert(entry.chat_id, entry.text);
        }
    }
    map
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sync_dedups_and_orders_chronologically() {
        let m1 = ChatMessage {
            id: "m2".into(),
            chat_id: "c1".into(),
            author_id: "u1".into(),
            sender_type: "user".into(),
            sender_name: "A".into(),
            member_uids: vec![],
            is_public: false,
            text: "second".into(),
            reply_to_message_id: String::new(),
            reply_preview_text: String::new(),
            forwarded_from_name: String::new(),
            reactions: Default::default(),
            attachment_type: "none".into(),
            attachment_name: String::new(),
            attachment_data: String::new(),
            is_silent: false,
            is_edited: false,
            view_count: 1,
            read_by_uids: vec![],
            created_at_epoch_ms: Some(200),
        };
        let mut m0 = m1.clone();
        m0.id = "m1".into();
        m0.text = "first".into();
        m0.created_at_epoch_ms = Some(100);
        let ordered = order_and_dedup_messages(vec![m1.clone(), m0, m1]);
        assert_eq!(ordered.len(), 2);
        assert_eq!(ordered[0].id, "m1");
        assert_eq!(ordered[1].id, "m2");
    }
}
