package zone.moddev.mc.skysrealisticweather.climate;

import java.util.Map;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.Season;

import static org.junit.jupiter.api.Assertions.*;

final class PresetCatalogTest {
    @Test
    void allStarterEntriesRoundTripInForgeConfig() {
        Configuration configuration = new Configuration();
        PresetCatalog.seed(configuration);
        Map<String, Preset> catalog = PresetCatalog.read(configuration);
        assertEquals(28, catalog.size());
        assertEquals(18, catalog.values().stream().filter(preset -> preset.group.ordinal() <= Preset.Group.HIGHLANDS.ordinal()).count());
        assertEquals(9, catalog.values().stream().filter(preset -> preset.group == Preset.Group.FANTASY).count());
        for (Preset expected : PresetCatalog.defaults().values()) {
            Preset actual = catalog.get(expected.id);
            assertEquals(expected.label, actual.label);
            assertEquals(expected.description, actual.description);
            assertEquals(expected.group, actual.group);
            assertArrayEquals(expected.phaseLabels(), actual.phaseLabels());
            assertArrayEquals(expected.weather.rows(), actual.weather.rows());
            assertEquals(expected.profile.strength, actual.profile.strength);
            assertEquals(expected.id.equals("short_seasons") ? 96 : 365, actual.profile.daysInYear());
            for (Season season : Season.values()) {
                ClimateProfile.Period a = expected.profile.period(season.ordinal());
                ClimateProfile.Period b = actual.profile.period(season.ordinal());
                assertEquals(a.days, b.days);
                assertEquals(a.temperature, b.temperature);
                assertEquals(a.wetness, b.wetness);
                assertEquals(a.thunder, b.thunder);
                assertEquals(a.fog, b.fog);
            }
        }
    }

    @Test
    void existingPresetGetsItsOwnWeatherDefaultsAndMalformedRowsAreRetained() {
        Configuration configuration = new Configuration();
        PresetCatalog.seed(configuration);
        String section = "presets.tropical_monsoon";
        configuration.getCategory(section).remove("weatherEvents");
        Preset upgraded = PresetCatalog.read(configuration).get("tropical_monsoon");
        assertArrayEquals(WeatherPattern.defaults("tropical_monsoon").rows(), upgraded.weather.rows());
        String[] custom = {"HEATWAVE,9,30,10,0,2100,2700,10000,30000,0.6,0.8"};
        configuration.getCategory(section).get("weatherEvents").set(custom);
        assertArrayEquals(custom, PresetCatalog.read(configuration).get("tropical_monsoon").weather.rows());
        String[] invalid = {"THUNDERSTORM,5,NaN,5,2"};
        configuration.getCategory(section).get("weatherEvents").set(invalid);
        assertArrayEquals(upgraded.weather.rows(), PresetCatalog.read(configuration).get("tropical_monsoon").weather.rows());
        assertArrayEquals(invalid, configuration.getCategory(section).get("weatherEvents").getStringList());
    }

    @Test
    void seedingPreservesEditedDefinitionsAndInvalidEntriesAreIsolated() {
        Configuration configuration = new Configuration();
        PresetCatalog.seed(configuration);
        configuration.getCategory("presets.oxford").get("name").set("My Rainy Isles");
        configuration.getCategory("presets.oxford.winter").get("days").set(95);
        configuration.getCategory("presets.burlington.summer").get("temperature").set("NaN");
        configuration.getCategory("presets.custom").setComment("reserved");
        PresetCatalog.seed(configuration);
        Map<String, Preset> catalog = PresetCatalog.read(configuration);

        assertFalse(catalog.containsKey("burlington"));
        assertFalse(catalog.containsKey("custom"));
        assertEquals(27, catalog.size());
        assertEquals("NaN", configuration.getCategory("presets.burlington.summer").get("temperature").getString());
        assertEquals("My Rainy Isles", catalog.get("oxford").label);
        assertEquals(95, catalog.get("oxford").profile.period(Season.WINTER.ordinal()).days);
    }

    @Test
    void versionFourMigrationUpdatesStockWeatherAndKeepsPlayerEdits() {
        Configuration configuration = new Configuration();
        PresetCatalog.seed(configuration);
        String category = "presets.oxford";
        configuration.getCategory(category).get("weatherEvents").set(new com.google.gson.Gson().fromJson(
                WeatherPattern.resource("weather_weights_v4.json").get("oxford"), String[].class));
        // The old Oxford profile, including a player-defined phase label.
        com.google.gson.JsonArray previous = new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/assets/skysrealisticweather/presets_v4.json"),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
        com.google.gson.JsonObject old = null;
        for (com.google.gson.JsonElement entry : previous) if (entry.getAsJsonObject().get("id").getAsString().equals("oxford")) old = entry.getAsJsonObject();
        for (Season season : Season.values()) {
            com.google.gson.JsonArray p = old.getAsJsonArray("periods").get(season.ordinal()).getAsJsonArray();
            PresetCatalog.writePeriod(configuration, category + "." + WeatherConfiguration.category(season),
                    new ClimateProfile.Period(p.get(0).getAsInt(), p.get(1).getAsFloat(), p.get(2).getAsFloat(), p.get(3).getAsFloat(), p.get(4).getAsFloat()),
                    season == Season.WINTER ? "My Winter" : Preset.defaultPhaseLabel(season));
        }
        configuration.getCategory("presets.burlington.winter").get("temperature").set("-1.7");
        String[] custom = {"COLD_SNAP,12,0,12,75"};
        configuration.getCategory("presets.burlington").get("weatherEvents").set(custom);
        PresetCatalog.upgradeWeatherDefaults(configuration, "4");
        Map<String, Preset> result = PresetCatalog.read(configuration);
        assertArrayEquals(PresetCatalog.defaults().get("oxford").weather.rows(), result.get("oxford").weather.rows());
        assertEquals(PresetCatalog.defaults().get("oxford").profile.period(3).temperature, result.get("oxford").profile.period(3).temperature);
        assertEquals("My Winter", result.get("oxford").phaseLabel(Season.WINTER));
        assertEquals(-1.7F, result.get("burlington").profile.period(3).temperature);
        assertArrayEquals(custom, result.get("burlington").weather.rows());
    }

    @Test
    void extremeAndTropicalProfilesHaveDistinctCurves() {
        Preset monsoon = PresetCatalog.defaults().get("tropical_monsoon");
        assertArrayEquals(new String[] {"Early Wet", "Peak Wet", "Late Wet", "Dry"}, monsoon.phaseLabels());
        assertEquals(210, monsoon.profile.period(Season.WINTER.ordinal()).days);
        assertTrue(period("tropical_savanna", Season.WINTER).days > period("tropical_monsoon", Season.WINTER).days);
        assertTrue(period("tropical_rainforest", Season.WINTER).wetness > period("tropical_monsoon", Season.WINTER).wetness);
        assertTrue(period("mediterranean", Season.SUMMER).wetness < period("mediterranean", Season.AUTUMN).wetness);
        assertTrue(period("ice_cap", Season.SUMMER).wetness < period("winter_wonderland", Season.SUMMER).wetness);
        assertTrue(period("two_winters", Season.SPRING).temperature < period("two_winters", Season.SUMMER).temperature);
        assertTrue(period("two_winters", Season.AUTUMN).temperature < period("two_winters", Season.WINTER).temperature);
        assertEquals(135, period("golden_autumn", Season.AUTUMN).days);
        assertEquals(220, period("endless_summer", Season.SUMMER).days);
    }

    private static ClimateProfile.Period period(String id, Season season) {
        return PresetCatalog.defaults().get(id).profile.period(season.ordinal());
    }
}
