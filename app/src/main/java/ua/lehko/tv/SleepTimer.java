package ua.lehko.tv;

import android.content.Context;
import android.view.KeyEvent;

import java.io.IOException;
import java.util.Map;

/**
 * The sleep timer: the home screen's button writes when the TV should go to sleep into helper.txt, and
 * {@link TvHelper} warns a minute before (any button keeps the TV on, and that press does nothing else) and then puts
 * it to sleep. The helper writes the timer it ended, slept or kept on, into its own file, so the home screen stops
 * showing it.
 */
final class SleepTimer {
    private SleepTimer() {}

    /** helper.txt: the moment (epoch ms), 0 for none. The helper's notes: the moment of the timer it ended. */
    static final String P_AT = "sleep", P_DONE = "sleep_done";
    /** The choices, in minutes (0: off). */
    static final int[] CHOICES = {0, 15, 30, 60, 90};
    /** The warning card comes this long before. */
    static final long CARD_MS = 60_000;
    /** Later than this (the TV was restarted meanwhile), the timer is forgotten. */
    static final long LATE_MS = 2 * 60_000L;

    /** What the helper does at a moment: nothing, wait for the card, show it, or put the TV to sleep. */
    static final int NONE = 0, WAIT = 1, CARD = 2, SLEEP = 3;

    /**
     * A key that still goes on to the app in front when it answers the warning: volume and mute change nothing on
     * screen, and the power keys are the TV's (they don't even reach the helper). Any other press only keeps the TV on:
     * in a player, OK would pause the video and Back leave it.
     */
    static boolean passes(int key) {
        return key == KeyEvent.KEYCODE_VOLUME_UP || key == KeyEvent.KEYCODE_VOLUME_DOWN
                || key == KeyEvent.KEYCODE_VOLUME_MUTE || key == KeyEvent.KEYCODE_MUTE
                || key == KeyEvent.KEYCODE_POWER || key == KeyEvent.KEYCODE_TV_POWER
                || key == KeyEvent.KEYCODE_SLEEP || key == KeyEvent.KEYCODE_WAKEUP;
    }

    static int step(long at, long done, long now) {
        if (at <= 0 || at == done || now > at + LATE_MS) return NONE;
        if (now >= at) return SLEEP;
        return now >= at - CARD_MS ? CARD : WAIT;
    }

    /** Set and still ahead, and not ended by the helper (a button pressed in the last minute, the TV turned off). */
    static boolean active(long at, long done, long now) {
        return at > now && at != done;
    }

    /** Main process: when the TV goes to sleep, or 0 when the timer isn't set. */
    static long at(Context c) {
        long at = Keys.number(Keys.read(c.getFilesDir()), P_AT);
        Map<String, String> state = Keys.read(c.getFilesDir(), Keys.STATE);
        return active(at, Keys.number(state, P_DONE), System.currentTimeMillis()) ? at : 0;
    }

    /** Sets the timer {@code minutes} from now; 0 turns it off. */
    static void set(Context c, int minutes) throws IOException {
        long at = minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : 0;
        Keys.put(c.getFilesDir(), P_AT, String.valueOf(at));
    }
}
