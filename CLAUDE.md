# Lehko: notes for Claude sessions working on this repo

Lehko ("легко", light/easy) is a light, ad-free home screen (launcher) for weak Android TV / Google TV sets,
first target: Xiaomi TV A 43 FHD 2026 (Google TV, 1 GB RAM, 4x Cortex-A53, Mali-G31 MP2). The repo is public and
meant for anyone to install. Owner: Eugene (GitHub manoff4sv-lgtm), Kyiv. He talks in Russian; UI is uk/ru/en.

## Hard constraints

- Native Java, framework widgets only. **No AndroidX, no Kotlin, no Compose, no libraries** in the app (tests may
  use junit + org.json). Keep RAM tiny: no blur, shaders, big bitmaps, long animations. One process, except
  `TvHelper`'s small `:helper` (see below).
- Java 8 language level (lambdas are fine in Gradle builds, but the local quick build uses dx: keep anonymous
  classes, they work everywhere). minSdk 26, targetSdk/compileSdk 34. Package and applicationId `ua.lehko.tv`.
- **R ids are not constants** (AGP 8 non-final R): never `switch` on `R.string.x`, use if/else.
- Never send anything to the TV's shell that came from the network. Only fixed commands in HomeMode/CleanupActivity.
  A value read from the TV goes back only after a strict pattern check (package and component names, numbers; the
  accessibility list as a whole against `COMPONENTS`, single-quoted because of '$').
- Never uninstall anything on the TV; only `pm disable-user --user 0` (reversible) and `pm enable`.
- Texts: no long dashes in user-facing strings; middle dot " · " or commas instead.

## Architecture (app/src/main/java/ua/lehko/tv)

