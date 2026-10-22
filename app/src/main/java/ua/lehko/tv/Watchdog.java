package ua.lehko.tv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Safety net: if Lehko crashes 3 times within 5 minutes while it is the home screen, it goes into safe mode. In safe
 * mode the home screen shows only a plain "restoring" message (nothing that could crash again), and the original
 * home screen is brought back right away on the worker thread, with a scheduled job as a second try. Safe mode ends
 * when the original home screen is back, or when you turn Lehko on again.
 */
final class Watchdog {
    private Watchdog() {}

    private static final String KEY = "crashes", SAFE = "safe_mode";
    private static final long WINDOW_MS = 5 * 60_000L;
    private static final int LIMIT = 3;
    private static volatile boolean reverting;

    static void install(final Context app) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    record(app);
                } catch (Throwable ignored) {}
                if (previous != null) previous.uncaughtException(t, e);
                else System.exit(2);
            }
        });
        check(app);
    }

    private static void record(Context c) {
        SharedPreferences p = Prefs.of(c);
        String list = p.getString(KEY, "");
        p.edit().putString(KEY, (list.isEmpty() ? "" : list + ",") + System.currentTimeMillis()).commit();
    }

    /** At start: too many recent crashes while Lehko is the home screen? Then safe mode and switch back. */
    private static void check(Context c) {
        SharedPreferences p = Prefs.of(c);
        if (p.getBoolean(SAFE, false)) {
            // Still in safe mode (the last try didn't finish, or the process was killed): try again.
            revertInBackground(c);
            return;
        }
        String list = p.getString(KEY, "");
        if (list.isEmpty()) return;
        long now = System.currentTimeMillis();
        int recent = 0;
        StringBuilder keep = new StringBuilder();
        for (String s : list.split(",")) {
            try {
                long t = Long.parseLong(s);
                if (now - t < WINDOW_MS && t <= now) {
                    recent++;
                    keep.append(keep.length() > 0 ? "," : "").append(t);
                }
            } catch (NumberFormatException ignored) {}
        }
        if (recent >= LIMIT && HomeMode.isOn(c)) {
            p.edit().putBoolean(SAFE, true).remove(KEY).commit();
            revertInBackground(c);
            ModeJob.schedule(c, ModeJob.REVERT, 60_000);
        } else {
            p.edit().putString(KEY, keep.toString()).apply();
        }
    }

    static void revertInBackground(final Context c) {
        if (reverting) return;
        reverting = true;
        App.ADB.execute(new Runnable() {
            @Override public void run() {
                try {
                    HomeMode.revertAfterCrash(c.getApplicationContext(), null);
                } finally {
                    reverting = false;
                }
            }
        });
    }

    static boolean isReverting() {
        return reverting;
    }

    static boolean safeMode(Context c) {
        return safeMode(Prefs.of(c));
    }

    static boolean safeMode(SharedPreferences p) {
        return p.getBoolean(SAFE, false);
    }

    /** Fresh start: no crash history, no safe mode (after turning Lehko on, or once the original home is back). */
    static void clear(SharedPreferences p) {
        p.edit().remove(KEY).remove(SAFE).commit();
    }
}
