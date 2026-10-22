# Lehko: notes for Claude sessions working on this repo

Lehko ("легко", light/easy) is a light, ad-free home screen (launcher) for weak Android TV / Google TV sets,
first target: Xiaomi TV A 43 FHD 2026 (Google TV, 1 GB RAM, 4x Cortex-A53, Mali-G31 MP2). The repo is public and
meant for anyone to install. Owner: Eugene (GitHub manoff4sv-lgtm), Kyiv. He talks in Russian; UI is uk/ru/en.

## Hard constraints

- Native Java, framework widgets only. **No AndroidX, no Kotlin, no Compose, no libraries** in the app (tests may
  use junit + org.json). Keep RAM tiny: no blur, shaders, big bitmaps, long animations. One process.
- Java 8 language level (lambdas are fine in Gradle builds, but the local quick build uses dx: keep anonymous
  classes, they work everywhere). minSdk 26, targetSdk/compileSdk 34. Package and applicationId `ua.lehko.tv`.
- **R ids are not constants** (AGP 8 non-final R): never `switch` on `R.string.x`, use if/else.
- Never send anything to the TV's shell that came from the network. Only fixed commands in HomeMode/CleanupActivity.
- Never uninstall anything on the TV; only `pm disable-user --user 0` (reversible) and `pm enable`.
- Texts: no long dashes in user-facing strings; middle dot " · " or commas instead.

## Architecture (app/src/main/java/ua/lehko/tv)

- `App` executors (IO x2, ADB single), REPO_URL, version. `Prefs` SharedPreferences helpers.
- `HomeActivity` (HOME intent): TextClock clock/date, weather card, outage card (`OutageBar` x2), favorites row
  (`Tile`s with banners), shortcuts row (HDMI inputs + Live TV via `Inputs`, All apps, TV settings, Lehko settings),
  one-time notice line. Ticker every minute; weather refresh 30 min; outages 10 min.
- `AllAppsActivity` GridView of tiles; long-press/Menu: open, add/remove favorite, info, uninstall.
- `Apps` app list (LEANBACK_LAUNCHER first, then LAUNCHER), default favorites, banner/icon LruCache.
- `Weather` Open-Meteo forecast + geocoding search; cached JSON so it shows offline (outages!). Default city
  Kyiv if TZ is Europe/Kiev.
- `Outages` + `OutageText` + `OutageBar`: schedules in 48 half-hour slots, Kyiv wall clock (TZ id "Europe/Kiev",
  NOT "Europe/Kyiv": old TV tzdata). See research below.
- `Adb`: own ADB client to 127.0.0.1:5555 (RSA key in noBackupFilesDir; signing with BigInteger PKCS#1 v1.5 over
  SHA-1 DigestInfo prefix + token; adbd pubkey blob format). First use pops "Allow USB debugging?" on the TV.
- `HomeMode`: enable(full?) / disable / verify / revertAfterCrash / setScreensaver / setAppEnabled / systemState.
  Records original home, checks Wi-Fi network owners (disabling an owner deletes the Wi-Fi network: refuse),
  `cmd package set-home-activity --user 0 ua.lehko.tv/.HomeActivity`, full mode disables original launcher pkg
  (+ tvrecommendations on classic ATV, + `com.google.android.tungsten.setupwraith` when it has a HOME activity:
  its RecoveryActivity takes over otherwise). Writes /data/local/tmp/lehko_state for the PC revert scripts.
- `Watchdog`: 3 crashes in 5 min while Lehko is home -> `ModeJob` REVERT. `BootReceiver` -> `ModeJob` VERIFY.
- `ClockDream`: optional dim moving clock screensaver.
- Approach inspired by github.com/simbasjungles/free-my-tv (MIT); no code copied. Credit it in README.

## Outage data (researched 2026-10-22)

- Kyiv city switched on 2026-01-29 from queues 1.1-6.2 to **60 per-building groups "N.1"** (DTEK KEM). Kyiv oblast
  and Odesa still 1.1-6.2. Groups are found by address.
- **YASNO** (primary, no auth): base `https://app.yasno.ua/api/blackout-service/public/shutdowns`
  - `/regions/{regionId}/dsos/{dsoId}/planned-outages` -> `{"11.1": {"today": {"slots":[{"start":0,"end":30,
    "type":"Definite"}...], "date":"2026-10-22T00:00:00+03:00", "status":"ScheduleApplies"}, "tomorrow": {...},
    "updatedOn": "..."}}`. start/end = minutes from local midnight; Definite = off, NotPlanned = on, other = maybe.
    status: ScheduleApplies | WaitingForSchedule | NoOutages | EmergencyShutdowns. Add a cache-buster param.
  - Kyiv = region 25, dso 902. Dnipro = region 3, dsos 301 (ДнЕМ) and 303 (ЦЕК).
  - Address lookup: `/addresses/v2/streets?regionId&dsoId&query`, `/addresses/v2/houses?regionId&dsoId&streetId&query`
    -> `[{"id":..,"value":".."}]`; `/addresses/v2/group?regionId&dsoId&streetId&houseId` -> `{"group":11,"subgroup":1}`.
