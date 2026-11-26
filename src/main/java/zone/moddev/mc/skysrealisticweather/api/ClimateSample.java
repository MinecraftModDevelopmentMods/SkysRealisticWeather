package zone.moddev.mc.skysrealisticweather.api;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Immutable local climate reading, including current conditions at an exposed position. */
public final class ClimateSample {
    public final BlockPos position;
    /** Registry ID, or null for an unregistered biome supplied by a generator. */
    public final ResourceLocation biomeId;
    public final ClimateMode mode;
    /** Resolved preset ID in per-biome mode, or the selected global mode. */
    public final String climateId;
    public final String phaseLabel;
    public final SeasonSnapshot season;
    public final BiomeClimate biomeClimate;
    /** The biome's baseline temperature before elevation. */
    public final float biomeTemperature;
    /** The vanilla temperature at this position, including elevation. */
    public final float vanillaTemperature;
    /** The position's temperature with the season applied, before temporary weather or live data. */
    public final float seasonalTemperature;
    /** Vanilla's biome rainfall value. This is a biome parameter, not measured humidity. */
    public final float biomeRainfall;
    /** Precipitation eligibility without a dry-biome exception from the current event. */
    public final boolean normallyAllowsPrecipitation;
    public final boolean naturallySnowy;
    public final float wetnessMultiplier;
    public final float thunderMultiplier;
    /** Seasonal fog potential, from zero to one. See openSkyWeather.fog for the current value. */
    public final float fogPotential;
    /** Current temperature and weather assuming an unobstructed sky. */
    public final LocalWeather openSkyWeather;

    ClimateSample(BlockPos position, ResourceLocation biomeId, ClimateMode mode, String climateId, String phaseLabel,
                  SeasonSnapshot season, BiomeClimate biomeClimate,
                  float biomeTemperature, float vanillaTemperature, float seasonalTemperature, float biomeRainfall,
                  boolean normallyAllowsPrecipitation, boolean naturallySnowy, float wetnessMultiplier,
                  float thunderMultiplier, float fogPotential, LocalWeather openSkyWeather) {
        this.position = position.toImmutable();
        this.biomeId = biomeId;
        this.mode = mode;
        this.climateId = climateId;
        this.phaseLabel = phaseLabel;
        this.season = season;
        this.biomeClimate = biomeClimate;
        this.biomeTemperature = biomeTemperature;
        this.vanillaTemperature = vanillaTemperature;
        this.seasonalTemperature = seasonalTemperature;
        this.biomeRainfall = biomeRainfall;
        this.normallyAllowsPrecipitation = normallyAllowsPrecipitation;
        this.naturallySnowy = naturallySnowy;
        this.wetnessMultiplier = wetnessMultiplier;
        this.thunderMultiplier = thunderMultiplier;
        this.fogPotential = fogPotential;
        this.openSkyWeather = openSkyWeather;
    }
}
