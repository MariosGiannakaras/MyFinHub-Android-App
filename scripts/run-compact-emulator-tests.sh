#!/usr/bin/env bash
set -euo pipefail

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then
  echo "ANDROID_SDK_ROOT/ANDROID_HOME is not configured." >&2
  exit 1
fi

find_sdk_tool() {
  local name="$1"
  local preferred="$sdk_root/cmdline-tools/latest/bin/$name"
  if [[ -x "$preferred" ]]; then
    printf '%s\n' "$preferred"
    return 0
  fi
  find "$sdk_root/cmdline-tools" -type f -name "$name" -perm -u+x 2>/dev/null | sort -V | tail -n 1
}

sdkmanager="$(find_sdk_tool sdkmanager)"
avdmanager="$(find_sdk_tool avdmanager)"
adb="$sdk_root/platform-tools/adb"
emulator="$sdk_root/emulator/emulator"

for tool in "$sdkmanager" "$avdmanager" "$adb"; do
  if [[ -z "$tool" || ! -x "$tool" ]]; then
    echo "Required Android SDK tool is unavailable: $tool" >&2
    exit 1
  fi
done

system_image='system-images;android-35;google_apis;x86_64'
avd_name='myfinhub-ci-api35'

yes | "$sdkmanager" --licenses >/dev/null 2>&1 || true
"$sdkmanager" "emulator" "$system_image"

if [[ ! -x "$emulator" ]]; then
  echo "Android emulator binary was not installed at $emulator" >&2
  exit 1
fi

"$adb" kill-server || true
"$adb" start-server

# A fresh AVD avoids snapshot corruption and stale package-manager state.
echo 'no' | "$avdmanager" create avd \
  --force \
  --name "$avd_name" \
  --package "$system_image" \
  --device 'pixel_6'

emulator_log="${RUNNER_TEMP:-/tmp}/myfinhub-emulator.log"
"$emulator" \
  -avd "$avd_name" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -wipe-data \
  -gpu swiftshader_indirect \
  >"$emulator_log" 2>&1 &
emulator_pid=$!

cleanup() {
  "$adb" emu kill >/dev/null 2>&1 || true
  kill "$emulator_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT

"$adb" wait-for-device

ready=false
for _ in $(seq 1 180); do
  boot_completed="$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  package_service="$("$adb" shell service check package 2>/dev/null | tr -d '\r' || true)"
  activity_service="$("$adb" shell service check activity 2>/dev/null | tr -d '\r' || true)"

  if [[ "$boot_completed" == '1' ]] \
    && [[ "$package_service" == *'found'* ]] \
    && [[ "$activity_service" == *'found'* ]] \
    && "$adb" shell cmd package list packages >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != true ]]; then
  echo 'Emulator did not reach a usable boot/package-manager state.' >&2
  tail -n 200 "$emulator_log" >&2 || true
  "$adb" shell getprop >&2 || true
  exit 1
fi

# Remove animation timing from Compose instrumentation assertions.
"$adb" shell settings put global window_animation_scale 0
"$adb" shell settings put global transition_animation_scale 0
"$adb" shell settings put global animator_duration_scale 0

./gradlew connectedDebugAndroidTest --stacktrace
