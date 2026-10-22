package ua.lehko.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Everything Lehko remembers, in one private SharedPreferences file. */
final class Prefs {
    private Prefs() {}

    static SharedPreferences of(Context c) {
        return c.getApplicationContext().getSharedPreferences("lehko", Context.MODE_PRIVATE);
    }

    // MARK: - Favorites (package names, in order)

    static final String FAVORITES = "favorites";
    static final String FAVORITES_SET = "favorites_set";

    static List<String> favorites(Context c) {
        String raw = of(c).getString(FAVORITES, "");
        List<String> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        out.addAll(Arrays.asList(raw.split("\n")));
        return out;
    }

    static void setFavorites(Context c, List<String> pkgs) {
        StringBuilder b = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String p : pkgs) {
            if (p == null || p.isEmpty() || !seen.add(p)) continue;
            b.append(p).append('\n');
        }
        if (b.length() > 0) b.setLength(b.length() - 1);
        of(c).edit().putString(FAVORITES, b.toString()).putBoolean(FAVORITES_SET, true).apply();
    }

    static boolean favoritesSet(Context c) {
        return of(c).getBoolean(FAVORITES_SET, false);
    }

    // MARK: - Weather city

    static final String CITY_NAME = "city_name", CITY_LAT = "city_lat", CITY_LON = "city_lon";

    static boolean hasCity(Context c) {
        return !of(c).getString(CITY_LAT, "").isEmpty();
    }

    static void setCity(Context c, String name, double lat, double lon) {
        of(c).edit().putString(CITY_NAME, name).putString(CITY_LAT, String.valueOf(lat))
                .putString(CITY_LON, String.valueOf(lon)).remove(Weather.CACHE).remove(Weather.CACHE_TIME).apply();
    }

    static void clearCity(Context c) {
        of(c).edit().remove(CITY_NAME).remove(CITY_LAT).remove(CITY_LON).remove(Weather.CACHE).remove(Weather.CACHE_TIME).apply();
    }

    // MARK: - Screensaver

    static final String SCREENSAVER_LEHKO = "screensaver_lehko";
    /** With SCREENSAVER_LEHKO: the fireplace rather than the clock. */
    static final String SCREENSAVER_FIRE = "screensaver_fire";

    // MARK: - A one-time notice shown on the home screen (results of background checks)

    static final String NOTICE = "notice";

    static void notice(Context c, String text) {
        of(c).edit().putString(NOTICE, text).apply();
    }

    static String takeNotice(Context c) {
        SharedPreferences p = of(c);
        String n = p.getString(NOTICE, null);
        if (n != null) p.edit().remove(NOTICE).apply();
        return n;
    }
}
