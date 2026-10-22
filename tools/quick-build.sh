#!/usr/bin/env bash
# Builds a debug APK without Gradle (aapt2 -> javac -> d8/dx -> zipalign -> apksigner), for a quick check
# on machines without Android Studio. The real build is Gradle: ./gradlew assembleRelease
#
# Needs a JDK and the Android build tools. Paths can be overridden:
#   ANDROID_JAR=/path/to/android.jar BUILD_TOOLS=/path/to/build-tools/34.0.0 tools/quick-build.sh
set -euo pipefail
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/lib/android-sdk}}"
BT="${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)}"
AJ="${ANDROID_JAR:-$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)}"
[ -x "$BT/aapt2" ] || { echo "aapt2 not found (set BUILD_TOOLS)"; exit 1; }
[ -f "$AJ" ] || { echo "android.jar not found (set ANDROID_JAR)"; exit 1; }

OUT=build/quick
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
MAIN=app/src/main

# Gradle takes the package from `namespace`; plain aapt2 needs it in the manifest.
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ua.lehko.tv">|' \
  "$MAIN/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BT/aapt2" compile --dir "$MAIN/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$AJ" --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/gen" "$OUT/res.zip" --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 1 --version-name quick --auto-add-overlay

javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -classpath "$AJ" \
  -d "$OUT/classes" $(find "$MAIN/java" "$OUT/gen" -name '*.java')

if [ -x "$BT/d8" ]; then
  "$BT/d8" --min-api 26 --lib "$AJ" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
else
  "$BT/dx" --dex --min-sdk-version=26 --output="$OUT/dex/classes.dex" "$OUT/classes"
fi
(cd "$OUT/dex" && zip -qj ../base.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

KS="$OUT/../quick-debug.keystore"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias debug -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android -dname "CN=Lehko debug" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$OUT/lehko-quick.apk" "$OUT/aligned.apk"
echo "Built $OUT/lehko-quick.apk ($(du -h "$OUT/lehko-quick.apk" | cut -f1))"
