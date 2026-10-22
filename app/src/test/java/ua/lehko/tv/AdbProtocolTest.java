package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.Socket;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lehko's ADB client against tools/fake_adbd.py (a fake TV, itself checked against the adb-shell library):
 * first connection with key approval, later ones by signature, shell output in many packets.
 */
public class AdbProtocolTest {
    /** LEHKO_TEST_PORT moves the fake adbd ports, so test runs in several checkouts don't collide. */
    private static final int BASE = Integer.parseInt(System.getenv().getOrDefault("LEHKO_TEST_PORT", "25555"));
    private static final int PORT = BASE;
    private static Process server;
    private static File keys;

    @BeforeClass public static void startFakeTv() throws Exception {
        File script = new File("tools/fake_adbd.py");
        if (!script.isFile()) script = new File("../tools/fake_adbd.py");
        assumeTrue("fake_adbd.py not found", script.isFile());
        try {
            server = new ProcessBuilder("python3", script.getPath(), String.valueOf(PORT)).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
        } catch (java.io.IOException e) {
            assumeTrue("python3 not available", false);
        }
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("127.0.0.1", PORT)) {
                break;
            } catch (java.io.IOException e) {
                Thread.sleep(100);
            }
        }
        keys = Files.createTempDirectory("lehko-keys").toFile();
    }

    @AfterClass public static void stopFakeTv() {
        if (server != null) server.destroy();
    }

    @Test public void connectApproveThenSign() throws Exception {
        final AtomicInteger prompts = new AtomicInteger();
        Adb.Approval approval = new Adb.Approval() {
            @Override public void waiting() {
                prompts.incrementAndGet();
            }
        };
        try (Adb adb = Adb.connect(keys, "127.0.0.1", PORT, 10_000, approval)) {
            assertEquals("hello\n", adb.shell("echo hello"));
            assertEquals("com.google.android.apps.tv.launcherx", adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME").trim());
        }
        assertEquals("first time the TV asks", 1, prompts.get());
        assertTrue(new File(keys, "adbkey").isFile());

        try (Adb adb = Adb.connect(keys, "127.0.0.1", PORT, 10_000, approval)) {
            String big = adb.shell("big");
            assertEquals(100_001, big.length());
            assertEquals("", adb.shell("unknown command"));
            assertEquals("package:com.example.off\n", adb.shell("pm list packages -d"));
        }
        assertEquals("the saved key is accepted without asking again", 1, prompts.get());
    }

    @Test public void closedPort() throws Exception {
        try {
            Adb.connect(keys, "127.0.0.1", PORT + 1, 1_000, null).close();
            throw new AssertionError("should fail");
        } catch (Adb.Failure e) {
            assertEquals(Adb.Failure.PORT_CLOSED, e.reason);
        }
    }
}
