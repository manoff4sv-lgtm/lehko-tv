#!/usr/bin/env bash
# Runs the unit tests without Gradle (after tools/quick-build.sh). Needs junit4, hamcrest and Android's org.json
# (Debian/Ubuntu: apt-get install junit4 libhamcrest-java libandroid-json-java). The real run is ./gradlew test.
set -euo pipefail
cd "$(dirname "$0")/.."
J=/usr/share/java
CP="$J/junit4.jar:$J/hamcrest-core.jar:$J/com.android.json.jar"
AJ="${ANDROID_JAR:-$(ls -d /usr/lib/android-sdk/platforms/android-*/android.jar | sort -V | tail -1)}"
OUT=build/quick
rm -rf "$OUT/test-classes" && mkdir -p "$OUT/test-classes"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -cp "$CP:$OUT/classes:$AJ" -d "$OUT/test-classes" \
  $(find app/src/test/java -name '*.java')
# org.json first, so the real implementation wins over the android.jar stubs.
java -cp "$CP:$OUT/test-classes:$OUT/classes:$AJ" org.junit.runner.JUnitCore \
  $(cd app/src/test/java && find . -name '*Test.java' | sed 's|^\./||; s|\.java$||; s|/|.|g')
