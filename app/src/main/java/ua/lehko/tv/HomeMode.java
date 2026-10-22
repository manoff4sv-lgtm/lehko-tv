package ua.lehko.tv;

import android.content.Context;
import android.content.SharedPreferences;

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
 * What Lehko changed is also written to /data/local/tmp/lehko_state, so scripts/revert can undo it from a computer
 * even if Lehko is gone.
 */
final class HomeMode {
    private HomeMode() {}

    static final String PKG = "ua.lehko.tv";
    static final String HOME = PKG + "/.HomeActivity";
    static final String DREAM = PKG + "/.ClockDream";
    static final String GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx/.home.HomeActivity";
    static final String ATV_HOME_PKG = "com.google.android.tvlauncher";
    static final String ATV_RECOMMENDATIONS = "com.google.android.tvrecommendations";
    /** Google TV's setup app has a RecoveryActivity that becomes the home screen when the launcher is off. */
    static final String SETUP = "com.google.android.tungsten.setupwraith";
    static final String STATE_FILE = "/data/local/tmp/lehko_state";

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

    static boolean isProtected(String pkg) {
        if (PROTECTED.contains(pkg)) return true;
        for (String p : PROTECTED_PREFIXES) if (pkg.startsWith(p)) return true;
        return false;
    }

    // MARK: - State

    static final String K_ON = "mode_on", K_FULL = "mode_full", K_HOME = "mode_original_home",
            K_SAVER = "mode_original_saver", K_SAVER_CHANGED = "mode_saver_changed", K_DISABLED = "mode_disabled";

    static SharedPreferences prefs(Context c) {
        return Prefs.of(c);
    }

    static boolean isOn(Context c) {
        return prefs(c).getBoolean(K_ON, false);
    }

    static boolean isFull(Context c) {
        return prefs(c).getBoolean(K_FULL, false);
    }

    static Set<String> disabled(Context c) {
        return new HashSet<>(prefs(c).getStringSet(K_DISABLED, Collections.<String>emptySet()));
    }

