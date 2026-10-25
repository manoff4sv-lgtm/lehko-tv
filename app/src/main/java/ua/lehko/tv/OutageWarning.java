package ua.lehko.tv;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The heads-up over any app, 15 minutes before an outage: the home screen's process writes the outages ahead into
 * helper.txt after each download, and {@link TvHelper} shows a card when one comes close. Moments are absolute (epoch
 * ms), so the helper needs no schedule, time zone or clock change logic of its own.
 */
final class OutageWarning {
    private OutageWarning() {}

    /** helper.txt: the outages ahead ("start:end:off,..."), and "0" while the warning is turned off. */
    static final String P_RUNS = "warn", P_ON = "warn_on";
    /** The helper's notes: the start of the last outage it warned about. */
    static final String P_WARNED = "warned";
    /** How long before an outage the card shows. */
    static final long LEAD_MS = 15 * 60_000L;

    /** An outage ahead: no power ({@link Outages#OFF}) or possibly none ({@link Outages#MAYBE}). */
    static final class Run {
        /** Epoch ms; end 0: the schedule doesn't say yet (it runs to the end of what is published). */
        final long start, end;
        final byte kind;

        Run(long start, long end, byte kind) {
            this.start = start;
            this.end = end;
            this.kind = kind;
        }
    }

    /** Main process: on unless turned off in Lehko settings (the helper reads the same key itself). */
    static boolean on(Context c) {
        return on(Keys.read(c.getFilesDir()));
    }

    static boolean on(Map<String, String> settings) {
        return !"0".equals(settings.get(P_ON));
    }

    /**
     * The outages that start after {@code now} today and tomorrow (Kyiv), in order. One that goes on past midnight is
     * one outage; a possible one right after no power is no news, so it is left out.
     */
    static List<Run> upcoming(Outages.Schedule s, long now) {
        List<Run> out = new ArrayList<>();
        if (s == null) return out;
        Calendar c = Calendar.getInstance(Outages.KYIV);
        c.setTimeInMillis(now);
        String today = Outages.format(c);
        c.add(Calendar.DAY_OF_MONTH, 1);
        Outages.Day d0 = s.day(today), d1 = s.day(Outages.format(c));
        byte[] slots = new byte[2 * Outages.SLOTS];
        Arrays.fill(slots, Outages.UNKNOWN);
        if (d0 != null) System.arraycopy(d0.slots, 0, slots, 0, Outages.SLOTS);
        if (d1 != null) System.arraycopy(d1.slots, 0, slots, Outages.SLOTS, Outages.SLOTS);
        int minutes = 1440 / Outages.SLOTS;
        for (int i = 0; i < slots.length; i++) {
            byte kind = slots[i];
            if (kind != Outages.OFF && kind != Outages.MAYBE) continue;
            int j = i;
            while (j + 1 < slots.length && slots[j + 1] == kind) j++;
            long start = Outages.at(today, i * minutes);
            // An emergency day or one not published yet: when it ends isn't known.
            boolean ends = j + 1 < slots.length && slots[j + 1] != Outages.UNKNOWN;
            boolean afterOff = i > 0 && slots[i - 1] == Outages.OFF;
            if (start > now && !(kind == Outages.MAYBE && afterOff)) {
                out.add(new Run(start, ends ? Outages.at(today, (j + 1) * minutes) : 0, kind));
            }
            i = j;
        }
        return out;
    }

    /** For helper.txt: "start:end:off,start:end:maybe"; null for none. */
    static String format(List<Run> runs) {
        StringBuilder b = new StringBuilder();
        for (Run r : runs) {
            if (b.length() > 0) b.append(',');
            b.append(r.start).append(':').append(r.end).append(':').append(r.kind == Outages.OFF ? "off" : "maybe");
        }
        return b.length() > 0 ? b.toString() : null;
    }

    /** {@link #format}'s text back; anything malformed is left out. */
    static List<Run> parse(String text) {
        List<Run> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String item : text.split(",")) {
            String[] p = item.split(":");
            if (p.length != 3 || !("off".equals(p[2]) || "maybe".equals(p[2]))) continue;
            try {
                out.add(new Run(Long.parseLong(p[0]), Long.parseLong(p[1]), "off".equals(p[2]) ? Outages.OFF : Outages.MAYBE));
            } catch (NumberFormatException ignored) {
                // Not ours: skipped.
            }
        }
        return out;
    }

    /**
     * The outage to warn about at {@code now}: it starts within {@link #LEAD_MS} and is later than {@code warned}, the
     * last one warned about (so each gets one card, also across a restart of the helper). Null when none.
     */
    static Run due(List<Run> runs, long now, long warned) {
        for (Run r : runs) if (r.start > now && r.start > warned && r.start - LEAD_MS <= now) return r;
        return null;
    }

    /** When the next card comes due after {@code now}, or Long.MAX_VALUE. */
    static long nextDue(List<Run> runs, long now, long warned) {
        long next = Long.MAX_VALUE;
        for (Run r : runs) if (r.start > warned && r.start - LEAD_MS > now) next = Math.min(next, r.start - LEAD_MS);
        return next;
    }

    /** The card: "Power outage in 15 min · 18:00 to 21:00", the minutes as they really are left. */
    static String text(Context c, Run r, long now) {
        int minutes = (int) Math.max(1, (r.start - now + 30_000) / 60_000);
        String when = r.end > 0 ? c.getString(R.string.outage_bar_range, hhmm(r.start, false), hhmm(r.end, true))
                : c.getString(R.string.warn_at, hhmm(r.start, false));
        return c.getString(r.kind == Outages.OFF ? R.string.warn_off : R.string.warn_maybe,
                OutageText.duration(c, minutes), when);
    }

    /** "18:00" on Kyiv's clock, as the schedule has it; an end at midnight is "24:00", as on the home screen. */
    static String hhmm(long t, boolean end) {
        Calendar c = Calendar.getInstance(Outages.KYIV);
        c.setTimeInMillis(t);
        int minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        return OutageText.hhmm(end && minutes == 0 ? 1440 : minutes);
    }

    /** After a download (on App.NET): the outages ahead for the helper. */
    static void update(Context c, Outages.Schedule after) {
        Map<String, String> put = new HashMap<>();
        put.put(P_RUNS, format(upcoming(after, System.currentTimeMillis())));
        write(c, put);
    }

    /** Another group, or no schedule any more: the old outages mustn't be warned about. */
    static void forget(Context c) {
        Map<String, String> put = new HashMap<>();
        put.put(P_RUNS, null);
        write(c, put);
    }

    private static void write(Context c, Map<String, String> put) {
        try {
            Keys.putAll(c.getFilesDir(), Keys.FILE, put);
        } catch (IOException ignored) {
            // Full storage: the helper keeps the last list, whose moments are still right.
        }
    }
}
