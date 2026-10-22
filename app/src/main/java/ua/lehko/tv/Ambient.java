package ua.lehko.tv;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The home screen's background tint: a dark, calm tone taken from the focused app's banner. Only an 8x4 sample of the
 * picture that is already on screen is read, once per app; the colour math is plain Java so it can be tested.
 */
final class Ambient {
    private Ambient() {}

    /** HSV value (brightness) of the tint: a brighter picture gives a slightly brighter tint, always dark. */
    static final float MIN_VALUE = 0.22f, MAX_VALUE = 0.30f, MAX_SATURATION = 0.6f;
    /** After sunset the tint is dimmer still. */
    static final float NIGHT = 0.7f;
    /**
     * No tint brighter than this (WCAG relative luminance): at the same HSV value a yellow or green is far brighter
     * than a blue, and the date and the rates line sit on the tint's brightest corner. Keeps all text above 4.5:1.
     */
    static final double MAX_LUMINANCE = 0.034;

    /** Mean colour of each app's picture (before toning), by package. */
    private static final Map<String, Integer> MEAN = Collections.synchronizedMap(new HashMap<String, Integer>());

    /** The mean colour of an app's picture (cached), or null when it can't be read (not a bitmap, or fully clear). */
    static Integer mean(String pkg, Drawable picture) {
        Integer cached = MEAN.get(pkg);
        if (cached != null) return cached;
        if (!(picture instanceof BitmapDrawable)) return null;
        Bitmap b = ((BitmapDrawable) picture).getBitmap();
        if (b == null || b.isRecycled()) return null;
        int[] px = new int[8 * 4];
        try {
            Bitmap small = Bitmap.createScaledBitmap(b, 8, 4, true);
            small.getPixels(px, 0, 8, 0, 0, 8, 4);
            if (small != b) small.recycle();
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
        int mean = average(px);
        if (mean == 0) return null;
        MEAN.put(pkg, mean);
        return mean;
    }

    static void forget(String pkg) {
        MEAN.remove(pkg);
    }

    /** Average of ARGB pixels weighted by their opacity (an icon's clear corners don't count); 0 if all are clear. */
    static int average(int[] argb) {
        long r = 0, g = 0, b = 0, weight = 0;
        for (int p : argb) {
            int a = p >>> 24;
            if (a == 0) continue;
            r += (long) ((p >> 16) & 0xFF) * a;
            g += (long) ((p >> 8) & 0xFF) * a;
            b += (long) (p & 0xFF) * a;
            weight += a;
        }
        if (weight == 0) return 0;
        return 0xFF000000 | (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
    }

    /** {@code rgb} turned into a background tint: same hue, saturation at most 0.6, value 0.22..0.30 (x0.7 at night). */
    static int tone(int rgb, boolean night) {
        float[] hsv = hsv(rgb);
        float s = Math.min(hsv[1], MAX_SATURATION);
        float v = MIN_VALUE + (MAX_VALUE - MIN_VALUE) * hsv[2];
        for (int i = 0; i < 40 && luminance(rgb(hsv[0], s, v)) > MAX_LUMINANCE; i++) v *= 0.98f;
        if (night) v *= NIGHT;
        return rgb(hsv[0], s, v);
    }

    /** The focused tile's glow: the tint's hue, bright and moderately saturated (its shadow is faint anyway). */
    static int glow(int rgb) {
        float[] hsv = hsv(rgb);
        return rgb(hsv[0], Math.min(hsv[1], 0.7f), 0.95f);
    }

    /** WCAG relative luminance of an sRGB colour, 0..1. */
    static double luminance(int rgb) {
        return 0.2126 * linear((rgb >> 16) & 0xFF) + 0.7152 * linear((rgb >> 8) & 0xFF) + 0.0722 * linear(rgb & 0xFF);
    }

    private static double linear(int c) {
        double x = c / 255.0;
        return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }

    /** Hue in degrees [0, 360), saturation and value in [0, 1]. */
    static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60 * ((g - b) / d);
        else if (max == g) h = 60 * ((b - r) / d + 2);
        else h = 60 * ((r - g) / d + 4);
        if (h < 0) h += 360;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    static int rgb(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        int sector = ((int) (h / 60)) % 6;
        if (sector == 0) { r = c; g = x; b = 0; }
        else if (sector == 1) { r = x; g = c; b = 0; }
        else if (sector == 2) { r = 0; g = c; b = x; }
        else if (sector == 3) { r = 0; g = x; b = c; }
        else if (sector == 4) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }
}
