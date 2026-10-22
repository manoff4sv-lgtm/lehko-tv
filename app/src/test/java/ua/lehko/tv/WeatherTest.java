package ua.lehko.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class WeatherTest {
    static final String FORECAST = "{\"latitude\":50.45,\"longitude\":30.52,\"timezone\":\"Europe/Kiev\","
            + "\"current\":{\"time\":\"2026-10-22T13:15\",\"interval\":900,\"temperature_2m\":12.4,\"weather_code\":3,\"is_day\":1},"
            + "\"minutely_15\":{\"time\":[\"2026-10-22T13:15\",\"2026-10-22T13:30\",\"2026-10-22T13:45\",\"2026-10-22T14:00\"],"
            + "\"precipitation\":[0.3,0.0,0.05,0.4]},"
            + "\"daily\":{\"time\":[\"2026-10-22\"],\"weather_code\":[61],\"temperature_2m_max\":[14.6],\"temperature_2m_min\":[7.5]}}";

    @Test public void parsesCurrentAndDaily() throws Exception {
        Weather.Data d = Weather.parse(FORECAST);
        assertEquals(12.4, d.temp, 0.001);
        assertEquals(3, d.code);
        assertTrue(d.day);
        assertEquals(14.6, d.max, 0.001);
        assertEquals(7.5, d.min, 0.001);
        // The slot at "now" is skipped; 0.05 mm is below the threshold; 14:00 is the first real rain.
        assertEquals("14:00", d.precipAt);
        assertFalse(d.precipSnow);
    }

    @Test public void noHintWhileItAlreadyRains() throws Exception {
        Weather.Data d = Weather.parse(FORECAST.replace("\"weather_code\":3", "\"weather_code\":63"));
        assertNull(d.precipAt);
    }

    @Test public void degrees() {
        assertEquals("+12°", Weather.degrees(12.4));
        assertEquals("−3°", Weather.degrees(-2.6));
        assertEquals("0°", Weather.degrees(-0.4));
    }

    @Test public void places() throws Exception {
        List<Weather.Place> p = Weather.parsePlaces("{\"results\":[{\"name\":\"Київ\",\"latitude\":50.45,\"longitude\":30.52,"
                + "\"country\":\"Україна\",\"admin1\":\"Київ\"},{\"name\":\"Kyiv\",\"latitude\":1,\"longitude\":2}]}");
        assertEquals(2, p.size());
        assertEquals("Київ, Україна", p.get(0).detail);
        assertEquals("", p.get(1).detail);
        assertTrue(Weather.parsePlaces("{\"generationtime_ms\":0.1}").isEmpty());
    }

    @Test public void snowCodes() {
        assertTrue(Weather.isSnow(71));
        assertTrue(Weather.isSnow(86));
        assertFalse(Weather.isSnow(61));
    }
}
