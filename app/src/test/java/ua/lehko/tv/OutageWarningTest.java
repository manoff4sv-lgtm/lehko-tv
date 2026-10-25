package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The outage warning the helper shows over other apps, and the files it reads and writes. */
public class OutageWarningTest {
    private static long kyiv(int y, int mo, int d, int h, int mi) {
        return OutagesTest.kyiv(y, mo, d, h, mi);
    }

    private static Outages.Schedule schedule(Outages.Day... days) {
        Outages.Schedule s = new Outages.Schedule();
        s.days.addAll(Arrays.asList(days));
        return s;
    }

    private static void fill(Outages.Day d, int from, int to, byte state) {
        for (int k = from; k < to; k++) d.slots[k] = state;
    }

    @Test public void outagesAhead() {
        Outages.Day today = new Outages.Day("2026-10-22", Outages.ON);
        fill(today, 16, 24, Outages.OFF); // 08:00 to 12:00, over by then
        fill(today, 36, 42, Outages.OFF); // 18:00 to 21:00
        fill(today, 42, 44, Outages.MAYBE); // possible right after: no news
        fill(today, 46, 48, Outages.MAYBE); // 23:00 to midnight
        List<OutageWarning.Run> runs = OutageWarning.upcoming(schedule(today), kyiv(2026, 10, 22, 13, 0));
        assertEquals(2, runs.size());
        assertEquals(kyiv(2026, 10, 22, 18, 0), runs.get(0).start);
        assertEquals(kyiv(2026, 10, 22, 21, 0), runs.get(0).end);
        assertEquals(Outages.OFF, runs.get(0).kind);
        assertEquals(Outages.MAYBE, runs.get(1).kind);
        assertEquals("tomorrow isn't published: no end yet", 0, runs.get(1).end);

        // One outage across midnight; then tomorrow's own.
        Outages.Day tomorrow = new Outages.Day("2026-10-23", Outages.ON);
        fill(tomorrow, 0, 4, Outages.MAYBE); // until 02:00
        fill(tomorrow, 12, 18, Outages.OFF); // 06:00 to 09:00
        runs = OutageWarning.upcoming(schedule(today, tomorrow), kyiv(2026, 10, 22, 22, 0));
        assertEquals(2, runs.size());
        assertEquals(kyiv(2026, 10, 22, 23, 0), runs.get(0).start);
        assertEquals(kyiv(2026, 10, 23, 2, 0), runs.get(0).end);
        assertEquals(kyiv(2026, 10, 23, 6, 0), runs.get(1).start);
        assertEquals(kyiv(2026, 10, 23, 9, 0), runs.get(1).end);

        assertTrue("one in progress is no warning", OutageWarning.upcoming(schedule(today), kyiv(2026, 10, 22, 18, 5))
                .get(0).start > kyiv(2026, 10, 22, 21, 0));
        assertTrue(OutageWarning.upcoming(null, 0).isEmpty());
    }

    @Test public void anEmergencyDayHasNone() {
        Outages.Day today = new Outages.Day("2026-10-22", Outages.UNKNOWN);
        today.emergency = true;
        assertTrue(OutageWarning.upcoming(schedule(today), kyiv(2026, 10, 22, 8, 0)).isEmpty());
        // An outage that runs into an emergency day: its end isn't known.
        Outages.Day before = new Outages.Day("2026-10-21", Outages.ON);
        fill(before, 46, 48, Outages.OFF);
        List<OutageWarning.Run> runs = OutageWarning.upcoming(schedule(before, today), kyiv(2026, 10, 21, 20, 0));
        assertEquals(1, runs.size());
        assertEquals(0, runs.get(0).end);
    }

    @Test public void theHelpersCopy() {
        List<OutageWarning.Run> runs = Arrays.asList(new OutageWarning.Run(1000, 2000, Outages.OFF),
                new OutageWarning.Run(3000, 0, Outages.MAYBE));
        String text = OutageWarning.format(runs);
        assertEquals("1000:2000:off,3000:0:maybe", text);
        List<OutageWarning.Run> back = OutageWarning.parse(text);
        assertEquals(2, back.size());
        assertEquals(3000, back.get(1).start);
        assertEquals(0, back.get(1).end);
        assertEquals(Outages.MAYBE, back.get(1).kind);
        assertNull("none: no key", OutageWarning.format(Collections.<OutageWarning.Run>emptyList()));
        assertTrue(OutageWarning.parse(null).isEmpty());
        assertEquals("the broken one is skipped", 1, OutageWarning.parse("x:1:off,5:6:on,7:8:off,9").size());
    }

