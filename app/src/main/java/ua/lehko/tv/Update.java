package ua.lehko.tv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lehko's own updates. Once a day the home screen asks GitHub for the latest release; Settings downloads its Lehko.apk
 * and hands it to the TV's installer, which asks for a confirmation (UpdateReceiver brings its screen up). The parsing
 * and the version order are pure, so they can be tested.
 */
final class Update {
    private Update() {}

    static final String LATEST = App.REPO_URL.replace("https://github.com/", "https://api.github.com/repos/")
            + "/releases/latest";
    static final String ASSET = "Lehko.apk";
    /** What a Gradle build without a release tag calls itself: nothing to compare. */
    static final String DEV = "0.0.0-dev";
    static final long EVERY_MS = 24 * 3_600_000L;
    /** After a failed check (offline, or GitHub's hourly limit for one address): again in an hour, not a day. */
    static final long RETRY_MS = 3_600_000L;
    /** Lehko is well under a megabyte: a file this big isn't it. */
    static final long MAX_BYTES = 30L * 1024 * 1024;

    /** An answer older than this would turn up in Settings with nothing to tie it to. */
    static final long RESULT_MS = 10 * 60_000L;

    static final String CHECKED = "update_checked", VERSION = "update_version", APK_URL = "update_url",
            SIZE = "update_size", NOTICED = "update_noticed", RESULT = "update_result", SESSION = "update_session",
            RESULT_AT = "update_result_at", RESULT_ERROR = "update_result_error";

    private static volatile boolean checking;
    private static volatile long failedAt;
    /** A download or hand-over is under way: Settings may have been closed and opened again meanwhile. */
    static volatile boolean installing;
    /** The installer's confirmation screen for the session handed over, until its answer. */
    private static volatile Intent confirmation;

    /** A release build: the dev build and the quick build ("quick") have no version to compare. */
    static boolean enabled() {
        return enabled(App.version);
    }

    static boolean enabled(String ours) {
        return !DEV.equals(ours) && version(ours) != null;
    }

    /** The newer version the last check found, or null. */
    static String available(Context c) {
        SharedPreferences p = Prefs.of(c);
        String v = p.getString(VERSION, null);
        return v != null && !p.getString(APK_URL, "").isEmpty() && newer(v, App.version) ? v : null;
    }

    /** Checked at least once, so "the latest" can be said. */
    static boolean checked(Context c) {
        return Prefs.of(c).getLong(CHECKED, 0) > 0;
    }

    // MARK: - Checking

    /** From the home screen's refresh (main thread): once a day, in the background. A new version gets a notice. */
    static void maybeCheck(Context c) {
        final Context app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        if (checking || !enabled() || !Net.stale(now, Prefs.of(app).getLong(CHECKED, 0), EVERY_MS)
                || !Net.stale(now, failedAt, RETRY_MS)) {
            return;
        }
        checking = true;
        App.NET.execute(new Runnable() {
            @Override public void run() {
                try {
                    check(app, true);
                    failedAt = 0;
                } catch (IOException | JSONException | RuntimeException e) {
                    // Offline, GitHub's limit, or the release's file not there yet: again in an hour.
                    failedAt = System.currentTimeMillis();
                } finally {
                    checking = false;
                }
            }
        });
    }

    /**
     * Asks GitHub for the latest release and remembers it. Returns its version when it is newer than this one, else
     * null. {@code announce}: a home notice the first time a version is found (the daily check; Settings says it
     * itself). Network: off the UI thread.
     */
    static String check(Context c, boolean announce) throws IOException, JSONException {
        Release r;
        try {
            r = parse(Net.get(LATEST, 15_000, "Accept", "application/vnd.github+json"));
        } catch (Net.HttpError e) {
            // No release published yet.
            if (e.code != 404) throw e;
            r = null;
        }
        String newer = r != null && newer(r.version, App.version) ? r.version : null;
        // Its Lehko.apk isn't there yet: not a check, so the daily one tries again in an hour.
        if (newer != null && r.url == null) throw new Failure(R.string.update_not_ready, null);
        SharedPreferences p = Prefs.of(c);
        SharedPreferences.Editor edit = p.edit().putLong(CHECKED, System.currentTimeMillis());
        if (newer == null) {
            edit.remove(VERSION).remove(APK_URL).remove(SIZE);
        } else {
            edit.putString(VERSION, newer).putString(APK_URL, r.url).putLong(SIZE, r.size);
            if (!newer.equals(p.getString(NOTICED, null))) {
                edit.putString(NOTICED, newer);
                if (announce) Prefs.notice(c, c.getString(R.string.update_notice, newer));
            }
        }
        edit.apply();
        return newer;
    }

