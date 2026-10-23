package ua.lehko.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Original home screen ⇄ Lehko. Everything goes through the TV's own debugging port ({@link Adb}).
 * <ul>
 * <li>On: Lehko becomes the HOME app. In "full" mode the stock launcher is also turned off (pm disable-user: still
 * installed, back with one button), which frees the most memory on weak TVs. On Google TV the setup app's recovery
 * screen would take over without its launcher, so it goes off too.</li>
 * <li>Original: every app Lehko turned off comes back and the stock home screen is the default again.</li>
 * </ul>
 * Rules that keep a TV safe:
 * <ul>
 * <li>A package is recorded as "turned off by Lehko" <i>before</i> the command runs, so nothing can end up off
 * without a record. The record is mirrored to /data/local/tmp/lehko_state, which survives a reinstall of Lehko and
 * is read back on the next connection (and by scripts/revert from a computer).</li>
 * <li>Nothing that saved a Wi-Fi network is turned off, and if that can't be checked for sure, nothing is.</li>
 * <li>"Lehko is on" is only cleared once the original home screen is really back.</li>
 * </ul>
 */
final class HomeMode {
    private HomeMode() {}

    static final String PKG = "ua.lehko.tv";
    static final String HOME = PKG + "/.HomeActivity";
    static final String DREAM = PKG + "/.ClockDream", FIRE_DREAM = PKG + "/.FireDream";
    static final int SAVER_SYSTEM = 0, SAVER_CLOCK = 1, SAVER_FIRE = 2;
    static final String GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx/.home.HomeActivity";
    static final String ATV_HOME_PKG = "com.google.android.tvlauncher";
    static final String ATV_RECOMMENDATIONS = "com.google.android.tvrecommendations";
    /** Google TV's setup app has a RecoveryActivity that becomes the home screen when the launcher is off. */
    static final String SETUP = "com.google.android.tungsten.setupwraith";
    static final String STATE_FILE = "/data/local/tmp/lehko_state";

    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    private static final Pattern COMPONENT = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+");
    private static final Pattern SETTING = Pattern.compile("[A-Za-z0-9_.$/,:]*");

