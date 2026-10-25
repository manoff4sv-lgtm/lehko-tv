package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Locale;

public class WeatherTest {
    static final String FORECAST = "{\"latitude\":50.45,\"longitude\":30.52,\"timezone\":\"Europe/Kiev\","
            + "\"utc_offset_seconds\":10800,"
            + "\"current\":{\"time\":\"2026-10-22T13:15\",\"interval\":900,\"temperature_2m\":12.4,\"weather_code\":3,\"is_day\":1},"
            + "\"minutely_15\":{\"time\":[\"2026-10-22T13:15\",\"2026-10-22T13:30\",\"2026-10-22T13:45\",\"2026-10-22T14:00\"],"
            + "\"precipitation\":[0.3,0.0,0.05,0.4]},"
            + "\"hourly\":{\"time\":[\"2026-10-22T13:00\",\"2026-10-22T14:00\",\"2026-10-22T15:00\",\"2026-10-22T16:00\","
            + "\"2026-10-22T17:00\",\"2026-10-22T18:00\",\"2026-10-22T19:00\"],\"temperature_2m\":[12.4,12.9,13.3,12.1,10.8,9.6,8.9],"
            + "\"weather_code\":[3,3,61,61,2,1,1],\"is_day\":[1,1,1,1,0,0,0]},"
            + "\"daily\":{\"time\":[\"2026-10-22\",\"2026-10-23\"],\"weather_code\":[61,3],\"temperature_2m_max\":[14.6,13.0],"
            + "\"temperature_2m_min\":[7.5,6.0],\"sunrise\":[\"2026-10-22T07:23\",\"2026-10-23T07:25\"],"
            + "\"sunset\":[\"2026-10-22T17:41\",\"2026-10-23T17:39\"]}}";
    /** 2026-10-22 13:15 in Kyiv (UTC+3). */
    static final long NOW = 1792664100000L;
    static final long HOUR = 3_600_000L;

    @Test public void parsesCurrentAndDaily() throws Exception {
        Weather.Data d = Weather.parse(FORECAST);
        assertEquals(12.4, d.temp, 0.001);
        assertEquals(3, d.code);
        assertTrue(d.day);
        assertEquals(14.6, d.max, 0.001);
        assertEquals(7.5, d.min, 0.001);
        // The slot at "now" is skipped; 0.05 mm is below the threshold; 14:00 is the first real rain.
        assertEquals(NOW + 45 * 60_000L, d.precipStart);
        assertFalse(d.precipSnow);
    }

    @Test public void comingHours() throws Exception {
        Weather.Data d = Weather.parse(FORECAST);
        assertEquals(7, d.hours.size());
        assertEquals("13:00", d.hours.get(0).time);
        assertEquals(1792663200000L, d.hours.get(0).at);
        List<Weather.Hour> next = Weather.next(d, NOW, 5);
        assertEquals(5, next.size());
        assertEquals("14:00", next.get(0).time);
        assertEquals(12.9, next.get(0).temp, 0.001);
        assertEquals(61, next.get(1).code);
        assertEquals("18:00", next.get(4).time);
        assertFalse(next.get(4).day);
        // Past the next full hour, before the download half an hour after this one, five are still left.
        assertEquals(5, Weather.next(d, NOW + HOUR, 5).size());
    }

    @Test public void sunriseAndSunset() throws Exception {
        Weather.Data d = Weather.parse(FORECAST);
        assertEquals("07:23", d.sunrise);
        assertEquals("17:41", d.sunset);
        assertFalse(Weather.night(d, NOW));
        assertTrue(Weather.night(d, NOW + 5 * HOUR)); // 18:15
        assertTrue(Weather.night(d, NOW - 7 * HOUR)); // 06:15
        // A forecast two days old still knows roughly when it is dark.
        assertFalse(Weather.night(d, NOW + 48 * HOUR));
        assertTrue(Weather.night(d, NOW + 48 * HOUR + 7 * HOUR));
    }

    @Test public void olderCacheWithoutDetails() throws Exception {
        String old = "{\"current\":{\"time\":\"2026-10-22T21:00\",\"temperature_2m\":5,\"weather_code\":0,\"is_day\":0}}";
        Weather.Data d = Weather.parse(old);
        assertTrue(d.hours.isEmpty());
        assertNull(d.sunrise);
        assertTrue(Weather.night(d, NOW));
        assertTrue(Weather.next(d, NOW, 5).isEmpty());
    }

    @Test public void epoch() {
        assertEquals(1792663200000L, Weather.epoch("2026-10-22T13:00", 10800));
        assertEquals(0, Weather.epoch("2026-10-22", 10800));
        assertEquals(0, Weather.epoch("soon", 0));
    }

    @Test public void noHintWhileItAlreadyRains() throws Exception {
        Weather.Data d = Weather.parse(FORECAST.replace("\"weather_code\":3", "\"weather_code\":63"));
        assertEquals(0, d.precipStart);
    }

