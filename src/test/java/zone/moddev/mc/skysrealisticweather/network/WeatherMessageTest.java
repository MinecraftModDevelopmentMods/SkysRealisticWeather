package zone.moddev.mc.skysrealisticweather.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.PresetCatalog;
import zone.moddev.mc.skysrealisticweather.climate.WeatherSettings;
import zone.moddev.mc.skysrealisticweather.live.Observation;

import static org.junit.jupiter.api.Assertions.*;

final class WeatherMessageTest {
    @Test
    void carriesServerSettingsAndObservationAndCanClearAnInactiveWorld() {
        WeatherMessage outgoing = new WeatherMessage();
        ClimateProfile oxford = PresetCatalog.defaults().get("oxford").profile;
        outgoing.settings = new WeatherSettings(oxford, false, true, false, true);
        outgoing.live = new Observation(19000, -2, Observation.Condition.SNOW);
        ByteBuf buffer = Unpooled.buffer();
        try {
            outgoing.toBytes(buffer);
            WeatherMessage decoded = new WeatherMessage();
            decoded.fromBytes(buffer);
            assertEquals(365, decoded.settings.profile.daysInYear());
            assertEquals(90, decoded.settings.profile.period(3).days);
            assertEquals(oxford.period(3).temperature, decoded.settings.profile.period(3).temperature);
            assertFalse(decoded.settings.foliage);
            assertFalse(decoded.settings.passive);
            assertEquals(-2, decoded.live.celsius);
            assertEquals(Observation.Condition.SNOW, decoded.live.condition);
            assertEquals(0, buffer.readableBytes());
            new WeatherMessage().toBytes(buffer);
            WeatherMessage clear = new WeatherMessage();
            clear.fromBytes(buffer);
            assertNull(clear.settings);
        } finally {
            buffer.release();
        }
    }

    @Test
    void carriesAnEpisodeWithTheFullDurationAndIntensity() {
        WeatherMessage outgoing = new WeatherMessage();
        outgoing.settings = new WeatherSettings(PresetCatalog.defaults().get("burlington").profile,
                false, true, true, true, PresetCatalog.defaults().get("burlington").weather);
        outgoing.scheduled = true;
        outgoing.episode = new WeatherEpisode(WeatherKind.TROPICAL_STORM, 36012, 48000, 0.86F, true);
        ByteBuf buffer = Unpooled.buffer();
        try {
            outgoing.toBytes(buffer);
            WeatherMessage decoded = new WeatherMessage();
            decoded.fromBytes(buffer);
            assertTrue(decoded.scheduled);
            assertArrayEquals(outgoing.settings.weather.rows(), decoded.settings.weather.rows());
            assertNull(decoded.live);
            assertEquals(WeatherKind.TROPICAL_STORM, decoded.episode.kind);
            assertEquals(36012, decoded.episode.startTick);
            assertEquals(84012, decoded.episode.endTick);
            assertEquals(0.86F, decoded.episode.peakIntensity);
            assertTrue(decoded.episode.includesDryBiomes);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
