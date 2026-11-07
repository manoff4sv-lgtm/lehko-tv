package ua.lehko.tv;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * The home's own inverter, as told by any program on the home network that watches it and broadcasts a short JSON
 * packet every ten seconds or so. Lehko only listens, so there is nothing to set up: no address, no account.
 *
 * <p>Packet (UDP broadcast to {@link #PORT}): {@code {"lehko":"inverter","v":1,"grid":true,"soc":99,"load":272,
 * "bat_a":0.7,"for_s":3600,"runtime":960}}. {@code for_s}: seconds in the present grid state (a duration, so the
 * sender's clock doesn't matter; -1 when not known); {@code runtime}: minutes the battery lasts at this load, -1 when
 * not known.
 *
 * <p>Nothing that comes in is shown as text: only numbers in sane ranges, put into Lehko's own sentences. The home
 * screen listens while it is in front; the helper listens all the time and shows a card when the power goes or comes
 * back, or the battery runs low.
 */
final class Inverter {
    private Inverter() {}

    static final int PORT = 8897;
    /** A status older than this is not shown: the phone or its program is off. */
    static final long FRESH_MS = 90_000;
    /** Battery levels that get a card while the house runs on the battery. */
    static final int[] LOW = {30, 20, 10};
    /** A gap this long between packets starts over: what changed meanwhile is old news. */
    static final long GAP_MS = 5 * 60_000L;

    static final int NONE = 0, OFF = 1, ON = 2, LOW_BATTERY = 3;

    static final class Status {
        final boolean grid;
        final int soc, loadW, forS, runtimeMin;
        final double batA;
        /** When it came in (System.currentTimeMillis of this TV). */
        final long at;

        Status(boolean grid, int soc, int loadW, double batA, int forS, int runtimeMin, long at) {
            this.grid = grid;
            this.soc = soc;
            this.loadW = loadW;
            this.batA = batA;
            this.forS = forS;
            this.runtimeMin = runtimeMin;
            this.at = at;
        }

        boolean fresh(long now) {
            return now - at < FRESH_MS && now >= at - 60_000;
        }
    }

    /** A packet, or null when it isn't one of ours or a number is out of range. */
    static Status parse(String text, long now) {
        try {
            JSONObject o = new JSONObject(text);
            if (!"inverter".equals(o.optString("lehko")) || o.optInt("v") != 1) return null;
            if (!o.has("grid") || !o.has("soc")) return null;
            boolean grid = o.getBoolean("grid");
            int soc = o.getInt("soc");
            int load = o.optInt("load", 0);
            double batA = o.optDouble("bat_a", 0);
            int forS = o.optInt("for_s", -1);
            int runtime = o.optInt("runtime", -1);
            if (soc < 0 || soc > 100 || load < -1000 || load > 100_000 || Double.isNaN(batA) || Math.abs(batA) > 1000) {
                return null;
            }
            if (runtime > 60 * 24 * 30) runtime = -1;
            return new Status(grid, soc, load, batA, forS, runtime, now);
        } catch (JSONException e) {
            return null;
        }
    }

    /** The last status the home screen's process heard (main thread only). */
    static Status last;

    /**
     * What a new status means against the one before: the power went (OFF), came back (ON), the battery passed a
     * {@link #LOW} level while on it, or nothing. The first status, or one after a long gap, only sets the start. A level
     * counts only on its second packet in a row: one reading in a load spike (LiFePO4 voltage sags under a kettle) must
     * not use up the 30 and 20 percent cards.
     */
    static final class Watch {
        private Status before;
        /** The lowest LOW level already told about while on the battery; 101 = none. */
        private int told = 101;

        int next(Status s) {
            Status b = before;
            before = s;
            if (b == null || s.at - b.at > GAP_MS || s.at < b.at) {
                told = passed(s);
                return NONE;
            }
            if (b.grid && !s.grid) {
                told = passed(s);
                return OFF;
            }
            if (!b.grid && s.grid) {
                told = 101;
                return ON;
            }
            if (!s.grid) {
                int p = passed(s);
                if (p < told && passed(b) <= p) {
                    told = p;
                    return LOW_BATTERY;
                }
            }
            return NONE;
        }

