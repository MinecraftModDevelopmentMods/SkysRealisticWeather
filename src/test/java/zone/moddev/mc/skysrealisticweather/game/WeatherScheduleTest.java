package zone.moddev.mc.skysrealisticweather.game;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.PresetCatalog;
import zone.moddev.mc.skysrealisticweather.climate.WeatherPattern;

import static org.junit.jupiter.api.Assertions.*;

final class WeatherScheduleTest {
    private static final int DAY = ClimateProfile.TICKS_PER_DAY;

    @Test
    void coldAirAndRainCanCoexistAndBothSurviveTimeSkips() {
        ClimateProfile profile = PresetCatalog.defaults().get("oxford").profile;
        WeatherPattern weather = new WeatherPattern(new String[] {
                "FRONTAL_PRECIPITATION,100,100,100,100,6000,6000,24000,24000,0.8,0.8",
                "COLD_SNAP,100,100,100,100,0,0,48000,48000,1,1"
        });
        WeatherSchedule schedule = new WeatherSchedule(123, profile, weather);
        assertEquals(WeatherKind.FRONTAL_PRECIPITATION, schedule.at(9000).kind);
        assertEquals(WeatherKind.COLD_SNAP, schedule.thermalAt(9000).kind);
        schedule.at(70L * DAY);
        WeatherSchedule restarted = new WeatherSchedule(123, profile, weather);
        assertEquals(restarted.at(9000).startTick, schedule.at(9000).startTick);
        assertEquals(restarted.thermalAt(9000).startTick, schedule.thermalAt(9000).startTick);
        assertEquals(-1, ClimateProfile.daylightTemperature(23000), 0.0001);
        assertEquals(1, ClimateProfile.daylightTemperature(9000), 0.0001);
    }

    @Test
    void monsoonHasAfternoonDownpoursAndLongBurstsConcentratedInTheWetSeason() {
        Map<WeatherKind, int[]> events = sample("tropical_monsoon");
        int[] showers = events.get(WeatherKind.CONVECTIVE_SHOWER);
        int[] bursts = events.get(WeatherKind.MONSOON_BURST);
        assertTrue(showers[1] * 210 > 5 * showers[3] * 75, "peak phase shower rate");
        assertTrue(bursts[1] * 210 > 8 * bursts[3] * 75, "peak phase burst rate");
        assertTrue(showers[1] > bursts[1], "short downpours are the usual event");
    }

    @Test
    void representativeClimatesHaveDifferentStormSeasons() {
        Map<WeatherKind, int[]> mediterranean = sample("mediterranean");
        int[] fronts = mediterranean.get(WeatherKind.FRONTAL_PRECIPITATION);
        assertTrue(fronts[2] > fronts[1] * 4);
        assertTrue(mediterranean.get(WeatherKind.HEATWAVE)[1] > 0);

        Map<WeatherKind, int[]> burlington = sample("burlington");
        assertTrue(burlington.get(WeatherKind.THUNDERSTORM)[1] > burlington.get(WeatherKind.THUNDERSTORM)[3] * 5);
        assertTrue(burlington.get(WeatherKind.WINTER_STORM)[3] > burlington.get(WeatherKind.WINTER_STORM)[1] * 5);
        assertTrue(burlington.get(WeatherKind.SNOW_SQUALL)[3] > 0);
        assertTrue(burlington.get(WeatherKind.FREEZING_RAIN)[3] > 0);

        Map<WeatherKind, int[]> iceCap = sample("ice_cap");
        assertTrue(iceCap.get(WeatherKind.BLOWING_SNOW)[3] > iceCap.get(WeatherKind.BLIZZARD)[3] * 3);
        assertFalse(iceCap.containsKey(WeatherKind.THUNDERSTORM));
        assertTrue(sample("cool_maritime").containsKey(WeatherKind.ADVECTION_FOG));
        assertTrue(sample("hot_desert").containsKey(WeatherKind.DUST_STORM));

        int[] twoWinters = sample("two_winters").get(WeatherKind.WINTER_STORM);
        assertTrue(Math.min(twoWinters[0], twoWinters[2]) > Math.max(twoWinters[1], twoWinters[3]) * 3);
    }

    @Test
    void editedTimingsAreReproducibleAcrossTimeSkipsAndRestarts() {
        ClimateProfile profile = PresetCatalog.defaults().get("burlington").profile;
        WeatherPattern pattern = new WeatherPattern(new String[] {
                "HEATWAVE,100,100,100,100,21000,21000,36000,36000,0.85,0.85"
        });
        WeatherSchedule schedule = new WeatherSchedule(123, profile, pattern);
        long time = DAY + 6000;
        WeatherEpisode original = schedule.at(time);
        assertEquals(21000, original.startTick);
        assertEquals(57000, original.endTick);
        assertEquals(0.85F, original.intensity(time));
        assertSame(original, schedule.at(time + 1));
        assertEquals(2 * DAY + 21000, schedule.at(3L * DAY + 300).startTick);
        assertEquals(original.startTick, schedule.at(time).startTick);
        assertEquals(original.peakIntensity, new WeatherSchedule(123, profile, pattern).at(time).peakIntensity);
        assertEquals(DAY + 21000, schedule.at(DAY + 21000).startTick);
        assertNull(new WeatherSchedule(123, profile, new WeatherPattern(new String[0])).at(time));
    }

    @Test
    void aSquallBuildsAndFadesAndItsCellsMove() {
        WeatherEpisode squall = new WeatherEpisode(WeatherKind.SNOW_SQUALL, 21123, 1300, 0.9F);
        assertEquals(0, squall.intensity(21122));
        assertTrue(squall.intensity(21123) < squall.intensity(21190));
        assertEquals(0.9F, squall.intensity(21600));
        assertTrue(squall.intensity(22400) < squall.intensity(21600));
        assertEquals(0, squall.intensity(22423));
        boolean wet = false;
        boolean dry = false;
        boolean moved = false;
        for (int x = -500; x <= 500; x += 8) {
            boolean initial = squall.covers(x, -30, 21123);
            wet |= initial;
            dry |= !initial;
            moved |= initial != squall.covers(x, -30, 22400);
        }
        assertTrue(wet && dry && moved);
    }

    private static Map<WeatherKind, int[]> sample(String name) {
        Preset preset = PresetCatalog.defaults().get(name);
        WeatherSchedule schedule = new WeatherSchedule(70289, preset.profile, preset.weather);
        Map<WeatherKind, int[]> collected = new EnumMap<>(WeatherKind.class);
        long previous = Long.MIN_VALUE;
        for (long tick = 0, end = 20L * preset.profile.daysInYear() * DAY; tick < end; tick += 200) {
            WeatherEpisode episode = schedule.at(tick);
            if (episode == null || episode.startTick == previous) continue;
            previous = episode.startTick;
            Season season = preset.profile.at(episode.startTick).calendar.season;
            collected.computeIfAbsent(episode.kind, ignored -> new int[4])[season.ordinal()]++;
            int onset = (int) (episode.startTick % DAY);
            if (episode.kind == WeatherKind.CONVECTIVE_SHOWER) assertTrue(onset >= 5400 && onset <= 11400);
            if (episode.kind == WeatherKind.RADIATION_FOG) assertTrue(onset >= 21100 && onset <= 23700);
            if (episode.kind == WeatherKind.MONSOON_BURST) {
                assertTrue(episode.endTick - episode.startTick >= 18000);
            }
        }
        return collected;
    }
}