- **DTEK mirror** (fallback for Kyiv, only source for Kyiv oblast/Odesa):
  `https://raw.githubusercontent.com/Baskerville42/outage-data-ua/main/data/{kyiv,kyiv-region,dnipro,odesa}.json`
  `fact.data` keyed by Unix seconds of Kyiv midnight -> `{"GPV11.1": {"1":"yes".."24":"no"}}`; values yes/no/first
  (off first half)/second/maybe/mfirst/msecond. `fact.data` is `[]` when there are no schedules. Group list in
  `preset.sch_names`. Dnipro mirror keys differ from YASNO (GPV1001.1 = ЦЕК 1.1): no fallback for Dnipro.
- Address lookup for people: https://www.dtek-kem.com.ua/ua/shutdowns (anti-bot, not usable from the app).

## Building

- Real build: `./gradlew assembleRelease` (AGP 8.7.3, Gradle 8.10.2 wrapper). CI does this.
- Sandbox without Google Maven (cloud sessions): `tools/quick-build.sh` = aapt2 + javac + dx + apksigner.
  Setup once: `apt-get install -y android-sdk-build-tools android-sdk-platform-23` and
  `curl -L -o /opt/sdkjars/android-33.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar`,
  then `ANDROID_JAR=/opt/sdkjars/android-33.jar tools/quick-build.sh`.
- Release signing: CI reads secrets LEHKO_KEYSTORE_BASE64, LEHKO_KEYSTORE_PASSWORD, LEHKO_KEY_ALIAS,
  LEHKO_KEY_PASSWORD; without them release is debug-signed. Keep the release key stable (updates need it).

## Status / TODO (in order)

1. [ ] `SettingsActivity` (layouts settings.xml, settings_row.xml, enable_dialog.xml exist): ListView rows:
   home screen (enable dialog with dev-options / USB-debugging status from Settings.Global DEVELOPMENT_SETTINGS_ENABLED
   / ADB_ENABLED, buttons to open ACTION_DEVICE_INFO_SETTINGS / ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "full mode"
   checkbox; disable with confirm), preview home (only when off), weather city (EditText search dialog -> results),
   outages (region list incl. "off" -> for YASNO regions "by address" (street -> house -> group) or "pick group";
   mirror regions: pick group from list), screensaver (system / Lehko clock, via HomeMode.setScreensaver),
   unneeded apps -> CleanupActivity, about (version, repo URL, privacy, "switch back to Original before uninstalling").
   Map errors: Adb.Failure PORT_CLOSED / NOT_ALLOWED / TLS_ONLY / PROTOCOL, HomeMode.WifiOwnerException, Refused.
2. [ ] `CleanupActivity`: list launchable apps + a few known optional system pkgs (play.games, youtube.tvmusic,
   tvrecommendations, katniss with mic warning), excluding HomeMode.isProtected and Wi-Fi owners; toggle via
   HomeMode.setAppEnabled on App.ADB; needs QUERY_ALL_PACKAGES in the manifest for labels of disabled apps.
3. [ ] Strings: values (en), values-uk, values-ru. Every R.string referenced in code/layouts must exist in all three.
4. [ ] Build green with tools/quick-build.sh, then unit tests (app/src/test): Outages.parseYasno/parseMirror/status,
   Weather.parse, Adb.sign (compare with JDK Signature "NONEwithRSA" over SHA1 prefix+token), Adb.publicKeyBlob.
5. [ ] `.github/workflows/build.yml`: build + test on push/PR, upload APK artifact; on tag v* attach
   `Lehko-<version>.apk` to a GitHub Release (stable URL releases/latest/download/Lehko.apk).
6. [ ] `scripts/install.sh|.bat` (adb connect IP:5555, install APK, open settings) and `scripts/revert.sh|.bat`
   (read /data/local/tmp/lehko_state, pm enable listed pkgs, set-home-activity original, optional uninstall).
7. [ ] README.md (Ukrainian first, then English): what it is, screenshots later, install without PC (Downloader app
   + URL) and with PC, enabling debugging, going back, what it changes, privacy (Open-Meteo, YASNO, GitHub mirror;
   no analytics), outage regions supported, credits, MIT LICENSE.
8. [ ] Independent review of HomeMode/Adb by a fresh agent before tagging v1.0.0.
