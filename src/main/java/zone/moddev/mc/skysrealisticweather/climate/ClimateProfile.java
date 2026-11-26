package zone.moddev.mc.skysrealisticweather.climate;

import java.util.Objects;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.api.SeasonSnapshot;

public final class ClimateProfile {
    public static final int TICKS_PER_DAY = 24000;
    public static final int SEASON_COUNT = 4;
    private static final Season[] SEASONS = Season.values();

    private final Period[] periods;
    private final long yearTicks;
    public final float strength;

    public ClimateProfile(float strength, Period... periods) {
        if (periods.length != SEASON_COUNT) throw new IllegalArgumentException("Expected four seasons");
        this.strength = bounded(strength, 0, 2);
        this.periods = periods.clone();
        long days = 0;
        for (Period period : this.periods) days += Objects.requireNonNull(period).days;
        yearTicks = days * TICKS_PER_DAY;
    }

    public Period period(int index) {
        return periods[index];
    }

    public int daysInYear() {
        return (int) (yearTicks / TICKS_PER_DAY);
    }

    ClimateProfile withSeasonLength(int days) {
        Period[] resized = new Period[SEASON_COUNT];
        for (int index = 0; index < SEASON_COUNT; index++) {
            Period period = periods[index];
            resized[index] = new Period(days, period.temperature, period.wetness, period.thunder, period.fog);
        }
        return new ClimateProfile(strength, resized);
    }

    public Reading at(long worldTime) {
        long ticks = Math.floorMod(worldTime, yearTicks);
        int index = 0;
        while (ticks >= (long) periods[index].days * TICKS_PER_DAY) {
            ticks -= (long) periods[index].days * TICKS_PER_DAY;
            index++;
        }
        Period current = periods[index];
        double progress = ticks / (current.days * (double) TICKS_PER_DAY);
        int adjacent = (index + (progress < 0.5 ? 3 : 1)) % SEASON_COUNT;
        double blend = Math.abs(progress - 0.5);
        Period other = periods[adjacent];
        SeasonSnapshot calendar = new SeasonSnapshot(SEASONS[index],
                (int) (ticks / TICKS_PER_DAY) + 1, current.days, progress);
        return new Reading(calendar,
                mix(current.temperature, other.temperature, blend) * strength,
                effectMultiplier(mix(current.wetness, other.wetness, blend)),
                effectMultiplier(mix(current.thunder, other.thunder, blend)),
                Math.min(1, mix(current.fog, other.fog, blend) * strength));
    }

    private float effectMultiplier(float value) {
        return Math.max(0.1F, 1 + (value - 1) * strength);
    }

    public static float mix(float a, float b, double fraction) {
        return (float) (a + (b - a) * fraction);
    }

    /** Unit day/night cycle: warmest at 15:00, coldest just before dawn. */
    public static float daylightTemperature(long time) {
        long sinceDawn = Math.floorMod(Math.floorMod(time, TICKS_PER_DAY) - 23000, TICKS_PER_DAY);
        return sinceDawn < 10000 ? (float) -Math.cos(Math.PI * sinceDawn / 10000)
                : (float) Math.cos(Math.PI * (sinceDawn - 10000) / 14000);
    }

    public static float temperatureRange(float wetness) {
        return 0.21F - 0.06F * Math.min(2.5F, wetness);
    }

    private static float bounded(float value, float min, float max) {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Climate setting outside its range");
        }
        return value;
    }

    public static final class Period {
        public final int days;
        public final float temperature;
        public final float wetness;
        public final float thunder;
        public final float fog;

        public Period(int days, float temperature, float wetness, float thunder, float fog) {
            if (days < 1 || days > 365) throw new IllegalArgumentException("Invalid season length");
            this.days = days;
            this.temperature = bounded(temperature, -2, 2);
            this.wetness = bounded(wetness, 0.25F, 4);
            this.thunder = bounded(thunder, 0.25F, 4);
            this.fog = bounded(fog, 0, 1);
        }
    }

    public static final class Reading {
        public final SeasonSnapshot calendar;
        public final float temperature;
        public final float wetness;
        public final float thunder;
        public final float fog;

        private Reading(SeasonSnapshot calendar, float temperature, float wetness, float thunder, float fog) {
            this.calendar = calendar;
            this.temperature = temperature;
            this.wetness = wetness;
            this.thunder = thunder;
            this.fog = fog;
        }

        public int weatherDuration(int ticks, int transition) {
            switch (transition) {
                case 0: return Math.max(2, Math.round(ticks * thunder));
                case 1: return Math.max(2, Math.round(ticks / thunder));
                case 2: return Math.max(2, Math.round(ticks * wetness));
                case 3: return Math.max(2, Math.round(ticks / wetness));
                default: throw new IllegalArgumentException("Unknown weather transition");
            }
        }
    }
}
