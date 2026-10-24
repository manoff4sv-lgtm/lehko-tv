package ua.lehko.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Debug;
import android.os.Process;

import java.io.IOException;

/**
 * Free memory and closing background apps. With the TV's debugging allowed this is "am kill-all" (only what Android
 * keeps cached); without it, Android's own killBackgroundProcesses for each app (works up to Android 13).
 */
final class Memory {
    private Memory() {}

    static final String P_AUTO = "memory_auto_clean", LAST_AUTO = "memory_last_auto";
    /** Automatic cleaning at most this often. */
    static final long AUTO_EVERY_MS = 30 * 60_000L;
    /** With no fireplace to come, the automatic check runs after this long without a key press on the home screen. */
    static final long AUTO_IDLE_MS = 5 * 60_000L;
    /** ...and at least this long before the TV's own screensaver starts. */
    static final long AUTO_BEFORE_SAVER_MS = 30_000L;
    static final long MB = 1024 * 1024;

    static ActivityManager.MemoryInfo info(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) am.getMemoryInfo(mi);
        return mi;
    }

    static long freeMb(Context c) {
        return info(c).availMem / MB;
    }

    /** Lehko's own memory (PSS) in KB, or 0. */
    static long ownPssKb(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        try {
            Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(new int[]{Process.myPid()});
            return mi != null && mi.length > 0 ? mi[0].getTotalPss() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static boolean autoEnabled(Context c) {
        return Prefs.of(c).getBoolean(P_AUTO, true);
    }

    /** What the button did: the text to show, and whether it says why nothing could be closed. */
    static final class Result {
        final String text;
        /** A reason to read (often with a command to type), not a quick "Freed 180 MB". */
        final boolean failed;

        Result(String text, boolean failed) {
            this.text = text;
            this.failed = failed;
        }
    }

    /**
     * The button: closes background apps and says how much memory that freed. May show "Allow USB debugging?" (then
     * {@code p} hears about it). Slow: call on the ADB thread.
     */
    static Result clean(Context c, HomeMode.Progress p) {
        long before = info(c).availMem;
        try {
            HomeMode.killAll(c, true, p);
        } catch (IOException | RuntimeException e) {
            // Android 14 has no other way: say why nothing was closed, not that nothing was running.
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                String why = HomeMode.explain(c, e);
                // Exception texts are English: only the kind of error goes into the translated sentence.
                return new Result(why != null ? why : c.getString(R.string.error_generic, e.getClass().getSimpleName()),
                        true);
            }
            killBackground(c);
        }
        // Give the kernel a moment to take the memory back.
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long freed = (info(c).availMem - before) / MB;
        return new Result(freed >= 10 ? c.getString(R.string.memory_freed, freed) : c.getString(R.string.memory_nothing),
                false);
    }

    /**
     * The home screen has been left alone for a while, so nobody is about to open an app: when Android says memory is
     * low and the last time was a while ago, closes cached apps through debugging in the background. Never asks for
     * permission and stays silent when it can't.
     */
    static void maybeAutoClean(Context c) {
        final Context app = c.getApplicationContext();
        if (!autoEnabled(app)) return;
        SharedPreferences p = Prefs.of(app);
        long now = System.currentTimeMillis();
        if (!autoDue(now, p.getLong(LAST_AUTO, 0), info(app).lowMemory)) return;
        p.edit().putLong(LAST_AUTO, now).apply();
        App.ADB.execute(new Runnable() {
            @Override public void run() {
                try {
                    HomeMode.killAll(app, false, null);
                } catch (IOException | RuntimeException ignored) {
                    // No debugging, or not allowed: nothing to do.
                }
            }
        });
    }

    /**
     * Only on Android's own "memory is low" (a fixed line of ours, 500 MB, cleaned on nearly every return home on a
     * 1 GB TV), at most every half hour. A last time after now (the clock was put right) doesn't hold it off.
     */
    static boolean autoDue(long now, long last, boolean lowMemory) {
        return lowMemory && Net.stale(now, last, AUTO_EVERY_MS);
    }

    /**
     * When the automatic check runs with no fireplace to come: after AUTO_IDLE_MS without a key press, but before the
     * TV's own screensaver ({@code tvMs}, SCREEN_OFF_TIMEOUT; 0 or less: none). Its start pauses the home screen, and
     * with the TV's usual 5 minutes (the very case where it wins over the fireplace) the two would race.
     */
    static long autoIdleMs(int tvMs) {
        if (tvMs <= 0) return AUTO_IDLE_MS;
        return Math.min(AUTO_IDLE_MS, Math.max(tvMs - AUTO_BEFORE_SAVER_MS, tvMs / 2L));
    }

    /** Without debugging: Android's own way, for every app that isn't system plumbing (Android 13 and older). */
    static void killBackground(Context c) {
        // Android 14 lets an app close only its own processes: hundreds of calls would do nothing.
        if (android.os.Build.VERSION.SDK_INT >= 34) return;
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        try {
            for (ApplicationInfo ai : c.getPackageManager().getInstalledApplications(0)) {
                if ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0 || HomeMode.isProtected(ai.packageName)) continue;
                try {
                    am.killBackgroundProcesses(ai.packageName);
                } catch (RuntimeException ignored) {
                    // Android 14 allows this only for the app itself.
                }
            }
        } catch (RuntimeException ignored) {
            // A package list too big for one binder call: nothing freed.
        }
    }
}
