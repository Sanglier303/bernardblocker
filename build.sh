#!/usr/bin/env bash
set -euo pipefail
chmod +x ./gradlew
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
