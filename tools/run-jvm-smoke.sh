#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
"$KOTLINC" app/src/main/java/com/blaze/circuitlab/core/*.kt tools/JvmSmoke.kt -include-runtime -d /tmp/blaze-circuitlab-jvm-smoke.jar
java -jar /tmp/blaze-circuitlab-jvm-smoke.jar
