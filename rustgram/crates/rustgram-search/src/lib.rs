use rustgram_chat::{matches_chat_filter, sort_chats_by_activity, ChatFilter};
use rustgram_common::{ChatMessage, ChatRoom};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ChatListQueryResult {
    pub filtered_chats: Vec<ChatRoom>,
    pub discoverable_channels: Vec<ChatRoom>,
}

pub fn filter_chat_workspace(
    chats: Vec<ChatRoom>,
    public_channels: Vec<ChatRoom>,
    current_uid: &str,
    filter: ChatFilter,
    query: &str,
) -> ChatListQueryResult {
    let q = query.trim().to_lowercase();
    let sorted_chats = sort_chats_by_activity(chats);

    let filtered_chats = sorted_chats
        .into_iter()
        .filter(|chat| {
            if !matches_chat_filter(chat, current_uid, filter) {
                return false;
            }
            if q.is_empty() {
                return true;
            }
            chat.title.to_lowercase().contains(&q)
                || chat.last_message_preview.to_lowercase().contains(&q)
                || chat.description.to_lowercase().contains(&q)
        })
        .collect();

    let mut discoverable: Vec<ChatRoom> = public_channels
        .into_iter()
        .filter(|channel| {
            !channel.member_uids.iter().any(|u| u == current_uid)
                && (q.is_empty()
                    || channel.title.to_lowercase().contains(&q)
                    || channel.description.to_lowercase().contains(&q))
        })
        .collect();
    discoverable.sort_by_key(|a| std::cmp::Reverse(a.member_uids.len()));

    ChatListQueryResult {
        filtered_chats,
        discoverable_channels: discoverable,
    }
}

pub fn search_messages_in_chat(messages: Vec<ChatMessage>, query: &str) -> Vec<ChatMessage> {
    let q = query.trim().to_lowercase();
    if q.is_empty() {
        return messages;
    }
    messages
        .into_iter()
        .filter(|msg| {
            msg.text.to_lowercase().contains(&q)
                || msg.sender_name.to_lowercase().contains(&q)
                || msg.attachment_name.to_lowercase().contains(&q)
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cyrillic_case_insensitive_search_works() {
        let msg = ChatMessage {
            id: "m1".into(),
            chat_id: "c1".into(),
            author_id: "u1".into(),
            sender_type: "user".into(),
            sender_name: "Алиса".into(),
            member_uids: vec!["u1".into()],
            is_public: false,
            text: "Привет, Rust Core!".into(),
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
            created_at_epoch_ms: None,
        };
        assert_eq!(
            search_messages_in_chat(vec![msg.clone()], "привет").len(),
            1
        );
        assert_eq!(search_messages_in_chat(vec![msg], "не найдено").len(), 0);
    }
}
