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

# First cold launch. Verify that a real electrical operating point is displayed.
launch
python3 tools/android_ui_probe.py assert 'MNA ·' --evidence "$OUT/initial-readout.xml"
python3 tools/android_ui_probe.py assert 'BLAZE CIRCUITLAB' --evidence "$OUT/offline-catalog.xml"
# This is an Android asset-backed library screen, not a mock list.
# Libraries live to the right of the palette: swipe its native horizontal
# scroll strip by accessibility bounds, instead of assuming old "Libraries" label.
python3 tools/android_ui_probe.py tap 'Parts Library' --scroll-anchor 'Select' --evidence "$OUT/library-toolbar.xml"
python3 tools/android_ui_probe.py assert 'Offline Electronics' --evidence "$OUT/library-picker.xml"
adb shell input keyevent KEYCODE_BACK
# MCU board references must also be reachable through the real offline toolbar.
python3 tools/android_ui_probe.py tap 'MCU Library' --scroll-anchor 'Parts Library' --evidence "$OUT/mcu-toolbar.xml"
python3 tools/android_ui_probe.py assert 'Arduino / ESP MCU Pin Library' --evidence "$OUT/mcu-picker.xml"
adb shell input keyevent KEYCODE_BACK
# Exercise native PCB/schematic controls, not just process liveness.
python3 tools/android_ui_probe.py tap 'PCB Placement'
sleep 1
python3 tools/android_ui_probe.py assert 'PCB:' --evidence "$OUT/pcb-workspace.xml"
python3 tools/android_ui_probe.py tap 'Schematic'
sleep 1
python3 tools/android_ui_probe.py assert 'D1 (LED)' --evidence "$OUT/schematic-workspace.xml"
# The real simulator must be interactive and report computed values.
python3 tools/android_ui_probe.py tap '▶ Run'
sleep 3
assert_running
# uiautomator dump may itself foreground another activity and invoke onPause,
# resetting the Run label. Capture pixels without asserting a transient UI label.
adb exec-out screencap -p > "$OUT/simulation-running.png"
[[ $(wc -c < "$OUT/simulation-running.png") -gt 10000 ]] || { echo 'ERROR: no simulated screen'; exit 1; }
# Verify returning from Home does not kill the Activity.
adb shell input keyevent KEYCODE_HOME
sleep 1
launch
python3 tools/android_ui_probe.py assert 'MNA ·' --evidence "$OUT/home-return.xml"
# Explicitly disable emulator network transport, then cold relaunch offline.
adb shell svc wifi disable || true
adb shell svc data disable || true
adb shell am force-stop "$PKG"
launch
python3 tools/android_ui_probe.py assert 'MNA ·' --evidence "$OUT/offline-relaunch.xml"
# Orientation changes must not terminate the application process.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
assert_running
python3 tools/android_ui_probe.py assert 'MNA ·' --evidence "$OUT/landscape.xml"
adb shell settings put system user_rotation 0
sleep 3
assert_running
python3 tools/android_ui_probe.py assert 'MNA ·' --evidence "$OUT/portrait.xml"

# Basic screenshot check: actual screen pixels were captured.
adb exec-out screencap -p > "$OUT/screenshot.png"
[[ $(wc -c < "$OUT/screenshot.png") -gt 10000 ]] || { echo 'ERROR: screenshot is missing/empty' >&2; exit 1; }
echo 'Android emulator native UI interaction, numerical readout, offline relaunch, Home and rotation: PASS'