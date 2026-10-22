package ua.lehko.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Weather from Open-Meteo (free, no key). The last answer is kept, so the card still shows during an outage. */
final class Weather {
    private Weather() {}

    static final String CACHE = "weather_json", CACHE_TIME = "weather_time";
    /** Refresh after this long. */
    static final long MAX_AGE_MS = 30 * 60_000L;

    static final class Data {
        double temp, max, min;
        int code;
        boolean day;
        /** "HH:mm" of the first rain or snow in the next hours (not counting what falls right now), or null. */
        String precipAt;
        boolean precipSnow;
        /** The coming hours, from the current one on (a few at most). */
        final List<Hour> hours = new ArrayList<>();
        /** Today's sunrise and sunset in the city: "HH:mm" (or null) and the moment itself (or 0). */
        String sunrise, sunset;
        long sunriseAt, sunsetAt;
    }

    static final class Hour {
        /** "HH:mm" in the city. */
        String time;
        long at;
        double temp;
        int code;
        boolean day;
    }

    static final class Place {
        final String name, detail;
        final double lat, lon;

        Place(String name, String detail, double lat, double lon) {
            this.name = name;
            this.detail = detail;
            this.lat = lat;
            this.lon = lon;
        }
    }

    static boolean fahrenheit() {
        return "US".equals(Locale.getDefault().getCountry());
    }