- `App` executors (IO x2: package manager and pictures; NET single: every download; ADB single: every ADB session),
  REPO_URL, version. In the `:helper` process (`processName()`: `Application.getProcessName()` on API 28+, null read as
  "", else `/proc/self/cmdline`) `App.onCreate` returns at once: no Watchdog (a helper crash is no home screen crash
  loop and must never start a switch back), no version read, and `onTrimMemory` does nothing there. In the main process
  it counts `App.screensShown` (ActivityLifecycleCallbacks: Lehko screens started and not yet stopped; a counter, so
  HomeActivity's resume-while-pausing order is safe); `Update.confirm` asks it, so the installer's confirmation comes
  up only over a Lehko screen. `Prefs` SharedPreferences helpers. No activity declares locale, fontScale, density or
  layoutDirection in configChanges: texts and sizes are set once, so those changes recreate the screens.
- `Net`: HTTP GET, `Net.HttpError` (status code), `Net.stale(now, savedAt, maxAge)` (a time after now counts as stale:
  the clock was ahead and was put right; cache ages and failure back-offs use it, so a corrected clock can't freeze
  refreshes) and `Net.online(c)` (active network; unknown = yes).
- `HomeActivity` (HOME intent), redesigned 2026-10-22 (Eugene picked a mix of mock-up directions A "quiet glass" and C
  "dial"; mocks were HTML in the session scratchpad): clock + date inside `DialView` (24-hour ring = today's schedule;
  plain ring without outages), a focusable `info` panel (power title/detail from `OutageText.headline`, weather line,
  compact rates) whose `details` (hours, city/max/min/sun, full rates, tomorrow's `OutageBar` without labels) show only
  while it has focus (Up from favorites), favorites row (`Tile`s 184x104, focus = white ring in `ring_pad` room +
  tint-coloured shadow on API 28+; the "Add apps" tile, key "add", only while the row has no favorites: with favorites
  the round All apps button is the only way in, and back from All apps with apps added focus lands on the first new
  favorite, by index), focused name line (favorites and buttons), round `IconButton` shortcuts (HDMI inputs + Live TV
  via `Inputs`, All apps, TV settings, Memory, the sleep timer while `TvHelper.running`, Lehko settings), one-time
  notice (goes by itself after 20 s; a key press puts it away once it has been up 4 s; the Memory button's
  "Freed"/"Nothing extra" 6 s and 1.5 s, its failure reason the usual 20/4 s). Both rows are `ScrollRow`s running edge
  to edge: the fading edge length is only the scroll margin, no fade layer is drawn (on the TV the faded, unclipped row
  ends looked "melted"). Round buttons are opaque. Ticker every minute; weather 30 min; outages 10 min through
  `Outages.refreshIfStale`; nothing is tried while `Net.online` is false, 5 min pause after a failure. Notices:
  `Prefs.notice` anywhere; while resumed a prefs listener shows them at once (one that comes before the first layout is
  fitted in a pre-draw listener). The column is bottom-anchored (Space, notice, favorites, name line, buttons): the
  notice takes the free room above the favorites; when it doesn't fit, the name line goes GONE and the favorites drop by
  its height, so the round buttons keep their place. `noticeFits` counts the favorites row at its height with tiles
  (tileH + 2 ring_pad + padding), so a notice shown before the apps load (right after turning Lehko on) doesn't push the
  buttons down once they arrive. Package events are debounced (1 s) and the favorites row is rebuilt only when what it
  shows (component, leanback, label) or a favorite itself changed; TV input events reload the buttons the same way. "No
  update since" hints (`weather_old` + `OutageText.since`) show only after a failed refresh or with no network (forecast
  older than `Weather.OLD_MS` 3 h, schedule per `Outages.old` 2 h). The fireplace idle timer is skipped when the TV's
  System SCREEN_OFF_TIMEOUT (the screensaver start) is no longer than it; Secure sleep_timeout isn't read. The idle
  runnable first runs `Memory.maybeAutoClean` (nobody is about to open an app), then starts the fireplace; with no
  fireplace to come it is still posted, after `Memory.autoIdleMs(SCREEN_OFF_TIMEOUT)`. Back closes the details; without
  CATEGORY_HOME in the intent (the "Try the Lehko home screen" preview from Settings) it finishes. Home closes a
  favorite's menu. The menu's Move left/right counts places on the row as shown (`Apps.moveFavorite` steps over saved
  favorites that aren't installed or are off). Safe mode screen: `safe_mode_text` with Try again
  (`Watchdog.revertInBackground`), TV settings, Lehko settings; after a failed switch back it shows `safe_mode_failed`
  plus the advice for `Watchdog.lastFailure` when that is PORT_CLOSED, NOT_ALLOWED or TLS_ONLY, else
  `reverted_crash_failed` (open Lehko settings and bring back the home screen). The info panel is clickable: OK starts
  Settings with `SettingsActivity.EXTRA_OUTAGES` (a second OK within 1 s is ignored). Outage invitation: while
  `SettingsActivity.outageInvite(c)` holds, `showOutage` shows `outage_invite_title` and `outage_invite_detail`
  (`outage_invite_ok` while the panel has focus) over a plain ring. `maybeRefresh` starts with `updateSleepButton`
  (every minute and on each return home; it rebuilds the button row when `TvHelper.running` changed) and ends with
  `Update.maybeCheck`; `buildQuickRow` calls `OutageJob.sync`. The sleep timer's dialog is kept in `menu` (Home closes
  it); a click within 700 ms of show() is ignored and the list's mark restored, and while a timer runs focus starts on
  15 min, not Off. onResume schedules `ModeJob` VERIFY (delay 0) when `HomeMode.helperDropped`, once per process (static
  `helperAsked`).
- `AllAppsActivity` GridView of tiles; long-press/Menu: open, add/remove favorite, info, uninstall. The grid runs edge
  to edge like the home rows; its root must not clip to its padding (that cut the first column's ring). A picture load
  is skipped when the grid has since given its tile to another app; "in favorites" is spoken again after the picture
  lands (show() resets the description). The remote's all-apps button (TvHelper, while launcherx is off) opens it with
  NEW_TASK, in a task of its own.
- `Apps` app list (LEANBACK_LAUNCHER first, then LAUNCHER), default favorites, banner/icon LruCache.
  `Apps.launchIntent(pm, pkg)`: the leanback launch intent, else the phone one (also the helper's and the star row's
  test of "can be opened").
- `Inputs`: physical inputs sorted HDMI first, then by the TV's own name (the system list comes in hash order); the
  `IconButton` badge is the port number from the TV's own label, so a custom name doesn't change it. Live TV opens the
  TV's own tuner (system app, a `/HW` id) over channel apps' tuner inputs. `Inputs.byId(c, id)`: one `getTvInputInfo`
  call, no labels, null when the TV no longer has it (for the helper's wake input).
- `CleanupActivity` (Unneeded apps): never lists the recorded and the current home, setupwraith (and tvrecommendations
  with the ATV launcher), Lehko, or `HomeMode.isProtected`. The current keyboard, accessibility services, screensaver
  (also the TV's own while Lehko's is set) and Live TV/HDMI players (catch-all "open with" apps don't count) are left
  out only while on: an off one stays listed so it can be turned back on. Its ADB calls pass a Progress, so the status
  says when the TV asks "Allow USB debugging?"; errors go through `HomeMode.explain` (never raw English exception
  text). singleTop, so a double OK doesn't open two.
- `Weather` Open-Meteo forecast + geocoding search; cached JSON so it shows offline (outages!). `place()` = the chosen
  city, else `Weather.defaultCity(Outages.chosenRegion id, TZ)`: Kyiv city and Kyiv region -> Kyiv (50.4501, 30.5234),
  Dnipro DnEM/CEK -> Dnipro (48.4647, 35.0462), Odesa -> Odesa (46.4825, 30.7233); no region -> Kyiv when
  `Weather.kyivZone(tz)` (Europe/Kiev or Europe/Kyiv), else none (the card asks for a city); `cityName` follows
  (city_kyiv/city_dnipro/city_odesa). The cache keeps the place it was downloaded for in pref `weather_place`
  (`Weather.CACHE_PLACE`, "lat,lon"): when that differs from `place()`, `cached()` is null and `stale()` true, so a
  region change never shows the old city's forecast. A cache from before that pref counts as the chosen city's, or
  Kyiv's when none is chosen (`Weather.cacheFits(cachedFor, cityChosen, place)`).
- `Outages` + `OutageText` + `OutageBar`: schedules in 48 half-hour slots, Kyiv wall clock (TZ id "Europe/Kiev", NOT
  "Europe/Kyiv": old TV tzdata). See research below. `Outages.refreshIfStale(c, done)` (main thread) is how the home
  screen and `FireScene` (fireplace and FireDream: home is paused under them) download: a static loading flag and
  failure back-off shared by both, 10 min max age, 5 min after a failure, and no try while `Net.online` is false (not
  recorded as a failure, so the home ticker's first try after the network is back isn't held off). Settings'
  `chooseOutage` fetches once directly (`Outages.refresh` on App.NET) right after a group is chosen, outside that flag
  and back-off. `done` re-reads `Outages.cached`, which is null for another group's cache (the weekly address re-check
  may move the group). `Outages.old` (2 h, only after a failed try or offline; a fetch time ahead of the clock counts as
  old) drives the "no update since" hint. `Outages.status` computes `Status.emergency`/`noSchedules` for the current
  Kyiv day only (`Day.emergency`, `Schedule.asOf`; there is no Schedule.emergency any more), `clearAhead` needs tomorrow
  published, `clearToday` drives "no more outages today" while tomorrow isn't; a change to MAYBE reads "then a possible
  outage", never "back on"; countdowns are real minutes across DST (`minutesUntil`: when the clocks go back, a change at
  03:00 or 03:30 counts from the first pass of the repeated hour unless that has passed; Calendar alone picks the
  second). An emergency day's bar is described as emergency, not "no outages". The group list falls back to the DTEK
  mirror's `fact.data` keys for Kyiv when YASNO fails or is empty. `refresh` reads the same group's previous cache
  (`before`) and, after writing the new one, calls `OutageWarning.update(c, before, s)` under the Outages lock (as
  choose(): an old group's outages never land after its forget()). `choose()`, `turnOff()` and the weekly address
  re-check when it moves the group call `OutageWarning.forget`; `turnOff` also `OutageJob.sync`. `refreshIfStale`
  returns whether it started a download (only then does `done` run). `Outages.at(date, minutes)` is the epoch moment of
  a Kyiv wall-clock time: the first pass when the clocks go back, and a time the clocks skip in spring is the moment
  they jump (`moment()`, which `minutesUntil` uses too: Calendar alone put 03:30 after 04:00).
- `Adb`: own ADB client to 127.0.0.1:5555 (RSA key in noBackupFilesDir; signing with BigInteger PKCS#1 v1.5 over
  SHA-1 DigestInfo prefix + token; adbd pubkey blob format). First use pops "Allow USB debugging?" on the TV.
- `HomeMode`: entry points enable(full?) (also full mode later, from the lighter one) / disable ("Bring back") /
  launcherOn (only `launcherPackages` come back on: the recorded launcher, setupwraith, tvrecommendations with the ATV
  launcher; Lehko stays home; full mode ends once the launcher itself is on, even if setupwraith stays off, so verify
  won't turn the launcher off again, and what stayed off is still recorded for "Bring back") / enableApps (Lehko off:
  turns recorded apps back on) / allowAgain ("Allow debugging again": may prompt, then check()) / verify (after boot or
  a Lehko update, never prompts) / revertAfterCrash / setScreensaver / setAppEnabled / systemState / killAll / sleep /
  canSleep / allowInstalls. Each except sleep and canSleep (Context-only, a silent Adb.connect of their own) has a
  Context wrapper over the `HomeMode.Env` version (prefs, text, connect, notice,
  sdk, debuggingOn, helperTurnedOff) that HomeModeTest drives against `tools/fake_adbd.py --tv`. Records original home,
  checks Wi-Fi network owners (disabling an owner deletes the Wi-Fi network: refuse), `cmd package set-home-activity
  --user 0 ua.lehko.tv/.HomeActivity`, full mode disables original launcher pkg (+ tvrecommendations on classic ATV, +
  `com.google.android.tungsten.setupwraith` when it has a HOME activity: its RecoveryActivity takes over otherwise).
  Writes /data/local/tmp/lehko_state for the PC revert scripts. Every user-started connection goes through `connect(Env,
  Progress)` = adopt() + acceptOutsideRevert(); verify and allowAgain reach the same pair through check(). The exception
  is revertAfterCrash (the safe screen's Try again too): its own 120 s connect and adopt() only, since it restores
  anyway; it calls goHome only when the home screen really came back (r.homeOk), so a failed try from the background
  never pulls the safe screen over the app in front. adopt() reads the note once after a reinstall and takes on/full
  only when the TV agrees (Lehko holds HOME, or in full mode the recorded launcher or setupwraith is still off); when
  the note says on and the TV doesn't agree (e.g. uninstalled in the lighter mode), it puts the debugging timeout back,
  takes the helper out (helperOff) and rewrites the note. Outside revert: Lehko's records say on, the note isn't
  `on=true` (scripts/revert removes it; a TV update leaves it) and HOME isn't Lehko: forgetSwitch() drops Lehko's
  records (the turned-off list keeps only apps still off; the a11y records go too) and changes no setting or package on
  the TV except taking out Lehko's own helper (helperOff: a revert by hand or an older script may have left it on); it
  rewrites the note (on=false), so a revert from a computer sticks. Screensaver: recorded again whenever the TV's
  current one isn't Lehko's; restored only while Lehko's dream is still set (a later pick in the TV's settings stays),
  screensaver_enabled only while it is 1, and a recorded saver package turned off on Unneeded apps is enabled first.
  SAVER_SYSTEM with no record but Lehko's dream set (picked in the TV's settings) deletes screensaver_components
  (Android's default) and leaves screensaver_enabled alone. keepDebuggingAllowed (API 30+, adb_allowed_connection_time
  0) runs in enable() and in check() after the outside-revert test, never before it; a refused turn-on and "Bring back"
  put the old value back. The stock launcher's PSS is measured on each turn-on from off (`!isOn`), not when full mode is
  added later (the launcher has been in the background behind Lehko by then). verify(): PORT_CLOSED with debugging on
  means the TV needs `adb tcpip 5555` again after a restart (verify_port_closed). A successful restore drops a stale
  `reverted_crash_failed` notice. `HomeMode.explain(c, e)` is the shared connection-error mapping (Settings, Unneeded
  apps, the Memory button): PORT_CLOSED -> `adb_closed` with ADB_ENABLED=1, else `cleanup_need_debugging`; NOT_ALLOWED
  -> `adb_rejected`; TLS_ONLY -> `adb_tls`; other `Adb.Failure`s and socket timeouts/EOF/resets -> `adb_no_answer`; null
  for anything else. `launcherOff(c)` = full mode, or a `launcherPackages` entry is still recorded off. Helper: `HELPER`
  = `ua.lehko.tv/.TvHelper` (the TV's settings write the long form; any `ua.lehko.tv/` entry counts). `helperOn`
  (enable() in both modes, on every TV, also after the user turned it off; check(), i.e. verify and allowAgain, without
  a notice and not counted as fixed, unless `Env.helperTurnedOff`) reads `settings get secure
  enabled_accessibility_services`, records the original `accessibility_enabled` once (prefs `K_A11Y_CHANGED`
  "mode_a11y_changed" and `K_A11Y_ENABLED` "mode_original_a11y_enabled"; note lines `a11y_changed=` and `a11y_enabled=`,
  which adopt() takes over and the revert scripts read), appends the helper to the services already on and puts
  `accessibility_enabled 1`. Only a list matching the strict `COMPONENTS` pattern is written back, single-quoted (a
  class name may hold '$'); a completely empty `settings get` answer (no shell) is unreadable: nothing changed, nothing
  recorded, and helperOff keeps its record. `helperOff` (restore(): Bring back and revertAfterCrash; adopt() when the
  note says on but Lehko isn't home; forgetSwitch) removes only Lehko's entries; with none left the setting is deleted
  and `accessibility_enabled` put back (deleted when it was unset). The original services list is not recorded, on
  purpose: an empty list after the removal means every other service was turned off meanwhile (by the user), and
  restoring it would undo that (TalkBack talking at Bring back). launcherOn keeps the helper. `helperListed(c)` reads
  ENABLED_ACCESSIBILITY_SERVICES without ADB (true when unreadable). `helperDropped(c)`: Lehko on, K_A11Y_CHANGED set,
  not safe mode, not listed and not `TvHelper.turnedOff` (Android drops a force-stopped app's accessibility services;
  HomeActivity then asks for VERIFY). `sleep(c)`: fixed `input keyevent KEYCODE_SLEEP` over `Adb.connect(c, 0, null)`
  (never prompts; the helper calls it on App.ADB); `canSleep(c)`: the same silent connect only. `allowInstalls(Env,
  Progress)`: fixed `appops set ua.lehko.tv REQUEST_INSTALL_PACKAGES allow` through connect(Env, Progress)
  (user-started, may prompt); Bring back doesn't undo it.
- `SettingsActivity`: network work (city, regions, groups, addresses) on `App.NET`; switches on `App.ADB` through run()
  (one at a time). A result that arrives while Settings is out of sight goes to `Prefs.notice` too (Lehko's home shows
  it) when Lehko is on. Home row: off -> the turn-on dialog (focus starts on "Turn on", not the "Also turn off the stock
  launcher" box); on -> a list: "Turn off the stock launcher" (confirm dialog with that title and a "Turn off" button,
  then enable(full=true)) or, while `HomeMode.launcherOff`, "Turn the stock launcher back on" (no confirm); "Allow
  debugging again"; "Bring back the TV's home screen". In the watchdog's safe mode the row goes straight to the "Bring
  back" dialog, which has no "Allow debugging again" button (it would report "Done" while the safe screen stays;
  disable() asks for the permission itself). "Turn apps back on" (asks first) shows while Lehko is off and its record of
  turned-off apps (`HomeMode.disabled`) isn't empty; an app turned on elsewhere stays in the record until this runs.
  `saver()` reads screensaver_components and tells the fireplace by the component's class name (the TV's settings write
  `ua.lehko.tv/ua.lehko.tv.FireDream`, Lehko the short form). The fireplace row says when the TV's screensaver timer
  comes first, unless the fireplace is the screensaver. Errors: a closed port always gives `adb_closed` here (the shared
  text's "first make Lehko the home screen" is what is being done), the rest goes through `HomeMode.explain`, then Wi-Fi
  owner/check and Refused texts; other exceptions show only their class name inside a translated sentence. Rows (plain
  int ids; spec B gave bundle H 20-29, N 30-39, U 40-49): STAR 15 right under Home (`remote_star`; shown when
  `HomeMode.isOn`, `KeyCharacterMap.deviceHasKey(Keys.STAR)` and `HomeMode.helperListed`; summary: the chosen app,
  `remote_star_tv`, or with nothing chosen `remote_star_default` (the first openable `Keys.DEFAULT_STAR` app) where
  launcherx is installed and such an app is, else `remote_star_tv`; a second line
  `remote_keys_too` while launcherx is installed and off, following `Keys.launcher`, not `HomeMode.launcherOff`; OK:
  `Apps.load` on App.IO, guarded against a double OK, then a single-choice list "As the TV does" + every openable app,
  where with nothing chosen the row of what the star does now is marked and OK on it keeps "nothing chosen"; writes
  `star=` to helper.txt). Section `section_beyond` after the fireplace rows while `TvHelper.running`: WARN 30
  (`outage_warning`, shown with a schedule; toggles `warn_on=0`/absent, then `OutageJob.sync`) and WAKE 31 (`wake`;
  `wake=home` or `wake=input:<id>`, summary `wake_instead` with the TV's label; hidden once `Inputs.load` finds no
  input). UPDATE 40 right before ABOUT, release builds only (`Update.enabled()`; see `Update`). `EXTRA_OUTAGES`
  ("ua.lehko.tv.extra.OUTAGES") is taken in onCreate (only with no saved state) or onNewIntent; onResume selects the
  outage row and, while `outageInvite(c)` (Kyiv zone, `!Outages.enabled`, pref `outages_declined` = `P_OUTAGES_DECLINED`
  unset; it is set when "Off" is picked in the region dialog), no switch runs (`busy`), no setup dialog is showing
  (`setupDialog`, assigned at every step: region, method, group list, street and house search, pickItem) and no lookup
  runs (`lookups`, counted in background()), opens the region dialog. `outageRegion()` selects the configured region, or
  the first, never "Off". Background jobs share the status card's progress through `steps()`. An allowInstalls result
  that arrives while Settings isn't visible opens nothing and downloads nothing (status `update_found`); the next OK
  goes straight to the download.
- `Watchdog`: 3 crashes in 5 min while Lehko is home -> safe mode, a revert right away and `ModeJob` REVERT after 60 s.
  Java crashes come from the uncaught-exception handler; on API 30+ `ApplicationExitInfo` adds REASON_CRASH_NATIVE and
  REASON_ANR at foreground importance newer than the `crashes_since` pref (set on clear() and on entering safe mode:
  Android's record outlives a fresh start). `lastFailure` keeps the last failed switch back's `Adb.Failure` reason for
  the safe screen. `BootReceiver` (boot, Lehko update) -> `ModeJob` VERIFY. `nativeCrashesAndHangs` reads all exit
  records (maxNum 0) and counts only the main process's (`getProcessName()` == the package): the helper's exits neither
  count nor push the home screen's out. Entering safe mode, or finding it set at a start, calls `OutageJob.sync` (which
  cancels the job) before the revert: each job start of the main process would otherwise retry the revert ("Allow USB
  debugging?", `am start` HOME) over whatever is playing.
- `TvHelper`: an AccessibilityService in its own process `:helper` (the one exception to one process, said in a manifest
  comment: it runs behind every app, so a few MB of its own instead of keeping the home screen's process alive, and it
  keeps working if the home screen crashes). Label `helper_label` ("Lehko helper" / "Помічник Lehko");
  `res/xml/tv_helper.xml`: flagRequestFilterKeyEvents, canRequestFilterKeyEvents, typeAnnouncement only (ignored),
  canRetrieveWindowContent false, isAccessibilityTool not set (it would be a false claim). It never reads
  SharedPreferences (another process): only helper.txt and its own helper_state.txt (see `Keys`). The main process's
  gate is `TvHelper.running(c)` = `HomeMode.isOn && HomeMode.helperListed`; the helper itself acts only while Lehko
  holds HOME (`lehkoHome`: resolveActivity(HOME, MATCH_DEFAULT_ONLY) is Lehko). Keys: only STAR, BUTTON_3 (YouTube on
  Xiaomi's remote) and ALL_APPS, never from a gamepad or joystick source. On a fresh press (DOWN, repeat 0) `Keys.opens`
  gives the targets; the first that `Apps.launchIntent` (or AllAppsActivity) can open is started on App.MAIN right after
  the key is answered (Android waits only 500 ms for a filter's answer) with NEW_TASK | RESET_TASK_IF_NEEDED, and the
  whole press (down, repeats, up) is kept (`taken`; a new press of the taken key resets it, so a lost UP can't swallow
  the next one). Nothing to open, Lehko not HOME or a RuntimeException: the key goes on. Why it works (Eugene,
  2026-10-21, on his TV with his own launcher project): `dumpsys window policy` mKeyMapping hands MACRO_1, BUTTON_3 and
  ALL_APPS to launcherx's `.remotecontrol.GlobalKeyListener_Receiver` in interceptKeyBeforeDispatching (with launcherx
  off, nothing happened), and the accessibility key filter sees them before that. Over other apps, only while the screen
  is on (SCREEN_ON/OFF receiver; TIME_CHANGED and TIMEZONE_CHANGED re-arm): one Handler timer (`tick`) for the next
  thing due; a FileObserver on the files folder (CLOSE_WRITE | MOVED_TO of helper.txt) marks the settings stale and
  re-arms, no polling. Cards: a TextView added as TYPE_ACCESSIBILITY_OVERLAY, FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE,
  top right inside `safe_h`/`safe_v`, `helper_card` drawable, 17sp, 200 ms fade in, 12 s (`CARD_MS`) then a 300 ms fade,
  announced for TalkBack, one at a time (the sleep card first, then an outage, then a change). Screen off removes the
  card, stops the timers and ends any pending sleep timer (writes `sleep_done`). Sleep: in the last minute any DOWN
  calls keepOn() (`sleep_done`), and a fresh press is kept unless `SleepTimer.passes` (volume, mute, power keys); at the
  target `HomeMode.sleep` on App.ADB, else `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` on API 28+. On API < 28 the
  last minute first asks `HomeMode.canSleep` (silent) and shows `sleep_cant` (error colour) instead of `sleep_card` when
  debugging doesn't answer; a failed sleep without that warning shows `sleep_cant` for 12 s. Wake input: SCREEN_ON ->
  `wakeLater(2)` (1.5 s, then one more look 3 s later); at its own start within 3 min of boot (`COLD_START_MS`), screen
  on, on the first start of this boot (`Settings.Global.BOOT_COUNT` noted as `boot`; every start counts where the TV has
  no count) -> `wakeLater(10)` (every 3 s). It opens the `wake=input:<id>` choice with `Inputs.byId` (on App.IO) and
  `Inputs.open`, only while Lehko's main process is IMPORTANCE_FOREGROUND (the TV came on to Lehko's home, not to a
  cast, a CEC device or an app's button) and no key was pressed since (`lastPress` >= `wakeSince` stops it). Turned off:
  `onUnbind` writes `turned_off=<ms>` when the TV's list no longer has the helper (the TV's accessibility settings or
  shortcut, or Bring back); a force stop, an update or a restart ends the process without onUnbind; `onServiceConnected`
  removes the note. `TvHelper.turnedOff(c)` (main process) makes check() skip helperOn and helperDropped() false;
  enable() (turning Lehko or full mode on) still adds it again.
- `Keys`: the helper's pure decisions plus two shared facts. `STAR` = 313 (KEYCODE_MACRO_1, Android 14; the quick
  build's android-33.jar lacks it), `LAUNCHERX` = com.google.android.apps.tv.launcherx, `YOUTUBE` =
  com.google.android.youtube.tv, `ALL_APPS` = "all-apps" (Lehko's AllAppsActivity), `DEFAULT_STAR` = org.ittv.android,
  ar.tvplayer.tv, studio.scillarium.ottnavigator, ru.iptvremote.android.iptv (the first installed wins).
  `handled(keyCode, source)`; `opens(keyCode, star, launcherOff)`: the star opens the chosen package always, `tv`
  passes, nothing chosen -> DEFAULT_STAR only while launcherx is installed and off; YouTube and all apps only while
  launcherx is installed and off (`launcher(pm)` is null when it isn't installed; `launcherOff(pm)` = installed and
  disabled, not installed counts as on; asked at each press). A chosen app uninstalled later passes the key to the
  system (the summary then shows the package name). `wakeInput(wake, inputs)`. Two files in getFilesDir(), each with one
  writing process: `helper.txt` (`Keys.FILE`), written only by the main process, read by the helper: `star=<pkg>|tv`,
  `wake=home|input:<id>`, `sleep=<epoch ms>`, `warn=start:end:off|maybe,...`, `warn_on=0` (absent = on), `changed=<id>
  <text>`; `helper_state.txt` (`Keys.STATE`), the helper's own notes, written only by the helper and read by the main
  process: `warned`, `changed_seen`, `sleep_done`, `boot`, `turned_off`. `putAll(dir, name, changes)`: synchronized, a
  null value removes the key, no write when nothing changes, a file it can't read is never rewritten (its other keys
  would be lost), written to `<name>.tmp` with fsync (a power cut must not leave an empty file), then renamed. `read`
  gives an empty map for a missing or unreadable file; `parse`/`format` are key=value lines.
- `OutageWarning` (the main process writes, the helper shows). `update(c, before, after)` after every download: `warn=`
  the OFF and MAYBE runs that start after now, today and tomorrow (Kyiv), as epoch ms through `Outages.at` (end 0 = not
  known: tomorrow unpublished, an emergency day); one run across midnight; a MAYBE right after OFF is left out; a run
  only inside the skipped spring hour is dropped. `changedDays(before, after, now)`: the outages ahead differ between
  the same group's previous cache and this download, from the same source (yasno/dtek: Kyiv's mirror steps in when YASNO
  fails and may lag it); today from the current slot, tomorrow whole; never the first download, a group change, a day
  only one side has, or an emergency day. With the warning on and `TvHelper.running` it writes `changed=<now>
  <outage_changed text>` for the helper's card only: no home notice (the card shows over Lehko's home too; a notice
  would show it twice and wait with a stale "today"). The helper shows a change once (id != `changed_seen`), within 1 h
  (`CHANGED_FOR_MS`) and on the same Kyiv day, and an outage card from 15 min (`LEAD_MS`) before a start, once per start
  (`warned` = the last start warned), with the real minutes left (`warn_off`/`warn_maybe` + `outage_bar_range`, or
  `warn_at` without an end; ember text for OFF, `power_maybe` for MAYBE). `forget(c)` clears `warn` and `changed`
  (choose, turnOff, the weekly re-check moving the group). `OutageJob` (JobService id 30; ModeJob has 1 and 2): periodic
  30 min, NETWORK_TYPE_ANY, persisted. `sync(c)` schedules it only when it isn't pending (scheduling again would start
  the half hour over) while `wanted` = Outages.enabled && OutageWarning.on && TvHelper.running && !Watchdog.safeMode,
  else cancels it; it runs after each download, on the warning toggle, from HomeActivity.buildQuickRow, in
  Outages.turnOff and from Watchdog. A run calls `Outages.refreshIfStale` (the loading flag and back-off shared with
  home and the fireplace) and downloads nothing while the TV is in standby (`!isInteractive`). HomeMode.disable doesn't
  cancel it: its next run finds Lehko off and cancels itself (one extra wake at most).
- `SleepTimer`: `CHOICES` off/15/30/60/90 min; the home screen writes `sleep=<epoch ms>` (0 = off) into helper.txt.
  `step(at, done, now)`: NONE (unset, ended = `sleep_done`, or more than 2 min past: the TV was restarted), WAIT, CARD
  (the last 60 s), SLEEP. `at(c)` (main process) hides a timer the helper ended. `passes(key)`: volume, mute and the
  power keys still reach the app while the card is up; every other press only keeps the TV on (OK would pause the video,
  Back leave it). HomeActivity's moon button (`ic_sleep`, after Memory, while `TvHelper.running`) is named
  `sleep_timer_at` ("Sleep timer · off at 23:40", the TV's 12/24-hour format) while set; card text `sleep_card`.
- Wake input ("When the TV turns on", Settings WAKE): `wake=home` (the default: nothing opens) or `wake=input:<id>`
  (`Keys.P_WAKE`; choices: the home screen, then `Inputs.load`'s order, HDMI then Live TV). Settings resolves it for its
  summary and picker with `Keys.wakeInput` (null when the TV no longer has it: the row says the home screen); the helper
  resolves the id itself with `Inputs.byId` on App.IO (null: nothing opens, see `TvHelper`). Whether Google TV comes
  back from standby to Lehko's home rather than the last app decides whether it ever fires.
- `Update` + `UpdateReceiver`: Lehko's own updates. `Update.maybeCheck` is the last call in HomeActivity.maybeRefresh
  (after the outage refresh: App.NET runs one download at a time): at most once a day (`EVERY_MS`, pref
  `update_checked`), again 1 h after a failure (`RETRY_MS`, in memory), never while `!Update.enabled()` (`0.0.0-dev`, or
  a version that doesn't parse, like the quick build's "quick"). GET
  `api.github.com/repos/manoff4sv-lgtm/lehko-tv/releases/latest` (from App.REPO_URL; Net's User-Agent, `Accept:
  application/vnd.github+json`, unauthenticated). 404 = no release yet (counts as checked); 403/429 =
  `Update.Failure(update_busy)` (GitHub's 60 an hour per address). `parse`: `tag_name` (v optional, the pre-release part
  as app/build.gradle reads it) and the asset named exactly `Lehko.apk` whose `browser_download_url` starts with
  `https://github.com/` and 0 < size <= 30 MB. A newer tag without its Lehko.apk (CI attaches it minutes after the
  release is made) throws `Failure(update_not_ready)` without recording a check. Order (`compare`/`newer`): numbers (1.2
  = 1.2.0, each capped at 9 digits), then a release beats any pre-release of the same numbers, then pre-release parts
  semver-style (beta.10 > beta.9, rc > beta). Prefs: `update_version`/`update_url`/`update_size` (only while newer),
  `update_noticed` (one home notice per version, `update_notice` through `Prefs.notice`; it names the row "→ Update"),
  `update_session`, `update_result`/`_at`/`_error`. Settings' UPDATE row: `update_available` ("Version X · Y is out"),
  `update_latest`, or `update_check_now` before the first check. OK with nothing newer checks now (`check(c, false)`, no
  notice); with a newer one: while `Update.installing` only "working"; a confirmation still waiting comes up again
  (`confirmAgain`); else, if `canRequestPackageInstalls()` is false, `HomeMode.allowInstalls` with ADB_ENABLED=1 (may
  prompt), else (or if that didn't take) the TV's ACTION_MANAGE_UNKNOWN_APP_SOURCES screen for Lehko with the
  `update_allow` line. `Update.install` on App.NET abandons Lehko's earlier installer sessions, asks GitHub again
  (nothing newer: "the latest"), downloads exactly the release's size into cacheDir/Lehko.apk (https to https redirects
  followed, the percentage in the status card), checks `getPackageArchiveInfo` (ua.lehko.tv, versionName >= ours: betas
  share a versionCode), commits a PackageInstaller session (no shell) with a mutable broadcast PendingIntent to
  `UpdateReceiver` (not exported), and deletes the APK once it is in the session, or on failure. `UpdateReceiver` acts
  only on `update_session`. PENDING_USER_ACTION hands the confirmation to `Update.confirm`, which keeps it and starts it
  only while `App.screensShown > 0` (as the home app, and with an accessibility service, Lehko could otherwise put it
  over a playing video); else it reports `update_waiting` and OK on the row brings it up. No confirmation intent: a
  failure, and the session is abandoned. Other answers and download failures go through `Update.report`: stored for
  Settings' status card (its prefs listener between onStart and onStop, or onStart within `RESULT_MS` 10 min) and, while
  Lehko is on, a home notice in the same apply, which `takeResult` takes back when Settings shows the answer first.
  `resultText`: ABORTED -> `update_cancelled` (normal colour); STORAGE -> `update_storage`; CONFLICT or a message with
  "signatures" -> `update_signature` with the advice (NO_CERTIFICATES/INCONSISTENT_CERTIFICATES broken files get the
  generic text); else `update_failed`. Left out on purpose: confirmAgain in Settings.onStart (after a cancel the ABORTED
  broadcast may arrive after onStart and the confirmation would come straight back), and comparing signers before the
  hand-over (Android's check is the authority; key rotation). Manifest: REQUEST_INSTALL_PACKAGES. In-app updating works
  only from the first release that contains it (the one after v0.10.0-beta.2).
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
- `Memory`: free memory, own PSS (`ownPssKb` sums all of Lehko's processes, the helper included:
  `getRunningAppProcesses` lists only the app's own); `clean()` = `HomeMode.killAll` (`am kill-all`, may show the
  debugging prompt) else `killBackgroundProcesses` per app; `maybeAutoClean()` (never prompts) runs only from
  HomeActivity's idle runnable: just before the fireplace, or with no fireplace to come after
  `Memory.autoIdleMs(SCREEN_OFF_TIMEOUT)` = min(5 min, max(tv - 30 s, tv / 2)), i.e. 5 min without a TV timer, 4.5 min
  with the TV's 5, 30 s with 1 min; and only when `ActivityManager.MemoryInfo.lowMemory` is true, at most every 30 min
  (`AUTO_EVERY_MS`; `autoDue` uses `Net.stale`). This replaced (2026-10-24, Eugene approved) the old "< 20% or < 500 MB
  on every Home resume, every 3 min" rule. With stock AOSP thresholds lowMemory comes only near the low-memory killer's
  cached-apps level (about 150 MB on a 1 GB TV), so expect it rarely. `clean()` returns a `Memory.Result` (text,
  failed): on Android 14+ (API 34+, no killBackgroundProcesses fallback) a failed killAll gives `HomeMode.explain`'s
  reason, or `error_generic` with the class name, as failed=true, and home shows it as an ordinary notice.
  `HomeMode.enable` from off stores the stock launcher's PSS (`K_LAUNCHER_PSS`, from `dumpsys meminfo`).
  `App.onTrimMemory`: from RUNNING_LOW up (UI_HIDDEN, BACKGROUND and COMPLETE too) `Apps.trimPictures` keeps only the
  favorites' pictures and never empties the cache: the home tiles hold those bitmaps anyway, so evicting them freed
  nothing and the next favorites rebuild showed names only. Android 14+ sends only UI_HIDDEN and BACKGROUND.
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
  AudioTrack, only by the idle fireplace (`new FireScene(c, withSound)`: `FireDream` never plays it since 5b92d3e, as
  the screensaver often starts over a paused video and taking that player's audio focus, then giving it back, is the
  last thing it needs), not started while music is active; it holds AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK and stops until
  the next FireScene.start on any focus loss, e.g. music, a video or casting started under the fire),
  `FireplaceActivity` (HomeActivity's idle timer, reset in `onUserInteraction`; volume and media keys pass through to
  the playing app, Search opens the assistant and ends it, any other key goes back; no keep-screen-on), `FireDream`.
  Screensaver choice: `HomeMode.setScreensaver(SAVER_SYSTEM | SAVER_CLOCK | SAVER_FIRE)`.
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
  then `ANDROID_JAR=/opt/sdkjars/android-33.jar tools/quick-build.sh`. Tests: `tools/quick-test.sh`. HomeModeTest,
  AdbProtocolTest and UpdateAdbTest (base + 7) start fake adbd servers on ports from `LEHKO_TEST_PORT` (default
  25555): give checkouts that test at the same time different ports.
- `tools/fake_adbd.py --tv` (the simulated Google TV) also models the accessibility settings (accessibility_enabled 0
  and no services list at the start, as on Eugene's TV), `settings put` with the shell's quoting (single quotes keep
  '$', an unquoted `$NAME` expands to nothing), the flag `settings_silent` (`settings get` answers nothing, as adbd does
  when it can't open a shell) and `appops set` modes (under settings["appops"] in fake:state). It has no `input
  keyevent`, its command log (self.log) isn't exposed through fake:state, and `HomeMode.sleep` has no Env version, so it
  has no test.
- Release signing: CI reads secrets LEHKO_KEYSTORE_BASE64, LEHKO_KEYSTORE_PASSWORD, LEHKO_KEY_ALIAS,
  LEHKO_KEY_PASSWORD; without them release is debug-signed. Keep the release key stable (updates need it).

## Status

v0.9 is feature-complete and reviewed: home screen, weather, outages, on-device home switching with safe mode,
unneeded-apps screen, uk/en/ru, CI with signed builds (incl. HomeModeTest against `tools/fake_adbd.py --tv`, a
simulated Google TV). v0.10 (2026-10-22) added the ambient background, hourly weather and sun times, the focused app
name, the rates line, memory cleaning and measurement, the fireplace, a design/accessibility pass and the dial
redesign of the home screen. First run on Eugene's TV (2026-10-22) found three things, fixed: the memory notice didn't
go away, the faded ends of the scrolling rows looked bad, the fireplace filled only part of the screen.
v0.10.0-beta.2 (2026-10-22) is published; it predates the audit, so everything below is on main only until
the next release. A full audit (2026-10-23: home switching and the revert scripts, outage and weather
data, the home screen, apps, Unneeded apps, build and CI) found ~70 defects; all are fixed. A follow-up pass the same
day fixed the "TV's own" screensaver choice, the safe-mode "Bring back" dialog, the launcher row's wording and its
"back on" with setupwraith left off, the debugging timeout after a reinstall, the Memory button's Android 14 errors
(shared `HomeMode.explain`), the notice layout before the apps load, the crackle over music, the DST countdown and
offline schedule tries (101 tests then).

2026-10-24/25 (on main, the commits after 26d565a up to 38a37d5; not released): Eugene answered the two open points
(auto clean's threshold, the feature ideas) and approved two specs. Spec A: the remote buttons helper (`TvHelper` in `:helper`, `Keys`, HomeMode's helperOn/helperOff,
the revert scripts, the star row) and auto clean only on Android's lowMemory while home is idle. Spec B, built as three
parallel bundles and merged: H (the "Add apps" tile only in an empty row, the weather city from the outage region, the
outage invitation on the home screen), N (the outage warning and the schedule-change card over any app, `OutageJob`, the
sleep timer, the wake input; the helper on with Lehko on every TV), U (`Update`: a daily check, installing after the
TV's confirmation). A review pass then fixed: the press that answers the sleep card reached the player; a helper turned
off in the TV's settings came back; the wake input's later tries could switch away from the user; Android 8's sleep
timer without debugging; the update's screens over another app (the installer only over a Lehko screen, nothing opens
after the debugging wait in the background); GitHub's rate limit read as "no connection"; a stray OK ending a running
sleep timer; stacked outage setup dialogs and the region dialog opening on "Off"; OutageJob re-running the safe-mode
revert every half hour; the weekly group re-check leaving the old group's warnings. 5b92d3e: the fireplace screensaver
never plays the crackle. 147 tests (HomeModeTest 50).

Nothing of this has run on a device or emulator, only the quick build and the JVM tests. To check on Eugene's TV: the
star, YouTube and all-apps buttons with launcherx off (does the key filter really see MACRO_1, BUTTON_3 and ALL_APPS
first?), the star with launcherx on and an app chosen, AllAppsActivity opened by the helper (its own task: focus, Back);
the overlay card over YouTube, HDMI and Live TV (readable, no stutter, doesn't take the remote), the real minutes after
turning the TV on, OutageJob with an app in front and nothing in standby; the sleep timer (KEYCODE_SLEEP, the card's
press not reaching the player, cancel by turning the TV off); the wake input (does Google TV come back from standby to
Lehko's home or to the last app? cold start); a force stop of Lehko, then Home: the helper listed again within a minute;
the `:helper` PSS (not measured yet; Eugene's own earlier RemoteKeys helper took about 4 MB); whether Google TV shows
Android 12+'s periodic "full control" reminder or shows the sideloaded helper as restricted; the update path (needs two
releases with this code: install the first by hand), Back on the confirmation, the "Unknown sources" path with Lehko off
and debugging off, nothing left in the cache. `scripts/revert.bat` has never been run (no Windows here; its new check
was only emulated in Python): one run with other services plus Lehko's, Lehko's only, an unset list and no notes. Auto
clean will rarely or never run on a 1 GB TV (see `Memory`). Before testing the star, Eugene's own earlier helper
`ua.lehkyi.launcher/.RemoteKeys` may still be in enabled_accessibility_services: with both on, one press starts two
apps; `adb shell settings get secure enabled_accessibility_services` shows it, and his own revert.bat removes it (Lehko
never touches other services).

Open question (2026-10-25): after the fireplace screensaver, a paused YouTube video's picture stayed frozen. Eugene is
checking whether the TV's own screensaver does the same; if it doesn't, look at `FireDream` (5b92d3e already stopped it
taking the player's audio focus).

Waiting for Eugene's OK on these choices: (1) the helper is on with Lehko on every TV (489c4f9 made that change; it no
longer reverts cleanly because b25d563 and later commits touched the same lines, so limiting it to Google TV or a star
key means putting back by hand the `present.contains(Keys.LAUNCHERX) || starKey()` test in helperOn, plus its Env method
and tests); (2) a changed schedule shows only as the helper's card, not as a home notice; (3) the wake input opens only
when the TV comes on to Lehko's home, not after every screen-on; (4) OutageJob downloads nothing in standby; (5) the
outage invitation shows only in the Kyiv time zone and says "address or group" (the spec said "address"), and the
focused panel shows only "OK: ..."; (6) OK on the panel after "Off" only selects the outage row, and installs that
turned the schedule off before `outages_declined` existed are invited once more; (7) the update notice says
"→ Оновлення" (the row's title), not the spec's "→ Оновити"; (8) the original enabled_accessibility_services isn't
recorded (see `HomeMode`); (9) a star app uninstalled later passes the key to the TV rather than to the default list.
Known gaps: `HomeMode.sleep` has no test; turning Lehko off doesn't cancel OutageJob directly; on Android 8 the sleep
timer needs debugging.

A release v0.10.0-beta.3 (the audit, its follow-ups and all of the above) waits for Eugene's OK. Its notes must say that
updating from Lehko's settings starts with it (earlier versions are updated by hand once), and trying the update path
needs one more release after it.

UI strings live in `tools/strings/strings.py` (edit there, then `python3 tools/strings/strings.py app/src/main/res`).
It writes line breaks as `\n` and quotes texts with double spaces, which aapt2 would otherwise squeeze.

**Do not** publish GitHub releases, change repository settings or rewrite git history without Eugene's OK. Pushing tags
from cloud sessions is blocked by the git proxy; releases are made on GitHub's "Draft a new release" page (tag
`v0.10.0-beta.3` style) and CI then attaches the signed `Lehko.apk`. Do NOT tick "Set as a pre-release", not even for a
beta: README, scripts/install.sh and scripts/install.bat download `/releases/latest/download/Lehko.apk`, and
/releases/latest skips pre-releases (CI only warns; releases CI creates itself are normal ones). Tags need two or three
numbers: v1.2.3, v1.2.3-beta.1, or v1.2 (= v1.2.0); versionCode = major*10000 + minor*100 + patch. Any other v* tag
fails CI at configuration (app/build.gradle). If CI goes red on a tag made on the release page, ask Eugene to delete
that release and its tag and make a correct one: otherwise "latest" has no Lehko.apk and the README link returns 404.
`Update` reads `/releases/latest` too and needs the asset named exactly `Lehko.apk`: a pre-release is invisible to
in-app updates, and a release without its file reads as "not ready yet".

## v0.10: look, memory, rates, fireplace (all done 2026-10-22; kept as the spec)

Deviations from the plan below: no USDT/UAH (the line wouldn't fit beside the weather); `forecast_days=2` (and
`forecast_hours=7` since the 2026-10-23 audit); on UI_HIDDEN the picture cache is trimmed rather than emptied (emptying
would reload every banner on each return home). The fireplace look was checked only in an AWT re-drawing of `FireView`,
not on a device. Auto clean: Eugene first raised the threshold to 500 MB (2026-10-22); on 2026-10-24 that rule gave way
to Android's own lowMemory signal while home is idle, at most every 30 min (see `Memory`). Review fixes: no clipPath per
frame (arch mask in the grid), picture cache keeps favorites except at BACKGROUND+ (since the 2026-10-23 audit at every
level, see `Memory` above), favorites row rebuilt on resume only when the list changed, fire paused without window focus
(Android 8-10 dreams).

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
   - Auto clean (Settings toggle, default on; superseded 2026-10-24, see `Memory` above): on `HomeActivity.onResume`, if
     availMem < 20% of total or < 200 MB and the last auto clean was > 3 min ago, run `am kill-all` in the background
     with `Adb.connect(c, 0, null)` (never prompts; silently skip on failure).
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
