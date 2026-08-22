#!/usr/bin/env bash
set -euo pipefail

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then
  echo "ANDROID_SDK_ROOT/ANDROID_HOME is not configured." >&2
  exit 1
fi

sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then
  sdkmanager="$(find "$sdk_root/cmdline-tools" -type f -name sdkmanager -perm -u+x 2>/dev/null | sort -V | tail -n 1 || true)"
fi
if [[ -z "$sdkmanager" || ! -x "$sdkmanager" ]]; then
  echo "sdkmanager was not found under $sdk_root/cmdline-tools." >&2
  exit 1
fi

# CI images can change PATH composition; call sdkmanager by its SDK-relative path.
yes | "$sdkmanager" --licenses >/dev/null 2>&1 || true
"$sdkmanager" \
  "platform-tools" \
  "platforms;android-37" \
  "build-tools;36.0.0"