    /** Never turned off, whatever happens: system, store, input, remote, Live TV and picture/sound services. */
    static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "android", PKG, "com.android.systemui", "com.android.tv.settings", "com.android.settings",
            "com.android.vending", "com.google.android.gms", "com.google.android.gsf", "com.google.android.webview",
            "com.android.webview", "com.google.android.inputmethod.latin", "com.google.android.tv.remote.service",
            "com.google.android.apps.mediashell", "com.android.bluetooth", "com.android.shell",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.android.externalstorage", "com.android.networkstack",
            "com.google.android.networkstack", "com.android.tv", "com.google.android.tv", "com.android.keychain",
            "com.android.certinstaller", "com.google.android.tvsetup", "com.android.inputdevices",
            "com.google.android.ext.services", "com.google.android.ext.shared", "com.android.wallpaperbackup",
            "com.android.location.fused", "com.android.dynsystem", "com.android.localtransport",
            "com.android.tv.settings.overlay", "com.dolby.android.audio.service"));
    /** Whole vendor/system families that are never offered for turning off. */
    static final String[] PROTECTED_PREFIXES = {
            "android.", "com.android.providers.", "com.android.server.", "com.android.internal.", "com.mediatek.",
            "com.droidlogic.", "com.amlogic.", "com.realtek.", "com.mstar.", "com.cvte.", "com.tvos", "org.droidtv",
            "com.android.wifi", "com.android.se", "com.android.ons", "com.android.cellbroadcast",
    };
    /** Parts of package names that mean "system plumbing" on any brand (settings, inputs, remote, updates...). */
    static final String[] PROTECTED_WORDS = {
            "settings", "setup", "provision", "systemui", "inputmethod", "keyboard", ".ime", "tvinput", "tvservice",
            "tvserver", "remote", "bluetooth", "factory", "update", ".ota", "upgrade", "launcher",
    };

    /** Core system packages: never turned off, not even by the home screen switch. */
    static boolean isCore(String pkg) {
        if (pkg == null || PROTECTED.contains(pkg)) return true;
        for (String p : PROTECTED_PREFIXES) if (pkg.startsWith(p)) return true;
        return false;
    }

    /** Not offered on the "unneeded apps" screen: core packages plus anything that looks like system plumbing. */
    static boolean isProtected(String pkg) {
        if (isCore(pkg)) return true;
        String lower = pkg.toLowerCase(java.util.Locale.ROOT);
        for (String w : PROTECTED_WORDS) if (lower.contains(w)) return true;
        return false;
    }

    // MARK: - Environment (the app, or a test talking to tools/fake_adbd.py)

    /** What HomeMode needs from Android, so its logic can be tested against a fake TV. */
    interface Env {
        SharedPreferences prefs();

        String text(int id, Object... args);

        Adb connect(int approvalMs, Adb.Approval approval) throws IOException;

        void notice(String text);

        int sdk();

        /** "USB debugging" is on in the TV's developer options. */
        boolean debuggingOn();
    }

    static Env env(final Context c) {
        final Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        return new Env() {
            @Override public SharedPreferences prefs() {
                return Prefs.of(app);
            }

            @Override public String text(int id, Object... args) {
                return app.getString(id, args);
            }

            @Override public Adb connect(int approvalMs, Adb.Approval approval) throws IOException {
                return Adb.connect(app, approvalMs, approval);
            }

            @Override public void notice(String text) {
                Prefs.notice(app, text);
            }

            @Override public int sdk() {
                return Build.VERSION.SDK_INT;
            }

            @Override public boolean debuggingOn() {
                return Settings.Global.getInt(app.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
            }
        };
    }

    // MARK: - State

    static final String K_ON = "mode_on", K_FULL = "mode_full", K_HOME = "mode_original_home",
            K_SAVER = "mode_original_saver", K_SAVER_ENABLED = "mode_original_saver_enabled",
            K_SAVER_CHANGED = "mode_saver_changed", K_DISABLED = "mode_disabled",
            K_ADB_TIME = "mode_original_adb_time", K_ADB_TIME_CHANGED = "mode_adb_time_changed",
            K_ADOPTED = "mode_adopted";
    /** How much memory (PSS, KB) the stock launcher used when Lehko was turned on: for the settings screen. */
    static final String K_LAUNCHER_PSS = "mode_launcher_pss_kb";

    static boolean isOn(Context c) {
        return Prefs.of(c).getBoolean(K_ON, false);
    }

    static boolean isFull(Context c) {
        return Prefs.of(c).getBoolean(K_FULL, false);
    }

    static Set<String> disabled(Context c) {
        return disabled(Prefs.of(c));
    }

    static Set<String> disabled(SharedPreferences p) {
        return new HashSet<>(p.getStringSet(K_DISABLED, Collections.<String>emptySet()));
    }

    private static Set<String> disabled(Env e) {
        return disabled(e.prefs());
    }

    private static boolean isOn(Env e) {
        return e.prefs().getBoolean(K_ON, false);
    }

    private static boolean isFull(Env e) {
        return e.prefs().getBoolean(K_FULL, false);
    }

    private static void setDisabled(Env e, Set<String> pkgs) {
        e.prefs().edit().putStringSet(K_DISABLED, new HashSet<>(pkgs)).commit();
    }

    /** Status messages for the settings screen while a switch runs (called on the worker thread). */
    interface Progress {
        void step(String text);

        /** The TV is showing "Allow USB debugging?". */
        void approval();
    }

    static final class WifiOwnerException extends IOException {
        WifiOwnerException(String pkg) {
            super(pkg);
        }
    }

    /** It couldn't be checked which apps saved Wi-Fi networks, so nothing was turned off. */
    static final class WifiCheckException extends IOException {
        WifiCheckException() {
            super("wifi check");
        }
    }

    static final class Refused extends IOException {
        Refused(String what) {
            super(what);
        }
    }

    // MARK: - Entry points for the app (Context) and for tests (Env)

    static String enable(Context c, boolean full, Progress p) throws IOException {
        return enable(env(c), full, p);
    }

    static String disable(Context c, Progress p) throws IOException {
        return disable(env(c), p);
    }

    static boolean revertAfterCrash(Context c, Progress p) {
        return revertAfterCrash(env(c), p);
    }

    static void verify(Context c) {
        verify(env(c));
    }

    static String setScreensaver(Context c, int which, Progress p) throws IOException {
        return setScreensaver(env(c), which, p);
    }

    static void setAppEnabled(Context c, String pkg, boolean enabled, Progress p) throws IOException {
        setAppEnabled(env(c), pkg, enabled, p);
    }

    static Set<String>[] systemState(Context c, Progress p) throws IOException {
        return systemState(env(c), p);
    }

    // MARK: - Connecting

    /**
     * Connects (asking for permission on the TV if needed), takes over notes from an earlier install and accepts a
     * switch undone from a computer, before anything rewrites the note from Lehko's own records.
     */
    static Adb connect(Env c, final Progress p) throws IOException {
        if (p != null) p.step(c.text(R.string.step_connecting));
        Adb adb = c.connect(90_000, p == null ? null : new Adb.Approval() {
            @Override public void waiting() {
                p.approval();
            }
        });
        try {
            adopt(c, adb);
            acceptOutsideRevert(c, adb);
        } catch (IOException | RuntimeException e) {
            adb.close();
            throw e;
        }
        return adb;
    }

    /**
     * After Lehko was reinstalled (or its data cleared) its own records are gone, but the TV still has
     * /data/local/tmp/lehko_state from before. Take those records back once, before anything overwrites the file.
     */
    private static void adopt(Env c, Adb adb) throws IOException {
        SharedPreferences p = c.prefs();
        if (p.getBoolean(K_ADOPTED, false)) return;
        String state = adb.shell("cat " + STATE_FILE + " 2>/dev/null");
        SharedPreferences.Editor e = p.edit().putBoolean(K_ADOPTED, true);
        Set<String> disabled = disabled(c);
        boolean on = false, full = false;
        String home = "";
        for (String line : state.split("\n")) {
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
            switch (key) {
                case "on": on = "true".equals(value); break;
                case "full": full = "true".equals(value); break;
                case "home": if (validHome(value)) home = value; break;
                case "disabled":
                    for (String pkg : value.split(",")) if (PACKAGE.matcher(pkg).matches()) disabled.add(pkg);
                    break;
                case "screensaver_changed": if ("true".equals(value)) e.putBoolean(K_SAVER_CHANGED, true); break;
                case "screensaver":
                    if (SETTING.matcher(value).matches() && !value.contains(PKG + "/")) e.putString(K_SAVER, value);
                    break;
                case "screensaver_enabled": if (value.matches("-?\\d+|null")) e.putString(K_SAVER_ENABLED, value); break;
                case "adb_time_changed": if ("true".equals(value)) e.putBoolean(K_ADB_TIME_CHANGED, true); break;
                case "adb_time": if (value.matches("-?\\d+|null")) e.putString(K_ADB_TIME, value); break;
                default: break;
            }
        }
        // "On" only while the TV agrees: uninstalling Lehko gives the home screen back but leaves the note as it was.
        boolean stillOn = false;
        if (on) {
            stillOn = homeHolder(adb).contains(PKG);
            if (!stillOn && full) {
                // Uninstalled in full mode: the stock launcher is still off, and "Original" has to bring it back.
                Set<String> off = packages(adb, "-d");
                if (off.isEmpty() && packages(adb, "").isEmpty()) throw new IOException("pm list packages answered nothing");
                stillOn = off.contains(packageOf(home)) || off.contains(SETUP);
            }
        }
        if (stillOn) {
            e.putBoolean(K_ON, true);
            if (full) e.putBoolean(K_FULL, true);
            if (p.getString(K_HOME, "").isEmpty() && !home.isEmpty()) e.putString(K_HOME, home);
        }
        e.putStringSet(K_DISABLED, disabled).commit();
        if (on && !stillOn) writeDeviceState(c, adb);
    }

    // MARK: - Switches

    /** Makes Lehko the home screen; {@code full}: also turns the stock launcher off. Returns a message to show. */
    static synchronized String enable(Env c, boolean full, Progress p) throws IOException {
        try (Adb adb = connect(c, p)) {
            p.step(c.text(R.string.step_saving));
            String original = c.prefs().getString(K_HOME, "");
            if (!validHome(original) || original.startsWith(PKG + "/")) {
                String found = currentHome(adb);
                original = found != null ? found : "";
                c.prefs().edit().putString(K_HOME, original).commit();
            }
            Set<String> present = packages(adb, "");
            if (present.isEmpty()) throw new IOException("pm list packages answered nothing");
            Set<String> offNow = packages(adb, "-d");
            List<String> targets = full ? targets(adb, original, present) : new ArrayList<String>();
            // Turning off an app makes Android forget the Wi-Fi networks it saved. Check before changing anything.
            if (!targets.isEmpty()) {
                Set<String> owners = wifiOwners(adb);
                for (String t : targets) if (owners.contains(t)) throw new WifiOwnerException(t);
            }
            keepDebuggingAllowed(c, adb);
            // Turned off later, the launcher has been in the background behind Lehko since and would measure small.
            if (!isOn(c)) measureLauncher(c, adb, original);

            p.step(c.text(R.string.step_switching));
            Watchdog.clear(c.prefs());
            // Recorded first: if anything dies halfway, the watchdog and "Original" still know Lehko may be home.
            c.prefs().edit().putBoolean(K_ON, true).putBoolean(K_FULL, full).commit();
            writeDeviceState(c, adb);
            String out = adb.shell("cmd package set-home-activity --user 0 " + HOME);
            if (!out.contains("Success") && !homeHolder(adb).contains(PKG)) {
                adb.shell("cmd role add-role-holder --user 0 android.app.role.HOME " + PKG);
                if (!homeHolder(adb).contains(PKG)) {
                    c.prefs().edit().putBoolean(K_ON, false).putBoolean(K_FULL, false).commit();
                    // Lehko stays off, so nothing needs the debugging permission kept any more.
                    restoreDebuggingTimeout(c, adb);
                    writeDeviceState(c, adb);
                    throw new Refused(out.trim());
                }
            }

            Set<String> ours = disabled(c);
            int i = 0, turnedOff = 0;
            for (String pkg : targets) {
                i++;
                p.step(c.text(R.string.step_disabling, i, targets.size()));
                if (isCore(pkg) || !present.contains(pkg)) continue;
                // Already off before Lehko (and not by Lehko): leave it alone, and don't claim it.
                if (offNow.contains(pkg) && !ours.contains(pkg)) continue;
                if (disablePackage(c, adb, pkg)) turnedOff++;
            }
            writeDeviceState(c, adb);
            String done = turnedOff > 0 ? c.text(R.string.result_on_full, turnedOff) : c.text(R.string.result_on);
            // The settings screen goes behind the new home screen: say it there.
            c.notice(done);
            goHome(adb);
            return done;
        }
    }

    /** Back to the TV's own home screen, with every app Lehko turned off turned back on. */
    static synchronized String disable(Env c, Progress p) throws IOException {
        try (Adb adb = connect(c, p)) {
            Restored r = restore(c, adb, p);
            // Anything short of complete stays on the settings screen, where the message is.
            if (r.homeOk && r.failed.isEmpty()) goHome(adb);
            return r.message(c);
        }
    }

    private static void goHome(Adb adb) throws IOException {
        adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
    }

    static final class Restored {
        boolean homeOk;
        final List<String> failed = new ArrayList<>();

        String message(Env c) {
            String text = homeOk ? (failed.isEmpty() ? c.text(R.string.result_off)
                    : c.text(R.string.result_off_partly, String.join(", ", failed)))
                    : c.text(R.string.result_home_failed);
            if (!homeOk && !failed.isEmpty()) text += " " + c.text(R.string.result_still_off, String.join(", ", failed));
            return text;
        }
    }

    private static Restored restore(Env c, Adb adb, Progress p) throws IOException {
        Restored r = new Restored();
        r.failed.addAll(enableRecorded(c, adb, p));
        if (p != null) p.step(c.text(R.string.step_restoring_home));
        r.homeOk = restoreHome(adb, c.prefs().getString(K_HOME, ""));
        restoreScreensaver(c, adb);
        restoreDebuggingTimeout(c, adb);
        if (r.homeOk) {
            // Only now is Lehko really off. Next time, the then-current launcher is recorded afresh.
            c.prefs().edit().putBoolean(K_ON, false).putBoolean(K_FULL, false).remove(K_HOME).commit();
            Watchdog.clear(c.prefs());
            dropCrashNotice(c);
        }
        writeDeviceState(c, adb);
        return r;
    }

    /** The safety net's "couldn't switch back" notice is stale once the original home screen is back. */
    private static void dropCrashNotice(Env c) {
        if (c.text(R.string.reverted_crash_failed).equals(c.prefs().getString(Prefs.NOTICE, null))) {
            c.prefs().edit().remove(Prefs.NOTICE).commit();
        }
    }

    /** Turns every app Lehko turned off back on. Returns the ones that stay off (and stay recorded). */
    private static List<String> enableRecorded(Env c, Adb adb, Progress p) throws IOException {
        Set<String> disabled = disabled(c);
        List<String> failed = new ArrayList<>();
        int i = 0;
        for (String pkg : sorted(disabled)) {
            i++;
            if (p != null) p.step(c.text(R.string.step_enabling, i, disabled.size()));
            if (!PACKAGE.matcher(pkg).matches()) continue;
            // Decided by pm's own answer, never by a package list that might have come back empty.
            String out = adb.shell("pm enable " + pkg);
            if (out.contains("new state: enabled")) continue;
            if (out.contains("Unknown package") || out.contains("does not exist") || out.contains("not found")) continue;
            failed.add(pkg);
        }
        setDisabled(c, new HashSet<>(failed));
        return failed;
    }

    /**
     * Lehko isn't the home screen, but apps turned off on the "unneeded apps" screen (or left over from a partial
     * "Original") are: turns them all back on, without touching the home screen or the screensaver.
     */
    static synchronized String enableApps(Env c, Progress p) throws IOException {
        try (Adb adb = connect(c, p)) {
            List<String> failed = enableRecorded(c, adb, p);
            writeDeviceState(c, adb);
            return failed.isEmpty() ? c.text(R.string.result_apps_on)
                    : c.text(R.string.result_still_off, String.join(", ", failed));
        }
    }

    static String enableApps(Context c, Progress p) throws IOException {
        return enableApps(env(c), p);
    }

    /**
     * Lehko's home screen kept crashing: go back to the original one. Waits up to 2 minutes for "Allow USB
     * debugging?" in case the TV forgot Lehko's key. Only runs while the watchdog's safe mode is on, so a stale
     * job can't undo a later "turn on". Returns true when the original home screen is back.
     */
    static synchronized boolean revertAfterCrash(Env c, Progress p) {
        Watchdog.lastFailure = 0;
        if (!Watchdog.safeMode(c.prefs())) return true;
        if (!isOn(c)) {
            Watchdog.clear(c.prefs());
            return true;
        }
        try (Adb adb = c.connect(120_000, p == null ? null : approvalOf(p))) {
            adopt(c, adb);
            Restored r = restore(c, adb, p);
            goHome(adb);
            if (r.homeOk) {
                String text = c.text(R.string.reverted_crash);
                if (!r.failed.isEmpty()) text += " " + r.message(c);
                c.notice(text);
                return true;
            }
        } catch (Adb.Failure e) {
            // The debugging port couldn't be reached: the safe screen says what to fix.
            Watchdog.lastFailure = e.reason;
        } catch (IOException e) {
            // Fall through: the safe screen stays and says what to do.
        }
        c.notice(c.text(R.string.reverted_crash_failed));
        return false;
    }

    private static Adb.Approval approvalOf(final Progress p) {
        return new Adb.Approval() {
            @Override public void waiting() {
                p.approval();
            }
        };
    }

    /**
     * After a reboot or an update (of the TV or of Lehko): if Lehko is on, make sure it is still the home screen and
     * that a TV update didn't turn the stock launcher back on. Never pops up a permission window; silent unless it
     * had to fix something or can't check.
     */
    static synchronized void verify(Env c) {
        if (!isOn(c) || Watchdog.safeMode(c.prefs())) return;
        List<String> fixed;
        try (Adb adb = c.connect(0, null)) {
            fixed = check(c, adb);
        } catch (Adb.Failure e) {
            // Debugging on but the port closed: some TVs open it only with "adb tcpip 5555", until the next restart.
            int text = e.reason != Adb.Failure.PORT_CLOSED ? R.string.verify_no_permission
                    : c.debuggingOn() ? R.string.verify_port_closed : R.string.verify_no_debugging;
            c.notice(c.text(text));
            return;
        } catch (IOException e) {
            return;
        }
        if (!fixed.isEmpty()) c.notice(c.text(R.string.verify_fixed, String.join(", ", fixed)));
    }

    /**
     * "Allow debugging again" after the TV forgot Lehko's key: may show "Allow USB debugging?", then makes sure the TV
     * remembers the key (the checks after a reboot never ask) and runs the same check as they do.
     */
    static synchronized String allowAgain(Env c, Progress p) throws IOException {
        // Only for "Allow USB debugging?". The timeout is kept off by check(), once it knows Lehko is still on.
        connect(c, p).close();
        // connect() found it given back from a computer: Lehko isn't the home screen any more.
        if (!isOn(c)) return c.text(R.string.home_status_off);
        List<String> fixed = new ArrayList<>();
        try (Adb adb = c.connect(0, null)) {
            if (!Watchdog.safeMode(c.prefs())) fixed = check(c, adb);
        } catch (Adb.Failure e) {
            // Allowed without "Always allow": the TV has forgotten it already.
            if (e.reason == Adb.Failure.NOT_ALLOWED) return c.text(R.string.allow_not_remembered);
            throw e;
        }
        return fixed.isEmpty() ? c.text(R.string.allow_ok) : c.text(R.string.allow_fixed, String.join(", ", fixed));
    }

    static String allowAgain(Context c, Progress p) throws IOException {
        return allowAgain(env(c), p);
    }

    /** Lehko still home, the stock launcher still off (full mode)? Fixes what isn't and returns what it fixed. */
    private static List<String> check(Env c, Adb adb) throws IOException {
        List<String> fixed = new ArrayList<>();
        adopt(c, adb);
        if (acceptOutsideRevert(c, adb)) return fixed;
        keepDebuggingAllowed(c, adb);
        if (!homeHolder(adb).contains(PKG)) {
            adb.shell("cmd package set-home-activity --user 0 " + HOME);
            fixed.add(c.text(R.string.the_home_screen));
        }
        if (isFull(c)) {
            Set<String> present = packages(adb, ""), off = packages(adb, "-d");
            if (present.isEmpty()) return fixed;
            List<String> todo = new ArrayList<>();
            for (String pkg : targets(adb, c.prefs().getString(K_HOME, ""), present)) {
                if (!off.contains(pkg) && !isCore(pkg)) todo.add(pkg);
            }
            if (!todo.isEmpty()) {
                Set<String> owners = wifiOwners(adb);
                for (String pkg : todo) {
                    if (!owners.contains(pkg) && disablePackage(c, adb, pkg)) fixed.add(pkg);
                }
            }
        }
        if (!fixed.isEmpty()) writeDeviceState(c, adb);
        return fixed;
    }

    /**
     * Lehko's records say on, but the note is gone and the home screen isn't Lehko: given back from a computer
     * (scripts/revert removes the note; a TV update leaves it). Accepts that and returns true.
     */
    private static boolean acceptOutsideRevert(Env c, Adb adb) throws IOException {
        if (!isOn(c) || noteSaysOn(adb) || homeHolder(adb).contains(PKG)) return false;
        forgetSwitch(c, adb);
        return true;
    }

    /** Whether the note on the TV still says Lehko is on. */
    private static boolean noteSaysOn(Adb adb) throws IOException {
        for (String line : adb.shell("cat " + STATE_FILE + " 2>/dev/null").split("\n")) {
            if (line.trim().equals("on=true")) return true;
        }
        return false;
    }

    /** The switch was undone from outside and everything is back already: only Lehko's own records go. */
    private static void forgetSwitch(Env c, Adb adb) throws IOException {
        if (packages(adb, "").isEmpty()) throw new IOException("pm list packages answered nothing");
        Set<String> stillOff = disabled(c);
        stillOff.retainAll(packages(adb, "-d"));
        c.prefs().edit().putBoolean(K_ON, false).putBoolean(K_FULL, false).remove(K_HOME)
                .putBoolean(K_SAVER_CHANGED, false).remove(K_SAVER).remove(K_SAVER_ENABLED)
                .putBoolean(Prefs.SCREENSAVER_LEHKO, false).putBoolean(Prefs.SCREENSAVER_FIRE, false)
                .putBoolean(K_ADB_TIME_CHANGED, false).remove(K_ADB_TIME)
                .putStringSet(K_DISABLED, stillOff).commit();
        Watchdog.clear(c.prefs());
        dropCrashNotice(c);
        writeDeviceState(c, adb);
    }

    // MARK: - Memory

    /**
     * Closes the apps Android keeps cached in the background ("am kill-all": only cached processes, never what is on
     * screen or playing). {@code ask}: the user pressed a button, so "Allow USB debugging?" may appear; otherwise
     * (automatic) it fails quietly instead.
     */
    static synchronized void killAll(Env c, boolean ask, Progress p) throws IOException {
        try (Adb adb = ask ? connect(c, p) : c.connect(0, null)) {
            adb.shell("am kill-all");
        }
    }

    static void killAll(Context c, boolean ask, Progress p) throws IOException {
        killAll(env(c), ask, p);
    }

    /** Remembers how much memory the stock launcher takes, while it is still running. Never fails the switch. */
    private static void measureLauncher(Env c, Adb adb, String home) {
        String pkg = packageOf(home);
        if (pkg == null || pkg.equals(PKG) || !PACKAGE.matcher(pkg).matches()) return;
        try {
            long kb = pssKb(adb.shell("dumpsys meminfo " + pkg));
            if (kb > 0) c.prefs().edit().putLong(K_LAUNCHER_PSS, kb).commit();
        } catch (IOException ignored) {
            // Only a number for the settings screen.
        }
    }

    private static final Pattern TOTAL_PSS = Pattern.compile("TOTAL PSS:\\s+(\\d+)");
    private static final Pattern TOTAL_ROW = Pattern.compile("(?m)^\\s*TOTAL:?\\s+(\\d+)");

    /**
     * Total PSS in KB from "dumpsys meminfo PKG", summed over the package's processes: the "TOTAL PSS:" summary
     * (Android 11+) or the first column of the "TOTAL" row (older). 0 if not running.
     */
    static long pssKb(String dump) {
        long sum = 0;
        Matcher m = TOTAL_PSS.matcher(dump);
        while (m.find()) sum += Long.parseLong(m.group(1));
        if (sum > 0) return sum;
        // Older Android: the table row, and on some versions an "App Summary" line "TOTAL:  N" for the same process.
        for (String section : dump.split("\\*\\* MEMINFO")) {
            Matcher r = TOTAL_ROW.matcher(section);
            if (r.find()) sum += Long.parseLong(r.group(1));
        }
        return sum;
    }

    // MARK: - Screensaver

    static String setScreensaver(Env c, boolean lehko, Progress p) throws IOException {
        return setScreensaver(c, lehko ? SAVER_CLOCK : SAVER_SYSTEM, p);
    }

    /** {@code which}: SAVER_SYSTEM (the TV's own, as it was), SAVER_CLOCK or SAVER_FIRE. */
    static synchronized String setScreensaver(Env c, int which, Progress p) throws IOException {
        boolean lehko = which == SAVER_CLOCK || which == SAVER_FIRE;
        try (Adb adb = connect(c, p)) {
            if (lehko) {
                String current = adb.shell("settings get secure screensaver_components").trim();
                boolean ours = current.contains(PKG + "/");
                // Recorded again whenever the TV's own is set: one picked in the TV's settings since the last record
                // is the one to put back.
                if (!ours || !c.prefs().getBoolean(K_SAVER_CHANGED, false)) {
                    // Lehko's own dream picked in the TV's settings isn't "the TV's own": that is Android's default.
                    if (ours) current = "null";
                    String enabled = adb.shell("settings get secure screensaver_enabled").trim();
                    c.prefs().edit().putString(K_SAVER, SETTING.matcher(current).matches() ? current : "null")
                            .putString(K_SAVER_ENABLED, enabled.matches("-?\\d+") ? enabled : "null")
                            .putBoolean(K_SAVER_CHANGED, true).commit();
                    writeDeviceState(c, adb);
                }
                adb.shell("settings put secure screensaver_components " + (which == SAVER_FIRE ? FIRE_DREAM : DREAM));
                adb.shell("settings put secure screensaver_enabled 1");
            } else {
                restoreScreensaver(c, adb);
            }
            c.prefs().edit().putBoolean(Prefs.SCREENSAVER_LEHKO, lehko).putBoolean(Prefs.SCREENSAVER_FIRE, which == SAVER_FIRE)
                    .commit();
            writeDeviceState(c, adb);
            return c.text(which == SAVER_FIRE ? R.string.result_saver_fire
                    : lehko ? R.string.result_saver_on : R.string.result_saver_off);
        }
    }

    private static void restoreScreensaver(Env c, Adb adb) throws IOException {
        if (!c.prefs().getBoolean(K_SAVER_CHANGED, false)) return;
        // Only while Lehko's is still set: a screensaver picked in the TV's settings since then stays.
        if (adb.shell("settings get secure screensaver_components").trim().startsWith(PKG + "/")) {
            String saver = c.prefs().getString(K_SAVER, "null"), enabled = c.prefs().getString(K_SAVER_ENABLED, "null");
            if (saver.isEmpty() || "null".equals(saver) || !SETTING.matcher(saver).matches()) {
                adb.shell("settings delete secure screensaver_components");
            } else {
                // Turned off on the "unneeded apps" screen while Lehko's was set: a dream that is off shows nothing.
                String pkg = packageOf(saver);
                Set<String> disabled = disabled(c);
                if (disabled.contains(pkg) && PACKAGE.matcher(pkg).matches()
                        && adb.shell("pm enable " + pkg).contains("new state: enabled")) {
                    disabled.remove(pkg);
                    setDisabled(c, disabled);
                }
                adb.shell("settings put secure screensaver_components " + saver);
            }
            // Lehko turned it on; "Turn screen off" chosen since then (0) stays too.
            if ("1".equals(adb.shell("settings get secure screensaver_enabled").trim())) {
                if (enabled.matches("-?\\d+")) adb.shell("settings put secure screensaver_enabled " + enabled);
                else adb.shell("settings delete secure screensaver_enabled");
            }
        }
        c.prefs().edit().putBoolean(K_SAVER_CHANGED, false).remove(K_SAVER).remove(K_SAVER_ENABLED)
                .putBoolean(Prefs.SCREENSAVER_LEHKO, false).putBoolean(Prefs.SCREENSAVER_FIRE, false).commit();
    }

    // MARK: - Debugging permission

    /**
     * Android 11+ forgets "Always allow" after 7 days without a debugging connection, and then the safety net and
     * the checks after TV updates could no longer get in. Same as Developer options > "Disable adb authorization
     * timeout"; put back with "Original".
     */
    private static void keepDebuggingAllowed(Env c, Adb adb) throws IOException {
        if (c.sdk() < 30) return;
        if (!c.prefs().getBoolean(K_ADB_TIME_CHANGED, false)) {
            String current = adb.shell("settings get global adb_allowed_connection_time").trim();
            c.prefs().edit().putString(K_ADB_TIME, current.matches("-?\\d+") ? current : "null")
                    .putBoolean(K_ADB_TIME_CHANGED, true).commit();
            writeDeviceState(c, adb);
        }
        adb.shell("settings put global adb_allowed_connection_time 0");
    }

    private static void restoreDebuggingTimeout(Env c, Adb adb) throws IOException {
        if (!c.prefs().getBoolean(K_ADB_TIME_CHANGED, false)) return;
        String original = c.prefs().getString(K_ADB_TIME, "null");
        if (original.matches("-?\\d+")) adb.shell("settings put global adb_allowed_connection_time " + original);
        else adb.shell("settings delete global adb_allowed_connection_time");
        c.prefs().edit().putBoolean(K_ADB_TIME_CHANGED, false).remove(K_ADB_TIME).commit();
    }

    // MARK: - Turning single apps off and on (the "unneeded apps" screen)

    static synchronized void setAppEnabled(Env c, String pkg, boolean enabled, Progress p) throws IOException {
        if (!PACKAGE.matcher(pkg).matches()) throw new Refused(pkg);
        try (Adb adb = connect(c, p)) {
            if (enabled) {
                String out = adb.shell("pm enable " + pkg);
                if (!out.contains("new state: enabled")) throw new Refused(pkg);
                Set<String> disabled = disabled(c);
                disabled.remove(pkg);
                setDisabled(c, disabled);
            } else {
                if (isProtected(pkg)) throw new Refused(pkg);
                if (wifiOwners(adb).contains(pkg)) throw new WifiOwnerException(pkg);
                if (!disablePackage(c, adb, pkg)) throw new Refused(pkg);
            }
            writeDeviceState(c, adb);
        }
    }

    /** Packages currently disabled on the TV, and the Wi-Fi network owners, for the "unneeded apps" screen. */
    static synchronized Set<String>[] systemState(Env c, Progress p) throws IOException {
        try (Adb adb = connect(c, p)) {
            Set<String> owners;
            try {
                owners = wifiOwners(adb);
            } catch (WifiCheckException e) {
                owners = null;
            }
            @SuppressWarnings("unchecked")
            Set<String>[] out = new Set[]{packages(adb, "-d"), owners};
            return out;
        }
    }

    // MARK: - Helpers (all through adb)

    /**
     * Turns one package off. It is written down (here and on the TV) before the command runs; if the TV then says it
     * isn't off, the note is taken back. Returns true when the package is off.
     */
    private static boolean disablePackage(Env c, Adb adb, String pkg) throws IOException {
        Set<String> d = disabled(c);
        boolean wasRecorded = d.contains(pkg);
        d.add(pkg);
        setDisabled(c, d);
        writeDeviceState(c, adb);
        if (adb.shell("pm disable-user --user 0 " + pkg).contains("disabled-user")) return true;
        if (packages(adb, "-d").contains(pkg)) return true;
        // Not off. Take the note back only if pm is clearly working; if unsure, keep it ("Original" turning a
        // package on that is already on is harmless).
        if (wasRecorded || !packages(adb, "").contains(pkg)) return false;
        d = disabled(c);
        d.remove(pkg);
        setDisabled(c, d);
        writeDeviceState(c, adb);
        return false;
    }

    /** What full mode turns off: the stock launcher, Android TV's recommendations, Google TV's recovery home. */
    private static List<String> targets(Adb adb, String originalHome, Set<String> present) throws IOException {
        List<String> out = new ArrayList<>();
        String homePkg = packageOf(originalHome);
        if (homePkg != null && !homePkg.equals(PKG) && !PROTECTED.contains(homePkg)) out.add(homePkg);
        if (ATV_HOME_PKG.equals(homePkg) && present.contains(ATV_RECOMMENDATIONS)) out.add(ATV_RECOMMENDATIONS);
        if (present.contains(SETUP) && setupIsHome(adb)) out.add(SETUP);
        return out;
    }

    /** The package that is the home screen now, or "". */
    static String homeHolder(Adb adb) throws IOException {
        String holder = adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME").trim();
        if (holder.matches("[\\w.]+(;[\\w.]+)*")) return holder;
        String[] lines = adb.shell("cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME").trim().split("\n");
        String component = lines[lines.length - 1].trim();
        // No default yet: the system's chooser answers ("android/...ResolverActivity").
        return component.contains("/") && !component.startsWith("android/") ? packageOf(component) : "";
    }

    /** The current home screen as "package/activity", or null if unknown (or if it is Lehko). */
    private static String currentHome(Adb adb) throws IOException {
        String holder = homeHolder(adb);
        if (holder.isEmpty() || holder.contains(PKG)) return null;
        String pkg = holder.split(";")[0];
        String component = homeComponentOf(adb, pkg);
        if (component != null) return component;
        return PACKAGE.matcher(pkg).matches() ? pkg : null;
    }

    private static List<String> homeCandidates(Adb adb) throws IOException {
        List<String> out = new ArrayList<>();
        String list = adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME");
        for (String line : list.split("\n")) {
            line = line.trim();
            if (COMPONENT.matcher(line).matches() && !line.contains("FallbackHome")) out.add(line);
        }
        return out;
    }

    private static String homeComponentOf(Adb adb, String pkg) throws IOException {
        for (String c : homeCandidates(adb)) if (c.startsWith(pkg + "/")) return c;
        return null;
    }

    private static boolean setupIsHome(Adb adb) throws IOException {
        return homeComponentOf(adb, SETUP) != null;
    }

    static boolean validHome(String home) {
        return home != null && (COMPONENT.matcher(home).matches() || PACKAGE.matcher(home).matches());
    }

    /** Gives the home screen back: the recorded one, else Google TV's, else another home the TV has. */
    private static boolean restoreHome(Adb adb, String original) throws IOException {
        String home = validHome(original) ? original : "";
        if (home.isEmpty() && adb.shell("pm path " + packageOf(GOOGLE_TV_HOME)).contains("package:")) home = GOOGLE_TV_HOME;
        if (!home.isEmpty()) {
            String pkg = packageOf(home);
            adb.shell("pm enable " + pkg);
            String component = home.contains("/") ? home : homeComponentOf(adb, pkg);
            if (setHome(adb, component)) return true;
            // The recorded activity may have been renamed by an update (or the record was a package only).
            String now = homeComponentOf(adb, pkg);
            if (now != null && !now.equals(component) && setHome(adb, now)) return true;
        }
        // Let the system choose, but only if there is something else to choose: never leave the TV without a home.
        boolean other = false;
        for (String c : homeCandidates(adb)) if (!c.startsWith(PKG + "/")) other = true;
        if (!other) return false;
        adb.shell("cmd role remove-role-holder --user 0 android.app.role.HOME " + PKG);
        return !homeHolder(adb).contains(PKG);
    }

    private static boolean setHome(Adb adb, String component) throws IOException {
        if (component == null || !COMPONENT.matcher(component).matches()) return false;
        if (!adb.shell("cmd package set-home-activity --user 0 " + component).contains("Success")) return false;
        return !homeHolder(adb).contains(PKG);
    }

    static Set<String> packages(Adb adb, String flags) throws IOException {
        Set<String> out = new HashSet<>();
        for (String line : adb.shell("pm list packages " + flags).split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) out.add(line.substring(8));
        }
        return out;
    }

    private static final Pattern WIFI_OWNER = Pattern.compile("(?:cname|creatorName)=([\\w.]+)");
    private static final Pattern SAVED_NETWORK = Pattern.compile("(?m)^\\s*\\d+\\s+\\S");

    /**
     * Apps that saved a Wi-Fi network: turning one off would make Android delete that network. Fails closed: if the
     * TV has saved networks but their owners can't be seen, this throws instead of answering "none".
     */
    static Set<String> wifiOwners(Adb adb) throws IOException {
        String dump = adb.shell("dumpsys wifi | grep -oE '(cname|creatorName)=[A-Za-z0-9_.]+|WifiConfigManager'");
        Set<String> owners = new HashSet<>();
        int withOwner = 0;
        Matcher m = WIFI_OWNER.matcher(dump);
        while (m.find()) {
            owners.add(m.group(1));
            withOwner++;
        }
        String list = adb.shell("cmd wifi list-networks 2>&1");
        int saved = -1;
        if (list.contains("Network Id")) {
            saved = 0;
            Matcher s = SAVED_NETWORK.matcher(list);
            while (s.find()) saved++;
        } else if (list.contains("No networks")) {
            saved = 0;
        }
        if (saved > 0 && withOwner == 0) throw new WifiCheckException();
        if (saved < 0 && !dump.contains("WifiConfigManager")) throw new WifiCheckException();
        owners.remove("android");
        return owners;
    }

    /** What Lehko changed, readable from a computer (adb shell cat /data/local/tmp/lehko_state). */
    private static void writeDeviceState(Env c, Adb adb) {
        SharedPreferences p = c.prefs();
        StringBuilder cmd = new StringBuilder();
        line(cmd, "on=" + p.getBoolean(K_ON, false), ">");
        line(cmd, "full=" + p.getBoolean(K_FULL, false), ">>");
        line(cmd, "home=" + p.getString(K_HOME, ""), ">>");
        line(cmd, "screensaver_changed=" + p.getBoolean(K_SAVER_CHANGED, false), ">>");
        line(cmd, "screensaver=" + p.getString(K_SAVER, ""), ">>");
        line(cmd, "screensaver_enabled=" + p.getString(K_SAVER_ENABLED, ""), ">>");
        line(cmd, "adb_time_changed=" + p.getBoolean(K_ADB_TIME_CHANGED, false), ">>");
        line(cmd, "adb_time=" + p.getString(K_ADB_TIME, ""), ">>");
        line(cmd, "disabled=" + String.join(",", sorted(disabled(c))), ">>");
        try {
            adb.shell(cmd.toString());
        } catch (IOException ignored) {
            // Only a convenience for the computer scripts and reinstalls.
        }
    }

    private static void line(StringBuilder cmd, String text, String redirect) {
        if (cmd.length() > 0) cmd.append("; ");
        cmd.append("echo '").append(text.replace("'", "")).append("' ").append(redirect).append(' ').append(STATE_FILE);
    }

    static String packageOf(String component) {
        if (component == null || component.isEmpty()) return null;
        int slash = component.indexOf('/');
        return slash < 0 ? component : component.substring(0, slash);
    }

    private static List<String> sorted(Set<String> s) {
        List<String> l = new ArrayList<>(s);
        Collections.sort(l);
        return l;
    }
}
