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
- `Ambient`: the home background tint. 8x4 sample of the focused favorite's cached picture, alpha-weighted mean, HSV
  value 0.22-0.30, saturation <= 0.6, x0.7 after sunset (`Weather.night`); cached per package. HomeActivity fades a
  TL_BR window `GradientDrawable` (one drawable) with `ValueAnimator` + `ArgbEvaluator`, 450 ms. A global focus
  listener also drives the focused app's name under the favorites row.
- `Weather` also keeps the next hours (`forecast_hours=6`, `forecast_days=2` so evening hours aren't cut at midnight)
  and sunrise/sunset as moments (`utc_offset_seconds`). Header: icon/temp/text on one row, 5 hour columns, sun line.
- `Rates`: Monobank (every 30 min, 1 h pause after HTTP 429) with NBU fallback; Binance MINI tickers for BTC/ETH
  every 10 min (change = (last - open) / open). Own compact JSON cache; one line under the date, hidden when off or
  empty or too old. `Net.HttpError` carries the status code.
- `Memory`: free memory, own PSS; `clean()` = `HomeMode.killAll` (`am kill-all`, may show the debugging prompt) else
  `killBackgroundProcesses` per app; `maybeAutoClean()` on Home resume when memory is low (< 20% or < 500 MB, at most
  every 3 min, never prompts). `HomeMode.enable` stores the stock launcher's PSS (`K_LAUNCHER_PSS`, from
  `dumpsys meminfo`). `App.onTrimMemory`: UI_HIDDEN trims the picture cache to ~3 MB (the favorites' tiles hold those
  bitmaps anyway), RUNNING_LOW and up empties it.