    /** A release: its version (the tag without the "v") and where its Lehko.apk is (null: not attached yet). */
    static final class Release {
        final String version, url;
        final long size;

        Release(String version, String url, long size) {
            this.version = version;
            this.url = url;
            this.size = size;
        }
    }

    /**
     * GitHub's answer for the latest release: {"tag_name":"v0.10.0-beta.3", "assets":[{"name":"Lehko.apk",
     * "browser_download_url":"https://github.com/...", "size":168242}, ...]}. Null without a version tag, or with a
     * Lehko.apk that isn't on GitHub or is empty or too big. Without a Lehko.apk, a Release with no url: CI attaches
     * it minutes after a release is made on GitHub's page.
     */
    static Release parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        String tag = o.optString("tag_name", "");
        String v = tag.startsWith("v") ? tag.substring(1) : tag;
        if (version(v) == null) return null;
        JSONArray assets = o.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null || !ASSET.equals(a.optString("name"))) continue;
            String url = a.optString("browser_download_url", "");
            long size = a.optLong("size", 0);
            // GitHub's own address (any repository name: a renamed one is sent on); it redirects to its file host.
            boolean usable = url.startsWith("https://github.com/") && size > 0 && size <= MAX_BYTES;
            return usable ? new Release(v, url, size) : null;
        }
        return new Release(v, null, 0);
    }

    // MARK: - Versions

    /** What app/build.gradle accepts as a release tag, so every release build can check. */
    private static final Pattern VERSION_FORMAT =
            Pattern.compile("(\\d{1,9})\\.(\\d{1,9})(?:\\.(\\d{1,9}))?(?:-([0-9A-Za-z.]+))?");

    /** A release tag's version without its "v": 1.2.3, 1.2 (= 1.2.0), or a pre-release such as 1.2.3-beta.4. */
    static final class Version {
        final int[] numbers;
        /** "beta.4", or null for a release. */
        final String pre;

        Version(int[] numbers, String pre) {
            this.numbers = numbers;
            this.pre = pre;
        }
    }

    /** {@code s} as a version, or null when it isn't one ("quick", "?"). */
    static Version version(String s) {
        Matcher m = VERSION_FORMAT.matcher(s == null ? "" : s);
        if (!m.matches()) return null;
        int[] n = {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3))};
        return new Version(n, m.group(4));
    }

    /** Whether {@code a} is a later version than {@code b}; false when either isn't a version. */
    static boolean newer(String a, String b) {
        Version x = version(a), y = version(b);
        return x != null && y != null && compare(x, y) > 0;
    }

    /**
     * Numbers first; then a release beats any pre-release of the same numbers; then the pre-release parts piece by
     * piece, numbers by value (beta.10 after beta.9) and words alphabetically (beta before rc).
     */
    static int compare(Version a, Version b) {
        for (int i = 0; i < 3; i++) {
            if (a.numbers[i] != b.numbers[i]) return Integer.compare(a.numbers[i], b.numbers[i]);
        }
        if (a.pre == null || b.pre == null) return a.pre == null ? (b.pre == null ? 0 : 1) : -1;
        String[] x = a.pre.split("\\."), y = b.pre.split("\\.");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            boolean numbers = x[i].matches("\\d{1,9}") && y[i].matches("\\d{1,9}");
            int c = numbers ? Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i])) : x[i].compareTo(y[i]);
            if (c != 0) return c;
        }
        return Integer.compare(x.length, y.length);
    }

    // MARK: - Installing

    /** Why the update didn't reach the installer, or can't yet, in words for Settings. */
    static final class Failure extends IOException {
        final int text;

        Failure(int text, Throwable cause) {
            super(cause);
            this.text = text;
        }
    }

    /**
     * Asks GitHub for the latest release again (since the last check it may have been made again, its file replaced
     * or taken down), downloads it into the cache, makes sure it is Lehko of this version or a later one and hands it
     * to the TV's installer, which then asks for a confirmation. False when nothing newer is out any more. The file
     * goes once the installer has its own copy, or on failure. A Failure says why in words; any other IOException is
     * the network. Slow: on App.NET.
     */
    static boolean install(Context c, HomeMode.Progress p) throws IOException {
        SharedPreferences prefs = Prefs.of(c);
        prefs.edit().remove(RESULT).apply();
        dropSessions(c);
        try {
            if (check(c, false) == null) return false;
        } catch (JSONException e) {
            throw new IOException(e);
        }
        File apk = new File(c.getCacheDir(), ASSET);
        try {
            download(prefs.getString(APK_URL, ""), prefs.getLong(SIZE, 0), apk, c, p);
            PackageInfo file = c.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
            if (file == null || !fits(file.packageName, file.versionName, App.version)) {
                throw new Failure(R.string.update_bad_file, null);
            }
            p.step(c.getString(R.string.update_installing));
            try {
                commit(c, apk);
            } catch (IOException | RuntimeException e) {
                throw new Failure(R.string.update_failed, e);
            }
            return true;
        } finally {
            apk.delete();
        }
    }

    /** The downloaded file is Lehko, of this version or a later one (Android itself refuses a lower versionCode). */
    static boolean fits(String pkg, String versionName, String ours) {
        Version v = version(versionName), o = version(ours);
        return HomeMode.PKG.equals(pkg) && v != null && o != null && compare(v, o) >= 0;
    }

    /** Exactly {@code size} bytes from {@code url} into {@code to}, the percentage as Settings' status. */
    private static void download(String url, long size, File to, Context c, HomeMode.Progress p) throws IOException {
        // Follows the redirect to GitHub's file host by itself (https to https only).
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("User-Agent", Net.userAgent());
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Net.HttpError(code);
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[16 * 1024];
                long total = 0;
                int shown = -1;
                for (int n; (n = in.read(buf)) != -1; ) {
                    total += n;
                    if (total > size) throw new Failure(R.string.update_bad_file, null);
                    out.write(buf, 0, n);
                    int percent = (int) (total * 100 / size);
                    if (percent != shown) {
                        shown = percent;
                        p.step(c.getString(R.string.update_downloading, percent));
                    }
                }
                if (total != size) throw new IOException("download cut short");
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Gives up on earlier tries. A session whose confirmation never came up (another app was in front) would wait
     * with its own copy of the file, and Android lets an app keep only so many. Their "aborted" answers are ignored.
     */
    private static void dropSessions(Context c) {
        confirmation = null;
        Prefs.of(c).edit().remove(SESSION).apply();
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        for (PackageInstaller.SessionInfo s : installer.getMySessions()) abandon(installer, s.getSessionId());
    }

    /** A PackageInstaller session (no shell): the installer answers UpdateReceiver. */
    private static void commit(Context c, File apk) throws IOException {
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        params.setSize(apk.length());
        int id = installer.createSession(params);
        boolean handed = false;
        try (PackageInstaller.Session s = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = s.openWrite(ASSET, 0, apk.length())) {
                byte[] buf = new byte[16 * 1024];
                for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
                s.fsync(out);
            }
            // Mutable: the installer adds its answer to the intent.
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            Intent answer = new Intent(c, UpdateReceiver.class);
            // Before the commit: the answer may come at once.
            Prefs.of(c).edit().putInt(SESSION, id).apply();
            s.commit(PendingIntent.getBroadcast(c, id, answer, flags).getIntentSender());
            handed = true;
        } finally {
            if (!handed) abandon(installer, id);
        }
    }

    static void abandon(PackageInstaller installer, int id) {
        try {
            installer.abandonSession(id);
        } catch (RuntimeException e) {
            // Already gone.
        }
    }

    /** The installer's answer is about the session handed over last, not one given up on. */
    static boolean current(Context c, int id) {
        return id != -1 && id == Prefs.of(c).getInt(SESSION, -1);
    }

    /**
     * The installer wants the user's OK (UpdateReceiver). Android lets Lehko bring its screen up only while a Lehko
     * screen is on show; when none is, it refuses without a word, so OK on "Update" in Settings brings it up again.
     * False when there is no such screen at all.
     */
    static boolean confirm(Context c, Intent screen) {
        confirmation = screen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Apps.start(c, screen);
    }

    /** Settings, in front: brings a confirmation that is still waiting up again. False when none is waiting. */
    static boolean confirmAgain(Context c) {
        Intent screen = confirmation;
        if (screen == null) return false;
        if (c.getPackageManager().getPackageInstaller().getSessionInfo(Prefs.of(c).getInt(SESSION, -1)) == null) {
            // The session ended without an answer reaching Lehko.
            confirmation = null;
            return false;
        }
        return Apps.start(c, screen);
    }

    /**
     * The installer's final answer (UpdateReceiver). On success Android restarts Lehko as the new version; anything
     * else is reported.
     */
    static void finished(Context c, int status, String message) {
        confirmation = null;
        Prefs.of(c).edit().remove(SESSION).apply();
        if (status == PackageInstaller.STATUS_SUCCESS) return;
        String text = c.getString(resultText(status, message), App.REPO_URL.replace("https://", ""));
        report(c, text, status != PackageInstaller.STATUS_FAILURE_ABORTED);
    }

    /**
     * Why the update didn't go in, or that it was cancelled, from any thread. Settings shows it in its status card,
     * at once when it is in sight (its prefs listener), else when it is opened within RESULT_MS. Lehko's home says it
     * too; when Settings shows it first, the home notice goes (takeResult).
     */
    static void report(Context c, String text, boolean error) {
        SharedPreferences.Editor edit = Prefs.of(c).edit().putString(RESULT, text).putBoolean(RESULT_ERROR, error)
                .putLong(RESULT_AT, System.currentTimeMillis());
        // Only Lehko's home shows notices: with Lehko off this one would pop up, stale, next time. One apply, so
        // Settings' listener finds the notice there to take back.
        if (HomeMode.isOn(c)) edit.putString(Prefs.NOTICE, text);
        edit.apply();
    }

    /** The installer's answer in words: cancelled, no room, another signing key (a test build), or anything else. */
    static int resultText(int status, String message) {
        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) return R.string.update_cancelled;
        if (status == PackageInstaller.STATUS_FAILURE_STORAGE) return R.string.update_storage;
        // "INSTALL_FAILED_UPDATE_INCOMPATIBLE: ... signatures do not match", which most versions report as a conflict.
        // Not "...NO_CERTIFICATES" or "...INCONSISTENT_CERTIFICATES": a broken file, which a new try may fix.
        if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || (message != null && message.contains("signatures"))) {
            return R.string.update_signature;
        }
        return R.string.update_failed;
    }

    /** The last answer for Settings' status card, once and only while it is fresh (RESULT_ERROR says how to show it). */
    static String takeResult(Context c) {
        SharedPreferences p = Prefs.of(c);
        String r = p.getString(RESULT, null);
        if (r == null) return null;
        boolean fresh = !Net.stale(System.currentTimeMillis(), p.getLong(RESULT_AT, 0), RESULT_MS);
        SharedPreferences.Editor edit = p.edit().remove(RESULT);
        // Seen here: the home screen needn't say it again.
        if (fresh && r.equals(p.getString(Prefs.NOTICE, null))) edit.remove(Prefs.NOTICE);
        edit.apply();
        return fresh ? r : null;
    }
}
