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
    public final long calendarOffset;
    private final String[] phaseLabels;
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
                BiomeClimateMap.defaults(), FarmingSettings.DEFAULT, 0, java.util.Arrays.stream(zone.moddev.mc.skysrealisticweather.api.Season.values()).map(Preset::defaultPhaseLabel).toArray(String[]::new));
    }

    private WeatherSettings(ClimateProfile profile, boolean passive, boolean surface, boolean foliage, boolean fog,
                    WeatherPattern weather, boolean scheduled, GenerationSettings generation, ClimateMode mode,
                    BiomeClimateMap biomes, FarmingSettings farming, long calendarOffset, String[] labels) {
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
        this.calendarOffset = zone.moddev.mc.skysrealisticweather.game.ClimateClock.checked(calendarOffset);
        if(labels.length!=4)throw new IllegalArgumentException("Four phase labels required");
        for(String label:labels)if(label==null||label.isEmpty()||label.length()>80)throw new IllegalArgumentException("Invalid phase label");
        this.phaseLabels=labels.clone();
    }
    public String[] phaseLabels() { return phaseLabels.clone(); }
    public String phaseLabel(int phase) { return phaseLabels[phase]; }
    public WeatherSettings withPhaseLabels(String[] labels) {
        return new WeatherSettings(profile,passive,surface,foliage,fog,weather,scheduled,generation,mode,biomes,farming,calendarOffset,labels);
    }

    public WeatherSettings withCalendarOffset(long value) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming, value, phaseLabels);
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
                new GenerationSettings(generation.enabled, ids), mode, biomes, farming, calendarOffset, phaseLabels);
    }

    public WeatherSettings withClimate(ClimateProfile profile, WeatherPattern weather) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming, calendarOffset, phaseLabels);
    }

    public WeatherSettings withOptions(boolean passive, boolean surface, boolean foliage, boolean fog, boolean scheduled) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming, calendarOffset, phaseLabels);
    }

    public WeatherSettings withMode(ClimateMode mode, BiomeClimateMap biomes) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming, calendarOffset, phaseLabels);
    }
    public WeatherSettings withFarming(FarmingSettings farming) {
        return new WeatherSettings(profile, passive, surface, foliage, fog, weather, scheduled, generation, mode, biomes, farming, calendarOffset, phaseLabels);
    }

}
