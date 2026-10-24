package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.InputDevice;
import android.view.KeyEvent;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class KeysTest {
    private static final int REMOTE = InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_DPAD;
    /** KeyEvent.KEYCODE_ALL_APPS (API 28): tools/quick-test.sh may compile the tests against an older android.jar. */
    private static final int ALL_APPS_KEY = 284;
    private static final List<String> NOTHING = Collections.emptyList();

    @Test public void onlyTheRemotesThreeButtons() {
        assertTrue(Keys.handled(Keys.STAR, REMOTE));
        assertTrue(Keys.handled(KeyEvent.KEYCODE_BUTTON_3, REMOTE));
        assertTrue(Keys.handled(ALL_APPS_KEY, REMOTE));
        assertFalse(Keys.handled(KeyEvent.KEYCODE_DPAD_CENTER, REMOTE));
        assertFalse("Netflix works without the launcher", Keys.handled(KeyEvent.KEYCODE_BUTTON_6, REMOTE));
        assertFalse("a gamepad's is a game's", Keys.handled(KeyEvent.KEYCODE_BUTTON_3, InputDevice.SOURCE_GAMEPAD));
        assertFalse(Keys.handled(KeyEvent.KEYCODE_BUTTON_3, InputDevice.SOURCE_JOYSTICK | InputDevice.SOURCE_GAMEPAD));
    }

    @Test public void star() {
        String ittv = "org.ittv.android", kodi = "org.xbmc.kodi";
        assertEquals("chosen: always", Arrays.asList(kodi), Keys.opens(Keys.STAR, kodi, false));
        assertEquals(Arrays.asList(kodi), Keys.opens(Keys.STAR, kodi, true));
        assertEquals("as the TV does", NOTHING, Keys.opens(Keys.STAR, Keys.STAR_TV, true));
        assertEquals("nothing chosen, the launcher answers it", NOTHING, Keys.opens(Keys.STAR, null, false));
        List<String> defaults = Keys.opens(Keys.STAR, null, true);
        assertEquals("nothing chosen, the launcher is off", ittv, defaults.get(0));
        assertEquals(Keys.DEFAULT_STAR, defaults);
        assertEquals(Keys.DEFAULT_STAR, Keys.opens(Keys.STAR, "", true));
    }

    @Test public void youTubeAndAllAppsOnlyWhileTheLauncherIsOff() {
        assertEquals(Arrays.asList(Keys.YOUTUBE), Keys.opens(KeyEvent.KEYCODE_BUTTON_3, null, true));
        assertEquals(NOTHING, Keys.opens(KeyEvent.KEYCODE_BUTTON_3, null, false));
        assertEquals(Arrays.asList(Keys.ALL_APPS), Keys.opens(ALL_APPS_KEY, "org.xbmc.kodi", true));
        assertEquals(NOTHING, Keys.opens(ALL_APPS_KEY, null, false));
        assertEquals(NOTHING, Keys.opens(KeyEvent.KEYCODE_DPAD_CENTER, null, true));
    }

    @Test public void settingsFile() throws Exception {
        File dir = Files.createTempDirectory("lehko-files").toFile();
        assertTrue("none yet", Keys.read(dir).isEmpty());
        Keys.put(dir, Keys.P_STAR, "org.ittv.android");
        Keys.put(dir, "later", "1");
        assertEquals("org.ittv.android", Keys.read(dir).get(Keys.P_STAR));
        Keys.put(dir, Keys.P_STAR, Keys.STAR_TV);
        Map<String, String> s = Keys.read(dir);
        assertEquals(Keys.STAR_TV, s.get(Keys.P_STAR));
        assertEquals("other keys stay", "1", s.get("later"));
        Keys.put(dir, "later", null);
        assertEquals("star=tv\n", new String(Files.readAllBytes(new File(dir, Keys.FILE).toPath()), "UTF-8"));
        assertFalse("no temp file left", new File(dir, Keys.FILE + ".tmp").exists());
    }

    @Test public void parseAndFormat() {
        Map<String, String> m = Keys.parse("star = org.ittv.android\n\nbroken line\n=nokey\nx=a=b\r\n");
        Map<String, String> want = new TreeMap<>();
        want.put("star", "org.ittv.android");
        want.put("x", "a=b");
        assertEquals(want, m);
        assertEquals(want, Keys.parse(Keys.format(want)));
    }
}
