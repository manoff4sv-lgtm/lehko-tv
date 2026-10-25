package ua.lehko.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lehko's own updates. Once a day the home screen asks GitHub for the latest release, and a newer one gets a notice
 * and shows in Settings. The parsing and the version order are pure, so they can be tested.
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

    static final String CHECKED = "update_checked", VERSION = "update_version", APK_URL = "update_url",
            SIZE = "update_size", NOTICED = "update_noticed";

    private static volatile boolean checking;
    private static volatile long failedAt;

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
                    // Offline, or GitHub's limit: again in an hour.
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
        SharedPreferences p = Prefs.of(c);
        SharedPreferences.Editor edit = p.edit().putLong(CHECKED, System.currentTimeMillis());
        String newer = r != null && newer(r.version, App.version) ? r.version : null;
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

    /** A release: its version (the tag without the "v") and where its Lehko.apk is. */
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
     * "browser_download_url":"https://...", "size":168242}, ...]}. Null without a version tag or a Lehko.apk (CI may
     * still be building it).
     */
    static Release parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        String tag = o.optString("tag_name", "");
        String v = tag.startsWith("v") ? tag.substring(1) : tag;
        JSONArray assets = o.optJSONArray("assets");
        if (version(v) == null || assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null || !ASSET.equals(a.optString("name"))) continue;
            String url = a.optString("browser_download_url", "");
            long size = a.optLong("size", 0);
            if (url.startsWith("https://") && size > 0 && size <= MAX_BYTES) return new Release(v, url, size);
        }
        return null;
    }

    // MARK: - Versions

    private static final Pattern VERSION_FORMAT =
            Pattern.compile("(\\d{1,9})\\.(\\d{1,9})(?:\\.(\\d{1,9}))?(?:-([0-9A-Za-z]+(?:\\.[0-9A-Za-z]+)*))?");

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
}
