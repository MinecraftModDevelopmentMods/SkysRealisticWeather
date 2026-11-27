package zone.moddev.mc.skysrealisticweather.api;

import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysrealisticweather.game.BiomeClimates;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;
import zone.moddev.mc.skysrealisticweather.game.TemperatureZones;

/** Query on the world's logical thread. */
public final class WeatherApi {
    public static final int VERSION = 8;
    public static final float SNOW_LINE = 0.15F;

    public enum PopulationPolicy { SEASONAL, NATIVE }

    private WeatherApi() {
    }

    /** Retrieve the calendar for a supported, initialized Overworld. */
    public static Optional<SeasonSnapshot> getSeason(World world) {
        WeatherState.Frame frame = WeatherState.frame(world);
        return frame == null ? Optional.empty() : Optional.of(frame.climate.calendar);
    }

    /** Retrieve weather at an already loaded position. Shelter is included. */
    public static Optional<LocalWeather> getWeather(World world, BlockPos position) {
        WeatherState.Frame frame = WeatherState.frame(world);
        if (frame == null || !world.isBlockLoaded(position, false)) return Optional.empty();
        return Optional.of(frame.local(world, position));
    }

    /** Current temperature in Minecraft biome units at an already loaded position. */
    public static OptionalDouble getTemperature(World world, BlockPos position) {
        WeatherState.Frame frame = WeatherState.frame(world);
        if (frame == null || !world.isBlockLoaded(position, false)) return OptionalDouble.empty();
        return OptionalDouble.of(frame.temperature(world.getBiome(position), position));
    }

    /** Effective local temperature, including registered zones and controlled climates. Never loads terrain. */
    public static OptionalDouble getLocalTemperature(World world, BlockPos position) {
        Optional<TemperatureSample> sample=getTemperatureSample(world,position);
        return sample.isPresent()?OptionalDouble.of(sample.get().temperature):OptionalDouble.empty();
    }
    public static Optional<TemperatureSample> getTemperatureSample(World world, BlockPos position) {
        return zone.moddev.mc.skysrealisticweather.game.TemperatureZones.sample(world,position);
    }
    /** Server world's logical thread only. Reusing an ID atomically replaces that world's zone. Not persisted. */
    public static boolean putTemperatureZone(World world, TemperatureZone zone) {
        return TemperatureZones.put(world,zone);
    }
    /** Server world's logical thread only. Returns false if the ID was not registered. */
    public static boolean removeTemperatureZone(World world, ResourceLocation id) {
        return zone.moddev.mc.skysrealisticweather.game.TemperatureZones.remove(world,id);
    }

    /** Current temperature for a caller-supplied biome. This does not look up or load chunks. */
    public static OptionalDouble getTemperature(World world, BlockPos position, Biome biome) {
        WeatherState.Frame frame = WeatherState.frame(world);
        return frame == null ? OptionalDouble.empty() : OptionalDouble.of(frame.temperature(biome, position));
    }

    /** Climate and open-sky conditions at an already loaded position. */
    public static Optional<ClimateSample> getClimate(World world, BlockPos position) {
        WeatherState.Frame frame = WeatherState.frame(world);
        if (frame == null || !world.isBlockLoaded(position, false)) return Optional.empty();
        return Optional.of(sample(frame, position, world.getBiome(position)));
    }

    /**
     * Climate and open-sky conditions for a caller-supplied biome and height.
     * Suitable for generation before a chunk exists. This does not look up terrain, load chunks or test shelter.
     */
    public static Optional<ClimateSample> getClimate(World world, BlockPos position, Biome biome) {
        WeatherState.Frame frame = WeatherState.frame(world);
        return frame == null ? Optional.empty() : Optional.of(sample(frame, position, biome));
    }

