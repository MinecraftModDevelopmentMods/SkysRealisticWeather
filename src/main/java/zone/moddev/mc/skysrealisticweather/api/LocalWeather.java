package zone.moddev.mc.skysrealisticweather.api;

/** Current weather at an already loaded position. Temperature uses Minecraft biome units. */
public final class LocalWeather {
    public enum Precipitation { NONE, RAIN, SNOW, HAIL, FREEZING_RAIN }
    public enum Source { VANILLA, SEASONAL, LIVE }

    public final Precipitation precipitation;
    public final Source source;
    public final float temperature;
    public final float fog;
    public final boolean thunder;
    /** Intensity of falling precipitation on a zero-to-one scale. */
    public final float intensity;
    /** Outdoor wind on the same scale, during a scheduled event. */
    public final float wind;

    public LocalWeather(Precipitation precipitation, Source source, float temperature, float fog, boolean thunder) {
        this(precipitation, source, temperature, fog, thunder, 0, 0);
    }

    public LocalWeather(Precipitation precipitation, Source source, float temperature, float fog, boolean thunder,
                        float intensity, float wind) {
        this.precipitation = precipitation;
        this.source = source;
        this.temperature = temperature;
        this.fog = fog;
        this.thunder = thunder;
        this.intensity = intensity;
        this.wind = wind;
    }
}
