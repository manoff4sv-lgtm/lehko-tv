package ua.lehko.tv;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Safety net: if Lehko crashes 3 times within 5 minutes while it is the home screen (on Android 11+ native crashes and
 * hangs on screen count too), it goes into safe mode. In safe mode the home screen shows only a plain "restoring"
 * message (nothing that could crash again), and the original home screen is brought back right away on the worker
 * thread, with a scheduled job as a second try. Safe mode ends when the original home screen is back, or when you turn
 * Lehko on again.
 */
final class Watchdog {
    private Watchdog() {}

    /** SINCE: nothing before it counts (Android's own record of crashes outlives a fresh start). */
    private static final String KEY = "crashes", SAFE = "safe_mode", SINCE = "crashes_since";
    private static final long WINDOW_MS = 5 * 60_000L;
    private static final int LIMIT = 3;
    private static volatile boolean reverting;
    /** Why the last switch back couldn't connect (an {@link Adb.Failure} reason), 0 if it could or failed otherwise. */
    static volatile int lastFailure;

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
        boolean on = HomeMode.isOn(c);
        if (on) recent += nativeCrashesAndHangs(c, now, p.getLong(SINCE, 0));
        if (recent >= LIMIT && on) {
            p.edit().putBoolean(SAFE, true).remove(KEY).putLong(SINCE, now).commit();
            revertInBackground(c);
            ModeJob.schedule(c, ModeJob.REVERT, 60_000);
        } else if (!list.isEmpty()) {
            p.edit().putString(KEY, keep.toString()).apply();
        }
    }

    /**
     * Native crashes (say, in the GPU driver) and hangs on screen never reach the handler above. Android 11+ keeps its
     * own record of them; Java crashes are in it too, but those are counted already.
     */
    private static int nativeCrashesAndHangs(Context c, long now, long since) {
        if (Build.VERSION.SDK_INT < 30) return 0;
        int n = 0;
        try {
            ActivityManager am = c.getSystemService(ActivityManager.class);
            // All records (Android keeps a few per app): the helper's exits must not push the home screen's out.
            for (ApplicationExitInfo x : am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 0)) {
                long t = x.getTimestamp();
                if (t <= since || t > now || now - t >= WINDOW_MS) continue;
                // Only the home screen's process: the remote's helper (":helper") is no home screen crash loop.
                if (!c.getPackageName().equals(x.getProcessName())) continue;
                // A hang counts only on screen: a slow receiver in the background of a busy 1 GB TV is no crash loop.
                if (x.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE
                        || (x.getReason() == ApplicationExitInfo.REASON_ANR
                        && x.getImportance() <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)) n++;
            }
        } catch (RuntimeException ignored) {
            // A vendor quirk: the handler's count alone.
        }
        return n;
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
        p.edit().remove(KEY).remove(SAFE).putLong(SINCE, System.currentTimeMillis()).commit();
    }
}
