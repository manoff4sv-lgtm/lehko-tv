#!/usr/bin/env bash
# Gives the TV its own home screen back and turns on every app Lehko turned off, from a computer.
# Works even if Lehko is broken or already uninstalled (Lehko keeps a note of its changes on the TV).
#   scripts/revert.sh 192.168.1.50              (keeps Lehko installed)
#   scripts/revert.sh 192.168.1.50 --uninstall  (also removes Lehko)
set -uo pipefail
IP="${1:?Usage: revert.sh TV_IP [--uninstall]}"
TV="$IP:5555"
command -v adb >/dev/null || { echo "adb not found (see scripts/install.sh)"; exit 1; }
adb connect "$TV"
echo "Waiting for the TV. If it asks \"Allow USB debugging?\", tick \"Always allow\" and press OK."
adb -s "$TV" wait-for-device || exit 1
tv() { adb -s "$TV" shell "$@" | tr -d '\r'; }

STATE="$(tv cat /data/local/tmp/lehko_state 2>/dev/null)"
get() { printf '%s\n' "$STATE" | sed -n "s/^$1=//p" | head -1; }
HOME_COMPONENT="$(get home)"
DISABLED="$(get disabled)"
SAVER_CHANGED="$(get screensaver_changed)"
SAVER="$(get screensaver)"
SAVER_ENABLED="$(get screensaver_enabled)"
ADB_TIME_CHANGED="$(get adb_time_changed)"
ADB_TIME="$(get adb_time)"
A11Y_CHANGED="$(get a11y_changed)"
A11Y_ENABLED="$(get a11y_enabled)"

# Only plain package/component names and numbers ever go to the TV's shell.
pkg_ok() { [[ "$1" =~ ^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$ ]]; }
component_ok() { [[ "$1" =~ ^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.\$]+$ ]]; }
C='[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+'
components_ok() { [[ "$1" =~ ^$C(:$C)*$ ]]; }

if [ -z "$STATE" ]; then
  echo "No Lehko notes on the TV; turning on the usual stock launcher apps."
  DISABLED="com.google.android.apps.tv.launcherx,com.google.android.tvlauncher,com.google.android.tungsten.setupwraith,com.google.android.tvrecommendations"
fi
for p in ${DISABLED//,/ }; do
  pkg_ok "$p" || continue
  echo "Turning on $p"
  tv pm enable "$p"
done
if component_ok "$HOME_COMPONENT"; then
  tv cmd package set-home-activity --user 0 "$HOME_COMPONENT"
elif tv pm path com.google.android.apps.tv.launcherx | grep -q package:; then
  tv cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
fi
tv cmd role remove-role-holder --user 0 android.app.role.HOME ua.lehko.tv >/dev/null 2>&1
# Only while Lehko's screensaver is still set, as in the app: one picked in the TV's settings since then stays.
if [ "$SAVER_CHANGED" = "true" ] && [[ "$(tv settings get secure screensaver_components)" == ua.lehko.tv/* ]]; then
  if [[ "$SAVER" =~ ^[A-Za-z0-9_.\$/,:]+$ ]] && [ "$SAVER" != "null" ]; then
    tv settings put secure screensaver_components "$SAVER"
  else
    tv settings delete secure screensaver_components
  fi
  # Lehko turned it on; "Turn screen off" chosen since then (0) stays too.
  if [ "$(tv settings get secure screensaver_enabled)" = "1" ]; then
    if [[ "$SAVER_ENABLED" =~ ^-?[0-9]+$ ]]; then tv settings put secure screensaver_enabled "$SAVER_ENABLED"
    else tv settings delete secure screensaver_enabled; fi
  fi
fi
# Lehko's remote helper (an accessibility service): only its own entry goes, every other service stays on. With none
# left, the list goes and accessibility_enabled is put back as Lehko found it. A list of anything but plain component
# names is left alone.
SERVICES="$(tv settings get secure enabled_accessibility_services)"
[ "$SERVICES" = "null" ] && SERVICES=""
if [ -z "$SERVICES" ] || components_ok "$SERVICES"; then
  REST="" OURS=""
  for s in ${SERVICES//:/ }; do
    if [[ "$s" == ua.lehko.tv/* ]]; then OURS=1; else REST="${REST:+$REST:}$s"; fi
  done
  # Single quotes: a class name may hold a '$', which the TV's shell would expand.
  if [ -n "$OURS" ] && [ -n "$REST" ]; then tv settings put secure enabled_accessibility_services "'$REST'"
  elif [ -n "$OURS" ]; then tv settings delete secure enabled_accessibility_services; fi
  if [ -z "$REST" ] && [ "$A11Y_CHANGED" = "true" ]; then
    if [[ "$A11Y_ENABLED" =~ ^-?[0-9]+$ ]]; then tv settings put secure accessibility_enabled "$A11Y_ENABLED"
    else tv settings delete secure accessibility_enabled; fi
  fi
fi
if [ "$ADB_TIME_CHANGED" = "true" ]; then
  if [[ "$ADB_TIME" =~ ^-?[0-9]+$ ]]; then tv settings put global adb_allowed_connection_time "$ADB_TIME"
  else tv settings delete global adb_allowed_connection_time; fi
fi
if [ "${2:-}" = "--uninstall" ]; then tv pm uninstall ua.lehko.tv; fi
tv rm -f /data/local/tmp/lehko_state
tv am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "Done: the TV's own home screen is back."
