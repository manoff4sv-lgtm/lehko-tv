package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class OutagesTest {
    /** Real YASNO answer for Kyiv on 2026-10-22 (two of 60 groups). */
    static final String YASNO = "{"
            + "\"1.1\":{\"today\":{\"slots\":[{\"start\":0,\"end\":30,\"type\":\"Definite\"},"
            + "{\"start\":30,\"end\":750,\"type\":\"NotPlanned\"},{\"start\":750,\"end\":1020,\"type\":\"Definite\"},"
            + "{\"start\":1020,\"end\":1440,\"type\":\"NotPlanned\"}],\"date\":\"2026-10-22T00:00:00+03:00\","
            + "\"status\":\"ScheduleApplies\"},\"tomorrow\":{\"slots\":[],\"date\":\"2026-10-23T00:00:00+03:00\","
            + "\"status\":\"WaitingForSchedule\"},\"updatedOn\":\"2026-10-22T06:45:01+00:00\"},"
            + "\"2.1\":{\"today\":{\"slots\":[{\"start\":0,\"end\":30,\"type\":\"Definite\"},"
            + "{\"start\":30,\"end\":840,\"type\":\"NotPlanned\"},{\"start\":840,\"end\":1050,\"type\":\"Definite\"},"
            + "{\"start\":1050,\"end\":1350,\"type\":\"NotPlanned\"},{\"start\":1350,\"end\":1440,\"type\":\"Definite\"}],"
            + "\"date\":\"2026-10-22T00:00:00+03:00\",\"status\":\"ScheduleApplies\"},"
            + "\"tomorrow\":{\"slots\":[],\"date\":\"2026-10-23T00:00:00+03:00\",\"status\":\"WaitingForSchedule\"},"
            + "\"updatedOn\":\"2026-10-22T06:45:01+00:00\"}}";

    static long kyiv(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(Outages.KYIV);
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    @Test public void yasnoSlots() throws Exception {
        Outages.Schedule s = Outages.parseYasno(YASNO, "1.1", "2026-10-22", "2026-10-23");
        assertNotNull(s);
        assertFalse(s.emergency);
        assertFalse(s.noSchedules);
        Outages.Day d = s.day("2026-10-22");
        assertNotNull(d);
        assertNull("tomorrow is not published yet", s.day("2026-10-23"));
        assertEquals(Outages.OFF, d.slots[0]);
        for (int k = 1; k <= 24; k++) assertEquals("slot " + k, Outages.ON, d.slots[k]);
        for (int k = 25; k <= 33; k++) assertEquals("slot " + k, Outages.OFF, d.slots[k]);
        for (int k = 34; k < 48; k++) assertEquals("slot " + k, Outages.ON, d.slots[k]);
    }

    @Test public void yasnoUnknownGroupOrOtherDay() throws Exception {
        assertNull(Outages.parseYasno(YASNO, "60.1", "2026-10-22", "2026-10-23"));
        Outages.Schedule stale = Outages.parseYasno(YASNO, "1.1", "2026-10-23", "2026-10-24");
        assertNull("yesterday's 'today' must not be shown as today", stale.day("2026-10-23"));
        assertNull(stale.day("2026-10-22"));
    }

    @Test public void statusBeforeDuringAfter() throws Exception {
        Outages.Schedule s = Outages.parseYasno(YASNO, "1.1", "2026-10-22", "2026-10-23");
        Outages.Status st = Outages.status(s, kyiv(2026, 10, 22, 10, 0));
        assertEquals(Outages.ON, st.state);
        assertEquals(Outages.OFF, st.next);
        assertEquals("12:30", st.changeAt);
        assertEquals(150, st.minutesToChange);
        assertFalse(st.changeTomorrow);

        st = Outages.status(s, kyiv(2026, 10, 22, 13, 5));
        assertEquals(Outages.OFF, st.state);
        assertEquals("17:00", st.changeAt);
        assertEquals(235, st.minutesToChange);

        st = Outages.status(s, kyiv(2026, 10, 22, 20, 0));
        assertEquals(Outages.ON, st.state);
        assertTrue("nothing more today and tomorrow unknown", st.clearAhead);
        assertTrue(st.untilEndOfData);
    }

    @Test public void statusLooksIntoTomorrow() {
        Outages.Schedule s = new Outages.Schedule();
        Outages.Day today = new Outages.Day("2026-10-22", Outages.ON);
        Outages.Day tomorrow = new Outages.Day("2026-10-23", Outages.ON);
        tomorrow.slots[12] = Outages.OFF; // 06:00
        s.days.add(today);
        s.days.add(tomorrow);
        Outages.Status st = Outages.status(s, kyiv(2026, 10, 22, 22, 0));
        assertEquals(Outages.ON, st.state);
        assertTrue(st.changeTomorrow);
        assertEquals("06:00", st.changeAt);
        assertEquals(8 * 60, st.minutesToChange);

        // Off until midnight, then on: "24:00" today, not tomorrow.
        today.slots[46] = Outages.OFF;
        today.slots[47] = Outages.OFF;
        st = Outages.status(s, kyiv(2026, 10, 22, 23, 10));
        assertEquals(Outages.OFF, st.state);
        assertEquals("24:00", st.changeAt);
        assertFalse(st.changeTomorrow);
        assertEquals(50, st.minutesToChange);
    }

    @Test public void yasnoStatuses() throws Exception {
        String none = "{\"5.1\":{\"today\":{\"slots\":[],\"date\":\"2026-10-22T00:00:00+03:00\",\"status\":\"NoOutages\"}}}";
        Outages.Schedule s = Outages.parseYasno(none, "5.1", "2026-10-22", "2026-10-23");
        assertTrue(s.noSchedules);
        Outages.Status st = Outages.status(s, kyiv(2026, 10, 22, 12, 0));
        assertEquals(Outages.ON, st.state);
        assertTrue(st.clearAhead);

        String emergency = "{\"5.1\":{\"today\":{\"slots\":[],\"date\":\"2026-10-22T00:00:00+03:00\",\"status\":\"EmergencyShutdowns\"}}}";
        s = Outages.parseYasno(emergency, "5.1", "2026-10-22", "2026-10-23");
        assertTrue(s.emergency);
        assertEquals(Outages.UNKNOWN, Outages.status(s, kyiv(2026, 10, 22, 12, 0)).state);

        String waiting = "{\"5.1\":{\"today\":{\"slots\":[],\"date\":\"2026-10-22T00:00:00+03:00\",\"status\":\"WaitingForSchedule\"}}}";
        s = Outages.parseYasno(waiting, "5.1", "2026-10-22", "2026-10-23");
        assertFalse("waiting is not 'no outages'", s.noSchedules);
        assertEquals(Outages.UNKNOWN, Outages.status(s, kyiv(2026, 10, 22, 12, 0)).state);
    }

    @Test public void mirrorHours() throws Exception {
        long today = Outages.epoch("2026-10-21"), tomorrow = Outages.epoch("2026-10-22");
        assertEquals("from the mirror's own fact.today", 1792530000L, today);
        String json = "{\"fact\":{\"today\":" + today + ",\"data\":{\"" + today + "\":{\"GPV1.1\":{"
                + "\"1\":\"yes\",\"2\":\"yes\",\"3\":\"yes\",\"4\":\"no\",\"5\":\"no\",\"6\":\"no\",\"7\":\"first\","
                + "\"8\":\"yes\",\"9\":\"yes\",\"10\":\"yes\",\"11\":\"yes\",\"12\":\"yes\",\"13\":\"no\",\"14\":\"no\","
                + "\"15\":\"no\",\"16\":\"first\",\"17\":\"yes\",\"18\":\"yes\",\"19\":\"second\",\"20\":\"mfirst\","
                + "\"21\":\"msecond\",\"22\":\"maybe\",\"23\":\"no\",\"24\":\"no\"}}}}}";
        Outages.Schedule s = Outages.parseMirror(json, "1.1", today, tomorrow);
        Outages.Day d = s.day("2026-10-21");
        assertNotNull(d);
        assertNull(s.day("2026-10-22"));
        assertEquals(Outages.ON, d.slots[0]);
        assertEquals(Outages.OFF, d.slots[6]);   // 03:00
        assertEquals(Outages.OFF, d.slots[12]);  // "first": 06:00-06:30 off
        assertEquals(Outages.ON, d.slots[13]);   // 06:30-07:00 on
        assertEquals(Outages.ON, d.slots[36]);   // "second": 18:00-18:30 on
        assertEquals(Outages.OFF, d.slots[37]);  // 18:30-19:00 off
        assertEquals(Outages.MAYBE, d.slots[38]);
        assertEquals(Outages.ON, d.slots[39]);
        assertEquals(Outages.ON, d.slots[40]);
        assertEquals(Outages.MAYBE, d.slots[41]);
        assertEquals(Outages.MAYBE, d.slots[42]);
        assertEquals(Outages.MAYBE, d.slots[43]);
        assertEquals(Outages.OFF, d.slots[47]);
    }

    @Test public void mirrorWithoutSchedules() throws Exception {
        Outages.Schedule s = Outages.parseMirror("{\"fact\":{\"today\":1792530000,\"data\":[]}}", "1.1", 1792530000L, 1792616400L);
        assertTrue(s.noSchedules);
        assertTrue(Outages.status(s, kyiv(2026, 10, 21, 12, 0)).clearAhead);
    }

    @Test public void daylightSavingDay() {
        // Clocks go back on 2026-10-25: that day is 25 hours long.
        assertEquals(25 * 3600, Outages.epoch("2026-10-26") - Outages.epoch("2026-10-25"));
        assertEquals("2026-10-25", Outages.dateOf(Outages.epoch("2026-10-25")));
    }

    @Test public void groupsSortNumerically() {
        List<String> g = new ArrayList<>(Arrays.asList("10.1", "1.2", "60.1", "2.1", "1.1"));
        Outages.sortGroups(g);
        assertEquals(Arrays.asList("1.1", "1.2", "2.1", "10.1", "60.1"), g);
    }

    @Test public void cacheRoundTrip() throws Exception {
        Outages.Schedule s = Outages.parseYasno(YASNO, "2.1", "2026-10-22", "2026-10-23");
        JSONObject o = Outages.toJson(s);
        Outages.Schedule back = Outages.fromJson(new JSONObject(o.toString()));
        assertEquals("2.1", back.group);
        assertEquals(s.days.size(), back.days.size());
        assertTrue(Arrays.equals(s.day("2026-10-22").slots, back.day("2026-10-22").slots));
    }
}
