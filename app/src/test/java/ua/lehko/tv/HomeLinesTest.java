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
        Outages.Schedule none = new Outages.Schedule();
        none.noSchedules = true;
        assertEquals(R.string.outage_none_planned_lc, OutageText.headline(none, clear).detail);
        assertEquals(R.string.outage_none_ahead, OutageText.headline(new Outages.Schedule(), clear).detail);
        Outages.Schedule emergency = new Outages.Schedule();
        emergency.emergency = true;
        assertEquals(R.string.outage_emergency_title, OutageText.headline(emergency, clear).title);
        assertEquals(R.string.outage_loading, OutageText.headline(null, clear).title);
        assertEquals(R.string.outage_no_schedule,
                OutageText.headline(new Outages.Schedule(), st(Outages.UNKNOWN, -1, null, Outages.UNKNOWN, false)).title);
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
