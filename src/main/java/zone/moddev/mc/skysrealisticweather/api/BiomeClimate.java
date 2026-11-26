package zone.moddev.mc.skysrealisticweather.api;

/** An optional override for a biome ID, in Minecraft temperature units. */
public final class BiomeClimate {
    public enum PrecipitationRule { BIOME_DEFAULT, ALLOW, DRY }

    public final float temperatureOffset;
    public final float seasonalScale;
    public final PrecipitationRule precipitation;

    public BiomeClimate(float temperatureOffset, float seasonalScale, PrecipitationRule precipitation) {
        if (!Float.isFinite(temperatureOffset) || Math.abs(temperatureOffset) > 2
                || !Float.isFinite(seasonalScale) || seasonalScale < 0 || seasonalScale > 2
                || precipitation == null) {
            throw new IllegalArgumentException("Invalid biome climate");
        }
        this.temperatureOffset = temperatureOffset;
        this.seasonalScale = seasonalScale;
        this.precipitation = precipitation;
    }
}
