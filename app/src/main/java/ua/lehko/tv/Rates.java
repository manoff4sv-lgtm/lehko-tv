package ua.lehko.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The dollar and euro in hryvnias for the home screen: Monobank's buy/sell, the NBU's official rate as a fallback.
 * Public data without keys. The last answer is kept, so the line still shows offline.
 */
final class Rates {
    private Rates() {}

    static final String P_FIAT = "rates_fiat_on";
    static final String FIAT = "rates_fiat", FIAT_TIME = "rates_fiat_time", MONO_PAUSE = "rates_mono_pause_until";
    /** Left over from the crypto line (removed): cleared once. */
    private static final String[] OLD_KEYS = {"rates_crypto_on", "rates_crypto", "rates_crypto_time"};

    /** Monobank answers from a cache and limits how often it may be asked: twice an hour is plenty. */
    static final long FIAT_EVERY_MS = 30 * 60_000L;
    /** After "429 Too many requests" Monobank is left alone for an hour (the NBU answers meanwhile). */
    static final long MONO_PAUSE_MS = 60 * 60_000L;
    /** Older than this, the line is hidden (a TV that was off for days shouldn't show old rates). */
    static final long FIAT_SHOW_MS = 3 * 86_400_000L;

    static final String MONOBANK = "https://api.monobank.ua/bank/currency";
    static final String NBU = "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json";

    /** ISO 4217 numeric codes, in the order shown. */
    private static final int[] CURRENCIES = {840, 978};
    private static final String[] CURRENCY_NAMES = {"USD", "EUR"};
    private static final int UAH = 980;

    /** A currency in hryvnias: buy and sell, or one rate (the NBU's, or Monobank's cross rate): then sell is 0. */
    static final class Fiat {
        final String code;
        final double buy, sell;

        Fiat(String code, double buy, double sell) {
            this.code = code;
            this.buy = buy;
            this.sell = sell;
        }
    }

    // MARK: - Settings

    static boolean fiatEnabled(Context c) {
        return fiatEnabled(Prefs.of(c));
    }

    /** Hryvnia rates are on by default on a TV set to the Kyiv time zone. */
    static boolean fiatEnabled(SharedPreferences p) {
        String tz = TimeZone.getDefault().getID();
        return p.getBoolean(P_FIAT, "Europe/Kiev".equals(tz) || "Europe/Kyiv".equals(tz));
    }

    /** Is a download due? */
    static boolean due(Context c) {
        SharedPreferences p = Prefs.of(c);
        return fiatEnabled(p) && Net.stale(System.currentTimeMillis(), p.getLong(FIAT_TIME, 0), FIAT_EVERY_MS);
    }

    // MARK: - Downloading

    /** Downloads the rates when due and caches them. Throws if they couldn't be had. Network: off the UI thread. */
    static void refresh(Context c) throws IOException {
        SharedPreferences p = Prefs.of(c);
        long now = System.currentTimeMillis();
        if (!fiatEnabled(p) || !Net.stale(now, p.getLong(FIAT_TIME, 0), FIAT_EVERY_MS)) return;
        p.edit().putString(FIAT, fiatJson(fetchFiat(p, now))).putLong(FIAT_TIME, now).apply();
    }

    private static List<Fiat> fetchFiat(SharedPreferences p, long now) throws IOException {
        List<Fiat> out = null;
        long pause = p.getLong(MONO_PAUSE, 0);
        // The pause is over, or ends further off than it lasts: set while the clock was ahead.
        if (now >= pause || pause - now > MONO_PAUSE_MS) {
            try {
                out = parseMonobank(Net.get(MONOBANK));
            } catch (Net.HttpError e) {
                if (e.code == 429) p.edit().putLong(MONO_PAUSE, now + MONO_PAUSE_MS).apply();
            } catch (IOException | JSONException e) {
                // The NBU below.
            }
        }
        if (out == null || out.isEmpty()) {
            try {
                out = parseNbu(Net.get(NBU));
            } catch (JSONException e) {
                throw new IOException(e);
            }
        }
        if (out.isEmpty()) throw new IOException("no rates");
        return out;
    }

    // MARK: - Parsing the answers

