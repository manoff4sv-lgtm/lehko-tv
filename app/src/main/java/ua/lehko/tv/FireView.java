package ua.lehko.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * A fireplace. The brick firebox is drawn once into a half-size RGB_565 bitmap (about 1 MB at 1080p); every frame
 * draws that, the {@link Fire} grid scaled into the arch with a filtering paint, three logs in front and a flickering
 * ember glow. About 30 frames a second while on screen, nothing at all otherwise.
 */
final class FireView extends View {
    private static final long FRAME_MS = 30;
    private static final int[] BRICKS = {0xFF4F2419, 0xFF5A2A1C, 0xFF46201A, 0xFF63301F, 0xFF542619, 0xFF3F1D16};
    /** Soot-dark bricks inside the firebox, drawn see-through over its dark back wall. */
    private static final int[] SOOT = {0x24603020, 0x1C502818, 0x14703828};

    private final Fire fire = new Fire(System.nanoTime());
    private final Bitmap frame = Bitmap.createBitmap(Fire.W, Fire.H, Bitmap.Config.ARGB_8888);
    private final Paint soft = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint();
    private final Path arch = new Path();
    private final RectF fireRect = new RectF(), r = new RectF();
    private final Rect boxDst = new Rect();
    private final Random random = new Random();
    /** Logs: centre x, centre y, length, thickness, angle in degrees (view pixels). */
    private final float[][] logs = new float[3][];
    private Bitmap box;
    /** Grid cells outside the arch, kept clear, so the fire needs no clip path (costly on some GPUs). */
    private int[] outside = new int[0];
    private boolean boxFailed, visible, attached, paused;
    private float glowX, glowY, glowR, flicker = 0.75f, flickerTarget = 0.75f;
    private long lastFrame;

