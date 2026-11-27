package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysrealisticweather.api.BiomeClimate;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather.Precipitation;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather.Source;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.WeatherSettings;
import zone.moddev.mc.skysrealisticweather.core.HookStatus;
import zone.moddev.mc.skysrealisticweather.live.Observation;

public final class WeatherState {
    public static final float SNOW_LINE = WeatherApi.SNOW_LINE;
    private static volatile Frame server;
    private static volatile Frame client;

    private WeatherState() {
    }

    public static boolean supports(World world) {
        return world != null && world.provider.getDimension() == 0 && world.provider.isSurfaceWorld()
                && !world.provider.hasNoSky() && world.getWorldType() != WorldType.DEBUG_WORLD;
    }

    public static Frame frame(World world) {
        if (world == null) return null;
        Frame frame = world.isRemote ? client : server;
        if (frame == null || frame.world != world) return null;
        // Server weather timers run before the daylight clock advances, terrain ticks run after.
        if (frame.time == world.getWorldTime()) return frame;
        Frame updated = new Frame(world, frame.settings, frame.live, frame.episode, frame.scheduled, frame.regions, frame.seed, frame.schedule);
        if (world.isRemote) client = updated;
        else server = updated;
        return updated;
    }

    public static Frame clientFrame() {
        return client;
    }

    public static void server(Frame value) {
        server = value;
    }

    public static void client(Frame value) {
        client = value;
    }

    public static final class Frame {
        public final World world;
        public final WeatherSettings settings;
        public final ClimateProfile.Reading climate;
        public final Observation live;
        public final WeatherEpisode episode;
        public final boolean scheduled;
        public final BiomeWeather regions;
        public final long seed;
        final long time;
        private final WeatherSchedule schedule;
        private final float daytimeTemperature;

        public Frame(World world, WeatherSettings settings, Observation live) {
            this(world, settings, live, null, false);
        }

        public Frame(World world, WeatherSettings settings, Observation live, WeatherEpisode episode, boolean scheduled) {
            this(world, settings, live, episode, scheduled, null, world.getSeed());
        }

        public Frame(World world, WeatherSettings settings, Observation live, WeatherEpisode episode, boolean scheduled,
                     BiomeWeather regions, long seed) {
            this(world, settings, live, episode, scheduled, regions, seed,
                    settings.mode == ClimateMode.PER_BIOME || settings.weather == null ? null
                            : new WeatherSchedule(seed, settings.profile, settings.weather, settings.calendarOffset));
        }

        Frame(World world, WeatherSettings settings, Observation live, WeatherEpisode episode, boolean scheduled,
                     BiomeWeather regions, long seed, WeatherSchedule schedule) {
            this.world = world;
            this.settings = settings;
            this.time = world.getWorldTime();
            this.climate = settings.profile.at(ClimateClock.shift(time, settings.calendarOffset));
            this.live = live;
            this.episode = episode;
            this.scheduled = scheduled;
            this.seed = seed;
            this.regions = settings.mode == ClimateMode.PER_BIOME
                    ? regions == null ? new BiomeWeather(settings, seed) : regions : null;
            this.schedule = schedule;
            this.daytimeTemperature = this.regions == null
                    ? ClimateProfile.daylightTemperature(time) * ClimateProfile.temperatureRange(climate.wetness) : 0;
        }

        public ClimateProfile.Reading climate(Biome biome) {
            return regions == null ? climate : regions.region(biome, time).reading;
        }

        public String climateId(Biome biome) {
            return regions == null ? settings.mode.name().toLowerCase(java.util.Locale.ROOT)
                    : regions.region(biome, time).preset.id;
        }

        public WeatherEpisode currentEpisode(Biome biome) {
            WeatherEpisode current = currentEpisode();
            return current != null && regions != null && scheduled && !current.forced && live == null
                    ? regions.region(biome, time).episode : current;
        }

        public boolean active() {
            return !settings.passive && HookStatus.available();
        }

        public float temperature(Biome biome, BlockPos position, float original) {
            if (!active()) return original;
            BiomeClimate adjustment = BiomeClimates.get(biome);
            if (live != null) {
                // Vanilla plains at sea level are the reference; keep biome and elevation departures.
                return live.referenceTemperature() + (original - 0.8F) + adjustment.temperatureOffset;
            }
            WeatherEpisode current = currentEpisode(biome);
            float anomaly = current == null ? 0 : current.kind.temperatureOffset * current.intensity(episodeTime(current));
            WeatherEpisode temperature = temperatureEpisode(biome);
            if (temperature != null && (current == null || !current.kind.temperatureSpell())) {
                float background = temperature.kind.temperatureOffset * temperature.intensity(episodeTime(temperature));
                // A cold front and its cold air mass describe the same cooling, not two independent extremes.
                anomaly = anomaly * background > 0
                        ? (anomaly < 0 ? Math.min(anomaly, background) : Math.max(anomaly, background)) : anomaly + background;
            }
            anomaly += (regions == null ? daytimeTemperature : regions.region(biome, time).daytimeTemperature)
                    * (current != null && current.kind.wet ? 0.6F : 1);
            return adjustedTemperature(biome, original, anomaly, adjustment);
        }

