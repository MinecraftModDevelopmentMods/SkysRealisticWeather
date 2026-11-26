package zone.moddev.mc.skysrealisticweather.game;

import java.util.Random;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.WeatherPattern;

/** Reproducible from seed and daylight time, including after a restart or a time skip. */
final class WeatherSchedule {
    private static final int DAY = ClimateProfile.TICKS_PER_DAY;
    private static final int LOOKBACK = WeatherEpisode.MAX_DURATION / DAY + 1;
    private final long seed;
    private final ClimateProfile profile;
    private final WeatherPattern pattern;
    private final WeatherEpisode[] candidates = new WeatherEpisode[LOOKBACK];
    private final WeatherEpisode[] temperatures = new WeatherEpisode[LOOKBACK];
    private long cachedDay = Long.MIN_VALUE;
    private long thermalTime = Long.MIN_VALUE;
    private WeatherEpisode thermal;

    WeatherSchedule(long seed, ClimateProfile profile, WeatherPattern pattern) {
        this.seed = seed;
        this.profile = profile;
        this.pattern = pattern;
    }

    WeatherEpisode at(long time) {
        update(time);
        WeatherEpisode weather = active(candidates, time);
        return weather == null ? thermalAt(time) : weather;
    }

    WeatherEpisode thermalAt(long time) {
        update(time);
        if (thermalTime != time) {
            thermalTime = time;
            thermal = active(temperatures, time);
        }
        return thermal;
    }

    private void update(long time) {
        long day = Math.floorDiv(time, DAY);
        if (day != cachedDay) {
            if (day == cachedDay + 1) {
                System.arraycopy(candidates, 0, candidates, 1, candidates.length - 1);
                System.arraycopy(temperatures, 0, temperatures, 1, temperatures.length - 1);
                candidates[0] = sample(day, false);
                temperatures[0] = sample(day, true);
            } else {
                for (int age = 0; age < candidates.length; age++) {
                    candidates[age] = sample(day - age, false);
                    temperatures[age] = sample(day - age, true);
                }
            }
            cachedDay = day;
        }
    }

    private static WeatherEpisode active(WeatherEpisode[] choices, long time) {
        for (WeatherEpisode candidate : choices) {
            if (candidate == null || candidate.startTick > time) continue;
            return candidate.contains(time) ? candidate : null;
        }
        return null;
    }

    private WeatherEpisode sample(long day, boolean temperature) {
        if (day < 0 || day > (Long.MAX_VALUE - DAY - WeatherEpisode.MAX_DURATION) / DAY) return null;
        long date = day * DAY;
        Random random = new Random(seed ^ (day * 0x9E3779B97F4A7C15L) ^ (temperature ? 0xD1B54A32D192ED03L : 0));
        double total = 0;
        WeatherPattern.Rule selected = null;
        long onset = 0;
        for (WeatherPattern.Rule rule : pattern.rules) {
            if (rule.kind.temperatureSpell() != temperature) continue;
            long start = date + between(random, rule.firstStart, rule.lastStart);
            double weight = rule.weight(profile.at(start), profile);
            if (weight <= 0) continue;
            total += weight;
            if (random.nextDouble() * total < weight) {
                selected = rule;
                onset = start;
            }
        }
        if (selected == null || random.nextDouble() * 100 >= Math.min(100, total)) return null;
        int duration = between(random, selected.minDuration, selected.maxDuration);
        float peak = ClimateProfile.mix(selected.minPeak, selected.maxPeak, random.nextFloat());
        return new WeatherEpisode(selected.kind, onset, duration, peak, selected.includesDryBiomes);
    }

    private static int between(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
