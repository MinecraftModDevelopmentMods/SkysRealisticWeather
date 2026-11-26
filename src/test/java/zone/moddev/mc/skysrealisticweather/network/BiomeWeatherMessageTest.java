package zone.moddev.mc.skysrealisticweather.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import zone.moddev.mc.skysrealisticweather.climate.*;
import static org.junit.jupiter.api.Assertions.*;

final class BiomeWeatherMessageTest {
    @Test
    void sendsAuthoritativeBiomeMappingsAndEditedClimateSchedules() {
        Preset original = PresetCatalog.defaults().get("tropical_monsoon");
        java.util.Map<String, Preset> presets = new java.util.LinkedHashMap<>(PresetCatalog.defaults());
        Preset edited = new Preset(original.id, "Server Monsoon", "", Preset.Group.TROPICAL,
                original.profile, original.phaseLabels(), new WeatherPattern(new String[] {"MONSOON_BURST,50,90,30,0"}));
        presets.put(edited.id, edited);
        WeatherMessage outgoing = new WeatherMessage();
        outgoing.settings = new WeatherSettings(original.profile, false, true, true, true)
                .withMode(ClimateMode.PER_BIOME, new BiomeClimateMap(presets,
                        Collections.singletonMap("example:tropics", edited.id)))
                .withNativeSnowBiomes(new String[] {"example:permafrost", "minecraft:ice_flats"});
        outgoing.seed = 123948;
        ByteBuf buffer = Unpooled.buffer();
        try {
            outgoing.toBytes(buffer);
            WeatherMessage incoming = new WeatherMessage();
            incoming.fromBytes(buffer);
            assertEquals(outgoing.seed, incoming.seed);
            assertEquals(ClimateMode.PER_BIOME, incoming.settings.mode);
            assertEquals(edited.id, incoming.settings.biomes.mappings.get("example:tropics"));
            assertEquals("Server Monsoon", incoming.settings.biomes.presets.get(edited.id).label);
            assertArrayEquals(outgoing.settings.nativeSnowBiomes(), incoming.settings.nativeSnowBiomes());
            assertArrayEquals(edited.weather.rows(), incoming.settings.biomes.presets.get(edited.id).weather.rows());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
