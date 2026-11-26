package zone.moddev.mc.skysrealisticweather.api;

/** Types of scheduled weather. A wet event can fall as rain or snow depending on local conditions. */
public enum WeatherKind {
    DRIZZLE(true, false, 0.12F, 0.08F, 0),
    FRONTAL_PRECIPITATION(true, false, 0.34F, 0.3F, 0),
    CONVECTIVE_SHOWER(true, false, 0.25F, 0.27F, 0),
    THUNDERSTORM(true, true, 0.4F, 0.50F, 0),
    SEVERE_THUNDERSTORM(true, true, 0.55F, 0.86F, 0),
    HAILSTORM(true, true, 0.49F, 0.74F, -0.07F),
    MONSOON_BURST(true, false, 0.58F, 0.62F, 0),
    TROPICAL_STORM(true, true, 0.66F, 0.95F, 0),
    WINTER_STORM(true, false, 0.46F, 0.46F, -0.42F),
    SNOW_SQUALL(true, false, 0.84F, 0.81F, -0.24F),
    FREEZING_RAIN(true, false, 0.26F, 0.23F, -0.08F),
    BLIZZARD(true, false, 0.92F, 1, -0.35F),
    RADIATION_FOG(false, false, 1, 0.04F, 0),
    ADVECTION_FOG(false, false, 1, 0.24F, 0),
    DUST_STORM(false, false, 0.91F, 0.91F, 0.10F),
    HEATWAVE(false, false, 0, 0.08F, 0.32F),
    COLD_SNAP(false, false, 0, 0.22F, -0.55F),
    BLOWING_SNOW(false, false, 0.76F, 0.92F, -0.20F);

    public final boolean wet;
    public final boolean thunder;
    public final float fog;
    public final float wind;
    public final float temperatureOffset;

    public boolean temperatureSpell() {
        return this == HEATWAVE || this == COLD_SNAP;
    }

    WeatherKind(boolean wet, boolean thunder, float fog, float wind, float temperatureOffset) {
        this.wet = wet;
        this.thunder = thunder;
        this.fog = fog;
        this.wind = wind;
        this.temperatureOffset = temperatureOffset;
    }
}
