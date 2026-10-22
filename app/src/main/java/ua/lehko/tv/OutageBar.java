package ua.lehko.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * One day of the outage schedule as a bar of 48 half-hours. The state shows in the shape as well as the colour, so it
 * reads without telling red from green: power on is a thin green line, off a solid red block, possible an amber
 * outlined block, unknown a thin grey line. The past is dimmed and a white mark shows "now". Hour labels below.
 */
public final class OutageBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private byte[] slots;
    /** Minute of the day for the "now" mark, or -1 for none (tomorrow). */
    private int nowMinute = -1;
    /** Hour numbers under the bar (off for the compact one in the home screen's details). */
    private boolean labels = true;
    private final int on, off, maybe, unknown, mark;
    private final float density;

    public OutageBar(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = getResources().getDisplayMetrics().density;
        on = getResources().getColor(R.color.power_on, null);
        off = getResources().getColor(R.color.power_off, null);
        maybe = getResources().getColor(R.color.power_maybe, null);
        unknown = getResources().getColor(R.color.track, null);
        mark = getResources().getColor(R.color.text_primary, null);
        text.setColor(getResources().getColor(R.color.text_dim, null));
        text.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        text.setTextAlign(Paint.Align.CENTER);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(Math.max(1, 1.5f * density));
        outline.setColor(maybe);
    }

    void showLabels(boolean show) {
        labels = show;
        requestLayout();
    }

    void set(byte[] slots, int nowMinute) {
        this.slots = slots;
        this.nowMinute = nowMinute;
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int h = labels ? Math.round(10 * density + 4 * density + text.getTextSize() + 2 * density) : Math.round(10 * density);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(h, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (slots == null) return;
        float w = getWidth(), barH = 10 * density, r = barH / 2, gap = Math.max(1, density);
        float cell = w / slots.length;
        int nowSlot = nowMinute < 0 ? -1 : nowMinute / 30;
        // Runs of the same state, so the bar has few rounded pieces instead of 48 boxes.
        int start = 0;
        for (int i = 1; i <= slots.length; i++) {
            // A run also ends where "now" starts, so the past part can be dimmed on its own.
            if (i < slots.length && slots[i] == slots[start] && !(nowSlot > 0 && i == nowSlot)) continue;
            byte state = slots[start];
            boolean past = nowSlot >= 0 && i <= nowSlot;
            boolean thin = state == Outages.ON || state == Outages.UNKNOWN;
            float top = thin ? barH * 0.35f : 0, bottom = thin ? barH * 0.65f : barH;
            rect.set(start * cell + (start == 0 ? 0 : gap / 2), top, i * cell - (i == slots.length ? 0 : gap / 2), bottom);
            paint.setColor(colorOf(state));
            if (state == Outages.MAYBE) {
                // Possible: a see-through block with an amber outline.
                paint.setAlpha(past ? 30 : 70);
                canvas.drawRoundRect(rect, r, r, paint);
                float inset = outline.getStrokeWidth() / 2;
                rect.inset(inset, inset);
                outline.setAlpha(past ? 110 : 255);
                canvas.drawRoundRect(rect, r - inset, r - inset, outline);
            } else {
                paint.setAlpha(past ? 90 : 255);
                float rr = (bottom - top) / 2;
                canvas.drawRoundRect(rect, rr, rr, paint);
            }
            start = i;
        }
        if (nowMinute >= 0) {
            float x = w * nowMinute / 1440f;
            paint.setColor(mark);
            paint.setAlpha(255);
            rect.set(x - density, -2 * density, x + density, barH + 2 * density);
            canvas.drawRoundRect(rect, density, density, paint);
        }
        if (!labels) return;
        float y = barH + 4 * density - text.ascent();
        for (int h = 0; h <= 24; h += 3) {
            float x = w * h / 24f;
            text.setTextAlign(h == 0 ? Paint.Align.LEFT : h == 24 ? Paint.Align.RIGHT : Paint.Align.CENTER);
            canvas.drawText(String.valueOf(h), x, y, text);
        }
    }

    private int colorOf(byte state) {
        switch (state) {
            case Outages.ON: return on;
            case Outages.OFF: return off;
            case Outages.MAYBE: return maybe;
            default: return unknown;
        }
    }
}
