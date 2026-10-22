package ua.lehko.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetTest {
    static final long NOW = 1792664100000L, MIN = 60_000L;

    @Test public void staleByAge() {
        assertFalse(Net.stale(NOW, NOW - 29 * MIN, 30 * MIN));
        assertTrue(Net.stale(NOW, NOW - 31 * MIN, 30 * MIN));
        assertTrue("never downloaded", Net.stale(NOW, 0, 30 * MIN));
    }

    /** Saved while the clock was a day ahead, now put right: download again rather than wait a day. */
    @Test public void aTimeInTheFutureIsStale() {
        assertTrue(Net.stale(NOW, NOW + 24 * 60 * MIN, 30 * MIN));
        assertTrue(Net.stale(NOW, NOW + 1, 30 * MIN));
    }
}