- Design/accessibility pass (2026-10-22, with the tvastar skill's platform-neutral rules): text colours and
  `Ambient.MAX_LUMINANCE` keep all text >= 4.5:1 (AmbientTest checks it); focus = white 2dp outline everywhere
  (tiles, `row_bg`, focusable `card_bg`); OutageBar shows state by shape too (on thin, off solid, maybe outlined) and
  has a spoken description (`Outages.runs`, `OutageText.describe`); with a spoken-feedback service on, the header and
  outage card are focusable; live regions on notices/status cards; Settings has section headers; FireView stands
  still when animator duration scale is 0. Rates arrows and error text use `rate_up`/`rate_down`/`error_text`.
- Fireplace: `Fire` (pure Doom-fire automaton, 160x90, tested), `FireView` (firebox drawn once into a half-size
  RGB_565 bitmap, fire scaled into the arch with a filtering paint, logs, flickering glow; ~30 fps via
  `postOnAnimation` only while visible), `FireScene` (shared: drifting dim clock + outage line, slow pixel shift,
  optional `Crackle` brown noise + pops on an AudioTrack), `FireplaceActivity` (HomeActivity's idle timer, reset in
  `onUserInteraction`; any non-volume key goes back; no keep-screen-on), `FireDream`. Screensaver choice:
  `HomeMode.setScreensaver(SAVER_SYSTEM | SAVER_CLOCK | SAVER_FIRE)`.
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

## Status

v0.9 is feature-complete and reviewed: home screen, weather, outages, on-device home switching with safe mode,
unneeded-apps screen, uk/en/ru, CI with signed builds (incl. HomeModeTest against `tools/fake_adbd.py --tv`, a
simulated Google TV). v0.10 (2026-10-22) added the ambient background, hourly weather and sun times, the focused app
name, the rates line, memory cleaning and measurement, and the fireplace; 62 tests now. Nothing has been run on a
real TV yet. UI strings live in `tools/strings/strings.py`
(edit there, then `python3 tools/strings/strings.py app/src/main/res`).

**Do not** publish GitHub releases, change repository settings or rewrite git history without Eugene's OK. Pushing
tags from cloud sessions is blocked by the git proxy; releases are made on GitHub's "Draft a new release" page
(tag `v0.9.0-beta.1` style = pre-release) and CI then attaches the signed `Lehko.apk`.

## v0.10: look, memory, rates, fireplace (all done 2026-10-22; kept as the spec)

Deviations from the plan below: no USDT/UAH (the line wouldn't fit beside the weather); `forecast_days=2`; on
UI_HIDDEN the picture cache is trimmed rather than emptied (emptying would reload every banner on each return home).
The fireplace look was checked only in an AWT re-drawing of `FireView`, not on a device. Auto clean: Eugene raised the
threshold to 500 MB (2026-10-22), so on a 1 GB TV it runs on nearly every return home (at most every 3 min).
Review fixes: no clipPath per frame (arch mask in the grid), picture cache keeps favorites except at BACKGROUND+,
favorites row rebuilt on resume only when the list changed, fire paused without window focus (Android 8-10 dreams).

Eugene asked for these on 2026-10-22. Keep every hard constraint above (no libraries, no blur/shaders, tiny RAM).
After each item: `tools/quick-build.sh` and `tools/quick-test.sh` green, small commit, push to main, CI green.

1. **Ambient background.** (done) When a favorite tile gets focus, take its (already scaled) picture, average its colors
   (`Bitmap.createScaledBitmap(b, 8, 4, true)`, mean RGB), clamp to a dark, moderately saturated tone (HSV value
   about 0.22-0.30, saturation <= 0.6) and animate a full-screen background `GradientDrawable` (top-left = ambient,
   bottom-right = `bg_bottom`) with `ValueAnimator` + `ArgbEvaluator`, 450 ms. Cache the color per package. After
   sunset (from item 2) use 70% of that brightness. One drawable, no extra layers.
2. **Weather details.** (done) Add `&hourly=temperature_2m,weather_code,is_day&forecast_hours=6` and daily `sunrise,sunset`
   to the Open-Meteo URL. Under the current weather show the next 5 hours (time 11sp dim, 20dp icon, temp 13sp) and a
   small line with sunrise/sunset times. Header must stay <= ~130dp tall so everything still fits 960x540dp with the
   outage card visible. Extend `Weather.Data`, `Weather.parse` and WeatherTest.
3. **Focused app name** (done) in a 13sp `text_secondary` TextView under the favorites row (updates on focus; a short fade).
4. **Rates line** (done) under the date (left column), one 14sp line, e.g.
   `USD 44,83 / 45,23 · EUR 50,38 / 51,01 · BTC $67 120 ▲1,2% · ETH $2 410 ▼0,4%`.
   - Currency: Monobank `https://api.monobank.ua/bank/currency` (verified 2026-10-22: JSON array of ~107 objects like
     `{"currencyCodeA":840,"currencyCodeB":980,"date":1792616473,"rateBuy":44.83,"rateSell":45.2345}`; EUR is 978;
     some items only have `rateCross`). Monobank rate-limits: cache, refresh at most every 30 min, back off on 429.
     Fallback: NBU `https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json` (`[{"r030":840,"cc":"USD","rate":..}]`).
   - Crypto: Binance `https://api.binance.com/api/v3/ticker/24hr?type=MINI&symbols=%5B%22BTCUSDT%22,%22ETHUSDT%22%5D`
     returns an array of `{"symbol","openPrice","lastPrice",...}` strings (no priceChangePercent with type=MINI:
     change = (last - open) / open). Same call Free My TV uses. Refresh every 10 min. Optionally USDT in UAH via a
     separate `USDTUAH` request that is simply hidden if Binance rejects the symbol.
   - New `Rates` class (parse functions pure + RatesTest), fetched on `App.NET` with the same back-off as weather,
     cached JSON so the line shows offline. Settings rows: "Курс валют" (default on when TZ is Europe/Kiev) and
     "Криптовалюти" (default on). Hide the line when both are off or nothing is cached.
5. **Memory.** (done)
   - Shortcut tile "Памʼять" whose label shows free memory (`ActivityManager.MemoryInfo.availMem`, "вільно 412 МБ").
     Press: kill cached background apps through ADB `am kill-all` (via `HomeMode`, user-initiated so the permission
     window may appear); without debugging, fall back to `ActivityManager.killBackgroundProcesses` for running
     packages (add `KILL_BACKGROUND_PROCESSES`; works up to Android 13). Then a notice "Звільнено 180 МБ".
   - Auto clean (Settings toggle, default on): on `HomeActivity.onResume`, if availMem < 20% of total or < 200 MB and
     the last auto clean was > 3 min ago, run `am kill-all` in the background with `Adb.connect(c, 0, null)` (never
     prompts; silently skip on failure).
   - Measure: in `HomeMode.enable`, before turning the launcher off, run `dumpsys meminfo <launcher package>` and parse
     `TOTAL PSS:\s+(\d+)` (or `TOTAL\s+(\d+)`), store in KB. Settings row "Памʼять" summary:
     "Lehko 38 МБ · стандартний лаунчер займав 240 МБ · вільно 412 з 1024 МБ" (Lehko's own PSS from
     `ActivityManager.getProcessMemoryInfo(new int[]{Process.myPid()})`). Extend the fake TV for dumpsys meminfo.
   - `App.onTrimMemory`: at TRIM_MEMORY_RUNNING_LOW or higher (and BACKGROUND/UI_HIDDEN), evict the picture cache.
6. **Fireplace ("Камін") after 5 minutes idle on the home screen.** (done) Eugene's own idea; make it look cozy.
   - `FireView`: the classic "Doom fire" cellular automaton on a small grid (about 160x90 cells, 36-color palette
     black, deep red, orange, yellow, near-white), random wind drift, updated at ~30 fps with `postOnAnimation`
     (stop when not visible), written into an int[] then `Bitmap.setPixels`, drawn scaled to the full view with a
     filtering `Paint` so flames look soft. A simple firebox drawn once into a cached bitmap at view size: dark brick
     arch, three logs (rounded rects with lighter cut ends), a pulsing ember glow (radial gradient alpha). A dim
     clock plus the outage status line that drift slowly (no burn-in). Budget: < 10% of one A53 core.
   - `FireplaceActivity`: fullscreen, any key returns home. Do NOT keep the screen on: the TV's own sleep and
     screensaver timers must still work.
   - `HomeActivity`: reset an idle timer in `onUserInteraction`; when resumed and idle for the set time, start
     `FireplaceActivity`. Setting "Камін у простої": 5 хв (default) / 10 хв / 15 хв / вимкнено.
   - Optional crackle sound, Settings toggle default off: `AudioTrack` streaming quiet procedurally generated brown
     noise with random short pops.
   - Also a `FireDream` (DreamService with `FireView`); the screensaver choice becomes system / clock / fireplace.
7. (done) README (uk + en): describe the new features honestly; update CLAUDE.md "Status"; mark items here as done.
8. (done; fixes in "Fix review findings") When all is done: a fresh independent review agent over the new code (crashes, leaks, CPU/RAM on 1 GB, focus),
   fix what it finds, then write Eugene a short summary in Russian (casual, no long dashes): what changed and what
   to try on the TV. Don't publish a release; ask him.
