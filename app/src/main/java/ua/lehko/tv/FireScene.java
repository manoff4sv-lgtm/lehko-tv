package ua.lehko.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Random;

/**
 * The fireplace screen, shared by {@link FireplaceActivity} and {@link FireDream}: the fire, a dim clock with the
 * outage status above the mantel, and the optional crackle. The clock and the whole scene drift slowly, so nothing
 * stays on the same pixels for long.
 */
final class FireScene {
    static final String P_IDLE = "fire_idle_min", P_SOUND = "fire_sound";
    /** Minutes of idling on the home screen before the fireplace starts (0: never). */
    static final int[] IDLE_CHOICES = {5, 10, 15, 0};

    static int idleMinutes(Context c) {
        return Prefs.of(c).getInt(P_IDLE, 5);
    }

    static boolean sound(Context c) {
        return Prefs.of(c).getBoolean(P_SOUND, false);
    }

    final FrameLayout root;
    private final Context context;
    private final FireView fire;
    private final LinearLayout box;
    private final TextView outage;
    private final Random random = new Random();
    private final int shift;
    private Crackle crackle;
    private boolean started, placed;

    FireScene(Context c) {
        context = c;
        float dp = c.getResources().getDisplayMetrics().density;
        shift = Math.round(10 * dp);
        // No background: the fire view covers everything (less to fill 30 times a second).
        root = new FrameLayout(c);
        fire = new FireView(c);
        fire.setPaused(true);
        // A little larger than the screen, so the slow drift never shows an edge.
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        fp.setMargins(-shift, -shift, -shift, -shift);
        root.addView(fire, fp);

        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        // Hidden until it has its first spot; dim through its colours (view alpha would cost a layer per frame).
        box.setVisibility(View.INVISIBLE);
        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        clock.setTextColor(0xCCB09A86);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        box.addView(clock);
        outage = new TextView(c);
        outage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        outage.setTextColor(0xFFA8968A); // dim, but still 7:1 on the dark wall
        outage.setMaxLines(1);
        outage.setPadding(0, Math.round(4 * dp), 0, 0);
        box.addView(outage);
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        updateOutage();
    }

    /** Every minute: the outage line, and a new spot for the clock (and the scene) to drift to. */
    private final Runnable drift = new Runnable() {
        @Override public void run() {
            updateOutage();
            int w = root.getWidth(), h = root.getHeight();
            if (w > 0 && box.getWidth() > 0) {
                float x = w * 0.06f + random.nextFloat() * Math.max(0, w * 0.88f - box.getWidth());
                float y = h * 0.025f + random.nextFloat() * Math.max(0, h * 0.175f - box.getHeight());
                float fx = random.nextInt(2 * shift + 1) - shift, fy = random.nextInt(2 * shift + 1) - shift;
                if (!placed) {
                    placed = true;
                    box.setTranslationX(x);
                    box.setTranslationY(y);
                    box.setVisibility(View.VISIBLE);
                } else {
                    box.animate().translationX(x).translationY(y).setDuration(40_000).start();
                    fire.animate().translationX(fx).translationY(fy).setDuration(40_000).start();
                }
            }
            App.MAIN.postDelayed(this, placed ? 60_000 : 200);
        }
    };

    private void updateOutage() {
        if (!Outages.enabled(context)) {
            outage.setVisibility(View.GONE);
            return;
        }
        Outages.Schedule s = Outages.cached(context);
        outage.setText(OutageText.status(context, s, Outages.status(s, System.currentTimeMillis())));
        outage.setVisibility(View.VISIBLE);
    }

    void start() {
        if (started) return;
        started = true;
        App.MAIN.removeCallbacks(drift);
        App.MAIN.post(drift);
        fire.setPaused(false);
        if (sound(context)) {
            crackle = new Crackle();
            crackle.start();
        }
    }

    void stop() {
        started = false;
        fire.setPaused(true);
        App.MAIN.removeCallbacks(drift);
        box.animate().cancel();
        fire.animate().cancel();
        if (crackle != null) {
            crackle.stop();
            crackle = null;
        }
    }
}
