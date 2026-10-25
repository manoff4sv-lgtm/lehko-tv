package ua.lehko.tv;

import android.content.Context;
import android.net.ConnectivityManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small HTTPS GET helper. Everything Lehko fetches is public data without keys or accounts. */
final class Net {
    private Net() {}

    static String userAgent() {
        return "Lehko/" + App.version + " (Android TV launcher; +" + App.REPO_URL + ")";
    }

    static String get(String url) throws IOException {
        return get(url, 12_000);
    }

    /** {@code headers}: name, value, name, value... */
    static String get(String url, int timeoutMs, String... headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", userAgent());
            c.setRequestProperty("Accept", "application/json");
            for (int i = 0; i + 1 < headers.length; i += 2) c.setRequestProperty(headers[i], headers[i + 1]);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new HttpError(code);
            try (InputStream in = c.getInputStream()) {
                return read(in, 2 * 1024 * 1024);
            }
        } finally {
            c.disconnect();
        }
    }

    /** The server answered, but not with 2xx (429 means "too many requests": back off). */
    static final class HttpError extends IOException {
        final int code;

        HttpError(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    /**
     * Is what was downloaded at {@code savedAt} older than {@code maxAgeMs}? A time after {@code now} counts as old too:
     * the clock was ahead then and has been put right, so its age is unknown (and waiting for it would take as long).
     */
    static boolean stale(long now, long savedAt, long maxAgeMs) {
        long age = now - savedAt;
        return age < 0 || age > maxAgeMs;
    }

    /** Is there a network at all (the router may be down in an outage)? Unknown counts as yes. */
    static boolean online(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm == null || cm.getActiveNetwork() != null;
    }

    static String read(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > limit) throw new IOException("response too large");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
