package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageInstaller;

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

    @Test public void notReadyYet() throws Exception {
        // The release is made on GitHub's page; CI attaches Lehko.apk minutes later.
        Update.Release r = Update.parse("{\"tag_name\":\"v0.11.0\",\"assets\":[]}");
        assertEquals("0.11.0", r.version);
        assertNull(r.url);
        assertNull(Update.parse("{\"tag_name\":\"v0.11.0\"}").url);
        assertNull(Update.parse(LATEST.replace("\"Lehko.apk\"", "\"Lehko-test.apk\"")).url);
    }

    @Test public void nothingToInstall() throws Exception {
        // Not a version tag; a Lehko.apk somewhere other than GitHub, over plain http, empty or oversized.
        String url = "\"https://github.com/manoff4sv-lgtm/lehko-tv/releases/download/v0.10.0-beta.2/Lehko.apk\"";
        assertNull(Update.parse(LATEST.replace("v0.10.0-beta.2\",\"name", "nightly\",\"name")));
        assertNull(Update.parse(LATEST.replace(url, "\"https://example.com/Lehko.apk\"")));
        assertNull(Update.parse(LATEST.replace(url, "\"http://github.com/manoff4sv-lgtm/lehko-tv/Lehko.apk\"")));
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
        // Whatever app/build.gradle takes as a tag.
        assertTrue(Update.enabled("1.0.0-beta."));
        assertTrue(Update.newer("1.0.0-beta.1", "1.0.0-beta."));
    }

    @Test public void theDownloadedFile() {
        assertTrue(Update.fits("ua.lehko.tv", "0.10.0-beta.3", "0.10.0-beta.2"));
        // Betas of one version share a versionCode: the same one again is fine.
        assertTrue(Update.fits("ua.lehko.tv", "0.10.0-beta.2", "0.10.0-beta.2"));
        assertFalse(Update.fits("ua.lehko.tv", "0.9.0", "0.10.0-beta.2"));
        assertFalse(Update.fits("com.example.other", "0.11.0", "0.10.0"));
        assertFalse(Update.fits("ua.lehko.tv", null, "0.10.0"));
    }

    @Test public void theInstallersAnswerInWords() {
        assertEquals(R.string.update_cancelled, Update.resultText(PackageInstaller.STATUS_FAILURE_ABORTED, null));
        assertEquals(R.string.update_storage, Update.resultText(PackageInstaller.STATUS_FAILURE_STORAGE, null));
        assertEquals(R.string.update_signature, Update.resultText(PackageInstaller.STATUS_FAILURE_CONFLICT, null));
        assertEquals(R.string.update_signature, Update.resultText(PackageInstaller.STATUS_FAILURE,
                "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package ua.lehko.tv signatures do not match previously installed version; ignoring!"));
        assertEquals(R.string.update_failed, Update.resultText(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, null));
        assertEquals(R.string.update_failed, Update.resultText(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_INTERNAL_ERROR"));
        // A damaged download, not another signing key.
        assertEquals(R.string.update_failed, Update.resultText(PackageInstaller.STATUS_FAILURE_INVALID,
                "INSTALL_PARSE_FAILED_NO_CERTIFICATES: Failed to collect certificates from /data/app/vmdl1.tmp/Lehko.apk"));
    }
}