    private final Runnable frameTick = new Runnable() {
        @Override public void run() {
            if (!visible || !attached || paused) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastFrame >= FRAME_MS) {
                lastFrame = now;
                fire.step();
                for (int i : outside) fire.pixels[i] = 0;
                frame.setPixels(fire.pixels, 0, Fire.W, 0, 0, Fire.W, Fire.H);
                if (random.nextInt(5) == 0) flickerTarget = 0.55f + random.nextFloat() * 0.45f;
                flicker += (flickerTarget - flicker) * 0.2f;
                invalidate();
            }
            postOnAnimation(this);
        }
    };

    FireView(Context c) {
        super(c);
        // Warm up, so the fire is already burning on the first frame.
        for (int i = 0; i < 40; i++) fire.step();
        frame.setPixels(fire.pixels, 0, Fire.W, 0, 0, Fire.W, Fire.H);
    }

    // MARK: - Running only while visible

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        update();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        update();
        if (box != null) {
            box.recycle();
            box = null;
        }
        super.onDetachedFromWindow();
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        update();
    }

    /** Paused by its scene (not started yet, or covered by the TV's own screensaver). */
    void setPaused(boolean p) {
        paused = p;
        update();
    }

    private void update() {
        removeCallbacks(frameTick);
        // "Remove animations" in the TV's accessibility settings: a still fire (the frame from the warm-up).
        if (visible && attached && !paused && ValueAnimator.areAnimatorsEnabled()) postOnAnimation(frameTick);
    }

    // MARK: - Layout

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (box != null) {
            box.recycle();
            box = null;
        }
        boxFailed = false;
        float left = w * 0.285f, right = w * 0.715f, sideTop = h * 0.50f, archTop = h * 0.37f, bottom = h * 0.875f;
        arch.reset();
        arch.moveTo(left, bottom);
        arch.lineTo(left, sideTop);
        r.set(left, archTop, right, sideTop + (sideTop - archTop));
        arch.arcTo(r, 180, 180, false);
        arch.lineTo(right, bottom);
        arch.close();
        fireRect.set(left, archTop + (bottom - archTop) * 0.04f, right, bottom);
        // Which grid cells fall outside the arch's round top.
        float cx = (left + right) / 2, rx = (right - left) / 2, ry = sideTop - archTop;
        float cellW = fireRect.width() / Fire.W, cellH = fireRect.height() / Fire.H;
        int[] cells = new int[Fire.W * Fire.H];
        int n = 0;
        for (int gy = 0; gy < Fire.H; gy++) {
            float y = fireRect.top + (gy + 0.5f) * cellH;
            if (y >= sideTop) break;
            float dy = (y - sideTop) / ry;
            for (int gx = 0; gx < Fire.W; gx++) {
                float dx = (fireRect.left + (gx + 0.5f) * cellW - cx) / rx;
                if (dx * dx + dy * dy > 1) cells[n++] = gy * Fire.W + gx;
            }
        }
        outside = java.util.Arrays.copyOf(cells, n);
        logs[0] = new float[]{w * 0.462f, h * 0.850f, w * 0.27f, h * 0.050f, -5};
        logs[1] = new float[]{w * 0.540f, h * 0.855f, w * 0.26f, h * 0.048f, 6};
        logs[2] = new float[]{w * 0.502f, h * 0.814f, w * 0.21f, h * 0.044f, -2};
        glowX = w * 0.5f;
        glowY = h * 0.84f;
        glowR = Math.max(1, w * 0.36f);
        glow.setShader(new RadialGradient(glowX, glowY, glowR, new int[]{0x70FF8A2A, 0x2EFF6A18, 0x00FF5010},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    // MARK: - Drawing

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (box == null && !boxFailed && w > 0 && h > 0) buildBox(w, h);
        if (box != null) {
            boxDst.set(0, 0, w, h);
            c.drawBitmap(box, null, boxDst, soft);
        } else {
            c.drawColor(0xFF070505);
        }
        c.drawBitmap(frame, null, fireRect, soft);
        for (float[] g : logs) if (g != null) drawLog(c, g);
        glow.setAlpha(Math.round(255 * flicker));
        c.drawCircle(glowX, glowY, glowR, glow);
    }

    private void drawLog(Canvas c, float[] g) {
        float cx = g[0], cy = g[1], len = g[2], t = g[3];
        c.save();
        c.rotate(g[4], cx, cy);
        paint.setColor(0xFF2B1A11);
        r.set(cx - len / 2, cy - t / 2, cx + len / 2, cy + t / 2);
        c.drawRoundRect(r, t / 2, t / 2, paint);
        // Firelight on the bark, and glowing coals along the underside.
        paint.setColor(0xFF4A2E1C);
        r.set(cx - len / 2 + t * 0.5f, cy - t * 0.38f, cx + len / 2 - t * 0.5f, cy - t * 0.2f);
        c.drawRoundRect(r, t * 0.1f, t * 0.1f, paint);
        paint.setColor(0x90FF6A1A);
        r.set(cx - len / 2 + t * 0.6f, cy + t * 0.3f, cx + len / 2 - t * 0.6f, cy + t * 0.46f);
        c.drawRoundRect(r, t * 0.1f, t * 0.1f, paint);
        // Sawn ends: pale wood with darker rings.
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * (len / 2 - t * 0.28f);
            r.set(ex - t * 0.28f, cy - t / 2, ex + t * 0.28f, cy + t / 2);
            paint.setColor(0xFF8E6C48);
            c.drawOval(r, paint);
            r.inset(t * 0.08f, t * 0.12f);
            paint.setColor(0xFF6E4D30);
            c.drawOval(r, paint);
            r.inset(t * 0.07f, t * 0.12f);
            paint.setColor(0xFF8A6440);
            c.drawOval(r, paint);
        }
        c.restore();
    }

    /** The room, the brick surround, the arch, mantel and hearth: drawn once, at half size. */
    private void buildBox(int w, int h) {
        try {
            box = Bitmap.createBitmap(Math.max(1, w / 2), Math.max(1, h / 2), Bitmap.Config.RGB_565);
        } catch (OutOfMemoryError | RuntimeException e) {
            boxFailed = true;
            return;
        }
        Canvas c = new Canvas(box);
        c.scale(box.getWidth() / (float) w, box.getHeight() / (float) h);
        // Dithered: the dark gradients would band in 16-bit colour.
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Random rnd = new Random(1); // the same bricks every time
        float line = Math.max(2, h * 0.004f);

        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF0D0907, 0xFF060404, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        float sx0 = w * 0.17f, sx1 = w * 0.83f, sy0 = h * 0.25f, sy1 = h * 0.875f;
        p.setColor(0xFF1E1410);
        c.drawRect(sx0, sy0, sx1, sy1, p);
        bricks(c, p, rnd, sx0, sy0, sx1, sy1, w * 0.058f, h * 0.045f, line * 1.2f, BRICKS);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h * 0.04f);
        p.setColor(0xFF34170F);
        c.drawPath(arch, p);
        p.setStyle(Paint.Style.FILL);

        c.save();
        c.clipPath(arch);
        p.setShader(new LinearGradient(0, h * 0.37f, 0, h * 0.875f, 0xFF040303, 0xFF1E0D07, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        bricks(c, p, rnd, w * 0.285f, h * 0.37f, w * 0.715f, h * 0.875f, w * 0.05f, h * 0.04f, line, SOOT);
        c.restore();

        p.setShader(new LinearGradient(0, h * 0.205f, 0, h * 0.25f, 0xFF3B2718, 0xFF1F140D, Shader.TileMode.CLAMP));
        c.drawRect(w * 0.12f, h * 0.205f, w * 0.88f, h * 0.25f, p);
        p.setShader(null);
        p.setColor(0xFF4C3422);
        c.drawRect(w * 0.12f, h * 0.205f, w * 0.88f, h * 0.205f + line, p);

        p.setShader(new LinearGradient(0, h * 0.875f, 0, h * 0.95f, 0xFF2E2621, 0xFF171310, Shader.TileMode.CLAMP));
        c.drawRect(w * 0.13f, h * 0.875f, w * 0.87f, h * 0.95f, p);
        p.setShader(null);
        p.setColor(0xFF3D332C);
        c.drawRect(w * 0.13f, h * 0.875f, w * 0.87f, h * 0.875f + line, p);

        // The room falls into darkness away from the fire.
        p.setShader(new RadialGradient(w * 0.5f, h * 0.72f, w * 0.62f, new int[]{0x00000000, 0x00000000, 0xB0000000},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private static void bricks(Canvas c, Paint p, Random rnd, float x0, float y0, float x1, float y1, float bw, float bh,
                               float gap, int[] colors) {
        int row = 0;
        for (float y = y0; y < y1; y += bh, row++) {
            float offset = (row & 1) == 0 ? 0 : bw / 2;
            for (float x = x0 - offset; x < x1; x += bw) {
                float l = Math.max(x + gap / 2, x0), rr = Math.min(x + bw - gap / 2, x1);
                float t = y + gap / 2, b = Math.min(y + bh - gap / 2, y1);
                if (rr - l < gap || b - t < gap) continue;
                p.setColor(colors[rnd.nextInt(colors.length)]);
                c.drawRoundRect(l, t, rr, b, gap, gap, p);
            }
        }
    }
}
