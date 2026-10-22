package ua.lehko.tv;

import android.content.Context;

/** The one-line summary on the outage card: "Power is on · outage at 18:00 (in 1 h 20 min)". */
final class OutageText {
    private OutageText() {}

    static String status(Context c, Outages.Schedule s, Outages.Status st) {
        if (s == null) return c.getString(R.string.outage_loading);
        if (s.emergency) return c.getString(R.string.outage_emergency);
        switch (st.state) {
            case Outages.ON:
                if (st.clearAhead) {
                    return c.getString(s.noSchedules ? R.string.outage_none_planned : R.string.outage_on_clear);
                }
                if (st.minutesToChange < 0) return c.getString(R.string.outage_on);
                if (st.changeTomorrow) {
                    return c.getString(st.next == Outages.MAYBE ? R.string.outage_on_maybe_tomorrow : R.string.outage_on_off_tomorrow,
                            st.changeAt);
                }
                return c.getString(st.next == Outages.MAYBE ? R.string.outage_on_maybe_at : R.string.outage_on_off_at,
                        st.changeAt, duration(c, st.minutesToChange));
            case Outages.OFF:
                if (st.minutesToChange < 0) return c.getString(R.string.outage_off_long);
                if (st.changeTomorrow) return c.getString(R.string.outage_off_until_tomorrow, st.changeAt);
                return c.getString(R.string.outage_off_until, st.changeAt, duration(c, st.minutesToChange));
            case Outages.MAYBE:
                if (st.minutesToChange < 0 || st.changeTomorrow) return c.getString(R.string.outage_maybe);
                return c.getString(R.string.outage_maybe_until, st.changeAt);
            default:
                return c.getString(R.string.outage_no_schedule);
        }
    }

    /** A day's bar for screen readers: "no power 08:00 to 12:00, 16:00 to 20:00; possibly no power 20:00 to 22:00". */
    static String describe(Context c, byte[] slots) {
        StringBuilder b = new StringBuilder();
        part(c, b, Outages.runs(slots, Outages.OFF), R.string.outage_bar_off);
        part(c, b, Outages.runs(slots, Outages.MAYBE), R.string.outage_bar_maybe);
        return b.length() > 0 ? b.toString() : c.getString(R.string.outage_bar_none);
    }

    private static void part(Context c, StringBuilder b, java.util.List<int[]> runs, int label) {
        if (runs.isEmpty()) return;
        StringBuilder ranges = new StringBuilder();
        for (int[] r : runs) {
            if (ranges.length() > 0) ranges.append(", ");
            ranges.append(c.getString(R.string.outage_bar_range, hhmm(r[0]), hhmm(r[1])));
        }
        if (b.length() > 0) b.append("; ");
        b.append(c.getString(label, ranges));
    }

    static String hhmm(int minutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    static String duration(Context c, int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return c.getString(R.string.dur_min, m);
        if (m == 0) return c.getString(R.string.dur_h, h);
        return c.getString(R.string.dur_h_min, h, m);
    }
}