    private static void setDisabled(Context c, Set<String> pkgs) {
        prefs(c).edit().putStringSet(K_DISABLED, new HashSet<>(pkgs)).commit();
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

    static final class Refused extends IOException {
        Refused(String what) {
            super(what);
        }
    }

    // MARK: - Switches

    static Adb connect(Context c, final Progress p) throws IOException {
        if (p != null) p.step(c.getString(R.string.step_connecting));
        return Adb.connect(c, 90_000, p == null ? null : new Adb.Approval() {
            @Override public void waiting() {
                p.approval();
            }
        });
    }

    /** Makes Lehko the home screen; {@code full}: also turns the stock launcher off. Returns a message to show. */
    static synchronized String enable(Context c, boolean full, Progress p) throws IOException {
        try (Adb adb = connect(c, p)) {
            p.step(c.getString(R.string.step_saving));
            SharedPreferences.Editor e = prefs(c).edit();
            String original = prefs(c).getString(K_HOME, "");
            if (original.isEmpty() || original.startsWith(PKG + "/")) {
                String found = currentHome(adb);
                original = found != null ? found : "";
                e.putString(K_HOME, original);
            }
            e.commit();
            Set<String> present = packages(adb, "");
            Set<String> owners = wifiOwners(adb);
            List<String> targets = full ? targets(adb, original, present) : new ArrayList<String>();
            // Turning off an app makes Android forget the Wi-Fi networks it saved. Stop before changing anything.
            for (String t : targets) if (owners.contains(t)) throw new WifiOwnerException(t);

            p.step(c.getString(R.string.step_switching));
            Watchdog.clear(c);
            String out = adb.shell("cmd package set-home-activity --user 0 " + HOME);
            if (!out.contains("Success") && !homeHolder(adb).contains(PKG)) {
                adb.shell("cmd role add-role-holder --user 0 android.app.role.HOME " + PKG);
                if (!homeHolder(adb).contains(PKG)) throw new Refused(out.trim());
            }
            prefs(c).edit().putBoolean(K_ON, true).putBoolean(K_FULL, full).commit();

            Set<String> disabled = disabled(c);
            int i = 0;
            for (String pkg : targets) {
                i++;
                p.step(c.getString(R.string.step_disabling, i, targets.size()));
                if (isProtected(pkg) || !present.contains(pkg)) continue;
                if (adb.shell("pm disable-user --user 0 " + pkg).contains("disabled-user")) {
                    disabled.add(pkg);
                    setDisabled(c, disabled);
                }
            }
            writeDeviceState(c, adb);
            adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
            return full ? c.getString(R.string.result_on_full, disabled.size()) : c.getString(R.string.result_on);
        }
    }

    /** Back to the TV's own home screen, with every app Lehko turned off turned back on. */
    static synchronized String disable(Context c, Progress p) throws IOException {
        try (Adb adb = connect(c, p)) {
            return restore(c, adb, p);
        }
    }

    private static String restore(Context c, Adb adb, Progress p) throws IOException {
        Set<String> disabled = disabled(c);
        Set<String> present = packages(adb, "");
        List<String> failed = new ArrayList<>();
        int i = 0;
        for (String pkg : sorted(disabled)) {
            i++;
            if (p != null) p.step(c.getString(R.string.step_enabling, i, disabled.size()));
            if (!present.contains(pkg)) continue;
            if (!adb.shell("pm enable " + pkg).contains("new state: enabled")) failed.add(pkg);
        }
        setDisabled(c, new HashSet<>(failed));
        if (p != null) p.step(c.getString(R.string.step_restoring_home));
        boolean homeOk = restoreHome(adb, prefs(c).getString(K_HOME, ""));
        restoreScreensaver(c, adb);
        prefs(c).edit().putBoolean(K_ON, false).putBoolean(K_FULL, false).commit();
        writeDeviceState(c, adb);
        adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        if (!homeOk) failed.add(c.getString(R.string.the_home_screen));
        return failed.isEmpty() ? c.getString(R.string.result_off)
                : c.getString(R.string.result_off_partly, android.text.TextUtils.join(", ", failed));
    }

    /** The home screen crashed again and again: go back to the original one, no questions asked. */
    static synchronized void revertAfterCrash(Context c) {
        if (!isOn(c)) return;
        try (Adb adb = Adb.connect(c, 5_000, null)) {
            restore(c, adb, null);
            Prefs.notice(c, c.getString(R.string.reverted_crash));
        } catch (IOException e) {
            Prefs.notice(c, c.getString(R.string.reverted_crash_failed));
        }
    }

    /**
     * After a reboot or an update (of the TV or of Lehko): if Lehko is on, make sure it is still the home screen and
     * that a TV update didn't turn the stock launcher back on. Silent unless it had to fix something.
     */
    static synchronized void verify(Context c) {
        if (!isOn(c)) return;
        List<String> fixed = new ArrayList<>();
        try (Adb adb = Adb.connect(c, 5_000, null)) {
            if (!homeHolder(adb).contains(PKG)) {
                adb.shell("cmd package set-home-activity --user 0 " + HOME);
                fixed.add(c.getString(R.string.the_home_screen));
            }
            if (isFull(c)) {
                Set<String> present = packages(adb, ""), off = packages(adb, "-d"), owners = wifiOwners(adb);
                Set<String> disabled = disabled(c);
                for (String pkg : targets(adb, prefs(c).getString(K_HOME, ""), present)) {
                    if (off.contains(pkg) || isProtected(pkg) || owners.contains(pkg)) continue;
                    if (adb.shell("pm disable-user --user 0 " + pkg).contains("disabled-user")) {
                        disabled.add(pkg);
                        fixed.add(pkg);
                    }
                }
                setDisabled(c, disabled);
            }
            if (!fixed.isEmpty()) writeDeviceState(c, adb);
        } catch (IOException e) {
            return;
        }
        if (!fixed.isEmpty()) Prefs.notice(c, c.getString(R.string.verify_fixed, android.text.TextUtils.join(", ", fixed)));
    }

    // MARK: - Screensaver

    static synchronized String setScreensaver(Context c, boolean lehko, Progress p) throws IOException {
        try (Adb adb = connect(c, p)) {
            if (lehko) {
                if (!prefs(c).getBoolean(K_SAVER_CHANGED, false)) {
                    String current = adb.shell("settings get secure screensaver_components").trim();
                    prefs(c).edit().putString(K_SAVER, current).putBoolean(K_SAVER_CHANGED, true).commit();
                }
                adb.shell("settings put secure screensaver_components " + DREAM);
                adb.shell("settings put secure screensaver_enabled 1");
            } else {
                restoreScreensaver(c, adb);
            }
            prefs(c).edit().putBoolean(Prefs.SCREENSAVER_LEHKO, lehko).commit();
            writeDeviceState(c, adb);
            return c.getString(lehko ? R.string.result_saver_on : R.string.result_saver_off);
        }
    }

    private static void restoreScreensaver(Context c, Adb adb) throws IOException {
        if (!prefs(c).getBoolean(K_SAVER_CHANGED, false)) return;
        String saver = prefs(c).getString(K_SAVER, "");
        if (saver.isEmpty() || "null".equals(saver) || !saver.matches("[\\w.$/,:]+")) {
            adb.shell("settings delete secure screensaver_components");
        } else {
            adb.shell("settings put secure screensaver_components " + saver);
        }
        prefs(c).edit().putBoolean(K_SAVER_CHANGED, false).remove(K_SAVER).putBoolean(Prefs.SCREENSAVER_LEHKO, false).commit();
    }

    // MARK: - Turning single apps off and on (the "unneeded apps" screen)

    static synchronized void setAppEnabled(Context c, String pkg, boolean enabled) throws IOException {
        if (!pkg.matches("[\\w.]+")) throw new Refused(pkg);
        try (Adb adb = Adb.connect(c, 90_000, null)) {
            Set<String> disabled = disabled(c);
            if (enabled) {
                if (!adb.shell("pm enable " + pkg).contains("new state: enabled")) throw new Refused(pkg);
                disabled.remove(pkg);
            } else {
                if (isProtected(pkg)) throw new Refused(pkg);
                if (wifiOwners(adb).contains(pkg)) throw new WifiOwnerException(pkg);
                if (!adb.shell("pm disable-user --user 0 " + pkg).contains("disabled-user")) throw new Refused(pkg);
                disabled.add(pkg);
            }
            setDisabled(c, disabled);
            writeDeviceState(c, adb);
        }
    }

    /** Packages currently disabled on the TV, and the Wi-Fi network owners, for the "unneeded apps" screen. */
    static synchronized Set<String>[] systemState(Context c) throws IOException {
        try (Adb adb = Adb.connect(c, 90_000, null)) {
            @SuppressWarnings("unchecked")
            Set<String>[] out = new Set[]{packages(adb, "-d"), wifiOwners(adb)};
            return out;
        }
    }

    // MARK: - Helpers (all through adb)

    /** What full mode turns off: the stock launcher, Android TV's recommendations, Google TV's recovery home. */
    private static List<String> targets(Adb adb, String originalHome, Set<String> present) throws IOException {
        List<String> out = new ArrayList<>();
        String homePkg = packageOf(originalHome);
        if (homePkg != null && !homePkg.equals(PKG) && !isProtected(homePkg)) out.add(homePkg);
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
        String component = homeComponentOf(adb, holder.split(";")[0]);
        return component != null ? component : holder;
    }

    private static String homeComponentOf(Adb adb, String pkg) throws IOException {
        String list = adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME");
        for (String line : list.split("\n")) {
            line = line.trim();
            if (line.startsWith(pkg + "/")) return line;
        }
        return null;
    }

    private static boolean setupIsHome(Adb adb) throws IOException {
        return adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME").contains(SETUP + "/");
    }

    /** Gives the home screen back: the recorded one, else Google TV's, else whatever the system picks. */
    private static boolean restoreHome(Adb adb, String original) throws IOException {
        String home = original;
        if (home.isEmpty() && adb.shell("pm path " + packageOf(GOOGLE_TV_HOME)).contains("package:")) home = GOOGLE_TV_HOME;
        if (!home.isEmpty()) {
            String pkg = packageOf(home);
            adb.shell("pm enable " + pkg);
            String component = home.contains("/") ? home : homeComponentOf(adb, pkg);
            if (component != null && adb.shell("cmd package set-home-activity --user 0 " + component).contains("Success")) {
                return true;
            }
        }
        adb.shell("cmd role remove-role-holder --user 0 android.app.role.HOME " + PKG);
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

    /** Apps that saved a Wi-Fi network: turning one off would make Android delete that network. */
    static Set<String> wifiOwners(Adb adb) throws IOException {
        Set<String> owners = new HashSet<>();
        Matcher m = WIFI_OWNER.matcher(adb.shell("dumpsys wifi | grep -oE '(cname|creatorName)=[A-Za-z0-9_.]+'"));
        while (m.find()) owners.add(m.group(1));
        owners.remove("android");
        return owners;
    }

    /** What Lehko changed, readable from a computer (adb shell cat /data/local/tmp/lehko_state). */
    private static void writeDeviceState(Context c, Adb adb) {
        SharedPreferences p = prefs(c);
        StringBuilder cmd = new StringBuilder();
        line(cmd, "on=" + p.getBoolean(K_ON, false), ">");
        line(cmd, "full=" + p.getBoolean(K_FULL, false), ">>");
        line(cmd, "home=" + p.getString(K_HOME, ""), ">>");
        line(cmd, "screensaver_changed=" + p.getBoolean(K_SAVER_CHANGED, false), ">>");
        line(cmd, "screensaver=" + p.getString(K_SAVER, ""), ">>");
        line(cmd, "disabled=" + android.text.TextUtils.join(",", sorted(disabled(c))), ">>");
        try {
            adb.shell(cmd.toString());
        } catch (IOException ignored) {
            // Only a convenience for the computer scripts.
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
