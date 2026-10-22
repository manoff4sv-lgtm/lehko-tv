@echo off
rem Gives the TV its own home screen back and turns on every app Lehko turned off.
rem Works even if Lehko is broken or already uninstalled (Lehko keeps a note of its changes on the TV).
rem   revert.bat 192.168.1.50              (keeps Lehko installed)
rem   revert.bat 192.168.1.50 --uninstall  (also removes Lehko)
setlocal EnableDelayedExpansion
if "%~1"=="" (echo Usage: revert.bat TV_IP [--uninstall] & exit /b 1)
set TV=%~1:5555
where adb >nul 2>nul || (echo adb not found, see install.bat & exit /b 1)
adb connect %TV%
echo Waiting for the TV. If it asks "Allow USB debugging?", tick "Always allow" and press OK.
adb -s %TV% wait-for-device
set HOMEC=
set DISABLED=
set SAVERCHANGED=
set SAVER=
set SAVERENABLED=
set ADBCHANGED=
set ADBTIME=
for /f "usebackq tokens=1,* delims==" %%a in (`adb -s %TV% shell cat /data/local/tmp/lehko_state 2^>nul`) do (
  if "%%a"=="home" set HOMEC=%%b
  if "%%a"=="disabled" set DISABLED=%%b
  if "%%a"=="screensaver_changed" set SAVERCHANGED=%%b
  if "%%a"=="screensaver" set SAVER=%%b
  if "%%a"=="screensaver_enabled" set SAVERENABLED=%%b
  if "%%a"=="adb_time_changed" set ADBCHANGED=%%b
  if "%%a"=="adb_time" set ADBTIME=%%b
)
if "!DISABLED!"=="" set DISABLED=com.google.android.apps.tv.launcherx,com.google.android.tvlauncher,com.google.android.tungsten.setupwraith,com.google.android.tvrecommendations
for %%p in (!DISABLED:,= !) do (
  echo %%p| findstr /R /X "[A-Za-z0-9_.][A-Za-z0-9_.]*" >nul && (
    echo Turning on %%p
    adb -s %TV% shell pm enable %%p
  )
)
set HOMEOK=
if not "!HOMEC!"=="" echo !HOMEC!| findstr /R /X "[A-Za-z0-9_.]*/[A-Za-z0-9_.$]*" >nul && set HOMEOK=1
if defined HOMEOK (
  adb -s %TV% shell cmd package set-home-activity --user 0 !HOMEC!
) else (
  adb -s %TV% shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
)
adb -s %TV% shell cmd role remove-role-holder --user 0 android.app.role.HOME ua.lehko.tv >nul 2>nul
if "!SAVERCHANGED!"=="true" (
  set SAVEROK=
  if not "!SAVER!"=="" if not "!SAVER!"=="null" echo !SAVER!| findstr /R /X "[A-Za-z0-9_.$/,:]*" >nul && set SAVEROK=1
  if defined SAVEROK (adb -s %TV% shell settings put secure screensaver_components !SAVER!) else (adb -s %TV% shell settings delete secure screensaver_components)
  set SEOK=
  echo !SAVERENABLED!| findstr /R /X "[0-9-][0-9-]*" >nul && set SEOK=1
  if defined SEOK (adb -s %TV% shell settings put secure screensaver_enabled !SAVERENABLED!) else (adb -s %TV% shell settings delete secure screensaver_enabled)
)
if "!ADBCHANGED!"=="true" (
  set ATOK=
  echo !ADBTIME!| findstr /R /X "[0-9-][0-9-]*" >nul && set ATOK=1
  if defined ATOK (adb -s %TV% shell settings put global adb_allowed_connection_time !ADBTIME!) else (adb -s %TV% shell settings delete global adb_allowed_connection_time)
)
if "%~2"=="--uninstall" adb -s %TV% shell pm uninstall ua.lehko.tv
adb -s %TV% shell rm -f /data/local/tmp/lehko_state
adb -s %TV% shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >nul
echo Done: the TV home screen is back.
