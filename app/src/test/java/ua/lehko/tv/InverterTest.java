package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InverterTest {
    static String packet(boolean grid, int soc) {
        return "{\"lehko\":\"inverter\",\"v\":1,\"grid\":" + grid + ",\"soc\":" + soc
                + ",\"load\":272,\"bat_a\":0.7,\"for_s\":3600,\"runtime\":960}";
    }

    static Inverter.Status at(boolean grid, int soc, long t) {
        return Inverter.parse(packet(grid, soc), t);
    }

    @Test public void parses() {
        Inverter.Status s = Inverter.parse(packet(true, 99), 1000);
        assertNotNull(s);
        assertTrue(s.grid);
        assertEquals(99, s.soc);
        assertEquals(272, s.loadW);
        assertEquals(0.7, s.batA, 1e-9);
        assertEquals(960, s.runtimeMin);
        assertEquals(1000, s.at);
    }

    @Test public void refusesOthers() {
        assertNull(Inverter.parse("set>server=1.2.3.4:8899;", 0));
        assertNull(Inverter.parse("{\"lehko\":\"other\",\"v\":1,\"grid\":true,\"soc\":5}", 0));
        assertNull(Inverter.parse("{\"lehko\":\"inverter\",\"v\":2,\"grid\":true,\"soc\":5}", 0));
        assertNull(Inverter.parse("{\"lehko\":\"inverter\",\"v\":1,\"soc\":5}", 0));
        assertNull(Inverter.parse("{\"lehko\":\"inverter\",\"v\":1,\"grid\":true,\"soc\":150}", 0));
        assertNull(Inverter.parse("{\"lehko\":\"inverter\",\"v\":1,\"grid\":true,\"soc\":50,\"load\":9999999}", 0));
    }

    @Test public void minimalPacket() {
        Inverter.Status s = Inverter.parse("{\"lehko\":\"inverter\",\"v\":1,\"grid\":false,\"soc\":40}", 0);
        assertNotNull(s);
        assertFalse(s.grid);
        assertEquals(-1, s.runtimeMin);
    }

    @Test public void fresh() {
        Inverter.Status s = at(true, 99, 100_000);
        assertTrue(s.fresh(100_000 + 60_000));
        assertFalse(s.fresh(100_000 + Inverter.FRESH_MS));
    }

    @Test public void firstStatusIsOnlyTheStart() {
        Inverter.Watch w = new Inverter.Watch();
        // Already on the battery at 15% when the TV starts listening: no card for that, nor for 20 or 30.
        assertEquals(Inverter.NONE, w.next(at(false, 15, 0)));
        assertEquals(Inverter.NONE, w.next(at(false, 14, 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 10, 20_000)));
        assertEquals(Inverter.LOW_BATTERY, w.next(at(false, 10, 30_000)));
    }

    @Test public void powerGoesAndComesBack() {
        Inverter.Watch w = new Inverter.Watch();
        long t = 0;
        assertEquals(Inverter.NONE, w.next(at(true, 99, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(true, 99, t += 10_000)));
        assertEquals(Inverter.OFF, w.next(at(false, 99, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 31, t += 10_000)));
        // A level counts on its second packet in a row.
        assertEquals(Inverter.NONE, w.next(at(false, 30, t += 10_000)));
        assertEquals(Inverter.LOW_BATTERY, w.next(at(false, 29, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 28, t += 10_000)));
        // Two levels at once: one card, at the lower one.
        assertEquals(Inverter.NONE, w.next(at(false, 9, t += 10_000)));
        assertEquals(Inverter.LOW_BATTERY, w.next(at(false, 8, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 8, t += 10_000)));
        assertEquals(Inverter.ON, w.next(at(true, 8, t += 10_000)));
        // The next outage counts from the start again.
        assertEquals(Inverter.OFF, w.next(at(false, 25, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 21, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 20, t += 10_000)));
        assertEquals(Inverter.LOW_BATTERY, w.next(at(false, 19, t += 10_000)));
    }

    @Test public void oneLowReadingInALoadSpikeUsesNothingUp() {
        Inverter.Watch w = new Inverter.Watch();
        long t = 0;
        assertEquals(Inverter.NONE, w.next(at(true, 70, t += 10_000)));
        assertEquals(Inverter.OFF, w.next(at(false, 62, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 18, t += 10_000))); // the kettle: one sagging reading
        assertEquals(Inverter.NONE, w.next(at(false, 58, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 31, t += 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 30, t += 10_000)));
        assertEquals(Inverter.LOW_BATTERY, w.next(at(false, 30, t += 10_000))); // 30 is still told
        assertEquals(Inverter.NONE, w.next(at(false, 20, t += 10_000)));
        assertEquals(Inverter.LOW_BATTERY, w.next(at(false, 20, t += 10_000))); // and 20
    }

    @Test public void offAtLowBatteryIsOneCard() {
        Inverter.Watch w = new Inverter.Watch();
        assertEquals(Inverter.NONE, w.next(at(true, 25, 0)));
        assertEquals(Inverter.OFF, w.next(at(false, 25, 10_000)));
        assertEquals(Inverter.NONE, w.next(at(false, 21, 20_000)));
    }

    @Test public void longGapStartsOver() {
        Inverter.Watch w = new Inverter.Watch();
        assertEquals(Inverter.NONE, w.next(at(true, 99, 0)));
        // The TV or the phone was off for an hour: the power went meanwhile, old news.
        assertEquals(Inverter.NONE, w.next(at(false, 60, 3_600_000)));
        assertEquals(Inverter.ON, w.next(at(true, 60, 3_610_000)));
    }
}
