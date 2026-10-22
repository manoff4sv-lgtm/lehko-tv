#!/usr/bin/env bash
# Gives the TV its own home screen back and turns on every app Lehko turned off, from a computer.
# Works even if Lehko is broken or already uninstalled (Lehko keeps a note of its changes on the TV).
#   scripts/revert.sh 192.168.1.50              (keeps Lehko installed)
#   scripts/revert.sh 192.168.1.50 --uninstall  (also removes Lehko)
set -euo pipefail
IP="${1:?Usage: revert.sh TV_IP [--uninstall]}"
TV="$IP:5555"
command -v adb >/dev/null || { echo "adb not found (see scripts/install.sh)"; exit 1; }
adb connect "$TV"
adb -s "$TV" wait-for-device
sh() { adb -s "$TV" shell "$@" | tr -d '\r'; }

STATE="$(sh cat /data/local/tmp/lehko_state 2>/dev/null || true)"
get() { printf '%s\n' "$STATE" | sed -n "s/^$1=//p" | head -1; }
HOME_COMPONENT="$(get home)"
DISABLED="$(get disabled)"
SAVER_CHANGED="$(get screensaver_changed)"
SAVER="$(get screensaver)"

if [ -z "$STATE" ]; then
  echo "No Lehko notes on the TV; turning on the usual stock launchers."
  DISABLED="com.google.android.apps.tv.launcherx,com.google.android.tvlauncher"
fi
for p in ${DISABLED//,/ }; do
  case "$p" in *[!A-Za-z0-9._]*|"") continue ;; esac
  echo "Turning on $p"
  sh pm enable "$p" || true
done
if [ -n "$HOME_COMPONENT" ] && [ "${HOME_COMPONENT#*/}" != "$HOME_COMPONENT" ]; then
  sh cmd package set-home-activity --user 0 "$HOME_COMPONENT" || true
elif sh pm path com.google.android.apps.tv.launcherx | grep -q package:; then
  sh cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity || true
fi
sh cmd role remove-role-holder --user 0 android.app.role.HOME ua.lehko.tv >/dev/null 2>&1 || true
if [ "$SAVER_CHANGED" = "true" ]; then
  if [ -z "$SAVER" ] || [ "$SAVER" = "null" ]; then sh settings delete secure screensaver_components
  else sh settings put secure screensaver_components "$SAVER"; fi
fi
if [ "${2:-}" = "--uninstall" ]; then sh pm uninstall ua.lehko.tv || true; fi
sh rm -f /data/local/tmp/lehko_state
sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "Done: the TV's own home screen is back."
