#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-app/build/outputs/apk/release/app-release.apk}"

if [[ ! -f "$APK_PATH" ]]; then
  APK_PATH="$(find app/build/outputs/apk/release -maxdepth 1 -name "*.apk" -print -quit 2>/dev/null || true)"
fi

if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
  echo "Release APK not found."
  exit 1
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME is not set. Cannot locate apksigner."
  exit 1
fi

APKSIGNER_BIN="$(ls -1 "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
if [[ -z "$APKSIGNER_BIN" || ! -x "$APKSIGNER_BIN" ]]; then
  echo "apksigner not found under \$ANDROID_HOME/build-tools."
  exit 1
fi

echo "Using apksigner: $APKSIGNER_BIN"
echo "Verifying APK: $APK_PATH"
"$APKSIGNER_BIN" verify --print-certs "$APK_PATH"
echo "Release APK signature verification passed."
