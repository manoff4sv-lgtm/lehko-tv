@echo off
rem Gives the TV its own home screen back and turns on every app Lehko turned off.
rem   revert.bat 192.168.1.50              (keeps Lehko installed)
rem   revert.bat 192.168.1.50 --uninstall  (also removes Lehko)
setlocal EnableDelayedExpansion
if "%~1"=="" (echo Usage: revert.bat TV_IP [--uninstall] & exit /b 1)
set TV=%~1:5555
where adb >nul 2>nul || (echo adb not found, see install.bat & exit /b 1)
adb connect %TV%
adb -s %TV% wait-for-device
set HOMEC=
set DISABLED=
set SAVERCHANGED=
set SAVER=
for /f "usebackq tokens=1,* delims==" %%a in (`adb -s %TV% shell cat /data/local/tmp/lehko_state 2^>nul`) do (
  if "%%a"=="home" set HOMEC=%%b
  if "%%a"=="disabled" set DISABLED=%%b
  if "%%a"=="screensaver_changed" set SAVERCHANGED=%%b
  if "%%a"=="screensaver" set SAVER=%%b
)
if "!DISABLED!"=="" set DISABLED=com.google.android.apps.tv.launcherx,com.google.android.tvlauncher
for %%p in (!DISABLED:,= !) do (
  echo Turning on %%p
  adb -s %TV% shell pm enable %%p
)
if not "!HOMEC!"=="" (
  adb -s %TV% shell cmd package set-home-activity --user 0 !HOMEC!
) else (
  adb -s %TV% shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
)
adb -s %TV% shell cmd role remove-role-holder --user 0 android.app.role.HOME ua.lehko.tv >nul 2>nul
if "!SAVERCHANGED!"=="true" (
  if "!SAVER!"=="" (adb -s %TV% shell settings delete secure screensaver_components) else if "!SAVER!"=="null" (adb -s %TV% shell settings delete secure screensaver_components) else (adb -s %TV% shell settings put secure screensaver_components !SAVER!)
)
if "%~2"=="--uninstall" adb -s %TV% shell pm uninstall ua.lehko.tv
adb -s %TV% shell rm -f /data/local/tmp/lehko_state
adb -s %TV% shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >nul
echo Done: the TV home screen is back.
