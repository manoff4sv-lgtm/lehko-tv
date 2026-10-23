package ua.lehko.tv;

import android.content.Context;

/** The one-line summary on the outage card: "Power is on · outage at 18:00 (in 1 h 20 min)". */
final class OutageText {
    private OutageText() {}

    static String status(Context c, Outages.Schedule s, Outages.Status st) {
        if (s == null) return c.getString(R.string.outage_loading);
        if (st.emergency) return c.getString(R.string.outage_emergency);
        switch (st.state) {
            case Outages.ON:
                if (st.clearAhead) {
                    return c.getString(st.noSchedules ? R.string.outage_none_planned : R.string.outage_on_clear);
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

    /** The two power lines on the home screen, as string ids and values (plain Java, so it can be tested). */
    static final class Headline {
        /** "Power is on", "No power", ... */
        int title;
        /** "22:00" for "until 22:00", or null. */
        String until;
        /** The second line's string id, or 0 for just the group. */
        int detail;
        /** Its value: a time ("HH:mm") for the "tomorrow at" lines, else a duration in minutes. */
        String detailTime;
        int detailMinutes = -1;
        byte state = Outages.UNKNOWN;
    }

    static Headline headline(Outages.Schedule s, Outages.Status st) {
        Headline h = new Headline();
        h.state = st.state;
        if (s == null) {
            h.title = R.string.outage_loading;
            return h;
        }
        if (st.emergency) {
            h.title = R.string.outage_emergency_title;
            h.detail = R.string.outage_emergency_detail;
            return h;
        }
        switch (st.state) {
            case Outages.ON:
                h.title = R.string.outage_on;
                if (st.clearAhead) {
                    h.detail = st.noSchedules ? R.string.outage_none_planned_lc : R.string.outage_none_ahead;
                } else if (st.minutesToChange >= 0 && st.changeTomorrow) {
                    h.detail = st.next == Outages.MAYBE ? R.string.outage_maybe_tomorrow : R.string.outage_next_tomorrow;
                    h.detailTime = st.changeAt;
                } else if (st.minutesToChange >= 0) {
                    h.until = st.changeAt;
                    h.detail = st.next == Outages.MAYBE ? R.string.outage_maybe_in : R.string.outage_in;
                    h.detailMinutes = st.minutesToChange;
                }
                return h;
            case Outages.OFF:
                h.title = R.string.outage_title_off;
                if (st.minutesToChange < 0) {
                    h.detail = R.string.outage_no_end;
                } else if (st.changeTomorrow) {
                    h.detail = R.string.outage_back_tomorrow;
                    h.detailTime = st.changeAt;
                } else {
                    h.until = st.changeAt;
                    h.detail = R.string.outage_back_in;
                    h.detailMinutes = st.minutesToChange;
                }
                return h;
            case Outages.MAYBE:
                h.title = R.string.outage_maybe;
                if (st.minutesToChange >= 0 && !st.changeTomorrow) h.until = st.changeAt;
                return h;
            default:
                h.title = R.string.outage_no_schedule;
                return h;
        }
    }

    /** {title, "until 22:00" or null, "outage in 2 h 18 min · group 11.1"}. */
    static String[] lines(Context c, Outages.Schedule s, Outages.Status st, String group) {
        Headline h = headline(s, st);
        String detail = h.detail == 0 ? null : h.detailTime != null ? c.getString(h.detail, h.detailTime)
                : h.detailMinutes >= 0 ? c.getString(h.detail, duration(c, h.detailMinutes)) : c.getString(h.detail);
        String g = group == null || group.isEmpty() ? null : c.getString(R.string.outage_group_lc, group);
        String second = detail == null ? g : g == null ? detail : detail + " · " + g;
        return new String[]{c.getString(h.title), h.until == null ? null : c.getString(R.string.outage_until, h.until), second};
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
