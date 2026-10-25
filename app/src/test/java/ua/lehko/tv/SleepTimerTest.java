package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/** The sleep timer: what the helper does when, and what the home screen shows. */
public class SleepTimerTest {
    private static final long AT = 1_792_700_000_000L;

    @Test public void aMinutesWarningThenSleep() {
        assertEquals(SleepTimer.WAIT, SleepTimer.step(AT, 0, AT - 15 * 60_000L));
        assertEquals(SleepTimer.WAIT, SleepTimer.step(AT, 0, AT - SleepTimer.CARD_MS - 1));
        assertEquals(SleepTimer.CARD, SleepTimer.step(AT, 0, AT - SleepTimer.CARD_MS));
        assertEquals(SleepTimer.CARD, SleepTimer.step(AT, 0, AT - 1));
        assertEquals(SleepTimer.SLEEP, SleepTimer.step(AT, 0, AT));
        assertEquals("the helper was restarted just then", SleepTimer.SLEEP, SleepTimer.step(AT, 0, AT + 90_000));
    }

    @Test public void endedOffOrForgotten() {
        assertEquals("kept on, or slept", SleepTimer.NONE, SleepTimer.step(AT, AT, AT - 30_000));
        assertEquals("turned off", SleepTimer.NONE, SleepTimer.step(0, 0, AT));
        assertEquals("the TV restarted meanwhile", SleepTimer.NONE, SleepTimer.step(AT, 0, AT + SleepTimer.LATE_MS + 1));
        // A new timer after an ended one.
        assertEquals(SleepTimer.WAIT, SleepTimer.step(AT + 3600_000L, AT, AT));
    }

    @Test public void theHomeScreenShowsItWhileItRuns() {
        assertTrue(SleepTimer.active(AT, 0, AT - 60_000));
        assertFalse("ended by the helper", SleepTimer.active(AT, AT, AT - 60_000));
        assertFalse("over", SleepTimer.active(AT, 0, AT));
        assertFalse(SleepTimer.active(0, 0, AT));
    }

    @Test public void theAnswerToTheCardStaysOffThePlayer() {
        assertFalse("OK would pause the video", SleepTimer.passes(KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse("Back would leave it", SleepTimer.passes(KeyEvent.KEYCODE_BACK));
        assertFalse(SleepTimer.passes(KeyEvent.KEYCODE_DPAD_LEFT));
        assertFalse(SleepTimer.passes(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        assertTrue("changes nothing on screen", SleepTimer.passes(KeyEvent.KEYCODE_VOLUME_UP));
        assertTrue(SleepTimer.passes(KeyEvent.KEYCODE_VOLUME_MUTE));
    }
}
