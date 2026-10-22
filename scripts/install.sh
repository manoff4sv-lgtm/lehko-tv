#!/usr/bin/env bash
# Installs Lehko on a TV from a Mac or Linux computer on the same Wi-Fi.
#   scripts/install.sh 192.168.1.50            (downloads the latest Lehko.apk)
#   scripts/install.sh 192.168.1.50 Lehko.apk  (uses a file you have)
# On the TV first: Settings > System > About > press "Android TV OS build" 7 times, then
# Settings > System > Developer options > USB debugging: on.
set -euo pipefail
IP="${1:?Usage: install.sh TV_IP [Lehko.apk]}"
APK="${2:-}"
TV="$IP:5555"
command -v adb >/dev/null || {
  echo "adb not found. Install Android platform-tools (macOS: brew install android-platform-tools," \
       "Ubuntu: sudo apt install adb) or get them from https://developer.android.com/tools/releases/platform-tools"
  exit 1
}
if [ -z "$APK" ]; then
  APK="$(mktemp -d)/Lehko.apk"
  echo "Downloading the latest Lehko..."
  curl -fL --progress-bar -o "$APK" https://github.com/manoff4sv-lgtm/lehko-tv/releases/latest/download/Lehko.apk
fi
adb connect "$TV"
echo "If the TV asks \"Allow USB debugging?\", tick \"Always allow\" and press OK."
adb -s "$TV" wait-for-device
adb -s "$TV" install -r "$APK"
adb -s "$TV" shell am start -n ua.lehko.tv/.SettingsActivity >/dev/null
echo "Installed. On the TV: Lehko settings > Home screen > Turn on."
