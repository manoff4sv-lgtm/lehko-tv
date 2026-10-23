package ua.lehko.tv;

import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

/** A light screensaver: a dim clock with the date and temperature that moves every minute (no burn-in). */
public final class ClockDream extends DreamService {
    private final Random random = new Random();
    private LinearLayout box;
    private FrameLayout root;
    private TextView weather;
    private long weatherAt;
    private boolean placed;

    private final Runnable move = new Runnable() {
        @Override public void run() {
            if (root == null || box == null) return;
            int w = root.getWidth() - box.getWidth(), h = root.getHeight() - box.getHeight();
            if (w > 0 && h > 0) {
                final View b = box;
                final float x = random.nextInt(w), y = random.nextInt(h);
                if (!placed) {
                    // Invisible until laid out: it fades in at its first spot, not in the corner.
                    placed = true;
                    b.setTranslationX(x);
                    b.setTranslationY(y);
                    b.animate().alpha(1f).setDuration(600).start();
                } else {
                    b.animate().alpha(0f).setDuration(600).withEndAction(new Runnable() {
                        @Override public void run() {
                            // The dream downloads nothing: a forecast that got old meanwhile goes, while unseen.
                            if (weather != null && Net.stale(System.currentTimeMillis(), weatherAt, Weather.OLD_MS)) {
                                weather.setVisibility(View.GONE);
                            }
                            b.setTranslationX(x);
                            b.setTranslationY(y);
                            b.animate().alpha(1f).setDuration(600).start();
                        }
                    }).start();
                }
            }
            App.MAIN.postDelayed(this, placed ? 60_000 : 200);
        }
    };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setAlpha(0f);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(96);
        clock.setTextColor(0xFFB8BEC6);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL));
        box.addView(clock);

        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        TextClock date = new TextClock(this);
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextSize(20);
        date.setTextColor(0xFF7A828C);
        box.addView(date);

        // Only a recent forecast: next to the clock, an old temperature would pass for the current one.
        Weather.Data d = Weather.cached(this);
        weatherAt = Weather.cacheTime(this);
        if (d != null && !Net.stale(System.currentTimeMillis(), weatherAt, Weather.OLD_MS)) {
            weather = new TextView(this);
            weather.setText(Weather.degrees(d.temp) + "  " + getString(Weather.text(d.code)));
            weather.setTextSize(20);
            weather.setTextColor(0xFF7A828C);
            weather.setPadding(0, 8, 0, 0);
            box.addView(weather);
        }
        root.addView(box, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        setContentView(root);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        App.MAIN.removeCallbacks(move);
        App.MAIN.post(move);
    }

    @Override public void onDreamingStopped() {
        App.MAIN.removeCallbacks(move);
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        App.MAIN.removeCallbacks(move);
        root = null;
        box = null;
        weather = null;
        super.onDetachedFromWindow();
    }
}
