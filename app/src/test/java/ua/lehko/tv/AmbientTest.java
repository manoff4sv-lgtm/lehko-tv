package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class AmbientTest {
    @Test public void averageIgnoresClearPixels() {
        assertEquals(0xFFFF0000, Ambient.average(new int[]{0xFFFF0000, 0x00FFFFFF, 0x0000FF00}));
        // Half-transparent white counts half as much as opaque black.
        assertEquals(0xFF555555, Ambient.average(new int[]{0xFF000000, 0x80FFFFFF}));
        assertEquals(0, Ambient.average(new int[]{0, 0x00123456}));
    }

    @Test public void toneOfPureRed() {
        int t = Ambient.tone(0xFFFF0000, false);
        assertEquals(77, (t >> 16) & 0xFF); // value 0.30
        assertEquals(31, (t >> 8) & 0xFF); // saturation capped at 0.6
        assertEquals(31, t & 0xFF);
        int night = Ambient.tone(0xFFFF0000, true);
        assertEquals(54, (night >> 16) & 0xFF); // 70% of 0.30
    }

    @Test public void greyStaysGrey() {
        int t = Ambient.tone(0xFF808080, false);
        assertEquals((t >> 16) & 0xFF, t & 0xFF);
        assertEquals((t >> 8) & 0xFF, t & 0xFF);
        assertEquals(66, t & 0xFF);
    }

    @Test public void alwaysDarkAndCalm() {
        Random r = new Random(7);
        for (int i = 0; i < 2000; i++) {
            int c = 0xFF000000 | r.nextInt(0x1000000);
            float[] hsv = Ambient.hsv(Ambient.tone(c, false));
            assertTrue(hsv[2] >= 0.215f && hsv[2] <= 0.305f);
            assertTrue(hsv[1] <= 0.62f);
            float[] night = Ambient.hsv(Ambient.tone(c, true));
            assertTrue(night[2] <= 0.215f);
        }
    }

    @Test public void hueSurvives() {
        for (int h = 0; h < 360; h += 15) {
            int c = Ambient.rgb(h, 1, 1);
            float[] back = Ambient.hsv(Ambient.tone(c, false));
            assertEquals(h, back[0], 3);
        }
    }
}
