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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The remote buttons Google TV hands to its own launcher (the star, YouTube and "all apps"), and what {@link TvHelper}
 * opens with them instead. The decisions are pure, so they can be tested; next to them the two facts they depend on,
 * shared with Lehko's settings screen: the stock launcher's state, and the helper's settings in a small file
 * (helper.txt), because the helper runs in a process of its own and SharedPreferences aren't shared between processes.
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
    static final String FILE = "helper.txt";

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
        try (InputStream in = new FileInputStream(new File(dir, FILE))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            for (int n; (n = in.read(buf)) > 0; ) bytes.write(buf, 0, n);
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new TreeMap<>();
        }
    }

    /**
     * Sets one setting ({@code value} null: removes it). Written next to the file and renamed over it, so the helper,
     * reading at any moment, gets the old file or the new one, never half of one.
     */
    static void put(File dir, String key, String value) throws IOException {
        Map<String, String> settings = read(dir);
        if (value == null) settings.remove(key);
        else settings.put(key, value);
        File tmp = new File(dir, FILE + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(format(settings).getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(new File(dir, FILE))) throw new IOException("rename " + tmp);
    }
}
