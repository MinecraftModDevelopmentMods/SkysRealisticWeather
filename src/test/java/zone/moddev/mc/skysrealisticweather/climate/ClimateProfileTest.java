package zone.moddev.mc.skysrealisticweather.climate;

import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.Season;

import static org.junit.jupiter.api.Assertions.*;

final class ClimateProfileTest {
    @Test
    void standardPresetsFollowThe365DayCalendar() {
        for (String id : new String[] {"burlington", "oxford", "winter_wonderland"}) {
            ClimateProfile profile = profile(id);
            assertEquals(365, profile.daysInYear());
            assertEquals(Season.SPRING, profile.at(0).calendar.season);
            assertEquals(Season.SUMMER, profile.at(92L * 24000).calendar.season);
            assertEquals(Season.AUTUMN, profile.at(184L * 24000).calendar.season);
            assertEquals(Season.WINTER, profile.at(275L * 24000).calendar.season);
            assertEquals(90, profile.at(365L * 24000 - 1).calendar.dayOfSeason);
            assertEquals(Season.SPRING, profile.at(365L * 24000).calendar.season);
        }
    }

    @Test
    void shortSeasonsUsesTheSameClimateWithA96DayYear() {
        ClimateProfile profile = profile("short_seasons");
        assertEquals(96, profile.daysInYear());
        assertEquals(Season.SPRING, profile.at(96L * 24000).calendar.season);
        for (int index = 0; index < ClimateProfile.SEASON_COUNT; index++) {
            ClimateProfile.Period period = profile.period(index);
            ClimateProfile.Period standard = profile("burlington").period(index);
            assertEquals(24, period.days);
            assertEquals(standard.temperature, period.temperature);
            assertEquals(standard.wetness, period.wetness);
            assertEquals(standard.thunder, period.thunder);
            assertEquals(standard.fog, period.fog);
        }
    }

    @Test
    void boundariesFollowUnequalMinecraftDaysAndWrap() {
        ClimateProfile profile = new ClimateProfile(1, days(1), days(3), days(2), days(4));
        assertEquals(Season.SPRING, profile.at(0).calendar.season);
        assertEquals(Season.SPRING, profile.at(23999).calendar.season);
        assertEquals(Season.SUMMER, profile.at(24000).calendar.season);
        assertEquals(3, profile.at(3L * 24000).calendar.dayOfSeason);
        assertEquals(Season.AUTUMN, profile.at(4L * 24000).calendar.season);
        assertEquals(Season.WINTER, profile.at(6L * 24000).calendar.season);
        assertEquals(Season.SPRING, profile.at(10L * 24000).calendar.season);
        assertEquals(Season.WINTER, profile.at(-1).calendar.season);
    }

    @Test
    void climateIsContinuousAcrossEveryBoundary() {
        ClimateProfile profile = profile("burlington");
        for (long boundary : new long[] {0, 92, 184, 275, 365}) {
            ClimateProfile.Reading before = profile.at(boundary * 24000 - 1);
            ClimateProfile.Reading after = profile.at(boundary * 24000);
            assertEquals(before.temperature, after.temperature, 0.00001);
            assertEquals(before.wetness, after.wetness, 0.00001);
            assertEquals(before.thunder, after.thunder, 0.00001);
            assertEquals(before.fog, after.fog, 0.00001);
        }
    }

    @Test
    void presetWintersAndWetnessAreDistinct() {
        long winter = 320L * 24000;
        assertTrue(0.8F + profile("burlington").at(winter).temperature < 0.15F);
        assertTrue(0.8F + profile("oxford").at(winter).temperature > 0.15F);
        assertTrue(profile("oxford").at(winter).wetness > profile("burlington").at(winter).wetness);
        for (long day : new long[] {46, 138, 229, 320}) {
            long middle = day * 24000;
            assertTrue(0.8F + profile("winter_wonderland").at(middle).temperature < 0.15F);
        }
    }

    @Test
    void zeroStrengthRetainsNormalTimingAndTemperature() {
        ClimateProfile.Period period = new ClimateProfile.Period(24, -1, 2, 0.25F, 1);
        ClimateProfile.Reading reading = new ClimateProfile(0, period, period, period, period).at(0);
        assertEquals(0, reading.temperature, 0);
        assertEquals(0, reading.fog, 0);
        for (int transition = 0; transition < 4; transition++) {
            assertEquals(100, reading.weatherDuration(100, transition));
        }
    }

    @Test
    void reducedSeasonalityUsesDurationWeightedAnnualEventRates() {
        ClimateProfile profile = new ClimateProfile(0, days(10), days(10), days(10), days(100));
        WeatherPattern.Rule rule = new WeatherPattern(new String[] {"DRIZZLE,100,100,0,0"}).rules.get(0);
        assertEquals(2000.0 / 130, rule.weight(profile.at(0), profile), 0.00001);
        assertEquals(rule.weight(profile.at(0), profile), rule.weight(profile.at(90L * 24000), profile));
    }

    @Test
    void rejectsNonFiniteSettingsAndInvalidSeasonLengths() {
        assertThrows(IllegalArgumentException.class, () -> days(0));
        assertThrows(IllegalArgumentException.class, () -> days(366));
        assertThrows(IllegalArgumentException.class,
                () -> new ClimateProfile.Period(1, Float.NaN, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ClimateProfile.Period(1, 0, 1, Float.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClimateProfile(1, days(1)));
    }

    private static ClimateProfile.Period days(int days) {
        return new ClimateProfile.Period(days, 0, 1, 1, 0);
    }

    private static ClimateProfile profile(String id) {
        return PresetCatalog.defaults().get(id).profile;
    }
}
