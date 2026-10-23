package ua.lehko.tv;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** One process, two low-priority worker threads: the home screen must stay light on a 1 GB TV. */
public final class App extends Application {
    /** Package-manager work and app pictures. The network goes on NET. */
    static final ExecutorService IO = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override public Thread newThread(final Runnable r) {
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    r.run();
                }
            }, "lehko-io-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    /** Weather and outage downloads: one thread, so a slow network never holds up app lists and banners. */
    static final ExecutorService NET = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(final Runnable r) {
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    r.run();
                }
            }, "lehko-net");
            t.setDaemon(true);
            return t;
        }
    });

    /** Talking to the TV's debugging port: one switch at a time, never on the UI thread. */
    static final ExecutorService ADB = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "lehko-adb");
            t.setDaemon(true);
            return t;
        }
    });

    static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final String REPO_URL = "https://github.com/manoff4sv-lgtm/lehko-tv";

    /** versionName, read once at start (no generated BuildConfig needed). */
    static volatile String version = "?";

    @Override public void onCreate() {
        super.onCreate();
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        Watchdog.install(this);
    }

    /**
     * Android asks for memory back (memory running low, Lehko hidden behind an app or on the background list, which
     * on a 1 GB TV is nearly every time an app is in front): keep only the favorites' pictures. Their tiles hold them
     * anyway, so dropping them would free nothing, and the next favorites row would show names until they load again.
     */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) Apps.trimPictures(this, false);
    }
}
