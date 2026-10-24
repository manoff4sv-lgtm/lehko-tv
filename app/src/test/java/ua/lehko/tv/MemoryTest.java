package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MemoryTest {
    /** Android 9: no "TOTAL PSS:" line, the App Summary says "TOTAL:" and the table row comes first. */
    static final String ANDROID_9 = "Applications Memory Usage (in Kilobytes):\n"
            + "Uptime: 1 Realtime: 1\n\n"
            + "** MEMINFO in pid 812 [com.google.android.tvlauncher] **\n"
            + "                   Pss  Private  Private  SwapPss     Heap     Heap     Heap\n"
            + "                 Total    Dirty    Clean    Dirty     Size    Alloc     Free\n"
            + "                ------   ------   ------   ------   ------   ------   ------\n"
            + "  Native Heap    20480    20400        0       12    30000    25000     5000\n"
            + "        TOTAL   183456   150000     9000      400    70000    50000    20000\n\n"
            + " App Summary\n"
            + "                       Pss(KB)\n"
            + "                        ------\n"
            + "           Java Heap:    12345\n"
            + "                TOTAL:   183456       TOTAL SWAP PSS:      400\n\n"
            + "** MEMINFO in pid 990 [com.google.android.tvlauncher:recommendations] **\n"
            + "        TOTAL    40000    30000     2000        0    10000     8000     2000\n"
            + "                TOTAL:    40000       TOTAL SWAP PSS:        0\n";

    static final String ANDROID_12 = "** MEMINFO in pid 4242 [com.google.android.apps.tv.launcherx] **\n"
            + "        TOTAL   245678   200000    10000     5000   305678    70000    50000    20000\n"
            + "               TOTAL PSS:   245678            TOTAL RSS:   305678       TOTAL SWAP PSS:     5000\n"
            + "** MEMINFO in pid 4300 [com.google.android.apps.tv.launcherx:search] **\n"
            + "               TOTAL PSS:    30000            TOTAL RSS:    50000       TOTAL SWAP PSS:        0\n";

    @Test public void launcherMemory() {
        assertEquals(223456, HomeMode.pssKb(ANDROID_9));
        assertEquals(275678, HomeMode.pssKb(ANDROID_12));
        assertEquals(0, HomeMode.pssKb("No process found for: com.example\n"));
        assertEquals(0, HomeMode.pssKb(""));
    }

    @Test public void autoCleanOnlyWhenAndroidSaysLowAndNotTooOften() {
        long now = 1_800_000_000_000L, min = 60_000L;
        assertTrue(Memory.autoDue(now, 0, true)); // never ran
        assertFalse("memory isn't low", Memory.autoDue(now, 0, false));
        assertFalse(Memory.autoDue(now, now - 29 * min, true)); // at most every half hour
        assertTrue(Memory.autoDue(now, now - 31 * min, true));
        assertTrue("the clock was ahead last time and has been put right", Memory.autoDue(now, now + 60 * min, true));
    }

    @Test public void autoCleanCheckComesBeforeTheTvsScreensaver() {
        long min = 60_000L;
        assertEquals("no screensaver timer", 5 * min, Memory.autoIdleMs(-1));
        assertEquals("never", 5 * min, Memory.autoIdleMs(Integer.MAX_VALUE));
        assertEquals(5 * min, Memory.autoIdleMs((int) (15 * min)));
        assertEquals("the TV's 5 minutes, the fireplace's place", 4 * min + 30_000, Memory.autoIdleMs((int) (5 * min)));
        assertEquals("a 1-minute screensaver", 30_000, Memory.autoIdleMs((int) min));
    }
}