    /** Monobank: [{"currencyCodeA":840,"currencyCodeB":980,"rateBuy":44.83,"rateSell":45.2345}, ...]. */
    static List<Fiat> parseMonobank(String json) throws JSONException {
        JSONArray a = new JSONArray(json);
        List<Fiat> out = new ArrayList<>();
        for (int k = 0; k < CURRENCIES.length; k++) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || o.optInt("currencyCodeA") != CURRENCIES[k] || o.optInt("currencyCodeB") != UAH) continue;
                double buy = o.optDouble("rateBuy", 0), sell = o.optDouble("rateSell", 0), cross = o.optDouble("rateCross", 0);
                if (buy > 0 && sell > 0) out.add(new Fiat(CURRENCY_NAMES[k], buy, sell));
                else if (cross > 0) out.add(new Fiat(CURRENCY_NAMES[k], cross, 0));
                break;
            }
        }
        return out;
    }

    /** The NBU: [{"r030":840,"cc":"USD","rate":41.2,...}, ...]. */
    static List<Fiat> parseNbu(String json) throws JSONException {
        JSONArray a = new JSONArray(json);
        List<Fiat> out = new ArrayList<>();
        for (int k = 0; k < CURRENCIES.length; k++) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || (o.optInt("r030") != CURRENCIES[k] && !CURRENCY_NAMES[k].equals(o.optString("cc")))) continue;
                double rate = o.optDouble("rate", 0);
                if (rate > 0) out.add(new Fiat(CURRENCY_NAMES[k], rate, 0));
                break;
            }
        }
        return out;
    }

    // MARK: - The cache (our own short JSON)

    static String fiatJson(List<Fiat> list) {
        JSONArray a = new JSONArray();
        try {
            for (Fiat f : list) a.put(new JSONObject().put("c", f.code).put("b", f.buy).put("s", f.sell));
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return a.toString();
    }

    static List<Fiat> fiatFromJson(String json) throws JSONException {
        List<Fiat> out = new ArrayList<>();
        JSONArray a = new JSONArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new Fiat(o.getString("c"), o.getDouble("b"), o.optDouble("s", 0)));
        }
        return out;
    }

    // MARK: - The line

    /**
     * The home screen's two versions from what is cached: {compact, full}, or null when there is nothing (fresh
     * enough) to show. The compact one is always visible, the full one while the details are open.
     */
    static String[] lines(Context c) {
        SharedPreferences p = Prefs.of(c);
        if (p.contains(OLD_KEYS[0]) || p.contains(OLD_KEYS[1])) {
            SharedPreferences.Editor e = p.edit();
            for (String k : OLD_KEYS) e.remove(k);
            e.apply();
        }
        String f = p.getString(FIAT, null);
        if (!fiatEnabled(p) || f == null || System.currentTimeMillis() - p.getLong(FIAT_TIME, 0) >= FIAT_SHOW_MS) return null;
        List<Fiat> fiat;
        try {
            fiat = fiatFromJson(f);
        } catch (JSONException e) {
            return null;
        }
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.getDefault());
        char d = sym.getDecimalSeparator(), g = sym.getGroupingSeparator();
        String full = line(fiat, d, g);
        return full.isEmpty() ? null : new String[]{compact(fiat, d, g), full};
    }

    /** "$ 41,80 · € 48,62": what a dollar and a euro cost (the selling rate, or the one rate there is). */
    static String compact(List<Fiat> fiat, char decimal, char grouping) {
        StringBuilder b = new StringBuilder();
        for (Fiat f : fiat) {
            if (b.length() > 0) b.append(" · ");
            String symbol = "USD".equals(f.code) ? "$" : "EUR".equals(f.code) ? "€" : f.code;
            b.append(symbol).append(' ').append(number(f.sell > 0 ? f.sell : f.buy, 2, decimal, grouping));
        }
        return b.toString();
    }

    /** "USD 44,83 / 45,23 · EUR 50,38 / 51,01": buy and sell (or the one rate there is). */
    static String line(List<Fiat> fiat, char decimal, char grouping) {
        StringBuilder b = new StringBuilder();
        for (Fiat f : fiat) {
            if (b.length() > 0) b.append(" · ");
            b.append(f.code).append(' ').append(number(f.buy, 2, decimal, grouping));
            if (f.sell > 0) b.append(" / ").append(number(f.sell, 2, decimal, grouping));
        }
        return b.toString();
    }

    /** {@code v} rounded to {@code decimals} places, thousands grouped: number(67120.4, 0, ',', ' ') = "67 120". */
    static String number(double v, int decimals, char decimal, char grouping) {
        long scale = 1;
        for (int i = 0; i < decimals; i++) scale *= 10;
        long r = Math.round(Math.abs(v) * scale);
        String whole = String.valueOf(r / scale);
        StringBuilder b = new StringBuilder();
        if (v < 0 && r != 0) b.append('−');
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) b.append(grouping);
            b.append(whole.charAt(i));
        }
        if (decimals > 0) {
            String frac = String.valueOf(r % scale);
            b.append(decimal);
            for (int i = frac.length(); i < decimals; i++) b.append('0');
            b.append(frac);
        }
        return b.toString();
    }
}