    @Test public void degrees() {
        assertEquals("+12°", Weather.degrees(12.4, false));
        assertEquals("−3°", Weather.degrees(-2.6, false));
        assertEquals("0°", Weather.degrees(-0.4, false));
        assertEquals("+54°F", Weather.degrees(12.4, true));
        assertEquals("−40°F", Weather.degrees(-40, true));
    }

    /** English (United States) is the usual English on TVs anywhere: Fahrenheit only in America. */
    @Test public void fahrenheitWhere() {
        assertFalse(Weather.fahrenheit(Locale.US, "Europe/Kiev"));
        assertTrue(Weather.fahrenheit(Locale.US, "America/New_York"));
        assertFalse(Weather.fahrenheit(Locale.forLanguageTag("uk-UA"), "America/New_York"));
        assertTrue(Weather.fahrenheit(Locale.forLanguageTag("en-GB-u-mu-fahrenhe"), "Europe/London"));
        assertFalse(Weather.fahrenheit(Locale.forLanguageTag("en-US-u-mu-celsius"), "America/Chicago"));
    }

    /** Asked for in Celsius now; a cache from a US TV before that is still read right. */
    @Test public void olderCacheInFahrenheit() throws Exception {
        Weather.Data d = Weather.parse("{\"current_units\":{\"temperature_2m\":\"°F\"},"
                + "\"current\":{\"time\":\"2026-10-22T13:15\",\"temperature_2m\":50,\"weather_code\":3,\"is_day\":1},"
                + "\"daily\":{\"temperature_2m_max\":[59],\"temperature_2m_min\":[41]}}");
        assertEquals(10, d.temp, 0.001);
        assertEquals(15, d.max, 0.001);
        assertEquals(5, d.min, 0.001);
    }

    @Test public void places() throws Exception {
        List<Weather.Place> p = Weather.parsePlaces("{\"results\":[{\"name\":\"Київ\",\"latitude\":50.45,\"longitude\":30.52,"
                + "\"country\":\"Україна\",\"admin1\":\"Київ\"},{\"name\":\"Kyiv\",\"latitude\":1,\"longitude\":2}]}");
        assertEquals(2, p.size());
        assertEquals("Київ, Україна", p.get(0).detail);
        assertEquals("", p.get(1).detail);
        assertTrue(Weather.parsePlaces("{\"generationtime_ms\":0.1}").isEmpty());
    }

    /** With no city chosen, the outage region says where the TV is; without one, the time zone. */
    @Test public void defaultCity() {
        assertEquals(Weather.KYIV, Weather.defaultCity("kyiv", "UTC"));
        assertEquals(Weather.KYIV, Weather.defaultCity("kyiv-region", "Europe/London"));
        assertEquals(Weather.DNIPRO, Weather.defaultCity("dnipro-dnem", "Europe/Kiev"));
        assertEquals(Weather.DNIPRO, Weather.defaultCity("dnipro-cek", "Europe/Kiev"));
        assertEquals(Weather.ODESA, Weather.defaultCity("odesa", "Europe/Kyiv"));
        assertEquals(Weather.KYIV, Weather.defaultCity(null, "Europe/Kiev"));
        assertEquals(Weather.KYIV, Weather.defaultCity(null, "Europe/Kyiv"));
        assertEquals(-1, Weather.defaultCity(null, "Europe/Berlin"));
        for (Outages.Region r : Outages.REGIONS) assertTrue(r.id, Weather.defaultCity(r.id, "UTC") >= 0);
    }

    /** A new outage region moves the default city under a fresh forecast: the old city's isn't shown. */
    @Test public void cacheForAnotherPlace() {
        double[] kyiv = {50.4501, 30.5234}, dnipro = {48.4647, 35.0462}, lviv = {49.8397, 24.0297};
        assertTrue(Weather.cacheFits(Weather.placeKey(kyiv), false, kyiv));
        assertTrue(Weather.cacheFits(Weather.placeKey(kyiv), false, kyiv.clone()));
        assertFalse(Weather.cacheFits(Weather.placeKey(kyiv), false, dnipro));
        assertFalse(Weather.cacheFits(Weather.placeKey(kyiv), false, null));
        assertFalse(Weather.cacheFits(Weather.placeKey(kyiv), true, lviv));
        // Kept before the place was: the chosen city's, else Kyiv's (then the only default), so Dnipro gets a new one.
        assertTrue(Weather.cacheFits(null, true, lviv));
        assertTrue(Weather.cacheFits(null, false, kyiv));
        assertFalse(Weather.cacheFits(null, false, dnipro));
        assertFalse(Weather.cacheFits(null, false, null));
    }

    @Test public void snowCodes() {
        assertTrue(Weather.isSnow(71));
        assertTrue(Weather.isSnow(86));
        assertFalse(Weather.isSnow(61));
    }
}
