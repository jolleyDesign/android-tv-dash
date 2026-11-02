#!/usr/bin/env bash
# Build the release APK and install it on the Streamer (default) or the Google TV emulator.
# Usage: tools/deploy.sh [emulator]
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME_OVERRIDE:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

if [[ "${1:-}" == "emulator" ]]; then
  serial=$("$ADB" devices -l | awk '/^emulator-/ && $0 ~ /atv|google_tv|gtv/ {print $1; exit}')
else
  match="device:kirkwood"
  serial=$("$ADB" devices -l | awk -v m="$match" '$0 ~ m {print $1; exit}')
fi

if [[ -z "${serial:-}" ]]; then
  echo "Target not connected. adb devices:" >&2
  "$ADB" devices -l >&2
  echo "See docs/DEVICE.md → Reconnecting adb." >&2
  exit 1
fi

./gradlew :app:assembleRelease -q
"$ADB" -s "$serial" install -r app/build/outputs/apk/release/app-release.apk
"$ADB" -s "$serial" shell appops set dev.jolley.dash GET_USAGE_STATS allow
"$ADB" -s "$serial" shell am start -n dev.jolley.dash/.MainActivity >/dev/null
echo "Installed on $serial"
