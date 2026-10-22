package ua.lehko.tv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Safety net: if Lehko crashes 3 times within 5 minutes while it is the home screen, the original home screen comes
 * back by itself (through {@link ModeJob}), so a bug can never leave the TV without a working home screen.
 */
final class Watchdog {
    private Watchdog() {}

    private static final String KEY = "crashes";
    private static final long WINDOW_MS = 5 * 60_000L;
    private static final int LIMIT = 3;

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

    /** At start: too many recent crashes while Lehko is the home screen? Then switch back to the original one. */
    private static void check(Context c) {
        SharedPreferences p = Prefs.of(c);
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
        p.edit().putString(KEY, keep.toString()).apply();
        if (recent >= LIMIT && HomeMode.isOn(c)) {
            clear(c);
            ModeJob.schedule(c, ModeJob.REVERT, 0);
        }
    }

    static void clear(Context c) {
        Prefs.of(c).edit().remove(KEY).commit();
    }
}