    static String url(double lat, double lon, boolean fahrenheit) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day"
                + "&hourly=temperature_2m,weather_code,is_day&forecast_hours=6"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                + "&minutely_15=precipitation&forecast_minutely_15=16&forecast_days=2&timezone=auto"
                + (fahrenheit ? "&temperature_unit=fahrenheit" : "");
    }

    /** Fetches and caches; returns the fresh data. Network: call off the UI thread. */
    static Data refresh(Context c) throws IOException, JSONException {
        double[] p = place(c);
        if (p == null) return null;
        String json = Net.get(url(p[0], p[1], fahrenheit()));
        Data d = parse(json);
        Prefs.of(c).edit().putString(CACHE, json).putLong(CACHE_TIME, System.currentTimeMillis()).apply();
        return d;
    }

    /** The cached data (any age), or null. */
    static Data cached(Context c) {
        String json = Prefs.of(c).getString(CACHE, null);
        if (json == null) return null;
        try {
            return parse(json);
        } catch (JSONException e) {
            return null;
        }
    }

    static long cacheTime(Context c) {
        return Prefs.of(c).getLong(CACHE_TIME, 0);
    }

    static boolean stale(Context c) {
        return System.currentTimeMillis() - cacheTime(c) > MAX_AGE_MS;
    }

    /** The chosen city, or Kyiv on a TV set to the Kyiv time zone, or null (then the card asks to choose a city). */
    static double[] place(Context c) {
        android.content.SharedPreferences p = Prefs.of(c);
        String lat = p.getString(Prefs.CITY_LAT, ""), lon = p.getString(Prefs.CITY_LON, "");
        if (!lat.isEmpty() && !lon.isEmpty()) {
            try {
                return new double[]{Double.parseDouble(lat), Double.parseDouble(lon)};
            } catch (NumberFormatException ignored) {}
        }
        String tz = TimeZone.getDefault().getID();
        if ("Europe/Kiev".equals(tz) || "Europe/Kyiv".equals(tz)) return new double[]{50.4501, 30.5234};
        return null;
    }

    static String cityName(Context c) {
        String name = Prefs.of(c).getString(Prefs.CITY_NAME, "");
        if (!name.isEmpty()) return name;
        return place(c) != null ? c.getString(R.string.city_kyiv) : "";
    }

    static Data parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        JSONObject cur = o.getJSONObject("current");
        Data d = new Data();
        d.temp = cur.getDouble("temperature_2m");
        d.code = cur.optInt("weather_code", 0);
        d.day = cur.optInt("is_day", 1) == 1;
        int offset = o.optInt("utc_offset_seconds", 0);
        JSONObject daily = o.optJSONObject("daily");
        if (daily != null) {
            d.max = daily.getJSONArray("temperature_2m_max").optDouble(0, d.temp);
            d.min = daily.getJSONArray("temperature_2m_min").optDouble(0, d.temp);
            JSONArray rise = daily.optJSONArray("sunrise"), set = daily.optJSONArray("sunset");
            if (rise != null && set != null) {
                d.sunriseAt = epoch(rise.optString(0, ""), offset);
                d.sunsetAt = epoch(set.optString(0, ""), offset);
                if (d.sunriseAt != 0 && d.sunsetAt != 0) {
                    d.sunrise = hhmm(rise.optString(0, ""));
                    d.sunset = hhmm(set.optString(0, ""));
                }
            }
        } else {
            d.max = d.min = d.temp;
        }
        String now = cur.optString("time", "");
        JSONObject hourly = o.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray times = hourly.optJSONArray("time"), temps = hourly.optJSONArray("temperature_2m"),
                    codes = hourly.optJSONArray("weather_code"), days = hourly.optJSONArray("is_day");
            if (times != null && temps != null) {
                // From the hour that holds "now" on; a handful is plenty.
                String hourNow = now.length() >= 13 ? now.substring(0, 13) : "";
                for (int i = 0; i < Math.min(times.length(), temps.length()) && d.hours.size() < 8; i++) {
                    String t = times.optString(i, "");
                    if (t.length() < 16 || t.substring(0, 13).compareTo(hourNow) < 0 || temps.isNull(i)) continue;
                    Hour h = new Hour();
                    h.time = hhmm(t);
                    h.at = epoch(t, offset);
                    h.temp = temps.optDouble(i, 0);
                    h.code = codes == null ? 0 : codes.optInt(i, 0);
                    h.day = days == null || days.optInt(i, 1) == 1;
                    d.hours.add(h);
                }
            }
        }
        JSONObject m = o.optJSONObject("minutely_15");
        if (m != null && !isPrecip(d.code)) {
            JSONArray times = m.optJSONArray("time"), precip = m.optJSONArray("precipitation");
            if (times != null && precip != null) {
                for (int i = 0; i < Math.min(times.length(), precip.length()); i++) {
                    String t = times.getString(i);
                    if (t.compareTo(now) <= 0) continue;
                    if (precip.optDouble(i, 0) >= 0.1) {
                        d.precipAt = t.length() >= 16 ? t.substring(11, 16) : t;
                        d.precipSnow = isSnow(d.code) || d.temp <= (o.toString().contains("\"°F\"") ? 33 : 0.5);
                        break;
                    }
                }
            }
        }
        return d;
    }

    /** Up to {@code max} of the coming hours that start after {@code now}. */
    static List<Hour> next(Data d, long now, int max) {
        List<Hour> out = new ArrayList<>();
        if (d == null) return out;
        for (Hour h : d.hours) {
            if (out.size() >= max) break;
            if (h.at > now) out.add(h);
        }
        return out;
    }

    /**
     * Is the sun down in the city at {@code now}? From today's sunrise and sunset (moved by whole days, so an old
     * forecast still answers), else from the forecast's own "is day".
     */
    static boolean night(Data d, long now) {
        if (d == null) return false;
        if (d.sunriseAt == 0 || d.sunsetAt <= d.sunriseAt || d.sunsetAt - d.sunriseAt >= DAY_MS) return !d.day;
        long days = Math.floorDiv(now - d.sunriseAt, DAY_MS);
        return now >= d.sunsetAt + days * DAY_MS;
    }

    private static final long DAY_MS = 86_400_000L;
    private static final java.util.TimeZone UTC = java.util.TimeZone.getTimeZone("UTC");

    /** "2026-10-22T13:00" in a place {@code offsetSec} ahead of UTC, as a moment; 0 if it can't be read. */
    static long epoch(String local, int offsetSec) {
        if (local == null || local.length() < 16) return 0;
        try {
            Calendar c = Calendar.getInstance(UTC);
            c.clear();
            c.set(Integer.parseInt(local.substring(0, 4)), Integer.parseInt(local.substring(5, 7)) - 1,
                    Integer.parseInt(local.substring(8, 10)), Integer.parseInt(local.substring(11, 13)),
                    Integer.parseInt(local.substring(14, 16)), 0);
            return c.getTimeInMillis() - offsetSec * 1000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String hhmm(String local) {
        return local.length() >= 16 ? local.substring(11, 16) : null;
    }

    static boolean isPrecip(int code) {
        return code >= 51;
    }

    static boolean isSnow(int code) {
        return (code >= 71 && code <= 77) || code == 85 || code == 86;
    }

    /** WMO weather code to icon. */
    static int icon(int code, boolean day) {
        if (code == 0 || code == 1) return day ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
        if (code == 2) return day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
        if (code == 3) return R.drawable.wx_cloudy;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 95) return R.drawable.wx_storm;
        if (isSnow(code)) return R.drawable.wx_snow;
        if (code >= 51) return R.drawable.wx_rain;
        return R.drawable.wx_cloudy;
    }

    /** WMO weather code to a short description. */
    static int text(int code) {
        switch (code) {
            case 0: return R.string.wx_clear;
            case 1: return R.string.wx_mainly_clear;
            case 2: return R.string.wx_partly_cloudy;
            case 3: return R.string.wx_overcast;
            case 45: case 48: return R.string.wx_fog;
            case 51: case 53: case 55: return R.string.wx_drizzle;
            case 56: case 57: case 66: case 67: return R.string.wx_freezing_rain;
            case 61: case 63: case 65: return R.string.wx_rain;
            case 71: case 73: case 75: case 77: return R.string.wx_snow;
            case 80: case 81: case 82: return R.string.wx_showers;
            case 85: case 86: return R.string.wx_snow_showers;
            case 95: return R.string.wx_storm;
            case 96: case 99: return R.string.wx_storm_hail;
            default: return R.string.wx_overcast;
        }
    }

    static String degrees(double v) {
        long r = Math.round(v);
        return (r > 0 ? "+" : r < 0 ? "−" : "") + Math.abs(r) + "°";
    }

    // MARK: - City search (Open-Meteo geocoding)

    static List<Place> search(String query, String language) throws IOException, JSONException {
        String json = Net.get("https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&name="
                + Net.enc(query.trim()) + "&language=" + Net.enc(language));
        return parsePlaces(json);
    }

    static List<Place> parsePlaces(String json) throws JSONException {
        List<Place> out = new ArrayList<>();
        JSONArray results = new JSONObject(json).optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            StringBuilder detail = new StringBuilder();
            String admin = r.optString("admin1", ""), country = r.optString("country", "");
            if (!admin.isEmpty()) detail.append(admin);
            if (!country.isEmpty()) detail.append(detail.length() > 0 ? ", " : "").append(country);
            out.add(new Place(r.getString("name"), detail.toString(), r.getDouble("latitude"), r.getDouble("longitude")));
        }
        return out;
    }
}
