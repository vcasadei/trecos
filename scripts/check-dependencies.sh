#!/usr/bin/env bash
# Fails when the release runtime classpath holds a dependency group that isn't
# in config/dependency-allowlist.txt, or any known advertising, analytics or
# crash-reporting SDK (task 14.6, help-and-support spec "No tracking").
set -euo pipefail
cd "$(dirname "$0")/.."

deps=$(./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath | grep -oE '[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:' | sed 's/:$//' | sort -u)

# Artifacts that are never allowed, whatever pulls them in.
deny='firebase-analytics|firebase-crashlytics|firebase-perf|firebase-messaging|play-services-ads|play-services-analytics|play-services-measurement|crashlytics|io\.sentry|com\.bugsnag|com\.facebook|com\.appsflyer|com\.adjust|com\.mixpanel|com\.amplitude|com\.segment|com\.flurry|com\.newrelic|com\.instabug|com\.datadoghq|com\.onesignal|com\.applovin|com\.unity3d\.ads|com\.ironsource|com\.mopub'
if denied=$(echo "$deps" | grep -E "$deny"); then
  echo "Forbidden tracking, advertising or crash-reporting dependencies:"
  echo "$denied"
  exit 1
fi

allowed=$(grep -vE '^\s*(#|$)' config/dependency-allowlist.txt)
unknown=""
for group in $(echo "$deps" | cut -d: -f1 | sort -u); do
  ok=no
  while read -r pattern; do
    case "$group" in
      $pattern) ok=yes; break ;;
    esac
  done <<< "$allowed"
  [ "$ok" = yes ] || unknown="$unknown $group"
done
if [ -n "$unknown" ]; then
  echo "Dependency groups not in config/dependency-allowlist.txt:$unknown"
  echo "Review them (no ads, analytics or crash reporting) and add them to the allowlist."
  exit 1
fi
echo "Dependencies OK: $(echo "$deps" | wc -l) artifacts, all groups allowed."
