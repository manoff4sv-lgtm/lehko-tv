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
    static final long AUTO_EVERY_MS = 3 * 60_000L;
    static final long MB = 1024 * 1024;
    /** "Low": under a fifth of all memory, or under 500 MB (Eugene's choice for 1 GB TVs: clean early). */
    static final long LOW_MB = 500;

    static ActivityManager.MemoryInfo info(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) am.getMemoryInfo(mi);
        return mi;
    }

    static long freeMb(Context c) {
        return info(c).availMem / MB;
    }

    static boolean low(long availBytes, long totalBytes) {
        return availBytes < totalBytes / 5 || availBytes < LOW_MB * MB;
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

    /**
     * The button: closes background apps and says how much memory that freed. May show "Allow USB debugging?" (then
     * {@code p} hears about it). Slow: call on the ADB thread.
     */
    static String clean(Context c, HomeMode.Progress p) {
        long before = info(c).availMem;
        try {
            HomeMode.killAll(c, true, p);
        } catch (IOException | RuntimeException e) {
            // Android 14 has no other way: say why nothing was closed, not that nothing was running.
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                String why = HomeMode.explain(c, e);
                // Exception texts are English: only the kind of error goes into the translated sentence.
                return why != null ? why : c.getString(R.string.error_generic, e.getClass().getSimpleName());
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
        return freed >= 10 ? c.getString(R.string.memory_freed, freed) : c.getString(R.string.memory_nothing);
    }

    /**
     * On the home screen: when memory is low and the last time was a while ago, closes cached apps through debugging
     * in the background. Never asks for permission and stays silent when it can't.
     */
    static void maybeAutoClean(Context c) {
        final Context app = c.getApplicationContext();
        if (!autoEnabled(app)) return;
        SharedPreferences p = Prefs.of(app);
        long now = System.currentTimeMillis();
        long last = p.getLong(LAST_AUTO, 0);
        if (now - last < AUTO_EVERY_MS && last <= now) return;
        ActivityManager.MemoryInfo mi = info(app);
        if (!low(mi.availMem, mi.totalMem)) return;
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