        /** The lowest LOW level this status is at or under while on the battery, else 101. */
        private static int passed(Status s) {
            if (s.grid) return 101;
            int p = 101;
            for (int l : LOW) if (s.soc <= l) p = Math.min(p, l);
            return p;
        }
    }

    // MARK: - Texts

    /** The home screen's line: "Inverter · from the grid · 99% · 272 W" or "on the battery · 87% · 5 h left". */
    static String line(Context c, Status s) {
        StringBuilder b = new StringBuilder(c.getString(s.grid ? R.string.inverter_grid : R.string.inverter_battery,
                s.soc));
        if (s.loadW > 0) b.append(" · ").append(c.getString(R.string.inverter_load, s.loadW));
        if (!s.grid && s.runtimeMin > 0) {
            b.append(" · ").append(c.getString(R.string.inverter_left, OutageText.duration(c, s.runtimeMin)));
        } else if (s.grid && s.batA > 0.3 && s.soc < 100) {
            b.append(" · ").append(c.getString(R.string.inverter_charging));
        }
        return b.toString();
    }

    /** The helper's card for an event. */
    static String card(Context c, int event, Status s) {
        String left = !s.grid && s.runtimeMin > 0
                ? " · " + c.getString(R.string.inverter_left, OutageText.duration(c, s.runtimeMin)) : "";
        if (event == OFF) return c.getString(R.string.inverter_card_off, s.soc) + left;
        if (event == ON) return c.getString(R.string.inverter_card_on, s.soc);
        return c.getString(R.string.inverter_card_low, s.soc) + left;
    }

    // MARK: - Listening

    /** Receives the packets on a thread of its own and hands each good one to the main thread. One use: start, stop. */
    static final class Listener implements Runnable {
        interface Sink {
            void got(Status s);
        }

        private final Context app;
        private final Sink sink;
        private volatile boolean stopped;
        private volatile DatagramSocket socket;
        private WifiManager.MulticastLock lock;

        Listener(Context c, Sink sink) {
            this.app = c.getApplicationContext();
            this.sink = sink;
        }

        void start() {
            // Wi-Fi chips may drop broadcasts to save power; this asks them not to while Lehko listens.
            try {
                WifiManager w = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
                if (w != null) {
                    lock = w.createMulticastLock("lehko-inverter");
                    lock.setReferenceCounted(false);
                    lock.acquire();
                }
            } catch (RuntimeException e) {
                lock = null; // no Wi-Fi (Ethernet) or not allowed: broadcasts usually come in anyway
            }
            Thread t = new Thread(this, "lehko-inverter");
            t.setDaemon(true);
            t.start();
        }

        void stop() {
            stopped = true;
            DatagramSocket s = socket;
            if (s != null) s.close();
            if (lock != null) {
                try {
                    lock.release();
                } catch (RuntimeException ignored) {
                    // already released
                }
                lock = null;
            }
        }

        @Override public void run() {
            byte[] buf = new byte[1024];
            while (!stopped) {
                DatagramSocket s = null;
                try {
                    s = new DatagramSocket(null);
                    // The home screen and the helper listen on the same port, each in its own process: a broadcast
                    // reaches every socket bound with this.
                    s.setReuseAddress(true);
                    s.setBroadcast(true);
                    s.bind(new InetSocketAddress(PORT));
                    socket = s;
                    if (stopped) break;
                    while (!stopped) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        s.receive(p);
                        final Status st = parse(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8),
                                System.currentTimeMillis());
                        if (st == null) continue;
                        App.MAIN.post(new Runnable() {
                            @Override public void run() {
                                if (!stopped) sink.got(st);
                            }
                        });
                    }
                } catch (IOException | RuntimeException e) {
                    // Closed by stop(), or the port is taken: try again in a while.
                } finally {
                    if (s != null) s.close();
                }
                if (stopped) break;
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
