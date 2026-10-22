package ua.lehko.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * The 24-hour ring around the home screen's clock: today's power schedule as arcs (off: a thick ember arc with round
 * ends; possible: a dashed amber arc; on: a thin light line), hour numbers every 3 hours and a white dot for "now",
 * midnight at the top. The past is dimmed. Without a schedule it is a plain 24-hour clock face. Drawn only when the
 * schedule or the minute changes.
 */
public final class DialView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), on = new Paint(Paint.ANTI_ALIAS_FLAG),
            off = new Paint(Paint.ANTI_ALIAS_FLAG), maybe = new Paint(Paint.ANTI_ALIAS_FLAG),
            label = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private static final String[] HOURS = {"0", "3", "6", "9", "12", "15", "18", "21"};
    private final float density;
    private byte[] slots;
    private int nowMinute = -1;

    public DialView(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = getResources().getDisplayMetrics().density;
        stroke(track, R.color.ring_track, 2f);
        stroke(on, R.color.ring_on, 2.5f);
        stroke(off, R.color.ember, 11f);
        off.setStrokeCap(Paint.Cap.ROUND);
        stroke(maybe, R.color.power_maybe, 9f);
        maybe.setPathEffect(new DashPathEffect(new float[]{3 * density, 4 * density}, 0));
        label.setColor(getResources().getColor(R.color.text_dim, null));
        label.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        label.setTextAlign(Paint.Align.CENTER);
        dot.setColor(getResources().getColor(R.color.text_primary, null));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void stroke(Paint p, int color, float widthDp) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(widthDp * density);
        p.setColor(getResources().getColor(color, null));
    }

    /** {@code slots}: today's 48 half-hours, or null; {@code nowMinute}: minutes since midnight, or -1. */
    void set(byte[] slots, int nowMinute) {
        this.slots = slots;
        this.nowMinute = nowMinute;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float labelGap = 17 * density, r = Math.min(cx, cy) - labelGap - label.getTextSize() / 2 - density;
        if (r <= 0) return;
        ring.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawCircle(cx, cy, r, track);
        if (slots != null) drawSchedule(canvas);
        float baseline = -(label.ascent() + label.descent()) / 2;
        for (int h = 0; h < 24; h += 3) {
            double a = Math.toRadians(h * 15 - 90);
            canvas.drawText(HOURS[h / 3], cx + (float) Math.cos(a) * (r + labelGap),
                    cy + (float) Math.sin(a) * (r + labelGap) + baseline, label);
        }
        if (nowMinute >= 0) {
            double a = Math.toRadians(nowMinute / 4.0 - 90);
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            dot.setAlpha(46);
            canvas.drawCircle(x, y, 9 * density, dot);
            dot.setAlpha(255);
            canvas.drawCircle(x, y, 5.5f * density, dot);
        }
    }

    /** Runs of the same state; a run also ends where "now" is, so the past part can be dimmed on its own. */
    private void drawSchedule(Canvas canvas) {
        int n = slots.length, nowSlot = nowMinute < 0 ? -1 : nowMinute * n / 1440;
        float perSlot = 360f / n;
        int start = 0;
        for (int i = 1; i <= n; i++) {
            if (i < n && slots[i] == slots[start] && i != nowSlot) continue;
            byte state = slots[start];
            boolean past = nowSlot >= 0 && i <= nowSlot;
            float from = start * perSlot - 90, sweep = (i - start) * perSlot;
            if (state == Outages.ON) {
                on.setAlpha(past ? 40 : 77);
                canvas.drawArc(ring, from, sweep, false, on);
            } else if (state == Outages.OFF || state == Outages.MAYBE) {
                Paint p = state == Outages.OFF ? off : maybe;
                // Leave a little room for the round ends, so neighbouring blocks stay apart.
                float inset = Math.min(sweep / 3, 2.4f);
                p.setAlpha(past ? 90 : 255);
                canvas.drawArc(ring, from + inset, sweep - 2 * inset, false, p);
            }
            start = i;
        }
    }
}
