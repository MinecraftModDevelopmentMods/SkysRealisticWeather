package zone.moddev.mc.skysrealisticweather.climate;

import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;

public final class WeatherSettings {
    public final ClimateProfile profile;
    public final boolean passive;
    public final boolean surface;
    public final boolean foliage;
    public final boolean fog;
    public final WeatherPattern weather;
    public final boolean scheduled;
    public final ClimateMode mode;
    public final BiomeClimateMap biomes;
    public final FarmingSettings farming;
    private final GenerationSettings generation;

    public WeatherSettings(ClimateProfile profile, boolean passive, boolean surface, boolean foliage, boolean fog) {
        this(profile, passive, surface, foliage, fog, null);
    }

    public WeatherSettings(ClimateProfile profile, boolean passive, boolean surface, boolean foliage, boolean fog,
                           WeatherPattern weather) {
        this(profile, passive, surface, foliage, fog, weather, weather != null);
    }

    public WeatherSettings(ClimateProfile profile, boolean passive, boolean surface, boolean foliage, boolean fog,
                           WeatherPattern weather, boolean scheduled) {
        this(profile, passive, surface, foliage, fog, weather, scheduled, GenerationSettings.DEFAULT);
    }

    WeatherSettings(ClimateProfile profile, boolean passive, boolean surface, boolean foliage, boolean fog,
                    WeatherPattern weather, boolean scheduled, GenerationSettings generation) {
        this(profile, passive, surface, foliage, fog, weather, scheduled, generation, ClimateMode.UNIVERSAL,
                BiomeClimateMap.defaults(), FarmingSettings.DEFAULT);
    }

    private WeatherSettings(ClimateProfile profile, boolean passive, boolean surface, boolean foliage, boolean fog,
                    WeatherPattern weather, boolean scheduled, GenerationSettings generation, ClimateMode mode,
                    BiomeClimateMap biomes, FarmingSettings farming) {
        this.profile = profile;
        this.passive = passive;
        this.surface = surface;
        this.foliage = foliage;
        this.fog = fog;
        this.weather = weather;
        this.scheduled = scheduled;
        this.generation = generation;
        this.mode = mode;
        this.biomes = biomes;
        this.farming = farming;
    }

    public boolean seasonalWorldgen() {
        return surface && generation.enabled;
    }

    public boolean keepsNativeSurface(ResourceLocation biome) {
        return generation.keepsNativeSurface(biome);
    }

    public String[] nativeSnowBiomes() {
        return generation.biomeIds();
    }

    public WeatherSettings withNativeSnowBiomes(String[] ids) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled,
                new GenerationSettings(generation.enabled, ids), mode, biomes, farming);
    }

    public WeatherSettings withClimate(ClimateProfile profile, WeatherPattern weather) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming);
    }

    public WeatherSettings withOptions(boolean passive, boolean surface, boolean foliage, boolean fog, boolean scheduled) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming);
    }

    public WeatherSettings withMode(ClimateMode mode, BiomeClimateMap biomes) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming);
    }
    public WeatherSettings withFarming(FarmingSettings farming) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming);
    }

}