        public WeatherEpisode temperatureEpisode(Biome biome) {
            if (!active() || live != null) return null;
            WeatherEpisode current = currentEpisode();
            if (current != null && current.forced) return current.kind.temperatureSpell() ? current : null;
            if (!scheduled) return null;
            return regions != null ? regions.region(biome, time).temperatureEpisode
                    : schedule == null ? null : schedule.thermalAt(time);
        }

        public float seasonalTemperature(Biome biome, float original) {
            return active() ? adjustedTemperature(biome, original, 0, BiomeClimates.get(biome)) : original;
        }

        private float adjustedTemperature(Biome biome, float original, float anomaly, BiomeClimate adjustment) {
            return adjustedTemperature(biome,original,anomaly,adjustment,climate(biome).temperature);
        }
        private float adjustedTemperature(Biome biome, float original, float anomaly, BiomeClimate adjustment,float seasonal) {
            float strength = settings.profile.strength;
            if (regions != null) {
                strength = Math.min(2, strength * regions.region(biome, time).preset.profile.strength);
                if (!settings.keepsNativeSurface(biome.getRegistryName())) {
                    // Mapped climates use the Plains reference; retain the supplied elevation departure.
                    original += (0.8F - biome.getTemperature()) * Math.min(1, strength * adjustment.seasonalScale);
                }
            }
            return original + adjustment.temperatureOffset + (seasonal + anomaly * strength)
                    * adjustment.seasonalScale;
        }

        /** Reference phase midpoint, excluding weather episodes, diurnal changes and local heating. */
        public float phaseTemperature(Biome biome,float original,int phase) {
            if(!active())return original;
            ClimateProfile profile=regions==null?settings.profile:regions.profile(biome);
            long days=0;for(int i=0;i<phase;i++)days+=profile.period(i).days;
            long midpoint=days*24000+profile.period(phase).days*12000L;
            return adjustedTemperature(biome,original,0,BiomeClimates.get(biome),profile.at(midpoint).temperature);
        }

        public float temperature(Biome biome, BlockPos position) {
            return temperature(biome, position, biome.getFloatTemperature(position));
        }

        public Precipitation precipitation(BlockPos position, Biome biome) {
            if (!active()) {
                if (biome.getEnableSnow() || biome.canRain() && biome.getFloatTemperature(position) < SNOW_LINE) {
                    return Precipitation.SNOW;
                }
                return biome.canRain() ? Precipitation.RAIN : Precipitation.NONE;
            }
            if (!mayPrecipitate(biome, position)) return Precipitation.NONE;
            float temperature = temperature(biome, position);
            return falling(temperature, currentEpisode(biome));
        }

        boolean snowfallAt(BlockPos position, Biome biome, float temperature) {
            if (!(temperature < SNOW_LINE)) return false;
            WeatherEpisode current = currentEpisode(biome);
            return falling(temperature, current) == Precipitation.SNOW && receivesPrecipitation(position, biome, current);
        }

        private static Precipitation falling(float temperature, WeatherEpisode current) {
            if (current != null) {
                if (current.kind == WeatherKind.HAILSTORM && temperature >= SNOW_LINE) return Precipitation.HAIL;
                if (current.kind == WeatherKind.FREEZING_RAIN
                        && temperature >= SNOW_LINE - 0.13F && temperature < SNOW_LINE) return Precipitation.FREEZING_RAIN;
            }
            return temperature < SNOW_LINE ? Precipitation.SNOW : Precipitation.RAIN;
        }

        public WeatherEpisode currentEpisode() {
            return active() && episode != null && episode.contains(episodeTime()) ? episode : null;
        }

        private long episodeTime() {
            return episode.time(time, world.getTotalWorldTime());
        }

        private long episodeTime(WeatherEpisode event) {
            return event.time(time, world.getTotalWorldTime());
        }

        public boolean mayPrecipitate(Biome biome, BlockPos position) {
            WeatherEpisode current = currentEpisode(biome);
            return receivesPrecipitation(position, biome, current)
                    && (!requiresSnow(current) || temperature(biome, position) < SNOW_LINE);
        }

        private boolean receivesPrecipitation(BlockPos position, Biome biome, WeatherEpisode current) {
            return BiomeClimates.allowsPrecipitation(biome, current != null && current.includesDryBiomes)
                    && (!regionalSchedule() || current != null && current.kind.wet)
                    && (current == null || current.covers(position.getX(), position.getZ(), episodeTime(current)));
        }

        boolean wetBiome(Biome biome) {
            WeatherEpisode current = currentEpisode(biome);
            return BiomeClimates.allowsPrecipitation(biome, current != null && current.includesDryBiomes)
                    && (!regionalSchedule() || current != null && current.kind.wet);
        }

        public boolean regionalSchedule() {
            return regions != null && scheduled && live == null && (episode == null || !episode.forced);
        }

