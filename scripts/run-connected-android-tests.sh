#!/usr/bin/env bash
set -euo pipefail

font_scale="${1:-}"

adb wait-for-device
ready=false
for attempt in $(seq 1 60); do
  if adb shell service check package 2>/dev/null | grep -q 'found' && \
     adb shell service check activity 2>/dev/null | grep -q 'found'; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != "true" ]]; then
  echo 'Android package/activity services did not become ready.' >&2
  exit 1
fi

if [[ -n "$font_scale" ]]; then
  adb shell settings put system font_scale "$font_scale"
fi

./gradlew connectedDebugAndroidTest --stacktrace
