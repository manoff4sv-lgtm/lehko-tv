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
 * One line under the date: the dollar and euro in hryvnias (Monobank's buy/sell, the NBU's official rate as a
 * fallback) and Bitcoin and Ether in dollars with their 24-hour change (Binance). Public data without keys. The last
 * answers are kept, so the line still shows offline.
 */
final class Rates {
    private Rates() {}

    static final String P_FIAT = "rates_fiat_on", P_CRYPTO = "rates_crypto_on";
    static final String FIAT = "rates_fiat", FIAT_TIME = "rates_fiat_time", CRYPTO = "rates_crypto",
            CRYPTO_TIME = "rates_crypto_time", MONO_PAUSE = "rates_mono_pause_until";

    /** Monobank answers from a cache and limits how often it may be asked: twice an hour is plenty. */
    static final long FIAT_EVERY_MS = 30 * 60_000L, CRYPTO_EVERY_MS = 10 * 60_000L;
    /** After "429 Too many requests" Monobank is left alone for an hour (the NBU answers meanwhile). */
    static final long MONO_PAUSE_MS = 60 * 60_000L;
    /** Older than this, that part of the line is hidden (a TV that was off for days shouldn't show old prices). */
    static final long FIAT_SHOW_MS = 3 * 86_400_000L, CRYPTO_SHOW_MS = 86_400_000L;

    static final String MONOBANK = "https://api.monobank.ua/bank/currency";
    static final String NBU = "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json";
    static final String BINANCE = "https://api.binance.com/api/v3/ticker/24hr?type=MINI"
            + "&symbols=%5B%22BTCUSDT%22,%22ETHUSDT%22%5D";

    /** ISO 4217 numeric codes, in the order shown. */
    private static final int[] CURRENCIES = {840, 978};
    private static final String[] CURRENCY_NAMES = {"USD", "EUR"};
    private static final int UAH = 980;
    private static final String[] COINS = {"BTC", "ETH"};

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

    /** A coin in dollars and its change over 24 hours, in percent. */
    static final class Coin {
        final String code;
        final double price, change;

        Coin(String code, double price, double change) {
            this.code = code;
            this.price = price;
            this.change = change;
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

    static boolean cryptoEnabled(Context c) {
        return Prefs.of(c).getBoolean(P_CRYPTO, true);
    }

    /** Is a download due for anything that is turned on? */
    static boolean due(Context c) {
        SharedPreferences p = Prefs.of(c);
        long now = System.currentTimeMillis();
        return fiatEnabled(p) && now - p.getLong(FIAT_TIME, 0) > FIAT_EVERY_MS
                || cryptoEnabled(c) && now - p.getLong(CRYPTO_TIME, 0) > CRYPTO_EVERY_MS;
    }

    // MARK: - Downloading

    /** Downloads what is due and caches it. Throws if something due couldn't be had. Network: off the UI thread. */
    static void refresh(Context c) throws IOException, JSONException {
        SharedPreferences p = Prefs.of(c);
        long now = System.currentTimeMillis();
        IOException failed = null;
        if (fiatEnabled(p) && now - p.getLong(FIAT_TIME, 0) > FIAT_EVERY_MS) {
            try {
                p.edit().putString(FIAT, fiatJson(fetchFiat(p, now))).putLong(FIAT_TIME, now).apply();
            } catch (IOException e) {
                failed = e;
            }
        }
        if (cryptoEnabled(c) && now - p.getLong(CRYPTO_TIME, 0) > CRYPTO_EVERY_MS) {
            List<Coin> coins = parseBinance(Net.get(BINANCE));
            if (coins.isEmpty()) throw new IOException("no prices");
            p.edit().putString(CRYPTO, coinJson(coins)).putLong(CRYPTO_TIME, now).apply();
        }
        if (failed != null) throw failed;
    }

    private static List<Fiat> fetchFiat(SharedPreferences p, long now) throws IOException {
        List<Fiat> out = null;
        if (now >= p.getLong(MONO_PAUSE, 0)) {
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

    /** Binance MINI tickers: [{"symbol":"BTCUSDT","openPrice":"67000.0","lastPrice":"67120.0",...}, ...]. */
    static List<Coin> parseBinance(String json) throws JSONException {
        JSONArray a = new JSONArray(json);
        List<Coin> out = new ArrayList<>();
        for (String coin : COINS) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !(coin + "USDT").equals(o.optString("symbol"))) continue;
                double last = number(o.optString("lastPrice")), open = number(o.optString("openPrice"));
                if (last > 0) out.add(new Coin(coin, last, open > 0 ? (last - open) / open * 100 : 0));
                break;
            }
        }
        return out;
    }

    private static double number(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
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

    static String coinJson(List<Coin> list) {
        JSONArray a = new JSONArray();
        try {
            for (Coin c : list) a.put(new JSONObject().put("c", c.code).put("p", c.price).put("d", c.change));
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return a.toString();
    }

    static List<Coin> coinsFromJson(String json) throws JSONException {
        List<Coin> out = new ArrayList<>();
        JSONArray a = new JSONArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new Coin(o.getString("c"), o.getDouble("p"), o.optDouble("d", 0)));
        }
        return out;
    }

    // MARK: - The line

    /** The line for the home screen from what is cached, or null when there is nothing (fresh enough) to show. */
    static String line(Context c) {
        SharedPreferences p = Prefs.of(c);
        long now = System.currentTimeMillis();
        List<Fiat> fiat = new ArrayList<>();
        List<Coin> coins = new ArrayList<>();
        try {
            String f = p.getString(FIAT, null);
            if (fiatEnabled(p) && f != null && now - p.getLong(FIAT_TIME, 0) < FIAT_SHOW_MS) fiat = fiatFromJson(f);
            String k = p.getString(CRYPTO, null);
            if (cryptoEnabled(c) && k != null && now - p.getLong(CRYPTO_TIME, 0) < CRYPTO_SHOW_MS) coins = coinsFromJson(k);
        } catch (JSONException e) {
            return null;
        }
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.getDefault());
        String line = line(fiat, coins, sym.getDecimalSeparator(), sym.getGroupingSeparator());
        return line.isEmpty() ? null : line;
    }

    /** "USD 44,83 / 45,23 · EUR 50,38 / 51,01 · BTC $67 120 ▲1,2% · ETH $2 410 ▼0,4%" (or a part of it). */
    static String line(List<Fiat> fiat, List<Coin> coins, char decimal, char grouping) {
        StringBuilder b = new StringBuilder();
        for (Fiat f : fiat) {
            if (b.length() > 0) b.append(" · ");
            b.append(f.code).append(' ').append(number(f.buy, 2, decimal, grouping));
            if (f.sell > 0) b.append(" / ").append(number(f.sell, 2, decimal, grouping));
        }
        for (Coin k : coins) {
            if (b.length() > 0) b.append(" · ");
            b.append(k.code).append(" $").append(number(k.price, k.price >= 100 ? 0 : 2, decimal, grouping))
                    .append(' ').append(k.change < 0 ? '▼' : '▲').append(number(Math.abs(k.change), 1, decimal, grouping))
                    .append('%');
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