    @Test public void oneCardPerOutage15MinutesAhead() {
        long start = kyiv(2026, 10, 22, 18, 0);
        List<OutageWarning.Run> runs = Arrays.asList(new OutageWarning.Run(start, 0, Outages.OFF),
                new OutageWarning.Run(start + 3 * 3600_000L, 0, Outages.MAYBE));
        long early = start - 20 * 60_000L;
        assertNull(OutageWarning.due(runs, early, 0));
        assertEquals(start - OutageWarning.LEAD_MS, OutageWarning.nextDue(runs, early, 0));
        long now = start - 15 * 60_000L;
        assertSame(runs.get(0), OutageWarning.due(runs, now, 0));
        assertNull("warned already", OutageWarning.due(runs, now, start));
        assertEquals("then the next one", runs.get(1).start - OutageWarning.LEAD_MS,
                OutageWarning.nextDue(runs, now, start));
        // The TV turned on 5 minutes before: the card still comes.
        assertSame(runs.get(0), OutageWarning.due(runs, start - 5 * 60_000L, 0));
        assertNull("not once it has started", OutageWarning.due(runs, start, 0));
        assertEquals(Long.MAX_VALUE, OutageWarning.nextDue(runs, now, runs.get(1).start));
    }

    @Test public void whatChangedAhead() {
        Outages.Day today = new Outages.Day("2026-10-22", Outages.ON), tomorrow = new Outages.Day("2026-10-23", Outages.ON);
        fill(today, 16, 24, Outages.OFF); // 08:00 to 12:00
        fill(today, 36, 42, Outages.OFF); // 18:00 to 21:00
        Outages.Schedule before = schedule(today, tomorrow);
        long now = kyiv(2026, 10, 22, 13, 0);
        assertTrue("the first download", OutageWarning.changedDays(null, before, now).isEmpty());

        Outages.Day today2 = new Outages.Day("2026-10-22", Outages.ON);
        fill(today2, 14, 24, Outages.OFF); // the morning's outage changed: it's over, no news
        fill(today2, 36, 42, Outages.OFF);
        Outages.Day tomorrow2 = new Outages.Day("2026-10-23", Outages.ON);
        Outages.Day after = new Outages.Day("2026-10-24", Outages.ON); // a new day: no change either
        fill(after, 0, 10, Outages.OFF);
        assertTrue(OutageWarning.changedDays(before, schedule(today2, tomorrow2, after), now).isEmpty());

        fill(tomorrow2, 12, 18, Outages.MAYBE);
        List<Outages.Day> changed = OutageWarning.changedDays(before, schedule(today2, tomorrow2), now);
        assertEquals(1, changed.size());
        assertEquals("2026-10-23", changed.get(0).date);

        fill(today2, 42, 44, Outages.OFF); // 18:00 to 22:00 now
        changed = OutageWarning.changedDays(before, schedule(today2, tomorrow2), now);
        assertEquals(2, changed.size());
        Outages.Day ahead = changed.get(0);
        assertEquals("2026-10-22", ahead.date);
        assertEquals("the morning is left out", 1, Outages.runs(ahead.slots, Outages.OFF).size());
        assertEquals(1320, Outages.runs(ahead.slots, Outages.OFF).get(0)[1]);

        // Tomorrow not published in one of them, or an emergency day: nothing to compare there.
        Outages.Day emergency = new Outages.Day("2026-10-23", Outages.UNKNOWN);
        emergency.emergency = true;
        assertEquals(1, OutageWarning.changedDays(before, schedule(today2, emergency), now).size());
        assertEquals(1, OutageWarning.changedDays(schedule(today), schedule(today2, tomorrow2), now).size());
    }

    @Test public void theOutageGoingOnStaysWhole() {
        Outages.Day d = new Outages.Day("2026-10-22", Outages.ON);
        fill(d, 16, 24, Outages.OFF); // 08:00 to 12:00
        fill(d, 36, 42, Outages.MAYBE); // 18:00 to 21:00
        Outages.Day a = OutageWarning.ahead(d, 38); // 19:00
        assertTrue(Outages.runs(a.slots, Outages.OFF).isEmpty());
        assertEquals(1080, Outages.runs(a.slots, Outages.MAYBE).get(0)[0]);
        assertEquals("not the day itself", 1, Outages.runs(d.slots, Outages.OFF).size());
    }

