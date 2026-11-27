#!/usr/bin/env bash
# Fresh disposable GitHub-hosted API 37.0 AVD. Wait for framework services,
# rather than treating sys.boot_completed alone as proof of healthy Android.
set -euo pipefail

if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_ENVIRONMENT:-}" != github-hosted || $# == 0 ]]; then
  echo 'Run only in the isolated GitHub-hosted Android test workflow.' >&2
  exit 1
fi

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then
  sdkmanager="$(find "$sdk_root/cmdline-tools" -type f -name sdkmanager -perm -u+x | sort -V | tail -n 1)"
fi
avdmanager="$(dirname "$sdkmanager")/avdmanager"
if [[ -z "$sdk_root" || ! -x "$sdkmanager" || ! -x "$avdmanager" ]]; then
  echo 'Android command line tools are unavailable.' >&2
  exit 1
fi
image='system-images;android-37.0;default;x86_64'
"$sdkmanager" --install emulator "$image" --channel=0
export PATH="$sdk_root/platform-tools:$PATH"
export ANDROID_AVD_HOME="$(mktemp -d "${RUNNER_TEMP:-/tmp}/myfinhub-api37-avd.XXXXXX")"
export ANDROID_SERIAL=emulator-5554
avd_name=myfinhub-api37
emu_log="$ANDROID_AVD_HOME/emulator.log"
emu_pid=''
cleanup() {
  rc=$?
  trap - EXIT
  if (( rc != 0 )) && [[ -f "$emu_log" ]]; then
    echo 'Last emulator host diagnostics:'
    tail -n 120 "$emu_log" || true
  fi
  if [[ -n "$emu_pid" ]]; then
    timeout 10 adb -s "$ANDROID_SERIAL" emu kill >/dev/null 2>&1 || true
    kill "$emu_pid" 2>/dev/null || true
    wait "$emu_pid" 2>/dev/null || true
  fi
  rm -rf -- "$ANDROID_AVD_HOME"
  exit "$rc"
}
trap cleanup EXIT
printf 'no\n' | "$avdmanager" create avd --force -n "$avd_name" --package "$image" --device pixel_6
python3 - "$ANDROID_AVD_HOME/$avd_name.avd/config.ini" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
values = {"hw.cpu.ncore": "2", "hw.ramSize": "4096", "hw.heapSize": "384", "disk.dataPartition.size": "8192M"}
lines = [line for line in p.read_text().splitlines() if line.partition("=")[0].strip() not in values]
p.write_text("\n".join(lines + [f"{k}={v}" for k, v in values.items()]) + "\n")
PY
# The prior Vulkan-backed modes crashed system_server or its graphics surfaces.
# Use the supported GLES software renderer and disable Vulkan on this test host.
"$sdk_root/emulator/emulator" -avd "$avd_name" -port 5554 \
  -no-window -no-audio -no-boot-anim -no-snapshot -accel on \
  -gpu swiftshader -feature -Vulkan -camera-back none -camera-front none \
  >"$emu_log" 2>&1 &
emu_pid=$!
adb_cmd() { timeout 10 adb -s "$ANDROID_SERIAL" "$@"; }
# Require consecutive healthy observations of core binder services and the same
# system_server PID; the boot property alone survived earlier host crashes.
stable=0
last_pid=''
for ((attempt=0; attempt<90; attempt++)); do
  kill -0 "$emu_pid" 2>/dev/null || { echo 'Emulator exited before framework readiness.' >&2; exit 1; }
  boot="$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)"
  pid="$(adb_cmd shell pidof system_server 2>/dev/null | tr -d '\r\n' || true)"
  healthy=true
  if [[ "$boot" != 1 || ! "$pid" =~ ^[0-9]+$ ]]; then
    healthy=false
  else
    for svc in package activity input settings window; do
      if ! adb_cmd shell service check "$svc" 2>/dev/null | grep -Eq "^Service $svc: found[[:space:]]*$"; then
        healthy=false
        break
      fi
    done
  fi
  if [[ "$healthy" == true ]]; then
    if [[ "$pid" != "$last_pid" ]]; then stable=0; fi
    last_pid="$pid"
    stable=$((stable+1))
    if (( stable >= 5 )); then break; fi
  else
    stable=0
    last_pid=''
  fi
  sleep 2
done
if (( stable < 5 )); then
  echo 'API 37 framework never reached stable service readiness.' >&2
  exit 1
fi
actual_api="$(adb_cmd shell getprop ro.build.version.sdk | tr -d '\r\n')"
[[ "$actual_api" == 37 ]] || { echo "Unexpected guest API: $actual_api" >&2; exit 1; }
echo "API $actual_api framework stable; guest data partition:"
adb_cmd shell df -k /data
adb_cmd shell input keyevent 82
# Keep the existing CI animation policy, not the app's production motion policy.
for key in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb_cmd shell settings put global "$key" 0
done
adb_cmd shell settings put system screen_off_timeout 2147483647
"$@"
