package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateTest {
    /** GitHub's answer for the latest release (2026-10-25), shortened. */
    static final String LATEST = "{\"tag_name\":\"v0.10.0-beta.2\",\"name\":\"Lehko 0.10.0 beta 2\",\"draft\":false,"
            + "\"prerelease\":false,\"assets\":[{\"name\":\"Lehko.apk.sha256\",\"size\":64,\"browser_download_url\":"
            + "\"https://github.com/manoff4sv-lgtm/lehko-tv/releases/download/v0.10.0-beta.2/Lehko.apk.sha256\"},"
            + "{\"name\":\"Lehko.apk\",\"size\":168242,\"content_type\":\"application/vnd.android.package-archive\","
            + "\"browser_download_url\":\"https://github.com/manoff4sv-lgtm/lehko-tv/releases/download/v0.10.0-beta.2/Lehko.apk\"}]}";

    @Test public void theLatestRelease() throws Exception {
        Update.Release r = Update.parse(LATEST);
        assertEquals("0.10.0-beta.2", r.version);
        assertEquals("https://github.com/manoff4sv-lgtm/lehko-tv/releases/download/v0.10.0-beta.2/Lehko.apk", r.url);
        assertEquals(168242, r.size);
        assertEquals("https://api.github.com/repos/manoff4sv-lgtm/lehko-tv/releases/latest", Update.LATEST);
    }

    @Test public void nothingToInstall() throws Exception {
        // CI is still building the APK.
        assertNull(Update.parse("{\"tag_name\":\"v0.11.0\",\"assets\":[]}"));
        assertNull(Update.parse("{\"tag_name\":\"v0.11.0\"}"));
        // Not a version tag, a plain-http address, an empty or oversized file.
        assertNull(Update.parse(LATEST.replace("v0.10.0-beta.2\",\"name", "nightly\",\"name")));
        assertNull(Update.parse(LATEST.replace("\"https://github.com/manoff4sv-lgtm/lehko-tv/releases/download/v0.10.0-beta.2/Lehko.apk\"",
                "\"http://example.com/Lehko.apk\"")));
        assertNull(Update.parse(LATEST.replace("168242", "0")));
        assertNull(Update.parse(LATEST.replace("168242", String.valueOf(31L * 1024 * 1024))));
    }

    @Test public void versionOrder() {
        assertTrue(Update.newer("0.10.0-beta.3", "0.10.0-beta.2"));
        assertTrue(Update.newer("0.10.0-beta.10", "0.10.0-beta.9"));
        // A release beats every beta of its numbers, and the numbers come first.
        assertTrue(Update.newer("0.10.0", "0.10.0-beta.9"));
        assertFalse(Update.newer("0.10.0-beta.9", "0.10.0"));
        assertTrue(Update.newer("0.10.1-beta.1", "0.10.0"));
        assertTrue(Update.newer("0.11", "0.10.12"));
        assertTrue(Update.newer("1.0.0", "0.99.99"));
        assertTrue(Update.newer("0.10.0-rc.1", "0.10.0-beta.5"));
        assertTrue(Update.newer("0.10.0-beta.2.1", "0.10.0-beta.2"));
        // The same version, written two ways.
        assertFalse(Update.newer("0.10", "0.10.0"));
        assertFalse(Update.newer("0.10.0-beta.2", "0.10.0-beta.2"));
        // Not versions: nothing is newer.
        assertFalse(Update.newer("0.11.0", "quick"));
        assertFalse(Update.newer("0.11.0", null));
        assertFalse(Update.newer("v0.11.0", "0.10.0"));
        assertFalse(Update.newer("0.11.0-", "0.10.0"));
    }

    @Test public void onlyReleaseBuildsCheck() {
        assertTrue(Update.enabled("0.10.0-beta.2"));
        assertTrue(Update.enabled("1.0"));
        assertFalse(Update.enabled(Update.DEV));
        assertFalse(Update.enabled("quick"));
        assertFalse(Update.enabled("?"));
    }
}
