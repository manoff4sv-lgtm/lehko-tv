package ua.lehko.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lehko's helper behind every app. The remote's star, YouTube and "all apps" buttons without the stock launcher:
 * Google TV hands those buttons to its launcher, so with the launcher turned off the system took the presses and
 * nothing happened. An accessibility service's key filter sees a key before the system hands it out, so this one opens
 * what {@link Keys} says and keeps the press; every other key, and any press it can't open anything for, goes on
 * untouched. Over whatever is on screen it shows the outage warning and a changed schedule ({@link OutageWarning}).
 * It reads no screen content. HomeMode turns it on with Lehko where it can act (Google TV's launcher installed, or a
 * star button on the remote), over the debugging connection, and off with "Bring back".
 */
public final class TvHelper extends AccessibilityService {
    /** The button whose press opened something: its repeats and its release are kept too, never half a press. */
    private int taken = KeyEvent.KEYCODE_UNKNOWN;

    /**
     * Main process: Lehko is on and the helper is on the TV's list, so what it does over other apps works. (The helper
     * itself asks {@link #lehkoHome} instead: the home screen's preferences belong to the other process.)
     */
    static boolean running(Context c) {
        return HomeMode.isOn(c) && HomeMode.helperListed(c);
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        if (!Keys.handled(key, e.getSource())) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            taken = open(key) ? key : KeyEvent.KEYCODE_UNKNOWN;
            return taken == key;
        }
        boolean ours = key == taken;
        if (ours && e.getAction() == KeyEvent.ACTION_UP) taken = KeyEvent.KEYCODE_UNKNOWN;
        return ours;
    }

    /**
     * Finds the first thing the button stands for that can be opened, and opens it once the key is answered: Android
     * waits only half a second for that answer before handing the key on anyway. The settings are read at each press
     * (a few bytes).
     */
    private boolean open(int key) {
        try {
            PackageManager pm = getPackageManager();
            String star = key == Keys.STAR ? Keys.read(getFilesDir()).get(Keys.P_STAR) : null;
            List<String> targets = Keys.opens(key, star, Keys.launcherOff(pm));
            // Left on after Lehko stopped being the home screen some other way (by hand, say): out of the way.
            if (targets.isEmpty() || !lehkoHome(pm)) return false;
            for (String target : targets) {
                Intent i = Keys.ALL_APPS.equals(target) ? new Intent(this, AllAppsActivity.class)
                        : Apps.launchIntent(pm, target);
                if (i == null) continue;
                // Allowed from the background: Android lets an accessibility service start activities.
                final Intent start = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        Apps.start(TvHelper.this, start);
                    }
                });
                return true;
            }
        } catch (RuntimeException e) {
            // The package manager failed (the system busy or restarting): the key goes on as before.
        }
        return false;
    }

    private boolean lehkoHome(PackageManager pm) {
        ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && home.activityInfo != null && getPackageName().equals(home.activityInfo.packageName);
    }

    /** Whether it may show or do anything now: only for Lehko's home screen, like the buttons. */
    private boolean mayAct() {
        try {
            return lehkoHome(getPackageManager());
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Keys only. Its config has to name some event type: announcements, which it ignores.
    }

    @Override public void onInterrupt() {}

    // MARK: - Over other apps: one timer for the next thing due, nothing while the screen is off

    /** Looks at helper.txt at least this often while the screen is on: a stat of one small file. */
    private static final long LOOK_MS = 60_000;
    private static final long CARD_MS = 12_000;

    private boolean screenOn, listening;
    /** helper.txt as last read, and its time: read again only when it changed. */
    private Map<String, String> settings = Collections.emptyMap();
    private long settingsTime = -1;
    /** The helper's own notes (Keys.STATE). */
    private Map<String, String> state;
    private TextView card;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(i.getAction());
            if (screenOn) {
                arm();
            } else {
                App.MAIN.removeCallbacks(tick);
                removeCard();
            }
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            arm();
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        screenOn = pm == null || pm.isInteractive();
        state = Keys.read(getFilesDir(), Keys.STATE);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
        listening = true;
        arm();
    }

    @Override public void onDestroy() {
        if (listening) unregisterReceiver(screen);
        listening = false;
        App.MAIN.removeCallbacks(tick);
        removeCard();
        super.onDestroy();
    }

    /** helper.txt, read again when its time changed (the home screen's process rewrites it whole). */
    private Map<String, String> settings() {
        long t = new File(getFilesDir(), Keys.FILE).lastModified();
        if (t != settingsTime) {
            settingsTime = t;
            settings = Keys.read(getFilesDir());
        }
        return settings;
    }

    /** Shows what is due now and sets the timer for the next thing, a minute away at most. */
    private void arm() {
        App.MAIN.removeCallbacks(tick);
        if (!screenOn) return;
        long now = System.currentTimeMillis(), next = now + LOOK_MS;
        Map<String, String> s = settings();
        // One card at a time: the next one is looked for when this one has gone.
        if (OutageWarning.on(s) && card == null) {
            List<OutageWarning.Run> runs = OutageWarning.parse(s.get(OutageWarning.P_RUNS));
            OutageWarning.Run due = OutageWarning.due(runs, now, Keys.number(state, OutageWarning.P_WARNED));
            String entry = s.get(OutageWarning.P_CHANGED);
            String changed = OutageWarning.changed(entry, now, Keys.number(state, OutageWarning.P_SEEN));
            if ((due != null || changed != null) && mayAct()) {
                // The outage first: the change waits the 12 seconds.
                if (due != null) {
                    note(OutageWarning.P_WARNED, String.valueOf(due.start));
                    showCard(OutageWarning.text(this, due, now), getColor(due.kind == Outages.OFF ? R.color.ember
                            : R.color.power_maybe));
                } else {
                    note(OutageWarning.P_SEEN, String.valueOf(OutageWarning.changedId(entry)));
                    showCard(changed, getColor(R.color.text_primary));
                }
            }
            next = Math.min(next, OutageWarning.nextDue(runs, now, Keys.number(state, OutageWarning.P_WARNED)));
        }
        App.MAIN.postDelayed(tick, Math.max(1_000, next - now));
    }

    /** Writes one of the helper's own notes. */
    private void note(String key, String value) {
        state.put(key, value);
        try {
            Keys.putAll(getFilesDir(), Keys.STATE, Collections.singletonMap(key, value));
        } catch (IOException ignored) {
            // Full storage: remembered until the helper restarts.
        }
    }

    // MARK: - The card

    /**
     * A small card at the top right, inside the TV's safe area, over whatever is on screen: the app's dark card with a
     * hairline. It takes no focus and no touch, so the app under it goes on as before.
     */
    private void showCard(String text, int color) {
        removeCard();
        float dp = getResources().getDisplayMetrics().density;
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setMaxWidth(Math.round(440 * dp));
        t.setBackgroundResource(R.drawable.helper_card);
        t.setPadding(Math.round(20 * dp), Math.round(12 * dp), Math.round(20 * dp), Math.round(12 * dp));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = getResources().getDimensionPixelSize(R.dimen.safe_h);
        lp.y = getResources().getDimensionPixelSize(R.dimen.safe_v);
        try {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).addView(t, lp);
        } catch (RuntimeException e) {
            return; // the window manager refused (the system restarting): no card this time
        }
        card = t;
        t.setAlpha(0f);
        t.animate().alpha(1f).setDuration(200).start();
        // Screen readers hear it too; posted, as it isn't attached yet.
        final String spoken = text;
        t.post(new Runnable() {
            @Override public void run() {
                if (card != null) card.announceForAccessibility(spoken);
            }
        });
        App.MAIN.postDelayed(fadeCard, CARD_MS);
    }

    private final Runnable fadeCard = new Runnable() {
        @Override public void run() {
            final View v = card;
            if (v == null) return;
            v.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override public void run() {
                    if (card != v) return;
                    removeCard();
                    arm(); // the next card, if one waited
                }
            }).start();
        }
    };

    private void removeCard() {
        App.MAIN.removeCallbacks(fadeCard);
        if (card == null) return;
        card.animate().cancel();
        try {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(card);
        } catch (RuntimeException ignored) {
            // Already gone with its window.
        }
        card = null;
    }
}
