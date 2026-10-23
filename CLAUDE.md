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

- `App` executors (IO x2: package manager and pictures; NET single: every download; ADB single: every ADB session),
  REPO_URL, version. `Prefs` SharedPreferences helpers. No activity declares locale, fontScale, density or
  layoutDirection in configChanges: texts and sizes are set once, so those changes recreate the screens.
- `Net`: HTTP GET, `Net.HttpError` (status code), `Net.stale(now, savedAt, maxAge)` (a time after now counts as stale:
  the clock was ahead and was put right; cache ages and failure back-offs use it, so a corrected clock can't freeze
  refreshes) and `Net.online(c)` (active network; unknown = yes).
- `HomeActivity` (HOME intent), redesigned 2026-10-22 (Eugene picked a mix of mock-up directions A "quiet glass" and C
  "dial"; mocks were HTML in the session scratchpad): clock + date inside `DialView` (24-hour ring = today's schedule;
  plain ring without outages), a focusable `info` panel (power title/detail from `OutageText.headline`, weather line,
  compact rates) whose `details` (hours, city/max/min/sun, full rates, tomorrow's `OutageBar` without labels) show
  only while it has focus (Up from favorites), favorites row (`Tile`s 184x104, focus = white ring in `ring_pad` room +
  tint-coloured shadow on API 28+), focused name line (favorites and buttons), round `IconButton` shortcuts (HDMI
  inputs + Live TV via `Inputs`, All apps, TV settings, Memory, Lehko settings), one-time notice (goes by itself after
  20 s; a key press puts it away once it has been up 4 s; the Memory button's "Freed"/"Nothing extra" 6 s and 1.5 s,
  its failure reason the usual 20/4 s). Both rows are `ScrollRow`s running edge to edge: the fading edge length is
  only the scroll margin, no fade layer is drawn (on the TV the faded, unclipped row ends looked "melted"). Round
  buttons are opaque. Ticker every minute; weather 30 min; outages 10 min through `Outages.refreshIfStale`; nothing is
  tried while `Net.online` is false, 5 min pause after a failure. Notices: `Prefs.notice` anywhere; while resumed a
  prefs listener shows them at once (one that comes before the first layout is fitted in a pre-draw listener). The
  column is bottom-anchored (Space, notice, favorites, name line, buttons): the notice takes the free room above the
  favorites; when it doesn't fit, the name line goes GONE and the favorites drop by its height, so the round buttons
  keep their place. `noticeFits` counts the favorites row at its height with tiles (tileH + 2 ring_pad + padding), so
  a notice shown before the apps load (right after turning Lehko on) doesn't push the buttons down once they arrive.
  Package events are debounced (1 s) and the favorites row is rebuilt only when what it shows (component, leanback,
  label) or a favorite itself changed; TV input events reload the buttons the same way. "No update since" hints
  (`weather_old` + `OutageText.since`) show only after a failed refresh or with no network (forecast older than
  `Weather.OLD_MS` 3 h, schedule per `Outages.old` 2 h). The fireplace idle timer is skipped when the TV's System
  SCREEN_OFF_TIMEOUT (the screensaver start) is no longer than it; Secure sleep_timeout isn't read. Back closes the
  details; without CATEGORY_HOME in the intent (the "Try the Lehko home screen" preview from Settings) it finishes.
  Home closes a favorite's menu. The menu's Move left/right counts places on the row as shown (`Apps.moveFavorite`
  steps over saved favorites that aren't installed or are off). Safe mode screen: `safe_mode_text` with Try again
  (`Watchdog.revertInBackground`), TV settings, Lehko settings; after a failed switch back it shows `safe_mode_failed`
  plus the advice for `Watchdog.lastFailure` when that is PORT_CLOSED, NOT_ALLOWED or TLS_ONLY, else
  `reverted_crash_failed` (open Lehko settings and bring back the home screen).
- `AllAppsActivity` GridView of tiles; long-press/Menu: open, add/remove favorite, info, uninstall. The grid runs
  edge to edge like the home rows; its root must not clip to its padding (that cut the first column's ring).
  A picture load is skipped when the grid has since given its tile to another app; "in favorites" is spoken again
  after the picture lands (show() resets the description).
- `Apps` app list (LEANBACK_LAUNCHER first, then LAUNCHER), default favorites, banner/icon LruCache.
- `Inputs`: physical inputs sorted HDMI first, then by the TV's own name (the system list comes in hash order); the
  `IconButton` badge is the port number from the TV's own label, so a custom name doesn't change it. Live TV opens the
  TV's own tuner (system app, a `/HW` id) over channel apps' tuner inputs.
- `CleanupActivity` (Unneeded apps): never lists the recorded and the current home, setupwraith, Lehko, the current
  keyboard, accessibility services, screensaver (also the TV's own while Lehko's is set), Live TV/HDMI players
  (catch-all "open with" apps don't count), or `HomeMode.isProtected`. Its ADB calls pass a Progress, so the status
  says when the TV asks "Allow USB debugging?"; errors go through `HomeMode.explain` (never raw English exception
  text). singleTop, so a double OK doesn't open two.
- `Weather` Open-Meteo forecast + geocoding search; cached JSON so it shows offline (outages!). Default city
  Kyiv if TZ is Europe/Kiev.
- `Outages` + `OutageText` + `OutageBar`: schedules in 48 half-hour slots, Kyiv wall clock (TZ id "Europe/Kiev", NOT
  "Europe/Kyiv": old TV tzdata). See research below. `Outages.refreshIfStale(c, done)` (main thread) is how the home
  screen and `FireScene` (fireplace and FireDream: home is paused under them) download: a static loading flag and
  failure back-off shared by both, 10 min max age, 5 min after a failure, and no try while `Net.online` is false (not
  recorded as a failure, so the home ticker's first try after the network is back isn't held off). Settings'
  `chooseOutage` fetches once directly (`Outages.refresh` on App.NET) right after a group is chosen, outside that flag
  and back-off. `done` re-reads `Outages.cached`, which is null for another group's cache (the weekly address re-check
  may move the group). `Outages.old` (2 h, only after a failed try or offline; a fetch time ahead of the clock counts
  as old) drives the "no update since" hint. `Outages.status` computes `Status.emergency`/`noSchedules` for the
  current Kyiv day only (`Day.emergency`, `Schedule.asOf`; there is no Schedule.emergency any more), `clearAhead`
  needs tomorrow published, `clearToday` drives "no more outages today" while tomorrow isn't; a change to MAYBE reads
  "then a possible outage", never "back on"; countdowns are real minutes across DST (`minutesUntil`: when the clocks
  go back, a change at 03:00 or 03:30 counts from the first pass of the repeated hour unless that has passed; Calendar
  alone picks the second). An emergency day's bar is described as emergency, not "no outages". The group list falls
  back to the DTEK mirror's `fact.data` keys for Kyiv when YASNO fails or is empty.
- `Adb`: own ADB client to 127.0.0.1:5555 (RSA key in noBackupFilesDir; signing with BigInteger PKCS#1 v1.5 over
  SHA-1 DigestInfo prefix + token; adbd pubkey blob format). First use pops "Allow USB debugging?" on the TV.
- `HomeMode`: entry points enable(full?) (also full mode later, from the lighter one) / disable ("Bring back") /
  launcherOn (only `launcherPackages` come back on: the recorded launcher, setupwraith, tvrecommendations with the ATV
  launcher; Lehko stays home; full mode ends once the launcher itself is on, even if setupwraith stays off, so verify
  won't turn the launcher off again, and what stayed off is still recorded for "Bring back") / enableApps (Lehko off:
  turns recorded apps back on) / allowAgain ("Allow debugging again": may prompt, then check()) / verify (after boot
  or a Lehko update, never prompts) / revertAfterCrash / setScreensaver / setAppEnabled / systemState / killAll. Each
  has a Context wrapper over the `HomeMode.Env` version (prefs, text, connect, notice, sdk, debuggingOn) that
  HomeModeTest drives against `tools/fake_adbd.py --tv`. Records original home, checks Wi-Fi network owners (disabling
  an owner deletes the Wi-Fi network: refuse), `cmd package set-home-activity --user 0 ua.lehko.tv/.HomeActivity`,
  full mode disables original launcher pkg (+ tvrecommendations on classic ATV, +
  `com.google.android.tungsten.setupwraith` when it has a HOME activity: its RecoveryActivity takes over otherwise).
  Writes /data/local/tmp/lehko_state for the PC revert scripts. Every user-started connection goes through
  `connect(Env, Progress)` = adopt() + acceptOutsideRevert(); verify and allowAgain reach the same pair through
  check(). The exception is revertAfterCrash (the safe screen's Try again too): its own 120 s connect and adopt()
  only, since it restores anyway. adopt() reads the note once after a reinstall and takes on/full only when the TV
  agrees (Lehko holds HOME, or in full mode the recorded launcher or setupwraith is still off); when the note says on
  and the TV doesn't agree (e.g. uninstalled in the lighter mode), it puts the debugging timeout back and rewrites the
  note. Outside revert: Lehko's records say on, the note isn't `on=true` (scripts/revert removes it; a TV update
  leaves it) and HOME isn't Lehko: forgetSwitch() drops Lehko's records and changes no setting or package on the TV
  (it only rewrites the note, on=false), so a revert from a computer sticks. Screensaver: recorded again whenever the
  TV's current one isn't Lehko's; restored only while Lehko's dream is still set (a later pick in the TV's settings
  stays), screensaver_enabled only while it is 1, and a recorded saver package turned off on Unneeded apps is enabled
  first. SAVER_SYSTEM with no record but Lehko's dream set (picked in the TV's settings) deletes
  screensaver_components (Android's default) and leaves screensaver_enabled alone. keepDebuggingAllowed (API 30+,
  adb_allowed_connection_time 0) runs in enable() and in check() after the outside-revert test, never before it; a
  refused turn-on and "Bring back" put the old value back. The stock launcher's PSS is measured on each turn-on from
  off (`!isOn`), not when full mode is added later (the launcher has been in the background behind Lehko by then).
  verify(): PORT_CLOSED with debugging on means the TV needs `adb tcpip 5555` again after a restart
  (verify_port_closed). A successful restore drops a stale `reverted_crash_failed` notice. `HomeMode.explain(c, e)` is
  the shared connection-error mapping (Settings, Unneeded apps, the Memory button): PORT_CLOSED -> `adb_closed` with
  ADB_ENABLED=1, else `cleanup_need_debugging`; NOT_ALLOWED -> `adb_rejected`; TLS_ONLY -> `adb_tls`; other
  `Adb.Failure`s and socket timeouts/EOF/resets -> `adb_no_answer`; null for anything else. `launcherOff(c)` = full
  mode, or a `launcherPackages` entry is still recorded off.
- `SettingsActivity`: network work (city, regions, groups, addresses) on `App.NET`; switches on `App.ADB` through
  run() (one at a time). A result that arrives while Settings is out of sight goes to `Prefs.notice` too (Lehko's
  home shows it) when Lehko is on. Home row: off -> the turn-on dialog (focus starts on "Turn on", not the "Also
  turn off the stock launcher" box); on -> a list: "Turn off the stock launcher" (confirm dialog with that title and
  a "Turn off" button, then enable(full=true)) or, while `HomeMode.launcherOff`, "Turn the stock launcher back on"
  (no confirm); "Allow debugging again"; "Bring back the TV's home screen". In the watchdog's safe mode the row goes
  straight to the "Bring back" dialog, which has no "Allow debugging again" button (it would report "Done" while the
  safe screen stays; disable() asks for the permission itself). "Turn apps back on" (asks first) shows while Lehko is
  off and its record of turned-off apps (`HomeMode.disabled`) isn't empty; an app turned on elsewhere stays in the
  record until this runs. `saver()` reads screensaver_components and tells the fireplace by the component's class
  name (the TV's settings write `ua.lehko.tv/ua.lehko.tv.FireDream`, Lehko the short form). The fireplace row says
  when the TV's screensaver timer comes first, unless the fireplace is the screensaver. Errors: a closed port always
  gives `adb_closed` here (the shared text's "first make Lehko the home screen" is what is being done), the rest
  goes through `HomeMode.explain`, then Wi-Fi owner/check and Refused texts; other exceptions show only their class
  name inside a translated sentence.
- `Watchdog`: 3 crashes in 5 min while Lehko is home -> safe mode, a revert right away and `ModeJob` REVERT after
  60 s. Java crashes come from the uncaught-exception handler; on API 30+ `ApplicationExitInfo` adds
  REASON_CRASH_NATIVE and REASON_ANR at foreground importance newer than the `crashes_since` pref (set on clear() and
  on entering safe mode: Android's record outlives a fresh start). `lastFailure` keeps the last failed switch back's
  `Adb.Failure` reason for the safe screen. `BootReceiver` (boot, Lehko update) -> `ModeJob` VERIFY.
- `ClockDream`: optional dim moving clock screensaver. It downloads nothing: weather only when the cache is younger
  than `Weather.OLD_MS`, hidden once it gets older; it fades in at its first spot.
- `Ambient`: the home background tint. 8x4 sample of the focused tile's own picture (`Tile.picture()`, not the
  picture cache, which may have been trimmed), alpha-weighted mean, HSV value 0.22-0.30, saturation <= 0.6, x0.7
  after sunset (`Weather.night`); cached per package. HomeActivity fades a TL_BR window `GradientDrawable` (one
  drawable) with `ValueAnimator` + `ArgbEvaluator`, 450 ms. A global focus listener also drives the focused app's name
  under the favorites row.
- `Weather` also keeps the next hours (`forecast_hours=7`: the current hour and six more, so five are still to come
  for the whole cache life; `forecast_days=2` so evening hours aren't cut at midnight) and sunrise/sunset as moments
  (`utc_offset_seconds`). Header: icon/temp/text on one row, 5 hour columns, sun line. The rain/snow hint is a moment
  (`precipStart`), formatted in the TV's 12/24-hour style and dropped once passed. Always downloaded in Celsius;
  shown as °F (with the unit) only when `Weather.fahrenheit(Locale, tz)` says so: the Android 14 "mu" regional
  preference, else a US locale with an America/, US/ or Pacific/ time zone (en-US TVs in Kyiv keep Celsius). A
  download for the previous city doesn't land in the new one's cache.
- `Rates`: USD/EUR in hryvnias from Monobank (every 30 min, 1 h pause after HTTP 429) with NBU fallback. Own compact
  JSON cache; compact line ("$ 41,80 · € 48,62") always, full buy/sell while the details are open; hidden when off,
  empty or too old. `Net.HttpError` carries the status code. Crypto (Binance BTC/ETH) was built and then removed at
  Eugene's request (2026-10-22): don't bring it back.
- `Memory`: free memory, own PSS; `clean()` = `HomeMode.killAll` (`am kill-all`, may show the debugging prompt) else
  `killBackgroundProcesses` per app; `maybeAutoClean()` on Home resume when memory is low (< 20% or < 500 MB, at most
  every 3 min, never prompts). `clean()` returns a `Memory.Result` (text, failed): on Android 14 (no
  killBackgroundProcesses fallback) a failed killAll gives `HomeMode.explain`'s reason, or `error_generic` with the
  class name, as failed=true, and home shows it as an ordinary notice. `HomeMode.enable` from off stores the stock
  launcher's PSS (`K_LAUNCHER_PSS`, from `dumpsys meminfo`). `App.onTrimMemory`: from RUNNING_LOW up (UI_HIDDEN,
  BACKGROUND and COMPLETE too) `Apps.trimPictures` keeps only the favorites' pictures and never empties the cache: the
  home tiles hold those bitmaps anyway, so evicting them freed nothing and the next favorites rebuild showed names
  only. Android 14 sends only UI_HIDDEN and BACKGROUND.
- Design/accessibility pass (2026-10-22, with the tvastar skill's platform-neutral rules): text colours and
  `Ambient.MAX_LUMINANCE` keep all text >= 4.5:1 (AmbientTest checks `text_secondary` #A7AFBA and `text_dim` #959DAA,
  the dial's hour numbers, on the brightest tint any colour or a white banner gives); focus = white 2dp outline
  everywhere (tiles, `row_bg`, focusable `card_bg`); the Lehko leaf turns dark on its focused (white) round button;
  OutageBar shows state by shape too (on thin, off solid, maybe outlined) and has a spoken description
  (`Outages.runs`, `OutageText.describe`); the info panel is always focusable (Up from favorites) and the power detail
  carries today's ring in words; live regions on notices/status cards; Settings has section headers; FireView stands
  still when animator duration scale is 0. Error text uses `error_text`.
- Fireplace: `Fire` (pure Doom-fire automaton, 160x90, tested), `FireView` (a close-up: the arched opening fills the
  screen, bricks to the edges; surround, firebox walls, floor and hearth drawn once into a half-size RGB_565 bitmap,
  fire scaled into the firebox with a filtering paint, logs, flickering glow; ~30 fps via `postOnAnimation` only while
  visible; `Fire.warm()` starts from a steady fire's shape, which is also the still picture with animations off),
  `FireScene` (shared: drifting dim clock + outage line with the "no update since" hint, downloads the schedule itself
  through `Outages.refreshIfStale`; the 40 s drift moves in whole pixels on the fire's own frames via
  `FireView.onFrame`, no ViewPropertyAnimator at 60 fps; slow pixel shift; optional `Crackle` brown noise + pops on an
  AudioTrack, not started while music is active; it holds AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK and stops until the next
  FireScene.start on any focus loss, e.g. music, a video or casting started under the fire), `FireplaceActivity` (HomeActivity's idle timer,
  reset in `onUserInteraction`; volume and media keys pass through to the playing app, Search opens the assistant and
  ends it, any other key goes back; no keep-screen-on), `FireDream`. Screensaver choice:
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
  then `ANDROID_JAR=/opt/sdkjars/android-33.jar tools/quick-build.sh`. Tests: `tools/quick-test.sh`. HomeModeTest and
  AdbProtocolTest start fake adbd servers on ports from `LEHKO_TEST_PORT` (default 25555): give checkouts that test
  at the same time different ports.
- Release signing: CI reads secrets LEHKO_KEYSTORE_BASE64, LEHKO_KEYSTORE_PASSWORD, LEHKO_KEY_ALIAS,
  LEHKO_KEY_PASSWORD; without them release is debug-signed. Keep the release key stable (updates need it).

## Status

v0.9 is feature-complete and reviewed: home screen, weather, outages, on-device home switching with safe mode,
unneeded-apps screen, uk/en/ru, CI with signed builds (incl. HomeModeTest against `tools/fake_adbd.py --tv`, a
simulated Google TV). v0.10 (2026-10-22) added the ambient background, hourly weather and sun times, the focused app
name, the rates line, memory cleaning and measurement, the fireplace, a design/accessibility pass and the dial
redesign of the home screen. First run on Eugene's TV (2026-10-22) found three things, fixed: the memory notice didn't
go away, the faded ends of the scrolling rows looked bad, the fireplace filled only part of the screen.
v0.10.0-beta.2 is published. A full audit (2026-10-23: home switching and the revert scripts, outage and weather
data, the home screen, apps, Unneeded apps, build and CI) found ~70 defects; all are fixed. A follow-up pass the same
day fixed the "TV's own" screensaver choice, the safe-mode "Bring back" dialog, the launcher row's wording and its
"back on" with setupwraith left off, the debugging timeout after a reinstall, the Memory button's Android 14 errors
(shared `HomeMode.explain`), the notice layout before the apps load, the crackle over music, the DST countdown and
offline schedule tries; 101 tests now. Waiting for Eugene's answer: auto clean's threshold and the feature ideas.

UI strings live in `tools/strings/strings.py` (edit there, then `python3 tools/strings/strings.py app/src/main/res`).
It writes line breaks as `\n` and quotes texts with double spaces, which aapt2 would otherwise squeeze.

**Do not** publish GitHub releases, change repository settings or rewrite git history without Eugene's OK. Pushing
tags from cloud sessions is blocked by the git proxy; releases are made on GitHub's "Draft a new release" page
(tag `v0.10.0-beta.3` style) and CI then attaches the signed `Lehko.apk`. Do NOT tick "Set as a pre-release", not
even for a beta: README, scripts/install.sh and scripts/install.bat download `/releases/latest/download/Lehko.apk`,
and /releases/latest skips pre-releases (CI only warns; releases CI creates itself are normal ones). Tags need two
or three numbers: v1.2.3, v1.2.3-beta.1, or v1.2 (= v1.2.0); versionCode = major*10000 + minor*100 + patch. Any
other v* tag fails CI at configuration (app/build.gradle). If CI goes red on a tag made on the release page, delete
that release and its tag and make a correct one: otherwise "latest" has no Lehko.apk and the README link returns 404.

## v0.10: look, memory, rates, fireplace (all done 2026-10-22; kept as the spec)

Deviations from the plan below: no USDT/UAH (the line wouldn't fit beside the weather); `forecast_days=2` (and
`forecast_hours=7` since the 2026-10-23 audit); on UI_HIDDEN the picture cache is trimmed rather than emptied
(emptying would reload every banner on each return home).
The fireplace look was checked only in an AWT re-drawing of `FireView`, not on a device. Auto clean: Eugene raised the
threshold to 500 MB (2026-10-22), so on a 1 GB TV it runs on nearly every return home (at most every 3 min).
Review fixes: no clipPath per frame (arch mask in the grid), picture cache keeps favorites except at BACKGROUND+
(since the 2026-10-23 audit at every level, see `Memory` above), favorites row rebuilt on resume only when the list
changed, fire paused without window focus (Android 8-10 dreams).

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
4. **Rates line** (done; the crypto part was later removed at Eugene's request) under the date, one 14sp line, e.g.
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
