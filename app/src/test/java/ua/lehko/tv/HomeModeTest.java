package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The home screen switch against a simulated Google TV (tools/fake_adbd.py --tv): turning on and off, Wi-Fi network
 * protection, reinstalling, TV updates, the crash safety net and failures halfway.
 */
public class HomeModeTest {
    /** LEHKO_TEST_PORT moves the fake adbd ports, so test runs in several checkouts don't collide. */
    private static final int BASE = Integer.parseInt(System.getenv().getOrDefault("LEHKO_TEST_PORT", "25555"));
    private static final int PORT = BASE + 2;
    private static final String LAUNCHERX = "com.google.android.apps.tv.launcherx", SETUP = "com.google.android.tungsten.setupwraith";
    private static Process server;
    private static File keys;

    @BeforeClass public static void startFakeTv() throws Exception {
        File script = new File("tools/fake_adbd.py");
        if (!script.isFile()) script = new File("../tools/fake_adbd.py");
        assumeTrue("fake_adbd.py not found", script.isFile());
        try {
            server = new ProcessBuilder("python3", script.getPath(), String.valueOf(PORT), "--tv")
                    .redirectErrorStream(true).redirectOutput(new File(System.getProperty("java.io.tmpdir"), "fake-tv.log"))
                    .start();
        } catch (IOException e) {
            assumeTrue("python3 not available", false);
        }
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("127.0.0.1", PORT)) {
                break;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        keys = Files.createTempDirectory("lehko-keys").toFile();
    }

    @AfterClass public static void stopFakeTv() {
        if (server != null) server.destroy();
    }

    @Before public void resetTv() throws Exception {
        tv("fake:reset");
    }

    // MARK: - A test environment: memory preferences, the fake TV, notices collected

    static final class Env implements HomeMode.Env {
        final MemPrefs prefs = new MemPrefs();
        final File keyDir;
        String notice;
        int port = PORT;
        boolean debugging = true;

        Env(File keyDir) {
            this.keyDir = keyDir;
        }

        @Override public SharedPreferences prefs() {
            return prefs;
        }

        @Override public String text(int id, Object... args) {
            return "#" + id + Arrays.toString(args);
        }

        @Override public Adb connect(int approvalMs, Adb.Approval approval) throws IOException {
            return Adb.connect(keyDir, "127.0.0.1", port, approvalMs <= 0 ? 0 : 5_000, approval);
        }

        @Override public void notice(String text) {
            notice = text;
        }

        @Override public int sdk() {
            return 34;
        }

        @Override public boolean debuggingOn() {
            return debugging;
        }
    }

    static final HomeMode.Progress QUIET = new HomeMode.Progress() {
        @Override public void step(String text) {}

        @Override public void approval() {}
    };

    static String tv(String command) throws IOException {
        try (Adb adb = Adb.connect(keys, "127.0.0.1", PORT, 5_000, null)) {
            return adb.shell(command);
        }
    }

    static JSONObject state() throws Exception {
        return new JSONObject(tv("fake:state"));
    }

    static boolean enabled(JSONObject state, String pkg) throws Exception {
        return state.getJSONObject("pkgs").getBoolean(pkg);
    }

    static int networks(JSONObject state) throws Exception {
        return state.getJSONArray("networks").length();
    }

    // MARK: - Tests

    @Test public void fullModeOnAndBackOff() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        JSONObject s = state();
        assertEquals("ua.lehko.tv", s.getString("holder"));
        assertFalse(enabled(s, LAUNCHERX));
        assertFalse(enabled(s, SETUP));
        assertEquals("the Wi-Fi network is still there", 1, networks(s));
        assertEquals("0", s.getJSONObject("settings").getJSONObject("global").getString("adb_allowed_connection_time"));
        assertTrue(e.prefs.getBoolean(HomeMode.K_ON, false));
        assertEquals(new HashSet<>(Arrays.asList(LAUNCHERX, SETUP)), HomeMode.disabled(e.prefs));
        String file = s.getJSONObject("files").getString(HomeMode.STATE_FILE);
        assertTrue(file, file.contains("on=true") && file.contains(LAUNCHERX) && file.contains(SETUP));

