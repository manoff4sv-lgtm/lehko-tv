package ua.lehko.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FireTest {
    @Test public void palette() {
        assertEquals(0, Fire.PALETTE[0] >>> 24);
        assertEquals(255, Fire.PALETTE[Fire.MAX] >>> 24);
        int hot = Fire.PALETTE[Fire.MAX];
        assertTrue(((hot >> 16) & 0xFF) > 0xF0 && ((hot >> 8) & 0xFF) > 0xE0);
        for (int i = 1; i <= Fire.MAX; i++) assertTrue((Fire.PALETTE[i] >>> 24) >= (Fire.PALETTE[i - 1] >>> 24));
    }

    @Test public void flamesRiseAndStayInTheGrid() {
        Fire f = new Fire(42);
        for (int i = 0; i < 300; i++) f.step();
        int hotCells = 0, upperHalf = 0, top = 0;
        for (int y = 0; y < Fire.H; y++) {
            for (int x = 0; x < Fire.W; x++) {
                int h = f.heat[y * Fire.W + x];
                assertTrue(h >= 0 && h <= Fire.MAX);
                if (h > 0) hotCells++;
                if (h > 0 && y < Fire.H / 2) upperHalf++;
                if (h > 0 && y == 0) top++;
            }
        }
        assertTrue("there is a fire", hotCells > Fire.W * Fire.H / 5);
        assertTrue("flames reach the upper half", upperHalf > 0);
        assertTrue("but rarely the very top", top < Fire.W / 4);
        // Nothing burns at the far edges of the bottom row (the logs are in the middle).
        assertEquals(0, f.heat[(Fire.H - 1) * Fire.W]);
        assertEquals(0, f.heat[Fire.H * Fire.W - 1]);
        assertEquals(Fire.PALETTE[f.heat[1234]], f.pixels[1234]);
    }

    /** FireView's warm-up (also the still picture with animations off): tall flames at once, not a low band. */
    @Test public void warmedUpFireIsTall() {
        Fire cold = new Fire(3), warm = new Fire(3);
        warm.warm();
        for (int i = 0; i < 40; i++) {
            cold.step();
            warm.step();
        }
        assertEquals("from cold, 40 steps leave the upper half dark", 0, hotCells(cold, Fire.H / 2));
        assertTrue(hotCells(warm, Fire.H / 2) > Fire.W * 10);
        assertTrue("but rarely the very top", hotCells(warm, 1) < Fire.W / 4);
    }

    /** Burning cells in the top {@code rows} rows. */
    private static int hotCells(Fire f, int rows) {
        int n = 0;
        for (int i = 0; i < rows * Fire.W; i++) if (f.heat[i] > 0) n++;
        return n;
    }

    @Test public void sameSeedSameFlames() {
        Fire a = new Fire(7), b = new Fire(7);
        for (int i = 0; i < 50; i++) {
            a.step();
            b.step();
        }
        assertArrayEquals(a.heat, b.heat);
    }

    /** Budget check on the build machine: a step must be far below a frame (the TV is maybe 20x slower). */
    @Test public void stepIsCheap() {
        Fire f = new Fire(1);
        for (int i = 0; i < 200; i++) f.step();
        long t = System.nanoTime();
        for (int i = 0; i < 300; i++) f.step();
        long perStepUs = (System.nanoTime() - t) / 300 / 1000;
        assertTrue("step took " + perStepUs + " us", perStepUs < 1500);
    }
}
