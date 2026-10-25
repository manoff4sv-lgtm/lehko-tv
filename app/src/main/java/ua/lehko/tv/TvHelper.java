package ua.lehko.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.FileObserver;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lehko's helper behind every app. The remote's star, YouTube and "all apps" buttons without the stock launcher:
 * Google TV hands those buttons to its launcher, so with the launcher turned off the system took the presses and
 * nothing happened. An accessibility service's key filter sees a key before the system hands it out, so this one opens
 * what {@link Keys} says and keeps the press; every other key, and any press it can't open anything for, goes on
 * untouched. Over whatever is on screen it shows the outage warning and a changed schedule ({@link OutageWarning}),
 * and it ends the sleep timer ({@link SleepTimer}); when the TV is turned on it can open an input. It reads no screen
 * content. HomeMode turns it on with Lehko where it can act (Google TV's launcher installed, or a star button on the
 * remote), over the debugging connection, and off with "Bring back".
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
        // Any button in the sleep timer's last minute keeps the TV on; the press goes on as usual.
        if (sleepCard != 0 && e.getAction() == KeyEvent.ACTION_DOWN) keepOn();
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

    private static final long CARD_MS = 12_000;

    private boolean screenOn, listening;
    /** helper.txt as last read, and whether it was rewritten since (then it is read again when next needed). */
    private Map<String, String> settings = Collections.emptyMap();
    private boolean settingsOld = true;
    /**
     * Tells when the home screen's process renames a new helper.txt over the old one (inotify, no polling), so a sleep
     * timer just set is seen at once. Kept in a field: an observer that is collected stops telling.
     */
    private FileObserver watcher;
    /** The helper's own notes (Keys.STATE). */
    private Map<String, String> state;
    private TextView card;
    /** The sleep timer (its moment) whose last minute the card shows, or 0. */
    private long sleepCard;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String action = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenOn = false;
                screenOff();
                return;
            }
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                screenOn = true;
                wakeLater(1);
            }
            // The screen on, or the clock or time zone set anew: what is due is counted again.
            arm();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            arm();
        }
    };

    /** helper.txt was written anew. */
    private final Runnable reread = new Runnable() {
        @Override public void run() {
            settingsOld = true;
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
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(screen, f);
        listening = true;
        // The path form: the File one is Android 10+.
        watcher = new FileObserver(getFilesDir().getPath(), FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
            @Override public void onEvent(int event, String path) {
                if (Keys.FILE.equals(path)) App.MAIN.post(reread);
            }
        };
        watcher.startWatching();
        // Started with the system: the TV was just turned on from cold, which brings no "screen on".
        if (screenOn && SystemClock.elapsedRealtime() < COLD_START_MS) wakeLater(COLD_TRIES);
        arm();
    }

    @Override public void onDestroy() {
        if (listening) unregisterReceiver(screen);
        listening = false;
        if (watcher != null) watcher.stopWatching();
        App.MAIN.removeCallbacks(reread);
        App.MAIN.removeCallbacks(tick);
        App.MAIN.removeCallbacks(wake);
        removeCard();
        super.onDestroy();
    }

    private void screenOff() {
        App.MAIN.removeCallbacks(tick);
        App.MAIN.removeCallbacks(wake);
        removeCard();
        // Turned off before the sleep timer's end or in its last moments (with the remote, or by the TV's own timer):
        // that ends it, so turning the TV on again doesn't put it straight back to sleep.
        long at = Keys.number(settings(), SleepTimer.P_AT);
        if (SleepTimer.step(at, Keys.number(state, SleepTimer.P_DONE), System.currentTimeMillis()) != SleepTimer.NONE) {
            note(SleepTimer.P_DONE, String.valueOf(at));
        }
    }

    /** helper.txt, read again after the home screen's process rewrote it. */
    private Map<String, String> settings() {
        if (settingsOld) {
            settingsOld = false;
            settings = Keys.read(getFilesDir());
        }
        return settings;
    }

    /** Shows what is due now and sets the timer for the next thing. */
    private void arm() {
        App.MAIN.removeCallbacks(tick);
        if (!screenOn) return;
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        Map<String, String> s = settings();
        long at = Keys.number(s, SleepTimer.P_AT);
        int step = SleepTimer.step(at, Keys.number(state, SleepTimer.P_DONE), now);
        // Turned off or set anew on the home screen meanwhile: the old timer's card goes.
        if (sleepCard != 0 && (sleepCard != at || step != SleepTimer.CARD)) removeCard();
        if (step == SleepTimer.SLEEP) {
            sleep(at);
        } else if (step == SleepTimer.CARD) {
            // Before any other card: it asks for a button press.
            if (sleepCard != at && mayAct()) {
                showCard(getString(R.string.sleep_card), getColor(R.color.text_primary), 0);
                if (card != null) sleepCard = at;
            }
            next = Math.min(next, at);
        } else if (step == SleepTimer.WAIT) {
            next = Math.min(next, at - SleepTimer.CARD_MS);
        }
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
                            : R.color.power_maybe), CARD_MS);
                } else {
                    note(OutageWarning.P_SEEN, String.valueOf(OutageWarning.changedId(entry)));
                    showCard(changed, getColor(R.color.text_primary), CARD_MS);
                }
            }
            next = Math.min(next, OutageWarning.nextDue(runs, now, Keys.number(state, OutageWarning.P_WARNED)));
        }
        // Nothing ahead: a new helper.txt, the screen coming on or the clock set anew looks again.
        if (next != Long.MAX_VALUE) App.MAIN.postDelayed(tick, Math.max(1_000, next - now));
    }

    /** A button pressed in the sleep timer's last minute: the TV stays on. */
    private void keepOn() {
        note(SleepTimer.P_DONE, String.valueOf(sleepCard));
        removeCard();
        arm();
    }

    /**
     * The sleep timer's end: noted first (the home screen stops showing it), then the TV goes to sleep through the
     * debugging connection, off the main thread (it never asks for permission).
     */
    private void sleep(long at) {
        note(SleepTimer.P_DONE, String.valueOf(at));
        removeCard();
        if (!mayAct()) return;
        App.ADB.execute(new Runnable() {
            @Override public void run() {
                try {
                    HomeMode.sleep(TvHelper.this);
                } catch (IOException | RuntimeException e) {
                    // No debugging: Android 9+ lets an accessibility service lock the screen, the nearest it has.
                    if (Build.VERSION.SDK_INT < 28) return;
                    App.MAIN.post(new Runnable() {
                        @Override public void run() {
                            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                        }
                    });
                }
            }
        });
    }

    // MARK: - When the TV is turned on

    /** After the screen comes on: the TV's own start (Lehko's home screen) comes first. */
    private static final long WAKE_DELAY_MS = 1_500, WAKE_RETRY_MS = 3_000;
    /** Booted this recently when the helper starts: a cold start. Its inputs may still be coming up, so it tries on. */
    private static final long COLD_START_MS = 3 * 60_000L;
    private static final int COLD_TRIES = 10;
    private int wakeTries;

    private void wakeLater(int tries) {
        App.MAIN.removeCallbacks(wake);
        wakeTries = tries;
        App.MAIN.postDelayed(wake, WAKE_DELAY_MS);
    }

    /** Opens the input chosen in Lehko settings, as the home screen's buttons do, if the TV still has it. */
    private final Runnable wake = new Runnable() {
        @Override public void run() {
            final String choice = settings().get(Keys.P_WAKE);
            if (choice == null || !choice.startsWith(Keys.WAKE_INPUT) || !screenOn || !mayAct()) return;
            final Context app = TvHelper.this;
            // The TV's input list takes a few calls to the system: not on the thread that answers the keys.
            App.IO.execute(new Runnable() {
                @Override public void run() {
                    final Inputs.Input in = Keys.wakeInput(choice, Inputs.load(app));
                    App.MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (!screenOn) return;
                            if (in != null) Inputs.open(app, in);
                            else if (--wakeTries > 0) App.MAIN.postDelayed(wake, WAKE_RETRY_MS);
                        }
                    });
                }
            });
        }
    };

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
     * hairline. It takes no focus and no touch, so the app under it goes on as before. It goes after {@code ms} (0:
     * when it is taken away).
     */
    private void showCard(String text, int color, long ms) {
        removeCard();
        float dp = getResources().getDisplayMetrics().density;
        final TextView t = new TextView(this);
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
                t.announceForAccessibility(spoken);
            }
        });
        if (ms > 0) App.MAIN.postDelayed(fadeCard, ms);
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
        sleepCard = 0;
    }
}
