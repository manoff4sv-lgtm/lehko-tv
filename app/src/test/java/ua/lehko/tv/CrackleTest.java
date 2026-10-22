package ua.lehko.tv;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrackleTest {
    @Test public void quietWithPops() {
        Crackle.Sound s = new Crackle.Sound(3);
        short[] buf = new short[Crackle.RATE]; // one second
        long sumSquares = 0;
        int peak = 0, loud = 0;
        for (int second = 0; second < 5; second++) {
            s.fill(buf);
            for (short v : buf) {
                sumSquares += (long) v * v;
                peak = Math.max(peak, Math.abs(v));
                if (Math.abs(v) > 1500) loud++;
            }
        }
        double rms = Math.sqrt(sumSquares / (5.0 * buf.length));
        assertTrue("quiet overall, rms " + rms, rms < 2500);
        assertTrue("but not silent, rms " + rms, rms > 100);
        assertTrue("never clips, peak " + peak, peak < 32767 / 2);
        assertTrue("there are pops", loud > 20);
    }
}
