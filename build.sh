#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
printf '\nAPK : app/build/outputs/apk/debug/app-debug.apk\n'
