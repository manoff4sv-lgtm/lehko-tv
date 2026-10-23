package ua.lehko.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Planned power outage schedules (Ukraine). Two public sources, no keys:
 * <ul>
 * <li>YASNO (DTEK's supplier): Kyiv city and the Dnipro region, with a lookup of your group by address.</li>
 * <li>outage-data-ua on GitHub, a mirror of DTEK's own pages updated every few minutes: Kyiv city (fallback),
 * Kyiv region, Odesa region.</li>
 * </ul>
 * Schedules are Kyiv wall-clock time in half-hour slots.
 */
final class Outages {
    private Outages() {}

    static final byte ON = 0, OFF = 1, MAYBE = 2, UNKNOWN = 3;
    static final int SLOTS = 48;
    static final TimeZone KYIV = TimeZone.getTimeZone("Europe/Kiev");

    static final String YASNO = "https://app.yasno.ua/api/blackout-service/public/shutdowns";
    static final String MIRROR = "https://raw.githubusercontent.com/Baskerville42/outage-data-ua/main/data/";

    // MARK: - Regions

    static final class Region {
        final String id;
        final int label;
        /** YASNO region and distribution company, or 0 when YASNO doesn't cover it. */
        final int yasnoRegion, yasnoDso;
        /** File name in the mirror (without .json), or null. */
        final String mirror;
        /** Group keys in the mirror look like "GPV11.1"; YASNO uses "11.1". The same numbers in both for Kyiv. */
        final boolean mirrorMatchesYasno;

        Region(String id, int label, int yasnoRegion, int yasnoDso, String mirror, boolean mirrorMatchesYasno) {
            this.id = id;
            this.label = label;
            this.yasnoRegion = yasnoRegion;
            this.yasnoDso = yasnoDso;
            this.mirror = mirror;
            this.mirrorMatchesYasno = mirrorMatchesYasno;
        }

        boolean yasno() {
            return yasnoRegion != 0;
        }
    }

    static final Region[] REGIONS = {
            new Region("kyiv", R.string.region_kyiv, 25, 902, "kyiv", true),
            new Region("kyiv-region", R.string.region_kyiv_oblast, 0, 0, "kyiv-region", false),
            new Region("dnipro-dnem", R.string.region_dnipro_dnem, 3, 301, null, false),
            new Region("dnipro-cek", R.string.region_dnipro_cek, 3, 303, null, false),
            new Region("odesa", R.string.region_odesa, 0, 0, "odesa", false),
    };

    static Region region(String id) {
        for (Region r : REGIONS) if (r.id.equals(id)) return r;
        return null;
    }

    // MARK: - Settings

    static final String P_REGION = "outage_region", P_GROUP = "outage_group", P_ADDRESS = "outage_address",
            P_STREET = "outage_street", P_HOUSE = "outage_house", P_RESOLVED = "outage_resolved",
            P_CACHE = "outage_cache", P_FETCHED = "outage_fetched", P_ETAG = "outage_etag";

    static Region chosenRegion(Context c) {
        return region(Prefs.of(c).getString(P_REGION, ""));
    }

    static String chosenGroup(Context c) {
        return Prefs.of(c).getString(P_GROUP, "");
    }

    static boolean enabled(Context c) {
        return chosenRegion(c) != null && !chosenGroup(c).isEmpty();
    }

    /**
     * Saves the region and group; street and house ids (YASNO) let Lehko re-check the group now and then.
     * Synchronized with the cache writes in {@link #refresh}: a download for the old choice must not land after it.
     */
    static synchronized void choose(Context c, Region r, String group, String address, long streetId, long houseId) {
        SharedPreferences.Editor e = Prefs.of(c).edit()
                .putString(P_REGION, r.id).putString(P_GROUP, group)
                .remove(P_CACHE).remove(P_FETCHED).remove(P_ETAG);
        if (address != null && streetId > 0 && houseId > 0) {
            e.putString(P_ADDRESS, address).putLong(P_STREET, streetId).putLong(P_HOUSE, houseId)
                    .putLong(P_RESOLVED, System.currentTimeMillis());
        } else {
            e.remove(P_ADDRESS).remove(P_STREET).remove(P_HOUSE).remove(P_RESOLVED);
        }
        e.apply();
    }

    static synchronized void turnOff(Context c) {
        Prefs.of(c).edit().remove(P_REGION).remove(P_GROUP).remove(P_ADDRESS).remove(P_STREET).remove(P_HOUSE)
                .remove(P_RESOLVED).remove(P_CACHE).remove(P_FETCHED).remove(P_ETAG).apply();
    }

    // MARK: - The schedule

    /**
     * The runs of {@code state} in a day's slots as {start, end} minutes after midnight (end exclusive, 1440 at most):
     * for reading the schedule aloud.
     */
    static java.util.List<int[]> runs(byte[] slots, byte state) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int minutes = 1440 / slots.length;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != state) continue;
            int j = i;
            while (j + 1 < slots.length && slots[j + 1] == state) j++;
            out.add(new int[]{i * minutes, (j + 1) * minutes});
            i = j;
        }
        return out;
    }

    static final class Day {
        /** "yyyy-MM-dd" in Kyiv. */
        final String date;
        final byte[] slots = new byte[SLOTS];
        /** The operator says emergency outages are on this day and there is no schedule (the slots are UNKNOWN). */
        boolean emergency;

        Day(String date, byte fill) {
            this.date = date;
            java.util.Arrays.fill(slots, fill);
        }
    }

    static final class Schedule {
        String group = "";
        final List<Day> days = new ArrayList<>();
        /**
         * The source had no schedules at all on {@link #asOf} (no outage regime); {@link Status#noSchedules} follows
         * the clock.
         */
        boolean noSchedules;
        /** The Kyiv day ("yyyy-MM-dd") the source answered on: the flag above is about that day only. */
        String asOf = "";
        String source = "";

        Day day(String date) {
            for (Day d : days) if (d.date.equals(date)) return d;
            return null;
        }
    }

    // MARK: - Fetching

    /** Fetches the schedule for the chosen region and group, caches it and returns it. Network: off the UI thread. */
    static Schedule refresh(Context c) throws IOException, JSONException {
        // A second round when another region or group was chosen during the download: the answer is for the old one.
        for (int round = 0; round < 2; round++) {
            Region r = chosenRegion(c);
            if (r == null || chosenGroup(c).isEmpty()) return null;
            maybeRecheckGroup(c, r);
            String group = chosenGroup(c);
            Schedule s = fetch(r, group);
            synchronized (Outages.class) {
                if (r != chosenRegion(c) || !group.equals(chosenGroup(c))) continue;
                Prefs.of(c).edit().putString(P_CACHE, toJson(s).toString()).putLong(P_FETCHED, System.currentTimeMillis()).apply();
            }
            return s;
        }
        return null;
    }

    /** YASNO first; the DTEK mirror when YASNO fails or has nothing for today (and its groups are the same). */
    private static Schedule fetch(Region r, String group) throws IOException, JSONException {
        Schedule s = null;
        IOException failure = null;
        if (r.yasno()) {
            try {
                String json = Net.get(YASNO + "/regions/" + r.yasnoRegion + "/dsos/" + r.yasnoDso
                        + "/planned-outages?_=" + (System.currentTimeMillis() / 60_000), 12_000, "Cache-Control", "no-cache");
                s = parseYasno(json, group, today(), tomorrow());
            } catch (IOException e) {
                failure = e;
            } catch (JSONException e) {
                failure = new IOException(e.getMessage());
            }
        }
        boolean yasnoUsable = s != null && (s.noSchedules || s.day(today()) != null);
        if (!yasnoUsable && r.mirror != null && (!r.yasno() || r.mirrorMatchesYasno)) {
            String json = Net.get(MIRROR + r.mirror + ".json", 15_000);
            Schedule m = parseMirror(json, group, epoch(today()), epoch(tomorrow()));
            if (m != null) s = m;
        }
        if (s == null) {
            if (failure != null) throw failure;
            s = new Schedule();
            s.group = group;
        }
        return s;
    }

    static long fetchedAt(Context c) {
        return Prefs.of(c).getLong(P_FETCHED, 0);
    }

    /** The schedule is downloaded again when older than this, but not within RETRY_MS of a failed try. */
    static final long MAX_AGE_MS = 10 * 60_000L, RETRY_MS = 5 * 60_000L;
    /**
     * Main thread only. Shared by the home screen, the fireplace and the fire screensaver, so when one takes over from
     * another it doesn't start a second download, or retry sooner after a failure.
     */
    private static boolean loading;
    private static long failedAt;

    /** No successful download for this long (offline in an outage): the schedule may be out of date, the line says so. */
    static final long OLD_MS = 2 * 3600_000L;

    /**
     * Main thread: should the cached schedule say since when it is? Only once updating has failed (or can't start):
     * back home after a film it is hours old, but renewed a second later.
     */
    static boolean old(Context c, long now) {
        return old(now, fetchedAt(c), failedAt, Net.online(c));
    }

    /** A fetch time ahead of the clock (it was put back since) can't be compared with a failure's: any failure counts. */
    static boolean old(long now, long fetched, long failed, boolean online) {
        return fetched > 0 && Net.stale(now, fetched, OLD_MS)
                && (failed > fetched || (failed != 0 && fetched > now) || !online);
    }

    /** Main thread: downloads the schedule in the background when it is old, then runs {@code done} on the main thread. */
    static void refreshIfStale(Context c, final Runnable done) {
        final Context app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        if (loading || !enabled(app) || !Net.stale(now, fetchedAt(app), MAX_AGE_MS) || !Net.stale(now, failedAt, RETRY_MS)) {
            return;
        }
        loading = true;
        App.NET.execute(new Runnable() {
            @Override public void run() {
                Schedule s = null;
                try {
                    s = refresh(app);
                } catch (Exception ignored) {
                    // Offline (e.g. during an outage): the cached schedule stays.
                }
                final boolean ok = s != null;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        loading = false;
                        if (!ok) failedAt = System.currentTimeMillis();
                        done.run();
                    }
                });
            }
        });
    }

    /** The cached schedule, or null (also when it is another group's: the weekly re-check may have moved us). */
    static Schedule cached(Context c) {
        String raw = Prefs.of(c).getString(P_CACHE, null);
        if (raw == null) return null;
        try {
            Schedule s = fromJson(new JSONObject(raw));
            return s.group.equals(chosenGroup(c)) ? s : null;
        } catch (JSONException e) {
            return null;
        }
    }

    /** Schedules are per building and the operator can move a building to another group: re-check weekly. */
    private static void maybeRecheckGroup(Context c, Region r) {
        SharedPreferences p = Prefs.of(c);
        long street = p.getLong(P_STREET, 0), house = p.getLong(P_HOUSE, 0);
        if (!r.yasno() || street <= 0 || house <= 0) return;
        if (!Net.stale(System.currentTimeMillis(), p.getLong(P_RESOLVED, 0), 7L * 24 * 3600_000)) return;
        try {
            String group = groupByAddress(r, street, house);
            synchronized (Outages.class) {
                // Something else was chosen meanwhile: this answer is for the old address.
                if (r != chosenRegion(c) || p.getLong(P_STREET, 0) != street || p.getLong(P_HOUSE, 0) != house) return;
                SharedPreferences.Editor e = p.edit().putLong(P_RESOLVED, System.currentTimeMillis());
                if (group != null && !group.equals(chosenGroup(c))) {
                    e.putString(P_GROUP, group);
                    Prefs.notice(c, c.getString(R.string.outage_group_changed, group));
                }
                e.apply();
            }
        } catch (IOException | JSONException ignored) {
            // Try again on the next refresh.
        }
    }

    // MARK: - Address lookup (YASNO)

    static final class Item {
        final long id;
        final String name;

        Item(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String toString() {
            return name;
        }
    }

    static List<Item> streets(Region r, String query) throws IOException, JSONException {
        return items(Net.get(YASNO + "/addresses/v2/streets?regionId=" + r.yasnoRegion + "&dsoId=" + r.yasnoDso
                + "&query=" + Net.enc(query.trim())));
    }

    static List<Item> houses(Region r, long streetId, String query) throws IOException, JSONException {
        return items(Net.get(YASNO + "/addresses/v2/houses?regionId=" + r.yasnoRegion + "&dsoId=" + r.yasnoDso
                + "&streetId=" + streetId + "&query=" + Net.enc(query.trim())));
    }

    /** "11.1" for the building, or null if YASNO doesn't know it. */
    static String groupByAddress(Region r, long streetId, long houseId) throws IOException, JSONException {
        JSONObject o = new JSONObject(Net.get(YASNO + "/addresses/v2/group?regionId=" + r.yasnoRegion + "&dsoId="
                + r.yasnoDso + "&streetId=" + streetId + "&houseId=" + houseId));
        if (!o.has("group")) return null;
        return o.get("group") + "." + o.optInt("subgroup", 1);
    }

    static List<Item> items(String json) throws JSONException {
        JSONArray a = new JSONArray(json);
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new Item(o.getLong("id"), o.getString("value")));
        }
        return out;
    }

    /** The groups the source knows, sorted 1.1, 1.2, 2.1, ... 10.1 (for picking from a list). */
    static List<String> groups(Region r) throws IOException, JSONException {
        List<String> out = new ArrayList<>();
        boolean mirror = r.mirror != null && (!r.yasno() || r.mirrorMatchesYasno);
        if (r.yasno()) {
            try {
                JSONObject o = new JSONObject(Net.get(YASNO + "/regions/" + r.yasnoRegion + "/dsos/" + r.yasnoDso + "/planned-outages"));
                for (Iterator<String> it = o.keys(); it.hasNext(); ) out.add(it.next());
            } catch (IOException | JSONException e) {
                // Kyiv's groups are the same in the DTEK mirror, which also stands in for YASNO's schedules.
                if (!mirror) throw e;
            }
        }
        if (out.isEmpty() && mirror) out = mirrorGroups(Net.get(MIRROR + r.mirror + ".json", 15_000));
        sortGroups(out);
        return out;
    }

    /**
     * The groups in a mirror file: those with schedules in fact.data, else the preset's list. (Kyiv's preset still
     * names the "N.2" groups it had before 2026-01-29, which have no schedules any more.)
     */
    static List<String> mirrorGroups(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        List<String> out = new ArrayList<>();
        JSONObject fact = o.optJSONObject("fact");
        JSONObject data = fact != null ? fact.optJSONObject("data") : null;
        if (data != null) {
            for (Iterator<String> it = data.keys(); it.hasNext(); ) {
                JSONObject day = data.optJSONObject(it.next());
                if (day != null) addGroups(day, out);
            }
        }
        if (out.isEmpty()) {
            JSONObject preset = o.optJSONObject("preset");
            JSONObject names = preset != null ? preset.optJSONObject("sch_names") : null;
            if (names != null) addGroups(names, out);
        }
        return out;
    }

    /** "GPV11.1" keys as "11.1", once each. */
    private static void addGroups(JSONObject keys, List<String> out) {
        for (Iterator<String> it = keys.keys(); it.hasNext(); ) {
            String k = it.next();
            if (k.startsWith("GPV") && !out.contains(k.substring(3))) out.add(k.substring(3));
        }
    }

    static void sortGroups(List<String> groups) {
        Collections.sort(groups, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int[] x = groupNumbers(a), y = groupNumbers(b);
                return x[0] != y[0] ? Integer.compare(x[0], y[0]) : Integer.compare(x[1], y[1]);
            }
        });
    }

    private static int[] groupNumbers(String g) {
        String[] p = g.split("\\.");
        try {
            return new int[]{Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 0};
        } catch (NumberFormatException e) {
            return new int[]{Integer.MAX_VALUE, 0};
        }
    }

    // MARK: - Parsing

    /**
     * YASNO planned-outages: {"11.1": {"today": {"slots": [{"start":0,"end":30,"type":"Definite"}...],
     * "date": "2026-10-22T00:00:00+03:00", "status": "ScheduleApplies"}, "tomorrow": {...}, "updatedOn": ...}}.
     * Slots are minutes from midnight; Definite = off, NotPlanned = on.
     */
    static Schedule parseYasno(String json, String group, String today, String tomorrow) throws JSONException {
        JSONObject all = new JSONObject(json);
        JSONObject g = all.optJSONObject(group);
        if (g == null) return null;
        Schedule s = new Schedule();
        s.group = group;
        s.source = "yasno";
        s.asOf = today;
        String todayStatus = "";
        for (String key : new String[]{"today", "tomorrow"}) {
            JSONObject d = g.optJSONObject(key);
            if (d == null) continue;
            String date = d.optString("date", "");
            date = date.length() >= 10 ? date.substring(0, 10) : "";
            if (!date.equals(today) && !date.equals(tomorrow)) continue;
            String status = d.optString("status", "");
            if (date.equals(today)) todayStatus = status;
            if ("EmergencyShutdowns".equals(status)) {
                Day day = new Day(date, UNKNOWN);
                day.emergency = true;
                s.days.add(day);
                continue;
            }
            if ("NoOutages".equals(status)) {
                s.days.add(new Day(date, ON));
                continue;
            }
            if ("WaitingForSchedule".equals(status)) continue;
            JSONArray slots = d.optJSONArray("slots");
            if (slots == null || slots.length() == 0) continue;
            Day day = new Day(date, UNKNOWN);
            for (int i = 0; i < slots.length(); i++) {
                JSONObject sl = slots.getJSONObject(i);
                int start = Math.max(0, sl.optInt("start", 0)), end = Math.min(1440, sl.optInt("end", 0));
                String type = sl.optString("type", "");
                byte state = "Definite".equals(type) ? OFF : "NotPlanned".equals(type) ? ON : MAYBE;
                // A half-hour slot gets the state that covers most of it.
                for (int k = (start + 15) / 30; k < Math.min(SLOTS, (end + 15) / 30); k++) day.slots[k] = state;
            }
            for (int k = 0; k < SLOTS; k++) if (day.slots[k] == UNKNOWN) day.slots[k] = ON;
            s.days.add(day);
        }
        s.noSchedules = "NoOutages".equals(todayStatus);
        return s;
    }

    /**
     * The DTEK mirror: {"fact": {"today": 1792530000, "data": {"1792530000": {"GPV11.1": {"1": "yes", ... "24": "no"}}}}}.
     * Day keys are Unix seconds of Kyiv midnight; hour "1" is 00:00-01:00. "data" is [] when there are no schedules.
     */
    static Schedule parseMirror(String json, String group, long todayEpoch, long tomorrowEpoch) throws JSONException {
        JSONObject o = new JSONObject(json);
        JSONObject fact = o.optJSONObject("fact");
        if (fact == null) return null;
        Schedule s = new Schedule();
        s.group = group;
        s.source = "dtek";
        s.asOf = dateOf(todayEpoch);
        Object data = fact.opt("data");
        if (!(data instanceof JSONObject)) {
            s.noSchedules = true;
            return s;
        }
        JSONObject days = (JSONObject) data;
        String key = "GPV" + group;
        for (long epoch : new long[]{todayEpoch, tomorrowEpoch}) {
            JSONObject day = days.optJSONObject(String.valueOf(epoch));
            if (day == null) continue;
            JSONObject hours = day.optJSONObject(key);
            if (hours == null) continue;
            Day d = new Day(dateOf(epoch), UNKNOWN);
            for (int h = 1; h <= 24; h++) {
                String v = hours.optString(String.valueOf(h), "");
                byte a, b;
                switch (v) {
                    case "yes": a = ON; b = ON; break;
                    case "no": a = OFF; b = OFF; break;
                    case "first": a = OFF; b = ON; break;
                    case "second": a = ON; b = OFF; break;
                    case "maybe": a = MAYBE; b = MAYBE; break;
                    case "mfirst": a = MAYBE; b = ON; break;
                    case "msecond": a = ON; b = MAYBE; break;
                    default: a = UNKNOWN; b = UNKNOWN;
                }
                d.slots[(h - 1) * 2] = a;
                d.slots[(h - 1) * 2 + 1] = b;
            }
            s.days.add(d);
        }
        if (s.days.isEmpty() && days.length() == 0) s.noSchedules = true;
        return s;
    }

    // MARK: - Cache format

    static JSONObject toJson(Schedule s) throws JSONException {
        JSONObject o = new JSONObject().put("group", s.group).put("none", s.noSchedules).put("as_of", s.asOf)
                .put("source", s.source);
        JSONArray days = new JSONArray();
        for (Day d : s.days) {
            StringBuilder b = new StringBuilder(SLOTS);
            for (byte x : d.slots) b.append((char) ('0' + x));
            JSONObject day = new JSONObject().put("date", d.date).put("slots", b.toString());
            if (d.emergency) day.put("emergency", true);
            days.put(day);
        }
        return o.put("days", days);
    }

    static Schedule fromJson(JSONObject o) throws JSONException {
        Schedule s = new Schedule();
        s.group = o.optString("group", "");
        s.noSchedules = o.optBoolean("none");
        s.asOf = o.optString("as_of", "");
        s.source = o.optString("source", "");
        JSONArray days = o.optJSONArray("days");
        if (days != null) {
            for (int i = 0; i < days.length(); i++) {
                JSONObject d = days.getJSONObject(i);
                String slots = d.getString("slots");
                if (slots.length() != SLOTS) continue;
                Day day = new Day(d.getString("date"), UNKNOWN);
                for (int k = 0; k < SLOTS; k++) day.slots[k] = (byte) Math.max(0, Math.min(3, slots.charAt(k) - '0'));
                day.emergency = d.optBoolean("emergency");
                s.days.add(day);
            }
        }
        return s;
    }

    // MARK: - Status now

    static final class Status {
        /** ON, OFF, MAYBE or UNKNOWN right now. */
        byte state = UNKNOWN;
        /** Minutes until the state changes, or -1 if no change is known (then {@link #untilEndOfData} says why). */
        int minutesToChange = -1;
        /** When it changes, "HH:mm" in Kyiv; "24:00" for midnight. */
        String changeAt;
        /** What it changes to: ON, OFF or MAYBE. */
        byte next = UNKNOWN;
        /** The change is tomorrow (after midnight). */
        boolean changeTomorrow;
        /** No change until the end of what the schedules tell (their last day, or an unknown stretch). */
        boolean untilEndOfData;
        /** No outages for the rest of today and all of tomorrow, or no outage regime at all. */
        boolean clearAhead;
        /** Nothing but power until midnight (tomorrow may not be published yet). */
        boolean clearToday;
        /** Today is an emergency day: the operator says the schedule doesn't apply. */
        boolean emergency;
        /** The source says there are no schedules today (no outage regime). */
        boolean noSchedules;
    }

    /**
     * What's happening at {@code nowMs} and what comes next, from the cached days. The source's flags count only on
     * their own day: offline after midnight, yesterday's emergency or "no outages" must not stick.
     */
    static Status status(Schedule s, long nowMs) {
        Status st = new Status();
        if (s == null) return st;
        Calendar now = Calendar.getInstance(KYIV);
        now.setTimeInMillis(nowMs);
        String today = format(now);
        Calendar next = (Calendar) now.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        String tomorrow = format(next);
        Day d0 = s.day(today), d1 = s.day(tomorrow);
        st.noSchedules = s.noSchedules && today.equals(s.asOf);
        if (d0 != null && d0.emergency) {
            st.emergency = true;
            return st;
        }
        if (d0 == null) {
            if (st.noSchedules) {
                st.state = ON;
                st.clearAhead = true;
            }
            return st;
        }
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int slot = Math.min(SLOTS - 1, minute / 30);
        st.state = d0.slots[slot];
        byte[] ahead = new byte[d1 != null ? SLOTS * 2 : SLOTS];
        System.arraycopy(d0.slots, 0, ahead, 0, SLOTS);
        if (d1 != null) System.arraycopy(d1.slots, 0, ahead, SLOTS, SLOTS);
        // Clear only when nothing but power lies ahead and tomorrow is known too: an unpublished tomorrow isn't clear.
        st.clearAhead = st.state == ON && (d1 != null || st.noSchedules);
        for (int k = slot; k < ahead.length && st.clearAhead; k++) if (ahead[k] != ON) st.clearAhead = false;
        st.clearToday = st.state == ON;
        for (int k = slot; k < SLOTS && st.clearToday; k++) if (d0.slots[k] != ON) st.clearToday = false;
        for (int k = slot + 1; k < ahead.length; k++) {
            if (ahead[k] == st.state) continue;
            // An emergency day or a gap in the data: no change we can promise.
            if (ahead[k] == UNKNOWN) break;
            st.next = ahead[k];
            st.minutesToChange = minutesUntil(now, k);
            int hm = k * 30 % 1440;
            st.changeAt = k == SLOTS ? "24:00" : String.format(Locale.ROOT, "%02d:%02d", hm / 60, hm % 60);
            st.changeTomorrow = k > SLOTS;
            return st;
        }
        st.untilEndOfData = true;
        return st;
    }

    /**
     * Real minutes from {@code now} (a Kyiv calendar) to the start of slot {@code k}, counted from today's midnight.
     * Not just the difference on the wall clock: the days the clocks change are 23 and 25 hours long.
     */
    private static int minutesUntil(Calendar now, int k) {
        Calendar at = (Calendar) now.clone();
        at.add(Calendar.DAY_OF_MONTH, k / SLOTS);
        at.set(Calendar.HOUR_OF_DAY, (k % SLOTS) / 2);
        at.set(Calendar.MINUTE, (k % 2) * 30);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        long from = now.getTimeInMillis() / 60_000 * 60_000, t = at.getTimeInMillis();
        // When the clocks go back, 03:00-03:59 comes twice and Calendar takes the second time. The slot starts at the
        // first, unless that has passed. (A time the clocks skip in spring comes out right: it is the hour after.)
        long back = KYIV.getOffset(t - 3600_000L) - KYIV.getOffset(t);
        if (back > 0 && t - back >= from) t -= back;
        return (int) ((t - from) / 60_000);
    }

    // MARK: - Dates (Kyiv wall clock)

    static String today() {
        return format(Calendar.getInstance(KYIV));
    }

    static String tomorrow() {
        Calendar c = Calendar.getInstance(KYIV);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return format(c);
    }

    static String format(Calendar c) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
    }

    /** Unix seconds of Kyiv midnight on "yyyy-MM-dd". */
    static long epoch(String date) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setTimeZone(KYIV);
        try {
            return f.parse(date).getTime() / 1000;
        } catch (ParseException e) {
            return 0;
        }
    }

    static String dateOf(long epochSeconds) {
        Calendar c = Calendar.getInstance(KYIV);
        c.setTimeInMillis(epochSeconds * 1000);
        return format(c);
    }
}
