#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Prefer an explicitly provided Gradle installation (useful on machines
# without network access to services.gradle.org).
if [ -x ".tools/gradle-8.9/bin/gradle" ]; then
  GRADLE=".tools/gradle-8.9/bin/gradle"
else
  GRADLE="./gradlew"
fi

"$GRADLE" :app:assembleRelease --no-daemon
mkdir -p release
cp app/build/outputs/apk/release/app-release.apk "release/小星星视频-v1.2.9.apk"
shasum -a 256 "release/小星星视频-v1.2.9.apk" | tee "release/小星星视频-v1.2.9.apk.sha256"
