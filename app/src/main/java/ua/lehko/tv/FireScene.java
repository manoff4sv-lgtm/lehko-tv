package ua.lehko.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.SystemClock;
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
 * outage status in the dark top of the firebox, and the optional crackle (idle fireplace only). The clock and the
 * whole scene drift slowly, so nothing stays on the same pixels for long.
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

    /** How long the clock and the scene take to drift to their next spot. */
    private static final long GLIDE_MS = 40_000;

    final FrameLayout root;
    private final Context context;
    private final FireView fire;
    private final LinearLayout box;
    private final TextView outage;
    private final Random random = new Random();
    private final int shift;
    /** The drift's start and end: the clock box's x and y, then the fire's. */
    private final float[] from = new float[4], to = new float[4];
    private long glideStart;
    private Crackle crackle;
    private final boolean withSound;
    private boolean started, placed, gliding;

    /**
     * {@code withSound}: the idle fireplace may crackle; the screensaver never does. It often starts over a paused
     * video, and taking the audio focus from that player (then giving it back) is the last thing it needs.
     */
    FireScene(Context c, boolean withSound) {
        context = c;
        this.withSound = withSound;
        float dp = c.getResources().getDisplayMetrics().density;
        shift = Math.round(10 * dp);
        // No background: the fire view covers everything (less to fill 30 times a second).
        root = new FrameLayout(c);
        fire = new FireView(c);
        fire.setPaused(true);
        fire.onFrame = glide;
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
        // A second line only for "no update since".
        outage.setMaxLines(2);
        outage.setGravity(Gravity.CENTER_HORIZONTAL);
        outage.setPadding(0, Math.round(4 * dp), 0, 0);
        box.addView(outage);
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        updateOutage();
    }

    /**
     * Every minute: the outage line, and a new spot for the clock (and the scene) to drift to. The home screen is
     * paused under the fire (and may not have been shown for hours before a FireDream), so the schedule is
     * downloaded from here too.
     */
    private final Runnable drift = new Runnable() {
        @Override public void run() {
            updateOutage();
            Outages.refreshIfStale(context, outageFetched);
            int w = root.getWidth(), h = root.getHeight();
            if (w > 0 && box.getWidth() > 0) {
                // Under the arch, above the flames.
                float x = w * 0.27f + random.nextFloat() * Math.max(0, w * 0.46f - box.getWidth());
                float y = h * 0.09f + random.nextFloat() * Math.max(0, h * 0.19f - box.getHeight());
                float fx = random.nextInt(2 * shift + 1) - shift, fy = random.nextInt(2 * shift + 1) - shift;
                if (!placed) {
                    placed = true;
                    box.setTranslationX(Math.round(x));
                    box.setTranslationY(Math.round(y));
                    box.setVisibility(View.VISIBLE);
                } else {
                    from[0] = box.getTranslationX();
                    from[1] = box.getTranslationY();
                    from[2] = fire.getTranslationX();
                    from[3] = fire.getTranslationY();
                    to[0] = x;
                    to[1] = y;
                    to[2] = fx;
                    to[3] = fy;
                    glideStart = SystemClock.uptimeMillis();
                    // With animations off the fire stands still and brings no frames: straight there.
                    gliding = ValueAnimator.areAnimatorsEnabled();
                    if (!gliding) moveTo(1);
                }
            }
            App.MAIN.postDelayed(this, placed ? 60_000 : 200);
        }
    };

    /**
     * The drift, a step on each of the fire's own frames. (A ViewPropertyAnimator would redraw the whole scene on
     * every vsync for the 40 seconds, twice the fire's rate.)
     */
    private final Runnable glide = new Runnable() {
        @Override public void run() {
            if (!gliding) return;
            float t = Math.min(1f, (SystemClock.uptimeMillis() - glideStart) / (float) GLIDE_MS);
            gliding = t < 1;
            moveTo(t * t * (3 - 2 * t));
        }
    };

    /** The clock and the fire {@code f} of the way along; whole pixels, so text and bricks aren't resampled. */
    private void moveTo(float f) {
        box.setTranslationX(Math.round(from[0] + (to[0] - from[0]) * f));
        box.setTranslationY(Math.round(from[1] + (to[1] - from[1]) * f));
        fire.setTranslationX(Math.round(from[2] + (to[2] - from[2]) * f));
        fire.setTranslationY(Math.round(from[3] + (to[3] - from[3]) * f));
    }

    /** A download finished: the new line now rather than in a minute. */
    private final Runnable outageFetched = new Runnable() {
        @Override public void run() {
            if (started) updateOutage();
        }
    };

    private void updateOutage() {
        if (!Outages.enabled(context)) {
            outage.setVisibility(View.GONE);
            return;
        }
        Outages.Schedule s = Outages.cached(context);
        long now = System.currentTimeMillis();
        String line = OutageText.status(context, s, Outages.status(s, now));
        // Offline for hours the schedule may have changed: it says since when, as on the home screen.
        if (s != null && Outages.old(context, now)) {
            line += "\n" + context.getString(R.string.weather_old, OutageText.since(context, Outages.fetchedAt(context)));
        }
        outage.setText(line);
        outage.setVisibility(View.VISIBLE);
    }

    void start() {
        if (started) return;
        started = true;
        App.MAIN.removeCallbacks(drift);
        App.MAIN.post(drift);
        fire.setPaused(false);
        if (withSound && sound(context) && !musicPlaying()) {
            crackle = new Crackle(context);
            crackle.start();
        }
    }

    /** Music left playing in the background: no crackle over it. (Asked before ours starts, which would count too.) */
    private boolean musicPlaying() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return am != null && am.isMusicActive();
    }

    void stop() {
        started = false;
        fire.setPaused(true);
        App.MAIN.removeCallbacks(drift);
        if (crackle != null) {
            crackle.stop();
            crackle = null;
        }
    }
}
