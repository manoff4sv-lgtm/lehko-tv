package ua.lehko.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * One day of the outage schedule as a bar of 48 half-hours: off in red, possible in amber, on in green. The past is
 * dimmed and a white mark shows "now". Hour labels below.
 */
public final class OutageBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private byte[] slots;
    /** Minute of the day for the "now" mark, or -1 for none (tomorrow). */
    private int nowMinute = -1;
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
        text.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
        text.setTextAlign(Paint.Align.CENTER);
    }

    void set(byte[] slots, int nowMinute) {
        this.slots = slots;
        this.nowMinute = nowMinute;
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int h = Math.round(10 * density + 4 * density + text.getTextSize() + 2 * density);
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
            int color = colorOf(slots[start]);
            boolean past = nowSlot >= 0 && i <= nowSlot;
            paint.setColor(color);
            paint.setAlpha(past ? 90 : slots[start] == Outages.ON ? 150 : 255);
            rect.set(start * cell + (start == 0 ? 0 : gap / 2), 0, i * cell - (i == slots.length ? 0 : gap / 2), barH);
            canvas.drawRoundRect(rect, r, r, paint);
            start = i;
        }
        if (nowMinute >= 0) {
            float x = w * nowMinute / 1440f;
            paint.setColor(mark);
            paint.setAlpha(255);
            rect.set(x - density, -2 * density, x + density, barH + 2 * density);
            canvas.drawRoundRect(rect, density, density, paint);
        }
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
