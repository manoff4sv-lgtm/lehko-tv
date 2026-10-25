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
 * ms), so the helper needs no schedule, time zone or clock change logic of its own. A download that changes what lies
 * ahead leaves a ready text for one more card.
 */
final class OutageWarning {
    private OutageWarning() {}

    /**
     * helper.txt: the outages ahead ("start:end:off,..."), "0" while the warning is turned off, and the last change
     * ("id text", the id the moment it was found).
     */
    static final String P_RUNS = "warn", P_ON = "warn_on", P_CHANGED = "changed";
    /** The helper's notes: the start of the last outage it warned about, and the last change it showed. */
    static final String P_WARNED = "warned", P_SEEN = "changed_seen";
    /** How long before an outage the card shows. */
    static final long LEAD_MS = 15 * 60_000L;
    /** A change is news for this long (and only on its own Kyiv day: its text says "today"). */
    static final long CHANGED_FOR_MS = 3600_000L;

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
            long end = ends ? Outages.at(today, (j + 1) * minutes) : 0;
            boolean afterOff = i > 0 && slots[i - 1] == Outages.OFF;
            // One only in the hour the clocks skip in spring never comes.
            if (start > now && (end == 0 || end > start) && !(kind == Outages.MAYBE && afterOff)) {
                out.add(new Run(start, end, kind));
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

    // MARK: - The schedule changed

    /**
     * The days (today, then tomorrow) whose outages from now on differ between two downloads, as the new one has them
     * with the outages already over left out; empty when nothing ahead changed. A day only one of them has (tomorrow
     * just published, yesterday gone) is no change, nor is an emergency day (it has no schedule). Nor is anything
     * between two sources: Kyiv's DTEK mirror steps in whenever YASNO fails and may lag it by minutes, so YASNO, then
     * the mirror, then YASNO again would tell of two changes that never were.
     */
    static List<Outages.Day> changedDays(Outages.Schedule before, Outages.Schedule after, long now) {
        List<Outages.Day> out = new ArrayList<>();
        if (before == null || after == null || !before.source.equals(after.source)) return out;
        Calendar c = Calendar.getInstance(Outages.KYIV);
        c.setTimeInMillis(now);
        int slot = (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) / (1440 / Outages.SLOTS);
        String today = Outages.format(c);
        c.add(Calendar.DAY_OF_MONTH, 1);
        for (String date : new String[]{today, Outages.format(c)}) {
            Outages.Day a = before.day(date), b = after.day(date);
            if (a == null || b == null || a.emergency || b.emergency) continue;
            int from = date.equals(today) ? Math.min(slot, Outages.SLOTS - 1) : 0;
            for (int k = from; k < Outages.SLOTS; k++) {
                if (kind(a.slots[k]) != kind(b.slots[k])) {
                    out.add(ahead(b, from));
                    break;
                }
            }
        }
        return out;
    }

    /** OFF, MAYBE, or ON for anything else (an unknown slot isn't an outage to tell about). */
    private static byte kind(byte state) {
        return state == Outages.OFF || state == Outages.MAYBE ? state : Outages.ON;
    }

    /** A copy of {@code d} without the outages over before slot {@code from}; the one going on stays whole. */
    static Outages.Day ahead(Outages.Day d, int from) {
        Outages.Day out = new Outages.Day(d.date, Outages.ON);
        System.arraycopy(d.slots, 0, out.slots, 0, Outages.SLOTS);
        int keep = from;
        while (keep > 0 && kind(out.slots[from]) != Outages.ON && out.slots[keep - 1] == out.slots[from]) keep--;
        Arrays.fill(out.slots, 0, keep, Outages.ON);
        return out;
    }

    /** "The schedule changed: today no power 18:00 to 21:00 · tomorrow no outages". */
    static String changedText(Context c, List<Outages.Day> days, String today) {
        StringBuilder b = new StringBuilder();
        for (Outages.Day d : days) {
            if (b.length() > 0) b.append(" · ");
            boolean isToday = d.date.equals(today);
            b.append(c.getString(isToday ? R.string.outage_today_lc : R.string.outage_tomorrow_lc)).append(' ');
            boolean none = Outages.runs(d.slots, Outages.OFF).isEmpty() && Outages.runs(d.slots, Outages.MAYBE).isEmpty();
            b.append(none && isToday ? c.getString(R.string.outage_none_left) : OutageText.describe(c, d));
        }
        return c.getString(R.string.outage_changed, b);
    }

    /**
     * The helper: the change to show at {@code now}, or null. Shown once ({@code seen}: the last one shown), within an
     * hour of the download and on the same Kyiv day. Once means not that one again, not "nothing older": a seen id
     * from a clock that was ahead would hide every change until the real time caught up.
     */
    static String changed(String entry, long now, long seen) {
        long id = changedId(entry);
        if (id == seen || now < id || now - id > CHANGED_FOR_MS) return null;
        return Outages.dateOf(id / 1000).equals(Outages.dateOf(now / 1000)) ? entry.substring(entry.indexOf(' ') + 1)
                : null;
    }

    /** The change's id (when it was found), or 0 for none. */
    static long changedId(String entry) {
        int space = entry == null ? -1 : entry.indexOf(' ');
        if (space < 0) return 0;
        try {
            return Long.parseLong(entry.substring(0, space));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // MARK: - Writing for the helper

    /**
     * After a download (on App.NET): the outages ahead for the helper and, when what lies ahead changed since the same
     * group's last download ({@code before}; null: the first one), the change for a card. The downloads while other
     * apps are in front start here too ({@link OutageJob}).
     */
    static void update(Context c, Outages.Schedule before, Outages.Schedule after) {
        long now = System.currentTimeMillis();
        Map<String, String> put = new HashMap<>();
        put.put(P_RUNS, format(upcoming(after, now)));
        List<Outages.Day> changed = changedDays(before, after, now);
        // Told only where the helper can show it, and with the warning on: it is part of the warning. Only as the
        // helper's card, which shows over Lehko's home screen too: a home notice would say it twice there, and it would
        // wait for the next return home with no end, its "today" wrong by the next day.
        String text = !changed.isEmpty() && on(c) && TvHelper.running(c)
                ? changedText(c, changed, Outages.dateOf(now / 1000)) : null;
        if (text != null) put.put(P_CHANGED, now + " " + text);
        write(c, put);
        OutageJob.sync(c);
    }

    /** Another group, or no schedule any more: the old outages mustn't be warned about. */
    static void forget(Context c) {
        Map<String, String> put = new HashMap<>();
        put.put(P_RUNS, null);
        put.put(P_CHANGED, null);
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
