#!/usr/bin/env bash
# Android runtime smoke only; not a numerical, KiCad, or physical-device certification.
set -euo pipefail

PKG='com.blaze.circuitlab'
APK="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="${SMOKE_OUTPUT_DIR:-build/android-smoke}"
mkdir -p "$OUT"

gather_evidence() {
  adb logcat -d -v time -t 1400 > "$OUT/logcat.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/screenshot.png" 2>/dev/null || true
  adb shell dumpsys activity activities > "$OUT/activities.txt" 2>/dev/null || true
}
trap gather_evidence EXIT

assert_running() {
  local pid
  pid="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "$pid" ]]; then
    echo "ERROR: $PKG stopped unexpectedly" >&2
    return 1
  fi
  echo "Alive PID(s): $pid"
}

launch() {
  local result
  result="$(adb shell am start -W -n "$PKG/.MainActivity" 2>&1)"
  printf '%s\n' "$result"
  if ! grep -Eq 'Status:[[:space:]]+ok' <<< "$result"; then
    echo 'ERROR: Activity Manager did not report a successful launch' >&2
    return 1
  fi
  sleep 3
  assert_running
}

[[ -s "$APK" ]] || { echo "ERROR: no APK at $APK" >&2; exit 1; }
adb wait-for-device
adb install -r "$APK"
adb shell am force-stop "$PKG" || true
adb logcat -c || true

# First cold launch.
launch
# Verify that returning from Home does not kill the Activity.
adb shell input keyevent KEYCODE_HOME
sleep 1
launch
# Explicitly disable emulator network transport, then cold relaunch offline.
adb shell svc wifi disable || true
adb shell svc data disable || true
adb shell am force-stop "$PKG"
launch
# Orientation changes must not terminate the application process.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
assert_running
adb shell settings put system user_rotation 0
sleep 3
assert_running

# Basic screenshot check: actual screen pixels were captured.
adb exec-out screencap -p > "$OUT/screenshot.png"
[[ $(wc -c < "$OUT/screenshot.png") -gt 10000 ]] || { echo 'ERROR: screenshot is missing/empty' >&2; exit 1; }
echo 'Android emulator install, launch, offline relaunch, Home return and rotation smoke: PASS'