    @Test public void aChangeIsShownOnceTheSameHour() {
        long found = kyiv(2026, 10, 22, 17, 30);
        String entry = found + " The schedule changed: today no power 18:00 to 22:00";
        assertEquals(found, OutageWarning.changedId(entry));
        assertEquals("The schedule changed: today no power 18:00 to 22:00",
                OutageWarning.changed(entry, found + 60_000, 0));
        assertNull("shown", OutageWarning.changed(entry, found + 60_000, found));
        assertNull("an hour later it's old", OutageWarning.changed(entry, found + 61 * 60_000L, 0));
        long late = kyiv(2026, 10, 22, 23, 40);
        assertNull("its \"today\" is over", OutageWarning.changed(late + " x", kyiv(2026, 10, 23, 0, 10), 0));
        assertNull(OutageWarning.changed(null, found, 0));
        assertEquals(0, OutageWarning.changedId("soon text"));
        assertEquals(0, OutageWarning.changedId("1792681200000"));
    }

    @Test public void kyivTimes() {
        assertEquals("18:00", OutageWarning.hhmm(kyiv(2026, 10, 22, 18, 0), false));
        assertEquals("00:00", OutageWarning.hhmm(kyiv(2026, 10, 23, 0, 0), false));
        assertEquals("an end at midnight", "24:00", OutageWarning.hhmm(kyiv(2026, 10, 23, 0, 0), true));
    }

    /** Past changes, so the answers don't depend on what a JDK's time zone data says about future years. */
    @Test public void wallClockAcrossClockChanges() {
        assertEquals(kyiv(2025, 10, 26, 5, 0), Outages.at("2025-10-26", 300));
        // 03:00 comes twice that night: the first time, in summer time.
        assertEquals(OutagesTest.utc(2025, 10, 26, 0, 0), Outages.at("2025-10-26", 180));
        assertEquals(OutagesTest.utc(2025, 10, 26, 0, 30), Outages.at("2025-10-26", 210));
        assertEquals("04:00 is winter time", OutagesTest.utc(2025, 10, 26, 2, 0), Outages.at("2025-10-26", 240));
        // In spring 03:00 doesn't come: the hour after.
        assertEquals(OutagesTest.utc(2025, 3, 30, 1, 0), Outages.at("2025-03-30", 180));
        assertEquals("the next day", kyiv(2025, 3, 31, 1, 30), Outages.at("2025-03-30", 1440 + 90));
    }

    @Test public void helperFiles() throws Exception {
        File dir = Files.createTempDirectory("lehko-helper").toFile();
        Map<String, String> put = new HashMap<>();
        put.put(OutageWarning.P_RUNS, "1:2:off");
        put.put(OutageWarning.P_ON, "0");
        Keys.putAll(dir, Keys.FILE, put);
        Map<String, String> s = Keys.read(dir);
        assertEquals("1:2:off", s.get(OutageWarning.P_RUNS));
        assertFalse(OutageWarning.on(s));
        File file = new File(dir, Keys.FILE);
        assertTrue(file.setLastModified(1000));
        Keys.putAll(dir, Keys.FILE, put);
        assertEquals("the same: not written again (the helper would read it again)", 1000, file.lastModified());
        put.put(OutageWarning.P_ON, null);
        Keys.putAll(dir, Keys.FILE, put);
        assertTrue(OutageWarning.on(Keys.read(dir)));

        // The helper's own notes are another file.
        Keys.putAll(dir, Keys.STATE, Collections.singletonMap(OutageWarning.P_WARNED, "1792681200000"));
        assertEquals(1792681200000L, Keys.number(Keys.read(dir, Keys.STATE), OutageWarning.P_WARNED));
        assertFalse(Keys.read(dir).containsKey(OutageWarning.P_WARNED));
        assertEquals("none", 0, Keys.number(Keys.read(dir), OutageWarning.P_WARNED));
        assertEquals("not a number", 0, Keys.number(Collections.singletonMap("x", "soon"), "x"));
    }
}
