#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUST_DIR="$ROOT_DIR/rustgram"
JNI_LIBS_DIR="$ROOT_DIR/app/src/main/jniLibs"
HOST_JNI_DIR="$ROOT_DIR/app/build/rust-host-jni"

if [ -f "$HOME/.cargo/env" ]; then
  # shellcheck disable=SC1091
  . "$HOME/.cargo/env"
fi

if ! command -v cargo >/dev/null 2>&1; then
  if [ -f "$JNI_LIBS_DIR/arm64-v8a/librustgram_bridge.so" ] && [ -f "$JNI_LIBS_DIR/x86_64/librustgram_bridge.so" ]; then
    echo "cargo not found; using prebuilt jniLibs/*.so"
    exit 0
  fi
  echo "cargo not found on PATH and prebuilt jniLibs are missing" >&2
  exit 1
fi

export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-$RUST_DIR/target}"

echo "=== 1. Building host JNI shared library for Robolectric/JVM tests ==="
mkdir -p "$HOST_JNI_DIR"
(cd "$RUST_DIR" && cargo build --release -p rust-bridge)
cp "$CARGO_TARGET_DIR/release/librustgram_bridge.so" "$HOST_JNI_DIR/librustgram_bridge.so"

echo "=== 2. Building Android JNI shared libraries via cargo-ndk (16KB page aligned) ==="
NDK_DIR=""
if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
  NDK_DIR="$ANDROID_NDK_HOME"
elif [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
  NDK_DIR="$ANDROID_NDK_ROOT"
else
  SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android/sdk}}"
  if [ -d "$SDK_DIR/ndk" ]; then
    NDK_DIR="$(find "$SDK_DIR/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
  fi
fi
if [ -z "$NDK_DIR" ] || [ ! -d "$NDK_DIR" ]; then
  echo "Android NDK not found (checked ANDROID_NDK_HOME, ANDROID_NDK_ROOT, and SDK ndk/)" >&2
  exit 1
fi
export ANDROID_NDK_HOME="$NDK_DIR"
export RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"

mkdir -p "$JNI_LIBS_DIR"
(
  cd "$RUST_DIR"
  cargo ndk \
    -t arm64-v8a \
    -t armeabi-v7a \
    -t x86_64 \
    -t x86 \
    -P 24 \
    -o "$JNI_LIBS_DIR" \
    build --release -p rust-bridge
)

echo "=== Built native libraries ==="
find "$JNI_LIBS_DIR" -name "*.so" -exec ls -lh {} +
ls -lh "$HOST_JNI_DIR/librustgram_bridge.so"
