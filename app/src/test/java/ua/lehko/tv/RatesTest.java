package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class RatesTest {
    static final String MONOBANK = "[{\"currencyCodeA\":840,\"currencyCodeB\":980,\"date\":1792616473,\"rateBuy\":44.83,\"rateSell\":45.2345},"
            + "{\"currencyCodeA\":978,\"currencyCodeB\":980,\"date\":1792616473,\"rateBuy\":50.38,\"rateSell\":51.0101},"
            + "{\"currencyCodeA\":978,\"currencyCodeB\":840,\"date\":1792616473,\"rateBuy\":1.116,\"rateSell\":1.129},"
            + "{\"currencyCodeA\":985,\"currencyCodeB\":980,\"date\":1792616473,\"rateCross\":11.52}]";
    static final String NBU = "[{\"r030\":36,\"txt\":\"Австралійський долар\",\"rate\":27.1,\"cc\":\"AUD\",\"exchangedate\":\"23.10.2026\"},"
            + "{\"r030\":978,\"txt\":\"Євро\",\"rate\":50.6123,\"cc\":\"EUR\",\"exchangedate\":\"23.10.2026\"},"
            + "{\"r030\":840,\"txt\":\"Долар США\",\"rate\":44.9876,\"cc\":\"USD\",\"exchangedate\":\"23.10.2026\"}]";
    static final String BINANCE = "[{\"symbol\":\"BTCUSDT\",\"openPrice\":\"66320.00000000\",\"highPrice\":\"67500.00000000\","
            + "\"lowPrice\":\"66000.00000000\",\"lastPrice\":\"67120.00000000\",\"volume\":\"1.0\",\"quoteVolume\":\"1.0\","
            + "\"openTime\":1792530000000,\"closeTime\":1792616400000,\"firstId\":1,\"lastId\":2,\"count\":2},"
            + "{\"symbol\":\"ETHUSDT\",\"openPrice\":\"2419.68000000\",\"lastPrice\":\"2410.00000000\"}]";

    @Test public void monobank() throws Exception {
        List<Rates.Fiat> f = Rates.parseMonobank(MONOBANK);
        assertEquals(2, f.size());
        assertEquals("USD", f.get(0).code);
        assertEquals(44.83, f.get(0).buy, 1e-9);
        assertEquals(45.2345, f.get(0).sell, 1e-9);
        assertEquals("EUR", f.get(1).code);
        assertEquals(51.0101, f.get(1).sell, 1e-9);
    }

    @Test public void monobankCrossRateOnly() throws Exception {
        List<Rates.Fiat> f = Rates.parseMonobank("[{\"currencyCodeA\":840,\"currencyCodeB\":980,\"rateCross\":44.9}]");
        assertEquals(1, f.size());
        assertEquals(44.9, f.get(0).buy, 1e-9);
        assertEquals(0, f.get(0).sell, 0);
        assertTrue(Rates.parseMonobank("[]").isEmpty());
    }

    @Test public void nbu() throws Exception {
        List<Rates.Fiat> f = Rates.parseNbu(NBU);
        assertEquals(2, f.size());
        assertEquals("USD", f.get(0).code);
        assertEquals(44.9876, f.get(0).buy, 1e-9);
        assertEquals(0, f.get(0).sell, 0);
        assertEquals("EUR", f.get(1).code);
    }

    @Test public void binance() throws Exception {
        List<Rates.Coin> c = Rates.parseBinance(BINANCE);
        assertEquals(2, c.size());
        assertEquals("BTC", c.get(0).code);
        assertEquals(67120, c.get(0).price, 1e-9);
        assertEquals(1.206, c.get(0).change, 0.001);
        assertEquals(-0.4, c.get(1).change, 0.001);
        assertTrue(Rates.parseBinance("[{\"symbol\":\"BTCUSDT\",\"lastPrice\":\"oops\"}]").isEmpty());
    }

    @Test public void theLine() throws Exception {
        String line = Rates.line(Rates.parseMonobank(MONOBANK), Rates.parseBinance(BINANCE), ',', ' ');
        assertEquals("USD 44,83 / 45,23 · EUR 50,38 / 51,01 · BTC $67 120 ▲1,2% · ETH $2 410 ▼0,4%", line);
        assertEquals("USD 44.99 · EUR 50.61", Rates.line(Rates.parseNbu(NBU), Collections.<Rates.Coin>emptyList(), '.', ','));
        assertEquals("BTC $67,120 ▲1.2% · ETH $2,410 ▼0.4%",
                Rates.line(Collections.<Rates.Fiat>emptyList(), Rates.parseBinance(BINANCE), '.', ','));
        assertEquals("", Rates.line(Collections.<Rates.Fiat>emptyList(), Collections.<Rates.Coin>emptyList(), ',', ' '));
    }

    @Test public void numbers() {
        assertEquals("1 234 567", Rates.number(1234567.4, 0, ',', ' '));
        assertEquals("1 000,00", Rates.number(999.995, 2, ',', ' '));
        assertEquals("5,00", Rates.number(5, 2, ',', ' '));
        assertEquals("0,0", Rates.number(0.04, 1, ',', ' '));
        assertEquals("0,05", Rates.number(0.05, 2, ',', ' '));
        assertEquals("−3,5", Rates.number(-3.5, 1, ',', ' '));
        assertEquals("999", Rates.number(999, 0, ',', ' '));
    }

    @Test public void cacheRoundTrip() throws Exception {
        List<Rates.Fiat> f = Rates.fiatFromJson(Rates.fiatJson(Rates.parseMonobank(MONOBANK)));
        assertEquals(2, f.size());
        assertEquals(45.2345, f.get(0).sell, 1e-9);
        List<Rates.Coin> c = Rates.coinsFromJson(Rates.coinJson(Rates.parseBinance(BINANCE)));
        assertEquals(-0.4, c.get(1).change, 0.001);
    }
}
