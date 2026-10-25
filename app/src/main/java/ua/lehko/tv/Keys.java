package ua.lehko.tv;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The remote buttons Google TV hands to its own launcher (the star, YouTube and "all apps"), and what {@link TvHelper}
 * opens with them instead (and when the TV is turned on). The decisions are pure, so they can be tested; next to them
 * the two facts they depend on, shared with Lehko's settings screen: the stock launcher's state, and the helper's
 * settings in a small file (helper.txt), because the helper runs in a process of its own and SharedPreferences aren't
 * shared between processes.
 */
final class Keys {
    private Keys() {}

    /** KeyEvent.KEYCODE_MACRO_1 (Android 14): the star on Xiaomi's remote. The quick build's android.jar lacks it. */
    static final int STAR = 313;
    static final String LAUNCHERX = "com.google.android.apps.tv.launcherx";
    static final String YOUTUBE = "com.google.android.youtube.tv";
    /** Lehko's All apps screen in a list of what to open: no package name looks like this. */
    static final String ALL_APPS = "all-apps";
    /** The star with nothing chosen (only while the stock launcher is off): the first of these that is installed. */
    static final List<String> DEFAULT_STAR = Collections.unmodifiableList(Arrays.asList(
            "org.ittv.android", "ar.tvplayer.tv", "studio.scillarium.ottnavigator", "ru.iptvremote.android.iptv"));

    /** helper.txt: what the star opens, a package or {@link #STAR_TV}. */
    static final String P_STAR = "star", STAR_TV = "tv";
    /** helper.txt: what opens when the TV is turned on, {@link #WAKE_HOME} (nothing: the default) or "input:<id>". */
    static final String P_WAKE = "wake", WAKE_HOME = "home", WAKE_INPUT = "input:";
    /** Written by the home screen's process only, read by the helper. */
    static final String FILE = "helper.txt";
    /**
     * The helper's own notes, written by the helper only (each file has one writing process, so a write never loses
     * another's): what it has shown, and the sleep timer it ended. The home screen reads the last.
     */
    static final String STATE = "helper_state.txt";

    /** One of the buttons the helper looks at. Gamepads have a BUTTON_3 too: theirs, like every other key, go on. */
    static boolean handled(int keyCode, int source) {
        boolean ours = keyCode == STAR || keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == KeyEvent.KEYCODE_ALL_APPS;
        return ours && (source & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
                && (source & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK;
    }

    /**
     * What a press opens, best first: the first one that can be opened wins. Empty: the key goes on and the TV does
     * what it did before. {@code star}: the choice in helper.txt (null: none); {@code launcherOff}: the stock launcher
     * is installed and turned off, so nothing else answers these buttons.
     */
    static List<String> opens(int keyCode, String star, boolean launcherOff) {
        if (keyCode == STAR) {
            if (STAR_TV.equals(star)) return Collections.emptyList();
            if (star != null && !star.isEmpty()) return Collections.singletonList(star);
            return launcherOff ? DEFAULT_STAR : Collections.<String>emptyList();
        }
        if (!launcherOff) return Collections.emptyList();
        if (keyCode == KeyEvent.KEYCODE_BUTTON_3) return Collections.singletonList(YOUTUBE);
        if (keyCode == KeyEvent.KEYCODE_ALL_APPS) return Collections.singletonList(ALL_APPS);
        return Collections.emptyList();
    }

    /** The input the "TV turned on" choice names, if the TV still has it; null for the home screen. */
    static Inputs.Input wakeInput(String wake, List<Inputs.Input> inputs) {
        if (wake == null || !wake.startsWith(WAKE_INPUT)) return null;
        String id = wake.substring(WAKE_INPUT.length());
        for (Inputs.Input in : inputs) if (in.id.equals(id)) return in;
        return null;
    }

    /** Google TV's launcher, or null when it isn't installed; its {@code enabled} says whether it is on. */
    static ApplicationInfo launcher(PackageManager pm) {
        try {
            return pm.getApplicationInfo(LAUNCHERX, PackageManager.MATCH_DISABLED_COMPONENTS);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Google TV's launcher is installed and turned off (Lehko's full mode): nothing else answers these buttons. Not
     * installed counts as on: other TVs answer them their own way. Asked at each press, as it can change any time.
     */
    static boolean launcherOff(PackageManager pm) {
        ApplicationInfo launcher = launcher(pm);
        return launcher != null && !launcher.enabled;
    }

    // MARK: - helper.txt: "key=value" lines

    static Map<String, String> parse(String text) {
        Map<String, String> out = new TreeMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return out;
    }

    static String format(Map<String, String> settings) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : settings.entrySet()) {
            b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return b.toString();
    }

    /** The helper's settings in {@code dir} (the app's files folder); empty when there are none yet. */
    static Map<String, String> read(File dir) {
        return read(dir, FILE);
    }

    /** {@link #FILE} or {@link #STATE} in {@code dir}; empty when there is none yet, or it can't be read. */
    static Map<String, String> read(File dir, String name) {
        try {
            return load(new File(dir, name));
        } catch (IOException e) {
            return new TreeMap<>();
        }
    }

    /** Empty when the file isn't there yet; one that is there and can't be read is an error, not "nothing set". */
    private static Map<String, String> load(File f) throws IOException {
        if (!f.exists()) return new TreeMap<>();
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            for (int n; (n = in.read(buf)) > 0; ) bytes.write(buf, 0, n);
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    /** Sets one setting ({@code value} null: removes it). */
    static void put(File dir, String key, String value) throws IOException {
        putAll(dir, FILE, Collections.singletonMap(key, value));
    }

    /**
     * Sets several values in {@code name} at once (a null value removes its key); a file that would stay the same isn't
     * touched, so the helper doesn't read it again for nothing. Written next to the file and renamed over it, so the
     * other process, reading at any moment, gets the old file or the new one, never half of one. Synchronized: a
     * process writes from more than one thread (the settings screen, downloads). A file it can't read isn't written:
     * the keys it doesn't change would be lost.
     */
    static synchronized void putAll(File dir, String name, Map<String, String> changes) throws IOException {
        Map<String, String> before = load(new File(dir, name)), settings = new TreeMap<>(before);
        for (Map.Entry<String, String> e : changes.entrySet()) {
            if (e.getValue() == null) settings.remove(e.getKey());
            else settings.put(e.getKey(), e.getValue());
        }
        if (settings.equals(before)) return;
        File tmp = new File(dir, name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(format(settings).getBytes(StandardCharsets.UTF_8));
            // On the disk before the rename: a power cut (an outage) right after must not leave an empty file.
            out.getFD().sync();
        }
        if (!tmp.renameTo(new File(dir, name))) throw new IOException("rename " + tmp);
    }

    /** A number in the helper's files: an id or a moment (epoch ms); 0 when there is none. */
    static long number(Map<String, String> settings, String key) {
        try {
            return Long.parseLong(settings.get(key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