        HomeMode.disable(e, QUIET);
        s = state();
        assertEquals(LAUNCHERX, s.getString("holder"));
        assertTrue(enabled(s, LAUNCHERX));
        assertTrue(enabled(s, SETUP));
        assertFalse(s.getJSONObject("settings").getJSONObject("global").has("adb_allowed_connection_time"));
        assertFalse(e.prefs.getBoolean(HomeMode.K_ON, false));
        assertTrue(HomeMode.disabled(e.prefs).isEmpty());
        assertTrue(s.getJSONObject("files").getString(HomeMode.STATE_FILE).contains("on=false"));
    }

    @Test public void softModeTurnsNothingOff() throws Exception {
        Env e = new Env(keys);
        String message = HomeMode.enable(e, false, QUIET);
        assertEquals("shown on Lehko's home screen too", message, e.notice);
        JSONObject s = state();
        assertEquals("ua.lehko.tv", s.getString("holder"));
        assertTrue(enabled(s, LAUNCHERX));
        assertTrue(enabled(s, SETUP));
        HomeMode.disable(e, QUIET);
        assertEquals(LAUNCHERX, state().getString("holder"));
    }

    @Test public void wifiOwnerIsNeverTurnedOff() throws Exception {
        tv("fake:networks " + SETUP);
        Env e = new Env(keys);
        try {
            HomeMode.enable(e, true, QUIET);
            fail("should refuse");
        } catch (HomeMode.WifiOwnerException expected) {
            assertEquals(SETUP, expected.getMessage());
        }
        JSONObject s = state();
        assertEquals("nothing changed", LAUNCHERX, s.getString("holder"));
        assertTrue(enabled(s, SETUP));
        assertEquals(1, networks(s));
        assertFalse(e.prefs.getBoolean(HomeMode.K_ON, false));
    }

    @Test public void uncheckableWifiFailsClosed() throws Exception {
        tv("fake:flag dumpsys_broken 1");
        Env e = new Env(keys);
        try {
            HomeMode.enable(e, true, QUIET);
            fail("should refuse");
        } catch (HomeMode.WifiCheckException expected) {
            // ok
        }
        JSONObject s = state();
        assertEquals(LAUNCHERX, s.getString("holder"));
        assertTrue(enabled(s, LAUNCHERX));
        assertEquals(1, networks(s));
    }

    @Test public void withoutListNetworksTheDumpMustBeThere() throws Exception {
        tv("fake:flag list_unsupported 1");
        HomeMode.enable(new Env(keys), true, QUIET); // dumpsys answers: fine
        tv("fake:reset");
        tv("fake:flag list_unsupported 1");
        tv("fake:flag dumpsys_broken 1");
        try {
            HomeMode.enable(new Env(keys), true, QUIET);
            fail("should refuse");
        } catch (HomeMode.WifiCheckException expected) {
            // ok
        }
    }

    @Test public void reinstallTakesOverTheNotes() throws Exception {
        HomeMode.enable(new Env(keys), true, QUIET);
        // Lehko reinstalled: new key, empty preferences. The TV still has the notes.
        Env fresh = new Env(Files.createTempDirectory("lehko-keys2").toFile());
        String message = HomeMode.disable(fresh, QUIET);
        JSONObject s = state();
        assertTrue(message, enabled(s, LAUNCHERX));
        assertTrue("setupwraith is not a HOME candidate while off, only the notes know it", enabled(s, SETUP));
        assertEquals(LAUNCHERX, s.getString("holder"));
    }

    @Test public void brokenPackageListDuringRestoreLosesNothing() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        tv("fake:flag pm_broken 1");
        HomeMode.disable(e, QUIET);
        JSONObject s = state();
        assertTrue(enabled(s, LAUNCHERX));
        assertTrue(enabled(s, SETUP));
        assertTrue(HomeMode.disabled(e.prefs).isEmpty());
    }

    @Test public void brokenPackageListBlocksTurningOn() throws Exception {
        tv("fake:flag pm_broken 1");
        try {
            HomeMode.enable(new Env(keys), true, QUIET);
            fail("should refuse");
        } catch (IOException expected) {
            // ok
        }
        assertEquals(LAUNCHERX, state().getString("holder"));
    }

    @Test public void failedTurnOffIsNotClaimed() throws Exception {
        tv("fake:flag pm_disable_fails 1");
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        assertTrue("nothing was turned off, nothing is recorded", HomeMode.disabled(e.prefs).isEmpty());
        assertEquals("ua.lehko.tv", state().getString("holder"));
    }

    @Test public void refusedTurnOnPutsTheDebuggingTimeoutBack() throws Exception {
        tv("fake:flag home_refused 1");
        Env e = new Env(keys);
        try {
            HomeMode.enable(e, true, QUIET);
            fail("should refuse");
        } catch (HomeMode.Refused expected) {
            // ok
        }
        assertFalse(state().getJSONObject("settings").getJSONObject("global").has("adb_allowed_connection_time"));
        assertFalse(e.prefs.getBoolean(HomeMode.K_ADB_TIME_CHANGED, false));
    }

    @Test public void turnOnCountsOnlyWhatItTurnedOff() throws Exception {
        Env e = new Env(keys);
        HomeMode.setAppEnabled(e, "com.google.android.play.games", false, null);
        assertEquals("the launcher and the setup app", e.text(R.string.result_on_full, 2), HomeMode.enable(e, true, QUIET));
    }

    @Test public void appsOffBeforeLehkoAreLeftAlone() throws Exception {
        tv("pm disable-user --user 0 com.google.android.play.games");
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        HomeMode.disable(e, QUIET);
        assertFalse("Lehko didn't turn it off, so it doesn't turn it on", enabled(state(), "com.google.android.play.games"));
    }

    @Test public void verifyAfterTvUpdate() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        e.notice = null;
        // A TV update turns the launcher back on and makes it the home screen again.
        tv("pm enable " + LAUNCHERX);
        tv("cmd package set-home-activity --user 0 " + LAUNCHERX + "/.home.HomeActivity");
        HomeMode.verify(e);
        JSONObject s = state();
        assertEquals("ua.lehko.tv", s.getString("holder"));
        assertFalse(enabled(s, LAUNCHERX));
        assertNotNull("the user is told", e.notice);
    }

    @Test public void verifyNeverAsksForPermission() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        // Same preferences, but a key the TV has never seen: verify must not pop up the permission window.
        Env other = new Env(Files.createTempDirectory("lehko-keys3").toFile());
        other.prefs.putAll(e.prefs);
        tv("pm enable " + LAUNCHERX);
        HomeMode.verify(other);
        assertTrue("nothing done without permission", enabled(state(), LAUNCHERX));
        assertNotNull(other.notice);
    }

    @Test public void allowingDebuggingAgainKeepsLehkoOn() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        Env forgotten = new Env(Files.createTempDirectory("lehko-keys6").toFile()); // a key the TV doesn't know
        forgotten.prefs.putAll(e.prefs);
        HomeMode.verify(forgotten);
        assertEquals(forgotten.text(R.string.verify_no_permission), forgotten.notice);
        assertEquals(forgotten.text(R.string.allow_ok), HomeMode.allowAgain(forgotten, QUIET));
        assertEquals("ua.lehko.tv", state().getString("holder"));
        assertTrue(forgotten.prefs.getBoolean(HomeMode.K_ON, false));
    }

    @Test public void closedPortIsToldApartFromDebuggingOff() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, false, QUIET);
        e.port = BASE + 9; // nothing listens: the TV closed the port after a restart
        HomeMode.verify(e);
        assertEquals(e.text(R.string.verify_port_closed), e.notice);
        e.debugging = false;
        HomeMode.verify(e);
        assertEquals(e.text(R.string.verify_no_debugging), e.notice);
    }

    /** scripts/revert.sh without --uninstall: everything back and the note removed; Lehko's own records stay. */
    static void revertFromAComputer() throws IOException {
        tv("pm enable " + LAUNCHERX);
        tv("pm enable " + SETUP);
        tv("cmd package set-home-activity --user 0 " + LAUNCHERX + "/.home.HomeActivity");
        tv("settings delete global adb_allowed_connection_time");
        tv("rm -f " + HomeMode.STATE_FILE);
    }

    static void assertRevertedFromAComputer(Env e) throws Exception {
        JSONObject s = state();
        assertEquals(LAUNCHERX, s.getString("holder"));
        assertTrue(enabled(s, LAUNCHERX));
        assertTrue(enabled(s, SETUP));
        assertFalse(s.getJSONObject("settings").getJSONObject("global").has("adb_allowed_connection_time"));
        assertFalse(e.prefs.getBoolean(HomeMode.K_ON, false));
    }

    @Test public void revertFromAComputerSticks() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        e.notice = null;
        revertFromAComputer();
        HomeMode.verify(e); // the next reboot
        assertRevertedFromAComputer(e);
        assertNull(e.notice);
    }

    @Test public void revertFromAComputerSticksThroughLehkosNextChange() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        revertFromAComputer();
        // Before the next reboot: a change in Lehko writes the note again, which must not say "on" any more.
        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET);
        assertFalse(e.prefs.getBoolean(HomeMode.K_ON, false));
        HomeMode.verify(e);
        assertRevertedFromAComputer(e);
    }

    @Test public void allowingDebuggingAgainAfterARevertFromAComputer() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        revertFromAComputer();
        assertEquals(e.text(R.string.home_status_off), HomeMode.allowAgain(e, QUIET));
        assertRevertedFromAComputer(e); // and the timeout the script put back stays
    }

    @Test public void uninstalledWhileOnIsNotTakenOverAgain() throws Exception {
        HomeMode.enable(new Env(keys), false, QUIET);
        // Uninstalled from the TV's settings: Android gives the home screen back, the note still says "on".
        tv("cmd package set-home-activity --user 0 " + LAUNCHERX + "/.home.HomeActivity");
        Env fresh = new Env(Files.createTempDirectory("lehko-keys4").toFile());
        HomeMode.systemState(fresh, null); // installed again, and the "unneeded apps" screen is the first to connect
        assertFalse(fresh.prefs.getBoolean(HomeMode.K_ON, false));
        assertTrue(state().getJSONObject("files").getString(HomeMode.STATE_FILE).contains("on=false"));
    }

    @Test public void uninstalledInFullModeKeepsTheWayBack() throws Exception {
        HomeMode.enable(new Env(keys), true, QUIET);
        // Uninstalled with the stock launcher still off: the TV has no home screen until "Original".
        tv("cmd role remove-role-holder --user 0 android.app.role.HOME ua.lehko.tv");
        Env fresh = new Env(Files.createTempDirectory("lehko-keys5").toFile());
        HomeMode.systemState(fresh, null);
        assertTrue(fresh.prefs.getBoolean(HomeMode.K_ON, false));
        HomeMode.disable(fresh, QUIET);
        assertEquals(LAUNCHERX, state().getString("holder"));
    }

    @Test public void crashSafetyNet() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        assertTrue("not in safe mode: nothing happens", HomeMode.revertAfterCrash(e, null));
        assertEquals("ua.lehko.tv", state().getString("holder"));

        e.prefs.edit().putBoolean("safe_mode", true).commit();
        assertTrue(HomeMode.revertAfterCrash(e, null));
        JSONObject s = state();
        assertEquals(LAUNCHERX, s.getString("holder"));
        assertTrue(enabled(s, SETUP));
        assertFalse(Watchdog.safeMode(e.prefs));
        assertFalse(e.prefs.getBoolean(HomeMode.K_ON, false));
    }

    @Test public void homeThatCantComeBackKeepsLehkoOn() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        // Both possible homes are gone (say, uninstalled by hand): Lehko must not claim it switched back.
        tv("pm uninstall " + LAUNCHERX);
        tv("pm uninstall " + SETUP);
        HomeMode.disable(e, QUIET);
        assertEquals("ua.lehko.tv", state().getString("holder"));
        assertTrue("still on, so the user can try again", e.prefs.getBoolean(HomeMode.K_ON, false));
    }

    @Test public void turningOnRemembersTheLaunchersMemory() throws Exception {
        Env e = new Env(keys);
        HomeMode.enable(e, true, QUIET);
        assertEquals(245678L, e.prefs.getLong(HomeMode.K_LAUNCHER_PSS, 0));
    }

    @Test public void closingBackgroundApps() throws Exception {
        Env e = new Env(keys);
        HomeMode.killAll(e, false, null);
        JSONObject s = state();
        assertEquals(1, s.getInt("kills"));
        assertFalse(s.getJSONObject("running").has("com.google.android.youtube.tv"));
        assertTrue("the home screen stays", s.getJSONObject("running").has(LAUNCHERX));
    }

    @Test public void screensaverOnAndBack() throws Exception {
        Env e = new Env(keys);
        HomeMode.setScreensaver(e, true, QUIET);
        JSONObject secure = state().getJSONObject("settings").getJSONObject("secure");
        assertEquals(HomeMode.DREAM, secure.getString("screensaver_components"));
        HomeMode.setScreensaver(e, false, QUIET);
        secure = state().getJSONObject("settings").getJSONObject("secure");
        assertEquals("com.google.android.apps.tv.dreamx/.service.Backdrop", secure.getString("screensaver_components"));
        assertEquals("1", secure.getString("screensaver_enabled"));
    }

    @Test public void fireplaceScreensaverAndBack() throws Exception {
        Env e = new Env(keys);
        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET);
        JSONObject secure = state().getJSONObject("settings").getJSONObject("secure");
        assertEquals(HomeMode.FIRE_DREAM, secure.getString("screensaver_components"));
        assertTrue(e.prefs.getBoolean(Prefs.SCREENSAVER_FIRE, false));
        HomeMode.setScreensaver(e, HomeMode.SAVER_CLOCK, QUIET);
        secure = state().getJSONObject("settings").getJSONObject("secure");
        assertEquals(HomeMode.DREAM, secure.getString("screensaver_components"));
        assertFalse(e.prefs.getBoolean(Prefs.SCREENSAVER_FIRE, false));
        HomeMode.setScreensaver(e, HomeMode.SAVER_SYSTEM, QUIET);
        secure = state().getJSONObject("settings").getJSONObject("secure");
        assertEquals("the original, not the clock", "com.google.android.apps.tv.dreamx/.service.Backdrop",
                secure.getString("screensaver_components"));
        assertFalse(e.prefs.getBoolean(Prefs.SCREENSAVER_LEHKO, true));
    }

    @Test public void screensaverChosenInTheTvsSettingsStays() throws Exception {
        Env e = new Env(keys);
        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET);
        String ambient = "com.google.android.apps.tv.dreamx/.service.Ambient";
        tv("settings put secure screensaver_components " + ambient);
        HomeMode.setScreensaver(e, HomeMode.SAVER_SYSTEM, QUIET);
        assertEquals(ambient, state().getJSONObject("settings").getJSONObject("secure").getString("screensaver_components"));

        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET);
        tv("settings put secure screensaver_enabled 0"); // "Turn screen off"
        HomeMode.disable(e, QUIET);
        JSONObject secure = state().getJSONObject("settings").getJSONObject("secure");
        assertEquals(ambient, secure.getString("screensaver_components"));
        assertEquals("0", secure.getString("screensaver_enabled"));
    }

    @Test public void screensaverPickedAgainAfterTheTvsSettings() throws Exception {
        Env e = new Env(keys);
        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET);
        String ambient = "com.google.android.apps.tv.dreamx/.service.Ambient";
        tv("settings put secure screensaver_components " + ambient);
        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET); // the row said "The TV's own", so Fireplace again
        HomeMode.setScreensaver(e, HomeMode.SAVER_SYSTEM, QUIET);
        assertEquals("the later choice, not the first record", ambient,
                state().getJSONObject("settings").getJSONObject("secure").getString("screensaver_components"));
    }

    @Test public void tvsOwnScreensaverIsTurnedBackOnIfLehkoTurnedItOff() throws Exception {
        String dreamx = "com.google.android.apps.tv.dreamx";
        Env e = new Env(keys);
        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET);
        tv("pm disable-user --user 0 " + dreamx); // off, but not by Lehko
        HomeMode.setScreensaver(e, HomeMode.SAVER_SYSTEM, QUIET);
        assertFalse("not Lehko's to turn on", enabled(state(), dreamx));

        tv("pm enable " + dreamx);
        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET);
        HomeMode.setAppEnabled(e, dreamx, false, null); // on the "unneeded apps" screen
        HomeMode.setScreensaver(e, HomeMode.SAVER_SYSTEM, QUIET);
        JSONObject s = state();
        assertTrue(enabled(s, dreamx));
        assertEquals(dreamx + "/.service.Backdrop", s.getJSONObject("settings").getJSONObject("secure")
                .getString("screensaver_components"));
        assertTrue(HomeMode.disabled(e.prefs).isEmpty());
    }

    @Test public void lehkosOwnDreamIsNotTheTvsOwn() throws Exception {
        tv("settings put secure screensaver_components " + HomeMode.DREAM); // picked in the TV's settings
        Env e = new Env(keys);
        HomeMode.setScreensaver(e, HomeMode.SAVER_FIRE, QUIET);
        HomeMode.setScreensaver(e, HomeMode.SAVER_SYSTEM, QUIET);
        assertFalse("Android's default", state().getJSONObject("settings").getJSONObject("secure").has("screensaver_components"));
    }

    @Test public void unneededAppsScreen() throws Exception {
        Env e = new Env(keys);
        HomeMode.setAppEnabled(e, "com.google.android.play.games", false, null);
        assertFalse(enabled(state(), "com.google.android.play.games"));
        try {
            HomeMode.setAppEnabled(e, "com.android.tv.settings", false, null);
            fail("protected");
        } catch (HomeMode.Refused expected) {
            // ok
        }
        tv("fake:networks com.netflix.ninja");
        try {
            HomeMode.setAppEnabled(e, "com.netflix.ninja", false, null);
            fail("saved Wi-Fi");
        } catch (HomeMode.WifiOwnerException expected) {
            // ok
        }
        try {
            HomeMode.setAppEnabled(e, "x; reboot", false, null);
            fail("not a package name");
        } catch (HomeMode.Refused expected) {
            // ok
        }
        HomeMode.disable(e, QUIET);
        assertTrue("Original turns everything back on", enabled(state(), "com.google.android.play.games"));
        assertEquals(1, networks(state()));
    }

    @Test public void appsTurnedOffWhileLehkoIsOffComeBackOn() throws Exception {
        Env e = new Env(keys);
        HomeMode.setAppEnabled(e, "com.google.android.play.games", false, null);
        assertEquals(e.text(R.string.result_apps_on), HomeMode.enableApps(e, QUIET));
        JSONObject s = state();
        assertTrue(enabled(s, "com.google.android.play.games"));
        assertTrue(HomeMode.disabled(e.prefs).isEmpty());
        assertEquals("the home screen is left alone", LAUNCHERX, s.getString("holder"));
    }

    // MARK: - In-memory SharedPreferences

    static final class MemPrefs implements SharedPreferences {
        private final Map<String, Object> map = new HashMap<>();

        void putAll(MemPrefs other) {
            map.putAll(other.map);
        }

        @Override public Map<String, ?> getAll() {
            return new HashMap<>(map);
        }

        @Override public String getString(String key, String def) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : def;
        }

        @SuppressWarnings("unchecked")
        @Override public Set<String> getStringSet(String key, Set<String> def) {
            Object v = map.get(key);
            return v instanceof Set ? new HashSet<>((Set<String>) v) : def;
        }

        @Override public int getInt(String key, int def) {
            Object v = map.get(key);
            return v instanceof Integer ? (Integer) v : def;
        }

        @Override public long getLong(String key, long def) {
            Object v = map.get(key);
            return v instanceof Long ? (Long) v : def;
        }

        @Override public float getFloat(String key, float def) {
            Object v = map.get(key);
            return v instanceof Float ? (Float) v : def;
        }

        @Override public boolean getBoolean(String key, boolean def) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : def;
        }

        @Override public boolean contains(String key) {
            return map.containsKey(key);
        }

        @Override public Editor edit() {
            return new Editor() {
                private final Map<String, Object> changes = new HashMap<>();
                private final Set<String> removals = new HashSet<>();

                @Override public Editor putString(String key, String value) {
                    changes.put(key, value);
                    return this;
                }

                @Override public Editor putStringSet(String key, Set<String> values) {
                    changes.put(key, values == null ? null : new HashSet<>(values));
                    return this;
                }

                @Override public Editor putInt(String key, int value) {
                    changes.put(key, value);
                    return this;
                }

                @Override public Editor putLong(String key, long value) {
                    changes.put(key, value);
                    return this;
                }

                @Override public Editor putFloat(String key, float value) {
                    changes.put(key, value);
                    return this;
                }

                @Override public Editor putBoolean(String key, boolean value) {
                    changes.put(key, value);
                    return this;
                }

                @Override public Editor remove(String key) {
                    removals.add(key);
                    return this;
                }

                @Override public Editor clear() {
                    map.clear();
                    return this;
                }

                @Override public boolean commit() {
                    for (String k : removals) map.remove(k);
                    map.putAll(changes);
                    return true;
                }

                @Override public void apply() {
                    commit();
                }
            };
        }

        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    }

    @SuppressWarnings("unused")
    private static JSONArray unused() {
        return null;
    }
}
