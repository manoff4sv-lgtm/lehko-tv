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
        // Value 0.26 would be 66, but that grey is brighter than the luminance cap allows.
        assertTrue(Ambient.luminance(t) <= Ambient.MAX_LUMINANCE);
        assertTrue((t & 0xFF) >= 48);
    }

    @Test public void alwaysDarkAndCalm() {
        Random r = new Random(7);
        for (int i = 0; i < 2000; i++) {
            int c = 0xFF000000 | r.nextInt(0x1000000);
            int t = Ambient.tone(c, false);
            float[] hsv = Ambient.hsv(t);
            assertTrue(hsv[2] >= 0.19f && hsv[2] <= 0.305f);
            assertTrue(hsv[1] <= 0.62f);
            assertTrue("text stays readable on it", Ambient.luminance(t) <= Ambient.MAX_LUMINANCE + 0.002);
            float[] night = Ambient.hsv(Ambient.tone(c, true));
            assertTrue(night[2] <= 0.215f);
        }
    }

    /**
     * text_secondary (#A7AFBA) and text_dim (#959DAA, the dial's hour numbers right on the tint) on the brightest
     * tint any colour, or a white banner, can give: at least WCAG AA.
     */
    @Test public void textOnTheTintPassesAa() {
        for (int color : new int[]{0xA7AFBA, 0x959DAA}) {
            double text = Ambient.luminance(color);
            for (int h = 0; h < 360; h++) {
                double bg = Ambient.luminance(Ambient.tone(Ambient.rgb(h, 1, 1), false));
                assertTrue(Integer.toHexString(color) + " on hue " + h, (text + 0.05) / (bg + 0.05) >= 4.5);
            }
            double white = Ambient.luminance(Ambient.tone(0xFFFFFFFF, false));
            assertTrue(Integer.toHexString(color) + " on white's tint", (text + 0.05) / (white + 0.05) >= 4.5);
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
