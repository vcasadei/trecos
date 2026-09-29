#!/bin/sh
# Fails if a release APK still calls android.util.Log.d or Log.v.
# Usage: scripts/check-release-logs.sh [path/to/app-release.apk]
set -eu
apk="${1:-app/build/outputs/apk/release/app-release-unsigned.apk}"
dexdump="$(ls -d "${ANDROID_HOME:?set ANDROID_HOME}"/build-tools/*/dexdump | sort -V | tail -1)"
calls="$("$dexdump" -d "$apk" | grep -E 'Landroid/util/Log;\.(d|v):' || true)"
if [ -n "$calls" ]; then
    echo "Debug or verbose log calls found in $apk:"
    echo "$calls"
    exit 1
fi
echo "No Log.d or Log.v calls in $apk"
