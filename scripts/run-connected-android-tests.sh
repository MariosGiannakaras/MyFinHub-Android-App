#!/usr/bin/env bash
set -euo pipefail

font_scale="${1:-}"

adb wait-for-device
if [[ -n "${MYFINHUB_EXPECTED_ANDROID_API:-}" ]]; then
  actual_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r\n')"
  if [[ "$actual_api" != "$MYFINHUB_EXPECTED_ANDROID_API" ]]; then
    echo "Expected Android API $MYFINHUB_EXPECTED_ANDROID_API; actual API $actual_api." >&2
    exit 1
  fi
  echo "Verified Android runtime API $actual_api."
fi
ready=false
for attempt in $(seq 1 60); do
  if adb shell service check package 2>/dev/null | grep -Eq '^Service package: found[[:space:]]*$' && \
     adb shell service check activity 2>/dev/null | grep -Eq '^Service activity: found[[:space:]]*$'; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != "true" ]]; then
  echo 'Android package/activity services did not become ready.' >&2
  exit 1
fi

# Record actual guest storage after boot; this contains no app/user data.
if [[ -n "${MYFINHUB_EXPECTED_ANDROID_API:-}" ]]; then
  adb shell df -k /data
fi

if [[ -n "$font_scale" ]]; then
  adb shell settings put system font_scale "$font_scale"
fi

./gradlew connectedDebugAndroidTest --stacktrace
