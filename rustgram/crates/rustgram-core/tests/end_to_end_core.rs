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

    let join_cmd = json!({
      "op": "join_public_channel",
      "uid": "uid_bob_02",
      "chat": room_res["data"]
    });
    let join_res: serde_json::Value =
        serde_json::from_str(&execute_command_json(&join_cmd.to_string())).unwrap();
    assert_eq!(join_res["ok"], true);
    let mut updated_room = room_res["data"].clone();
    updated_room["memberUids"] = join_res["data"].clone();

    let leave_cmd = json!({
      "op": "leave_public_channel",
      "uid": "uid_bob_02",
      "chat": updated_room
    });
    let leave_res: serde_json::Value =
        serde_json::from_str(&execute_command_json(&leave_cmd.to_string())).unwrap();
    assert_eq!(leave_res["ok"], true);
    assert_eq!(leave_res["data"], json!(["uid_alice_01"]));

    let receipts_cmd = json!({
      "op": "plan_read_receipts",
      "uid": "uid_bob_02",
      "messages": [send_res["data"]["message"]]
    });
    let receipts_res: serde_json::Value =
        serde_json::from_str(&execute_command_json(&receipts_cmd.to_string())).unwrap();
    assert_eq!(receipts_res["ok"], true);
    assert_eq!(receipts_res["data"].as_array().unwrap().len(), 1);
    assert_eq!(
        receipts_res["data"][0]["readByUids"],
        json!(["uid_alice_01", "uid_bob_02"])
    );

    let sort_cmd = json!({
      "op": "sort_chats",
      "chats": [room_res["data"]]
    });
    let sort_res: serde_json::Value =
        serde_json::from_str(&execute_command_json(&sort_cmd.to_string())).unwrap();
    assert_eq!(sort_res["ok"], true);
    assert_eq!(sort_res["data"].as_array().unwrap().len(), 1);
}
