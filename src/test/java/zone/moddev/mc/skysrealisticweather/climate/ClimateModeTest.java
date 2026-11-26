package zone.moddev.mc.skysrealisticweather.climate;

import java.nio.file.Path;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import static org.junit.jupiter.api.Assertions.*;

final class ClimateModeTest {
    @TempDir Path directory;
    private Object previousHome;

    @org.junit.jupiter.api.BeforeEach
    void initializeForgeConfigHome() throws Exception {
        java.lang.reflect.Field home = net.minecraftforge.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        previousHome = home.get(null);
        home.set(null, directory.toFile());
    }

    @org.junit.jupiter.api.AfterEach
    void restoreForgeConfigHome() throws Exception {
        java.lang.reflect.Field home = net.minecraftforge.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, previousHome);
    }

    @Test
    void freshConfigUsesBiomeClimatesAndKeepsMappingsAndCustomPresets() {
        java.io.File file = directory.resolve("weather.cfg").toFile();
        WeatherConfiguration.load(file);
        assertEquals(ClimateMode.PER_BIOME, WeatherConfiguration.startupSettings().mode);
        assertNull(WeatherConfiguration.startupLocation());
        WeatherConfiguration.file().get(WeatherConfiguration.BIOMES, "example:enchanted_forest", "mistwood");
        WeatherConfiguration.file().getCategory(WeatherConfiguration.GENERAL).get("biomeStrength").set(0.6);
        WeatherConfiguration.saveEdits();
        WeatherConfiguration.load(file);
        assertEquals("burlington", WeatherConfiguration.activePreset());
        assertEquals(0.6F, WeatherConfiguration.startupSettings().profile.strength);
        assertEquals("mistwood", WeatherConfiguration.startupSettings().biomes.mappings.get("example:enchanted_forest"));
        WeatherConfiguration.usePreset("short_seasons");
        assertEquals(365, WeatherConfiguration.read().profile.daysInYear());
        for (zone.moddev.mc.skysrealisticweather.api.Season season : zone.moddev.mc.skysrealisticweather.api.Season.values()) {
            WeatherConfiguration.file().getCategory(WeatherConfiguration.CALENDAR)
                    .get(WeatherConfiguration.category(season) + "Days").set(24);
        }
        assertEquals(96, WeatherConfiguration.read().profile.daysInYear());
        WeatherConfiguration.useMode(ClimateMode.UNIVERSAL);
        WeatherConfiguration.usePreset("winter_wonderland");
        WeatherConfiguration.load(file);
        assertEquals(ClimateMode.UNIVERSAL, WeatherConfiguration.startupSettings().mode);
        assertEquals("winter_wonderland", WeatherConfiguration.startupPreset());
        WeatherConfiguration.useMode(ClimateMode.REAL_WORLD);
        WeatherConfiguration.load(file);
        assertNotNull(WeatherConfiguration.startupLocation());
        WeatherConfiguration.useMode(ClimateMode.PER_BIOME);
        WeatherConfiguration.load(file);
        assertNull(WeatherConfiguration.startupLocation());
        assertEquals("winter_wonderland", WeatherConfiguration.startupPreset());
    }

    @Test
    void calibrationUpgradeKeepsEditedWeatherAndArcticExceptions() {
        java.io.File file = directory.resolve("v3.cfg").toFile();
        Configuration old = new Configuration(file, "3");
        PresetCatalog.seed(old);
        com.google.gson.JsonObject rows = WeatherPattern.resource("weather_weights_v3.json");
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : rows.entrySet()) {
            old.getCategory(PresetCatalog.path(entry.getKey())).get("weatherEvents")
                    .set(new com.google.gson.Gson().fromJson(entry.getValue(), String[].class));
        }
        String[] custom = {"DRIZZLE,4,5,6,7"};
        old.getCategory("presets.oxford").get("weatherEvents").set(custom);
        old.get("general", "nativeSnowBiomes", new String[] {"example:arctic"});
        old.save();
        WeatherConfiguration.load(file);
        assertArrayEquals(custom, WeatherConfiguration.preset("oxford").weather.rows());
        assertArrayEquals(WeatherPattern.defaults("mediterranean").rows(), WeatherConfiguration.preset("mediterranean").weather.rows());
        assertArrayEquals(WeatherPattern.defaults("tropical_rainforest").rows(), WeatherConfiguration.preset("tropical_rainforest").weather.rows());
        assertArrayEquals(new String[] {"example:arctic"}, WeatherConfiguration.startupSettings().nativeSnowBiomes());
        WeatherConfiguration.load(file);
        assertArrayEquals(custom, WeatherConfiguration.preset("oxford").weather.rows());
    }

    @Test
    void existingLiveConfigMigratesAndInvalidModeFallsBackToBiomeDefault() {
        java.io.File file = directory.resolve("legacy.cfg").toFile();
        Configuration legacy = new Configuration(file, "2");
        legacy.get("live_weather", "enabled", true);
        legacy.get("live_weather", "location", "OXFORD");
        legacy.save();
        WeatherConfiguration.load(file);
        assertEquals(ClimateMode.REAL_WORLD, WeatherConfiguration.mode());
        assertEquals(zone.moddev.mc.skysrealisticweather.live.LiveLocation.OXFORD.latitude,
                WeatherConfiguration.startupLocation().latitude);
        WeatherConfiguration.file().getCategory("general").get("climateMode").set("invalid");
        assertEquals(ClimateMode.PER_BIOME, WeatherConfiguration.read().mode);
    }
}
