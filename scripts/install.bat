@echo off
rem Installs Lehko on a TV from a Windows computer on the same Wi-Fi.
rem   install.bat 192.168.1.50            (downloads the latest Lehko.apk)
rem   install.bat 192.168.1.50 Lehko.apk  (uses a file you have)
rem Needs adb (Android platform-tools): https://developer.android.com/tools/releases/platform-tools
setlocal
if "%~1"=="" (echo Usage: install.bat TV_IP [Lehko.apk] & exit /b 1)
set TV=%~1:5555
set APK=%~2
where adb >nul 2>nul || (echo adb not found. Unzip platform-tools and run this from that folder, or add it to PATH. & exit /b 1)
if "%APK%"=="" (
  set APK=%TEMP%\Lehko.apk
  echo Downloading the latest Lehko...
  curl -fL -o "%TEMP%\Lehko.apk" https://github.com/manoff4sv-lgtm/lehko-tv/releases/latest/download/Lehko.apk || exit /b 1
)
adb connect %TV%
echo If the TV asks "Allow USB debugging?", tick "Always allow" and press OK.
adb -s %TV% wait-for-device
adb -s %TV% install -r "%APK%" || exit /b 1
adb -s %TV% shell am start -n ua.lehko.tv/.SettingsActivity >nul
echo Installed. On the TV: Lehko settings ^> Home screen ^> Turn on.
