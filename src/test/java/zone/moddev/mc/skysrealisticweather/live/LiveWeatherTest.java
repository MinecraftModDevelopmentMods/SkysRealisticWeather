package zone.moddev.mc.skysrealisticweather.live;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.live.Observation.Condition;

import static org.junit.jupiter.api.Assertions.*;

final class LiveWeatherTest {
    @Test
    void mapsSupportedWeatherAndRejectsInvalidJSON() throws IOException {
        assertEquals(Condition.CLEAR, reading(3).condition);
        assertEquals(Condition.FOG, reading(48).condition);
        assertEquals(Condition.RAIN, reading(67).condition);
        assertEquals(Condition.SNOW, reading(85).condition);
        assertEquals(Condition.THUNDER, reading(97).condition);
        assertTrue(reading(85).referenceTemperature() < 0.15F);
        assertTrue(reading(61).referenceTemperature() > 0.15F);
        assertThrows(IOException.class, () -> reading(123));
        assertThrows(IOException.class, () -> OpenMeteo.parse("{\"current\":{}}"));
        assertThrows(IOException.class, () -> OpenMeteo.parse(
                "{\"current\":{\"temperature_2m\":4,\"weather_code\":71.2,\"time\":1000}}"));
        assertThrows(IllegalArgumentException.class, () -> new LiveLocation(Double.NaN, 0));
    }

    @Test
    void checksModelTimeAndFutureSkew() throws IOException {
        Observation reading = reading(95);
        assertTrue(reading.fresh(12700));
        assertFalse(reading.fresh(12701));
        assertTrue(reading.fresh(9880));
        assertFalse(reading.fresh(9879));
    }

    @Test
    void reusesFreshReadingsAndRecoversAfterStaleFallback() throws Exception {
        AtomicLong now = new AtomicLong(10000);
        AtomicInteger request = new AtomicInteger();
        AtomicInteger mode = new AtomicInteger();
        WeatherProvider fixture = location -> {
            request.incrementAndGet();
            if (mode.get() == 1) return new Observation(10000, 2, Condition.RAIN);
            if (mode.get() == 2) return new Observation(now.get(), -4, Condition.SNOW);
            throw new IOException("Fixture outage");
        };
        Clock time = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochSecond(now.get()); }
        };
        try (LiveWeather service = new LiveWeather(LiveLocation.BURLINGTON, fixture, time, 10, TimeUnit.MILLISECONDS)) {
            await(() -> request.get() > 0);
            assertNull(service.current());
            mode.set(1);
            await(() -> service.current() != null);
            assertEquals(Condition.RAIN, service.current().condition);
            int polls = request.get();
            mode.set(0);
            await(() -> request.get() > polls + 1);
            assertEquals(Condition.RAIN, service.current().condition);
            now.set(12701);
            assertNull(service.current());
            mode.set(2);
            await(() -> service.current() != null);
            assertEquals(Condition.SNOW, service.current().condition);
        }
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "fixture did not advance");
    }

    private static Observation reading(int code) throws IOException {
        return OpenMeteo.parse("{\"current\":{\"time\":10000,\"temperature_2m\":4,\"weather_code\":" + code + "}}");
    }
}
