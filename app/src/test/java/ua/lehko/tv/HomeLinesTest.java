package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The home screen's short power lines, the shortcut badges and the focus glow. */
public class HomeLinesTest {
    private static Outages.Status st(byte state, int minutes, String at, byte next, boolean tomorrow) {
        Outages.Status s = new Outages.Status();
        s.state = state;
        s.minutesToChange = minutes;
        s.changeAt = at;
        s.next = next;
        s.changeTomorrow = tomorrow;
        return s;
    }

    @Test public void powerOnUntilAnOutage() {
        OutageText.Headline h = OutageText.headline(new Outages.Schedule(), st(Outages.ON, 138, "22:00", Outages.OFF, false));
        assertEquals(R.string.outage_on, h.title);
        assertEquals("22:00", h.until);
        assertEquals(R.string.outage_in, h.detail);
        assertEquals(138, h.detailMinutes);
        h = OutageText.headline(new Outages.Schedule(), st(Outages.ON, 60, "21:00", Outages.MAYBE, false));
        assertEquals(R.string.outage_maybe_in, h.detail);
    }

    @Test public void powerOnUntilTomorrow() {
        OutageText.Headline h = OutageText.headline(new Outages.Schedule(), st(Outages.ON, 600, "06:00", Outages.OFF, true));
        assertNull("no 'until' for a time tomorrow", h.until);
        assertEquals(R.string.outage_next_tomorrow, h.detail);
        assertEquals("06:00", h.detailTime);
    }

    @Test public void noPower() {
        OutageText.Headline h = OutageText.headline(new Outages.Schedule(), st(Outages.OFF, 65, "23:00", Outages.ON, false));
        assertEquals(R.string.outage_title_off, h.title);
        assertEquals("23:00", h.until);
        assertEquals(R.string.outage_back_in, h.detail);
        h = OutageText.headline(new Outages.Schedule(), st(Outages.OFF, -1, null, Outages.UNKNOWN, false));
        assertEquals(R.string.outage_no_end, h.detail);
        assertNull(h.until);
    }

    @Test public void clearDaysEmergencyAndNothingYet() {
        Outages.Status clear = st(Outages.ON, -1, null, Outages.UNKNOWN, false);
        clear.clearAhead = true;
        assertEquals(R.string.outage_none_ahead, OutageText.headline(new Outages.Schedule(), clear).detail);
        clear.noSchedules = true;
        assertEquals(R.string.outage_none_planned_lc, OutageText.headline(new Outages.Schedule(), clear).detail);
        Outages.Status emergency = st(Outages.UNKNOWN, -1, null, Outages.UNKNOWN, false);
        emergency.emergency = true;
        assertEquals(R.string.outage_emergency_title, OutageText.headline(new Outages.Schedule(), emergency).title);
        assertEquals(R.string.outage_loading, OutageText.headline(null, clear).title);
        assertEquals(R.string.outage_no_schedule,
                OutageText.headline(new Outages.Schedule(), st(Outages.UNKNOWN, -1, null, Outages.UNKNOWN, false)).title);
    }

    /** Offline after midnight with the next day's schedule cached: yesterday's emergency doesn't stick. */
    @Test public void emergencyEndsWithItsDay() {
        Outages.Schedule s = new Outages.Schedule();
        Outages.Day today = new Outages.Day("2026-10-22", Outages.UNKNOWN);
        today.emergency = true;
        s.days.add(today);
        s.days.add(new Outages.Day("2026-10-23", Outages.ON));
        long evening = OutagesTest.kyiv(2026, 10, 22, 20, 0), morning = OutagesTest.kyiv(2026, 10, 23, 9, 0);
        assertEquals(R.string.outage_emergency_title, OutageText.headline(s, Outages.status(s, evening)).title);
        assertEquals(R.string.outage_on, OutageText.headline(s, Outages.status(s, morning)).title);
    }

    @Test public void badges() {
        assertEquals("1", IconButton.badgeOf("HDMI 1"));
        assertEquals("2", IconButton.badgeOf("HDMI 2"));
        assertEquals("", IconButton.badgeOf("Live TV"));
        assertEquals("", IconButton.badgeOf("Памʼять · вільно 412 МБ"));
        assertEquals("", IconButton.badgeOf("Port 2026"));
        assertEquals("", IconButton.badgeOf("4K"));
        assertEquals("", IconButton.badgeOf(""));
    }

    @Test public void glowIsBrightButKeepsTheHue() {
        int g = Ambient.glow(0xFF4A1F1F);
        float[] hsv = Ambient.hsv(g);
        assertEquals(0, hsv[0], 4);
        assertTrue(hsv[2] > 0.9f);
        assertTrue(hsv[1] <= 0.71f);
    }
}