        public boolean requiresSnow() {
            return requiresSnow(currentEpisode());
        }

        private static boolean requiresSnow(WeatherEpisode current) {
            return current != null && (current.kind == WeatherKind.SNOW_SQUALL || current.kind == WeatherKind.BLIZZARD);
        }

        public float visualRainStrength(float vanilla) {
            if (!scheduled || !active()) return vanilla;
            return currentEpisode() != null && episode.kind.wet ? vanilla * episode.intensity(episodeTime()) : 0;
        }

        public float visualRainStrength(float vanilla, BlockPos position, Biome biome) {
            if (!regionalSchedule()) return visualRainStrength(vanilla);
            WeatherEpisode current = currentEpisode(biome);
            return current != null && current.kind.wet
                    ? vanilla * current.intensity(episodeTime(current)) : 0;
        }

        /** Precipitation opacity belongs to the rendered column, independent of the camera's climate. */
        public float columnRainStrength(float vanilla, BlockPos position, Biome biome) {
            if (!active()) return vanilla;
            return mayPrecipitate(biome, position) ? visualRainStrength(vanilla, position, biome) : 0;
        }

        public float wind(BlockPos position, Biome biome) {
            WeatherEpisode current = currentEpisode(biome);
            return current != null && affects(position, biome, current)
                    ? current.kind.wind * current.intensity(episodeTime(current)) : 0;
        }

        private boolean affects(BlockPos position, Biome biome, WeatherEpisode current) {
            switch (current.kind) {
                case DUST_STORM: return biome.getRainfall() <= 0.55F;
                case BLOWING_SNOW: return temperature(biome, position) < SNOW_LINE;
                case RADIATION_FOG:
                case ADVECTION_FOG: return BiomeClimates.allowsPrecipitation(biome);
                default: return !current.kind.wet || precipitation(position, biome) != Precipitation.NONE;
            }
        }

        public LocalWeather local(World world, BlockPos position) {
            Biome biome = world.getBiome(position);
            boolean exposed = world.canSeeSky(position)
                    && world.getPrecipitationHeight(position).getY() <= position.getY();
            return weather(position, biome, exposed);
        }

        public LocalWeather openSky(BlockPos position, Biome biome) {
            return weather(position, biome, true);
        }

        private LocalWeather weather(BlockPos position, Biome biome, boolean exposed) {
            Precipitation falling = exposed && world.isRaining()
                    ? precipitation(position, biome) : Precipitation.NONE;
            float intensity = falling == Precipitation.NONE ? 0 : visualRainStrength(world.getRainStrength(1), position, biome);
            if (regionalSchedule() && falling != Precipitation.NONE) {
                WeatherEpisode local = currentEpisode(biome);
                intensity = local == null ? 0 : local.intensity(episodeTime(local));
            }
            WeatherEpisode current = currentEpisode(biome);
            return new LocalWeather(falling, !active() ? Source.VANILLA : live == null ? Source.SEASONAL : Source.LIVE,
                    temperature(biome, position), exposed ? fog(position, biome) : 0,
                    (falling == Precipitation.RAIN || falling == Precipitation.HAIL) && world.isThundering()
                            && (!regionalSchedule() || current != null && current.kind.thunder),
                    intensity, exposed ? wind(position, biome) : 0);
        }

        public float fog(BlockPos position) {
            return fog(position, world.getBiome(position));
        }

        private float fog(BlockPos position, Biome biome) {
            if (!active() || !settings.fog) return 0;
            if (live != null) return live.condition == Observation.Condition.FOG ? 0.8F : 0;
            float elevation = Math.max(0.25F, 1 - Math.max(0, position.getY() - 64) / 120F);
            if (scheduled) {
                WeatherEpisode current = currentEpisode(biome);
                if (current == null || !affects(position, biome, current)) return 0;
                float fog = current.kind.fog * current.intensity(episodeTime(current));
                if (current.kind == WeatherKind.RADIATION_FOG || current.kind == WeatherKind.ADVECTION_FOG) {
                    return fog * elevation * Math.min(1, climate(biome).fog * 2);
                }
                return fog;
            }
            if (!BiomeClimates.allowsPrecipitation(biome)) return 0;
            long dayTime = Math.floorMod(time, ClimateProfile.TICKS_PER_DAY);
            long dawnDistance = Math.min(dayTime, ClimateProfile.TICKS_PER_DAY - dayTime);
            float dawn = Math.max(0, 1 - dawnDistance / 2700F);
            return climate(biome).fog * elevation * Math.max(dawn * 0.72F, world.getRainStrength(1) * 0.38F);
        }

        public boolean rainAt(BlockPos position) {
            if (!world.isRaining() || !world.canSeeSky(position)
                    || world.getPrecipitationHeight(position).getY() > position.getY()) return false;
            Precipitation falling = precipitation(position, world.getBiome(position));
            return falling != Precipitation.SNOW && falling != Precipitation.NONE;
        }
    }
}
