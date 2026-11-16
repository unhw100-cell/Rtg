use rustgram_core::execute_command_json;
use serde_json::json;

#[test]
fn end_to_end_profile_chat_message_ai_search_workflow() {
    let profile_cmd = json!({
      "op": "build_profile_update",
      "uid": "uid_alice_01",
      "display_name": "Алиса",
      "username": "@alice_rust",
      "bio": "Systems engineer",
      "avatar_color_hex": "#d96b27",
      "language_pref": "ru",
      "dark_mode_pref": true,
      "blocked_uids": []
    });
    let profile_res: serde_json::Value =
        serde_json::from_str(&execute_command_json(&profile_cmd.to_string())).unwrap();
    assert_eq!(profile_res["ok"], true);
    assert_eq!(profile_res["data"]["profile"]["username"], "alice_rust");

    let room_cmd = json!({
      "op": "build_chat_room",
      "uid": "uid_alice_01",
      "chat_type": "channel",
      "title": "Rust Core Channel",
      "description": "Новости ядра",
      "avatar_color_hex": "#0ea5e9",
      "extra_member_uids": [],
      "is_public": true,
      "bot_role_id": "",
      "bot_model_name": "",
      "bot_system_prompt": "",
      "initial_preview": "Добро пожаловать",
      "initial_sender": "Алиса",
      "chat_id": "chan-rust-01"
    });
    let room_res: serde_json::Value =
        serde_json::from_str(&execute_command_json(&room_cmd.to_string())).unwrap();
    assert_eq!(room_res["ok"], true);
    assert_eq!(room_res["data"]["isPublic"], true);
    assert_eq!(room_res["data"]["inviteCode"], "CHANRUST");

    let send_cmd = json!({
      "op": "prepare_send_message",
      "uid": "uid_alice_01",
      "chat_id": "chan-rust-01",
      "message_id": "msg-001",
      "text": "Миграция на Rust Core выполнена!",
      "sender_name": "Алиса",
      "sender_type": "user",
      "member_uids": ["uid_alice_01"],
      "is_public": true,
      "reply_to_message_id": "",
      "reply_preview_text": "",
      "forwarded_from_name": "",
      "attachment_type": "none",
      "attachment_name": "",
      "attachment_data": "",
      "is_silent": false
    });
    let send_res: serde_json::Value =
        serde_json::from_str(&execute_command_json(&send_cmd.to_string())).unwrap();
    assert_eq!(send_res["ok"], true);
    let msg = send_res["data"]["message"].clone();

    let ai_cmd = json!({
      "op": "prepare_gemini_request",
      "requested_model": "gemini-3.8-flash",
      "system_prompt": "",
      "history_messages": [msg],
      "user_prompt": "Summarize in English",
      "has_image": false
    });
    let ai_res: serde_json::Value =
        serde_json::from_str(&execute_command_json(&ai_cmd.to_string())).unwrap();
    assert_eq!(ai_res["ok"], true);
    assert_eq!(ai_res["data"]["modelName"], "gemini-3.8-flash");
    assert_eq!(ai_res["data"]["history"].as_array().unwrap().len(), 1);
}
