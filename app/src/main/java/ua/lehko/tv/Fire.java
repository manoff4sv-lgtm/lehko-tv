package ua.lehko.tv;

/**
 * The classic "Doom fire": heat enters at the bottom row of a small grid and rises, cooling a little and drifting
 * sideways at random on every row, with a slow random wind. Plain Java (tested); {@link FireView} draws the grid
 * scaled up with filtering, so the flames look soft.
 */
final class Fire {
    static final int W = 160, H = 90, MAX = 36;

    /** Black, deep red, orange, yellow, warm white. Thin flame is see-through, so the dark firebox shows behind it. */
    static final int[] PALETTE = new int[MAX + 1];

    static {
        int[] rgb = {
                0x070707, 0x1F0707, 0x2F0F07, 0x470F07, 0x571707, 0x671F07, 0x771F07, 0x8F2707, 0x9F2F07,
                0xAF3F07, 0xBF4707, 0xC74707, 0xDF4F07, 0xDF5707, 0xDF5707, 0xD75F07, 0xD75F07, 0xD7670F,
                0xCF6F0F, 0xCF770F, 0xCF7F0F, 0xCF8717, 0xC78717, 0xC78F17, 0xC7971F, 0xBF9F1F, 0xBF9F1F,
                0xBFA727, 0xBFA727, 0xBFAF2F, 0xB7AF2F, 0xB7B72F, 0xB7B737, 0xCFCF6F, 0xDFDF9F, 0xEFEBC7,
                0xFFF4D6,
        };
        for (int i = 0; i <= MAX; i++) {
            int alpha = i == 0 ? 0 : Math.min(255, 40 + i * 24);
            PALETTE[i] = alpha << 24 | rgb[i];
        }
    }

    /** Heat 0..MAX per cell, row by row from the top. */
    final byte[] heat = new byte[W * H];
    /** The colours of the last step, for Bitmap.setPixels. */
    final int[] pixels = new int[W * H];
    /** How hot each column of the bottom row burns: the logs lie in the middle. */
    private final int[] source = new int[W];
    private int seed, wind, windLeft;

    Fire(long seed) {
        this.seed = (int) (seed ^ (seed >>> 32)) | 1;
        for (int x = 0; x < W; x++) {
            // Flat in the middle, soft at both ends, nothing at the edges.
            float t = (x - (W - 1) / 2f) / (W * 0.40f);
            source[x] = Math.max(0, Math.min(MAX, Math.round(MAX * 1.6f * (1 - t * t))));
        }
    }

    /** xorshift32: fast and good enough for flames. */
    private int next() {
        int s = seed;
        s ^= s << 13;
        s ^= s >>> 17;
        s ^= s << 5;
        return seed = s;
    }

    /** One frame: new heat at the bottom, everything rises one row, colours for the bitmap. */
    void step() {
        int bottom = (H - 1) * W;
        for (int x = 0; x < W; x++) {
            int v = source[x];
            int r = next();
            // Short dips in the bottom row split the flame into tongues.
            if (v > 0 && (r & 7) == 0) v = Math.max(0, v - ((r >>> 3) & 15));
            heat[bottom + x] = (byte) v;
        }
        if (--windLeft <= 0) {
            int r = next();
            wind = (r & 3) == 0 ? (((r >>> 2) & 1) == 0 ? -1 : 1) : 0;
            windLeft = 40 + ((r >>> 4) & 127);
        }
        for (int y = 1; y < H; y++) {
            int row = y * W, above = row - W;
            for (int x = 0; x < W; x++) {
                int p = heat[row + x];
                if (p == 0) {
                    heat[above + x] = 0;
                    continue;
                }
                int r = next() >>> 1;
                int dx = r % 3 - 1;
                if (wind != 0 && (r & 0x300) == 0) dx += wind;
                int to = x + dx;
                if (to < 0) to = 0;
                else if (to >= W) to = W - 1;
                int cooled = p - ((r >>> 10) & 1);
                heat[above + to] = (byte) cooled;
            }
        }
        for (int i = 0; i < pixels.length; i++) pixels[i] = PALETTE[heat[i]];
    }
}
