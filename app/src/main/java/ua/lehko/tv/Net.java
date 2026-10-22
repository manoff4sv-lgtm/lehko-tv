package ua.lehko.tv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small HTTPS GET helper. Everything Lehko fetches is public data without keys or accounts. */
final class Net {
    private Net() {}

    private static String userAgent() {
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
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                return read(in, 2 * 1024 * 1024);
            }
        } finally {
            c.disconnect();
        }
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
