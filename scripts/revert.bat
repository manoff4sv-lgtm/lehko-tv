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
set A11YCHANGED=
set A11YENABLED=
set GOTSTATE=
for /f "usebackq tokens=1,* delims==" %%a in (`adb -s %TV% shell cat /data/local/tmp/lehko_state 2^>nul`) do (
  set GOTSTATE=1
  if "%%a"=="home" set HOMEC=%%b
  if "%%a"=="disabled" set DISABLED=%%b
  if "%%a"=="screensaver_changed" set SAVERCHANGED=%%b
  if "%%a"=="screensaver" set SAVER=%%b
  if "%%a"=="screensaver_enabled" set SAVERENABLED=%%b
  if "%%a"=="adb_time_changed" set ADBCHANGED=%%b
  if "%%a"=="adb_time" set ADBTIME=%%b
  if "%%a"=="a11y_changed" set A11YCHANGED=%%b
  if "%%a"=="a11y_enabled" set A11YENABLED=%%b
)
rem The usual stock launcher apps only without notes, like revert.sh: an empty disabled= means Lehko turned nothing off.
if not defined GOTSTATE (
  echo No Lehko notes on the TV, turning on the usual stock launcher apps.
  set DISABLED=com.google.android.apps.tv.launcherx,com.google.android.tvlauncher,com.google.android.tungsten.setupwraith,com.google.android.tvrecommendations
)
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
rem Only while Lehko's screensaver is still set, as in the app: one picked in the TV's settings since then stays.
rem And screensaver_enabled only while it is 1, as Lehko set it: "Turn screen off" chosen since then stays too.
set SAVERNOW=
for /f "usebackq delims=" %%s in (`adb -s %TV% shell settings get secure screensaver_components`) do set SAVERNOW=%%s
if not "!SAVERNOW:~0,12!"=="ua.lehko.tv/" set SAVERCHANGED=
set SENOW=
for /f "usebackq delims=" %%s in (`adb -s %TV% shell settings get secure screensaver_enabled`) do set SENOW=%%s
if "!SAVERCHANGED!"=="true" (
  set SAVEROK=
  if not "!SAVER!"=="" if not "!SAVER!"=="null" echo !SAVER!| findstr /R /X "[A-Za-z0-9_.$/,:]*" >nul && set SAVEROK=1
  if defined SAVEROK (adb -s %TV% shell settings put secure screensaver_components !SAVER!) else (adb -s %TV% shell settings delete secure screensaver_components)
  if "!SENOW:~0,1!"=="1" (
    set SEOK=
    echo !SAVERENABLED!| findstr /R /X "[0-9-][0-9-]*" >nul && set SEOK=1
    if defined SEOK (adb -s %TV% shell settings put secure screensaver_enabled !SAVERENABLED!) else (adb -s %TV% shell settings delete secure screensaver_enabled)
  )
)
rem Lehko's remote helper (an accessibility service): only its own entry goes, every other service stays on. With none
rem left, the list goes and accessibility_enabled is put back as Lehko found it. A list of anything but plain component
rem names is left alone.
set SERVICES=
for /f "usebackq delims=" %%s in (`adb -s %TV% shell settings get secure enabled_accessibility_services`) do set SERVICES=%%s
if "!SERVICES!"=="null" set SERVICES=
set LISTOK=1
set OURS=
set REST=
if defined SERVICES (
  echo !SERVICES!| findstr /R /X "[A-Za-z0-9_.:/$]*" >nul || set LISTOK=
)
if defined SERVICES if defined LISTOK (
  for %%c in (!SERVICES::= !) do (
    echo %%c| findstr /R /X "[A-Za-z0-9_.][A-Za-z0-9_.]*/[A-Za-z0-9_.$][A-Za-z0-9_.$]*" >nul || set LISTOK=
    set C=%%c
    if "!C:~0,12!"=="ua.lehko.tv/" (set OURS=1) else if defined REST (set REST=!REST!:%%c) else (set REST=%%c)
  )
)
rem Single quotes: a class name may hold a $, which the TV's shell would expand.
if defined LISTOK (
  if defined OURS (
    if defined REST (adb -s %TV% shell settings put secure enabled_accessibility_services '!REST!') else (adb -s %TV% shell settings delete secure enabled_accessibility_services)
  )
  if not defined REST if "!A11YCHANGED!"=="true" (
    set A11YOK=
    echo !A11YENABLED!| findstr /R /X "[0-9-][0-9-]*" >nul && set A11YOK=1
    if defined A11YOK (adb -s %TV% shell settings put secure accessibility_enabled !A11YENABLED!) else (adb -s %TV% shell settings delete secure accessibility_enabled)
  )
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
