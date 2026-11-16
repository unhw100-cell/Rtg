use rustgram_common::{limits, truncate_chars, ChatMessage};
use serde::{Deserialize, Serialize};

pub const MODEL_GENERAL_FLASH: &str = "gemini-3.8-flash";
pub const MODEL_FAST_LITE: &str = "gemini-3.5-flash-lite";
pub const MODEL_DEEP_REASONING: &str = "gemini-4-argon";

pub const AVAILABLE_MODELS: [&str; 3] =
    [MODEL_GENERAL_FLASH, MODEL_FAST_LITE, MODEL_DEEP_REASONING];

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct GeminiBotPreset {
    pub role_id: String,
    pub title_ru: String,
    pub title_en: String,
    pub subtitle_ru: String,
    pub subtitle_en: String,
    pub default_model: String,
    pub avatar_color_hex: String,
    pub system_instruction: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct GeminiTurn {
    pub role: String,
    pub text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct PreparedGeminiRequest {
    pub model_name: String,
    pub system_instruction: String,
    pub temperature: f32,
    pub history: Vec<GeminiTurn>,
    pub user_prompt: String,
}

pub fn resolve_model_name(requested: &str) -> String {
    let trimmed = requested.trim();
    if AVAILABLE_MODELS.contains(&trimmed) {
        trimmed.to_string()
    } else {
        MODEL_GENERAL_FLASH.to_string()
    }
}

pub fn default_presets() -> Vec<GeminiBotPreset> {
    vec![
    GeminiBotPreset {
      role_id: "rust_architect".into(),
      title_ru: "Архитектор Rustgram".into(),
      title_en: "Rustgram Architect".into(),
      subtitle_ru: "Системная архитектура, Rust, Tokio, криптография и сеть".into(),
      subtitle_en: "Systems architecture, Rust, Tokio, networking & security".into(),
      default_model: MODEL_GENERAL_FLASH.into(),
      avatar_color_hex: "#D96B27".into(),
      system_instruction: "You are the Rustgram Principal Systems Architect. Help the user design modular Rust crates, async Tokio pipelines, secure network protocols, database schemas, and clean Android/Kotlin integrations. Reply in the user's language (Russian or English) concisely and technically accurately.".into(),
    },
    GeminiBotPreset {
      role_id: "deep_reasoner".into(),
      title_ru: "Глубокий аналитик AI".into(),
      title_en: "Deep Reasoning Core".into(),
      subtitle_ru: "Сложный аудит кода, алгоритмы и системный разбор".into(),
      subtitle_en: "Complex code audits, algorithms & multi-step reasoning".into(),
      default_model: MODEL_DEEP_REASONING.into(),
      avatar_color_hex: "#6D4AFF".into(),
      system_instruction: "You are a senior staff engineer and security auditor. Analyze complex architectural problems, edge cases, concurrency hazards, and cryptographic invariants step by step. Provide clear, structured conclusions in the user's language.".into(),
    },
    GeminiBotPreset {
      role_id: "quick_translator".into(),
      title_ru: "Быстрый переводчик".into(),
      title_en: "Instant Translator".into(),
      subtitle_ru: "Мгновенный перевод RU/EN, резюме и редактура".into(),
      subtitle_en: "Ultra-fast RU/EN translation, summaries & copy editing".into(),
      default_model: MODEL_FAST_LITE.into(),
      avatar_color_hex: "#0EA5E9".into(),
      system_instruction: "You are a fast, accurate bilingual Russian-English assistant inside Rustgram. Translate text naturally, summarize long threads, or polish draft messages cleanly without extra filler.".into(),
    },
    GeminiBotPreset {
      role_id: "channel_editor".into(),
      title_ru: "Редактор каналов".into(),
      title_en: "Channel Editor Bot".into(),
      subtitle_ru: "Подготовка постов, заголовков и структурированных анонсов".into(),
      subtitle_en: "Drafts engaging channel posts, release notes & summaries".into(),
      default_model: MODEL_GENERAL_FLASH.into(),
      avatar_color_hex: "#10B981".into(),
      system_instruction: "You are an editorial assistant for Telegram-style channels. Help craft crisp announcements, release changelogs, polls, and structured posts with clear formatting in Russian or English.".into(),
    },
  ]
}

pub fn prepare_gemini_request(
    requested_model: &str,
    system_prompt: &str,
    history_messages: &[ChatMessage],
    user_prompt: &str,
    has_image: bool,
) -> PreparedGeminiRequest {
    let model_name = resolve_model_name(requested_model);
    let instruction = {
        let trimmed = system_prompt.trim();
        if trimmed.is_empty() {
            "You are a helpful AI assistant inside the Rustgram messenger.".to_string()
        } else {
            truncate_chars(trimmed, limits::MAX_BOT_PROMPT_LEN)
        }
    };

    let filtered: Vec<&ChatMessage> = history_messages
        .iter()
        .filter(|m| !m.text.trim().is_empty())
        .collect();
    let start = filtered
        .len()
        .saturating_sub(limits::MAX_AI_HISTORY_MESSAGES);
    let history = filtered[start..]
        .iter()
        .map(|msg| GeminiTurn {
            role: if msg.sender_type == "ai_bot" {
                "model".to_string()
            } else {
                "user".to_string()
            },
            text: truncate_chars(msg.text.trim(), limits::MAX_MESSAGE_TEXT_LEN),
        })
        .collect();

    let prompt = {
        let trimmed = user_prompt.trim();
        if trimmed.is_empty() && has_image {
            "Please analyze the attached image.".to_string()
        } else {
            truncate_chars(trimmed, limits::MAX_MESSAGE_TEXT_LEN)
        }
    };

    PreparedGeminiRequest {
        model_name,
        system_instruction: instruction,
        temperature: 0.7,
        history,
        user_prompt: prompt,
    }
}

pub fn finalize_stream_response(accumulated: &str) -> Result<String, String> {
    let cleaned = truncate_chars(accumulated.trim(), limits::MAX_MESSAGE_TEXT_LEN);
    if cleaned.is_empty() {
        Err("Gemini returned an empty response.".to_string())
    } else {
        Ok(cleaned)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gemini_request_builder_caps_history_and_validates_models() {
        let req = prepare_gemini_request("unknown-model", "", &[], "", true);
        assert_eq!(req.model_name, MODEL_GENERAL_FLASH);
        assert_eq!(req.user_prompt, "Please analyze the attached image.");
        assert!(!req.system_instruction.is_empty());

        let presets = default_presets();
        assert_eq!(presets.len(), 4);
        for p in presets {
            assert!(AVAILABLE_MODELS.contains(&p.default_model.as_str()));
        }
    }
}