    private static ClimateSample sample(WeatherState.Frame frame, BlockPos position, Biome biome) {
        float vanilla = biome.getFloatTemperature(position);
        boolean active = frame.active();
        zone.moddev.mc.skysrealisticweather.climate.ClimateProfile.Reading climate = frame.climate(biome);
        return new ClimateSample(position, biome.getRegistryName(), frame.settings.mode, frame.climateId(biome),
                frame.regions == null ? frame.settings.phaseLabel(climate.calendar.season.ordinal())
                        : frame.settings.biomes.preset(biome).phaseLabel(climate.calendar.season),
                climate.calendar, getBiomeClimate(biome),
                biome.getTemperature(), vanilla, frame.seasonalTemperature(biome, vanilla), biome.getRainfall(),
                active ? BiomeClimates.allowsPrecipitation(biome) : biome.canRain() || biome.getEnableSnow(),
                biome.getEnableSnow(), active ? climate.wetness : 1,
                active ? climate.thunder : 1, active ? climate.fog : 0, frame.openSky(position, biome));
    }

    /** The resolved modifier for a biome. Read after mod loading completes. */
    public static BiomeClimate getBiomeClimate(Biome biome) {
        return BiomeClimates.get(biome);
    }

    /**
     * The server's snow and ice policy for this biome. Applies to generators that use Sky's surface hooks.
     * Empty on clients or before the server's Overworld is initialized.
     */
    public static Optional<PopulationPolicy> getPopulationPolicy(World world, Biome biome) {
        WeatherState.Frame frame = WeatherState.frame(world);
        if (frame == null || world.isRemote) return Optional.empty();
        return Optional.of(frame.active() && frame.settings.seasonalWorldgen()
                && !frame.settings.keepsNativeSurface(biome.getRegistryName())
                ? PopulationPolicy.SEASONAL : PopulationPolicy.NATIVE);
    }

    /** The current scheduled or operator-forced Overworld episode. Use its clock for timing and coverage. */
    public static Optional<WeatherEpisode> getWeatherEpisode(World world) {
        WeatherState.Frame frame = WeatherState.frame(world);
        return frame == null ? Optional.empty() : Optional.ofNullable(frame.currentEpisode());
    }

    /** Local episode for an already loaded position. */
    public static Optional<WeatherEpisode> getWeatherEpisode(World world, BlockPos position) {
        return world == null || !world.isBlockLoaded(position, false) ? Optional.empty()
                : getWeatherEpisode(world, world.getBiome(position));
    }

    /** Local episode for a supplied biome, including during generation without loading chunks. */
    public static Optional<WeatherEpisode> getWeatherEpisode(World world, Biome biome) {
        WeatherState.Frame frame = WeatherState.frame(world);
        return frame == null ? Optional.empty() : Optional.ofNullable(frame.currentEpisode(biome));
    }

    /** Background cold snap or heatwave, which can coexist with the local weather episode. No chunks are loaded. */
    public static Optional<WeatherEpisode> getTemperatureEpisode(World world, Biome biome) {
        WeatherState.Frame frame = WeatherState.frame(world);
        return frame == null ? Optional.empty() : Optional.ofNullable(frame.temperatureEpisode(biome));
    }

    /** Background cold snap or heatwave at an already loaded position. */
    public static Optional<WeatherEpisode> getTemperatureEpisode(World world, BlockPos position) {
        return world == null || !world.isBlockLoaded(position, false) ? Optional.empty()
                : getTemperatureEpisode(world, world.getBiome(position));
    }

    /** Register during initialization or post-initialization, on both distributions. */
    public static void registerBiomeClimate(ResourceLocation biome, BiomeClimate climate) {
        BiomeClimates.register(biome, climate);
    }
    /** Register before mod loading completes. Config mappings override these registrations. */
    public static void registerPlantGrowthProfile(String plantSelector, PlantGrowthProfile profile) {
        zone.moddev.mc.skysrealisticweather.game.PlantProfiles.register(plantSelector, profile);
    }

    /** Natural-growth suitability at a loaded plant, including supported controlled climates. Never loads terrain. */
    public static Optional<PlantGrowthSample> getPlantGrowth(World world, BlockPos position) {
        return Optional.ofNullable(zone.moddev.mc.skysrealisticweather.game.PlantGrowthHooks.sample(world, position));
    }

}
