package zone.moddev.mc.skysrealisticweather.game;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.WeatherSettings;

/** Reuses local schedules across ticks and samples each climate reading once per daylight tick. */
public final class BiomeWeather {
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final WeatherSettings settings;
    private long solarTime = Long.MIN_VALUE;
    private float solar;

    public BiomeWeather(WeatherSettings settings, long seed) {
        this.settings = settings;
        settings.biomes.presets.values().forEach(preset -> regions.put(preset.id, new Region(preset, seed, settings.profile)));
    }

    public Region region(Biome biome, long time) {
        Region region = regions.get(settings.biomes.preset(biome).id);
        if (region == null) region = regions.values().iterator().next();
        region.update(time, solar(time));
        return region;
    }

    /** A vanilla weather carrier. Local queries use the episode for their own biome. */
    WeatherEpisode carrier(long time) {
        WeatherEpisode selected = null;
        for (Region region : regions.values()) {
            if (!settings.biomes.scheduledPresets.contains(region.preset.id)) continue;
            region.update(time, solar(time));
            WeatherEpisode episode = region.episode;
            if (episode != null && (selected == null || priority(episode) > priority(selected))) selected = episode;
        }
        return selected;
    }

    private float solar(long time) {
        if (solarTime != time) {
            solarTime = time;
            solar = ClimateProfile.daylightTemperature(time);
        }
        return solar;
    }

    private static int priority(WeatherEpisode episode) {
        return episode.kind.thunder ? 2 : episode.kind.wet ? 1 : 0;
    }

    public static final class Region {
        public final Preset preset;
        private final ClimateProfile profile;
        private final WeatherSchedule schedule;
        public ClimateProfile.Reading reading;
        public WeatherEpisode episode;
        public WeatherEpisode temperatureEpisode;
        public float daytimeTemperature;
        private long sampledTime = Long.MIN_VALUE;

        Region(Preset preset, long seed, ClimateProfile calendar) {
            this.preset = preset;
            ClimateProfile.Period[] periods = new ClimateProfile.Period[4];
            int remaining = calendar.daysInYear();
            for (int i = 0; i < periods.length; i++) {
                ClimateProfile.Period p = preset.profile.period(i);
                int desired = Math.round(p.days * calendar.daysInYear() / (float) preset.profile.daysInYear());
                int minimum = Math.max(1, remaining - (3 - i) * 365);
                int maximum = Math.min(365, remaining - (3 - i));
                int days = i == 3 ? remaining : Math.max(minimum, Math.min(maximum, desired));
                remaining -= days;
                periods[i] = new ClimateProfile.Period(days, p.temperature, p.wetness, p.thunder, p.fog);
            }
            profile = new ClimateProfile(Math.min(2, preset.profile.strength * calendar.strength), periods);
            schedule = new WeatherSchedule(seed, profile, preset.weather);
        }

        void update(long time, float solar) {
            if (sampledTime == time) return;
            sampledTime = time;
            reading = profile.at(time);
            episode = schedule.at(time);
            temperatureEpisode = schedule.thermalAt(time);
            daytimeTemperature = solar * ClimateProfile.temperatureRange(reading.wetness);
        }
    }
}
