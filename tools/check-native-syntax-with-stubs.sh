#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
"${KOTLINC:-kotlinc}" tools/android-api-stubs/*.kt app/src/main/java/com/blaze/circuitlab/core/*.kt app/src/main/java/com/blaze/circuitlab/*.kt -d /tmp/blaze-native-android-api-stub-check.jar > /tmp/blaze-native-stub-check.log 2>&1
printf 'PASS Kotlin-source compile against temporary API stubs (NOT Android SDK or APK compilation)\n'
