package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;

/** Letting Lehko install its update over debugging (HomeMode.allowInstalls) on the simulated Google TV. */
public class UpdateAdbTest {
    /** LEHKO_TEST_PORT moves the fake adbd ports, so test runs in several checkouts don't collide. */
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("LEHKO_TEST_PORT", "25555")) + 7;
    private static Process server;
    private static File keys;

    @BeforeClass public static void startFakeTv() throws Exception {
        File script = new File("tools/fake_adbd.py");
        if (!script.isFile()) script = new File("../tools/fake_adbd.py");
        assumeTrue("fake_adbd.py not found", script.isFile());
        try {
            server = new ProcessBuilder("python3", script.getPath(), String.valueOf(PORT), "--tv")
                    .redirectErrorStream(true)
                    .redirectOutput(new File(System.getProperty("java.io.tmpdir"), "fake-tv-update.log"))
                    .start();
        } catch (IOException e) {
            assumeTrue("python3 not available", false);
        }
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("127.0.0.1", PORT)) {
                break;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        keys = Files.createTempDirectory("lehko-keys").toFile();
    }

    @AfterClass public static void stopFakeTv() {
        if (server != null) server.destroy();
    }

    private static JSONObject state() throws Exception {
        try (Adb adb = Adb.connect(keys, "127.0.0.1", PORT, 5_000, null)) {
            return new JSONObject(adb.shell("fake:state"));
        }
    }

    @Test public void allowsInstallsWithItsOneCommand() throws Exception {
        // A key the TV hasn't seen: "Allow USB debugging?" comes up first, and the status says so.
        HomeModeTest.Env e = new HomeModeTest.Env(Files.createTempDirectory("lehko-keys").toFile());
        e.port = PORT;
        final int[] asked = {0};
        HomeMode.allowInstalls(e, new HomeMode.Progress() {
            @Override public void step(String text) {}

            @Override public void approval() {
                asked[0]++;
            }
        });
        assertEquals(1, asked[0]);
        JSONObject state = state();
        assertEquals("allow", state.getJSONObject("settings").getJSONObject("appops")
                .getString("ua.lehko.tv REQUEST_INSTALL_PACKAGES"));
        // Nothing else changes: Lehko isn't the home screen here, and it stays that way.
        assertEquals("com.google.android.apps.tv.launcherx", state.getString("holder"));
        assertEquals(1, state.getJSONObject("settings").getJSONObject("appops").length());
    }
}
