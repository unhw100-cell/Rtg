use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use rustgram_common::sanitize_id;
use rustgram_core::{core_info, execute_command_json, localized_strings};

fn from_jstring(env: &mut JNIEnv, input: &JString) -> Result<String, String> {
    env.get_string(input)
        .map(|s| s.into())
        .map_err(|e| format!("Failed reading JNI string: {e}"))
}

fn to_jstring(env: &mut JNIEnv, output: &str) -> jstring {
    match env.new_string(output) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_RustBridge_nativeCoreInfoJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let json = serde_json::to_string(&core_info())
        .unwrap_or_else(|e| serde_json::json!({ "error": e.to_string() }).to_string());
    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_RustBridge_nativeDispatchJson(
    mut env: JNIEnv,
    _class: JClass,
    command_json: JString,
) -> jstring {
    let result = match from_jstring(&mut env, &command_json) {
        Ok(raw) => execute_command_json(&raw),
        Err(e) => serde_json::json!({
            "ok": false,
            "data": serde_json::Value::Null,
            "error": e,
        })
        .to_string(),
    };
    to_jstring(&mut env, &result)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_RustBridge_nativeSanitizeId(
    mut env: JNIEnv,
    _class: JClass,
    raw: JString,
    fallback: JString,
) -> jstring {
    let raw_str = from_jstring(&mut env, &raw).unwrap_or_default();
    let fb_str = from_jstring(&mut env, &fallback).unwrap_or_else(|_| "item".into());
    let out = sanitize_id(&raw_str, &fb_str);
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_RustBridge_nativeLocalizedStringsJson(
    mut env: JNIEnv,
    _class: JClass,
    lang: JString,
) -> jstring {
    let lang_str = from_jstring(&mut env, &lang).unwrap_or_else(|_| "ru".into());
    let json =
        serde_json::to_string(&localized_strings(&lang_str)).unwrap_or_else(|_| "{}".to_string());
    to_jstring(&mut env, &json)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jni_underlying_handlers_execute_real_rust_core() {
        let res = execute_command_json(r#"{"op":"get_gemini_models"}"#);
        assert!(res.contains("gemini-3.8-flash"));
        assert!(res.contains("gemini-4-argon"));
    }
}
