package zone.moddev.mc.skysrealisticweather.api;

/** Selects how the Overworld obtains its local climates. */
public enum ClimateMode {
    PER_BIOME("Per Biome Climate"),
    UNIVERSAL("Universal Climate"),
    REAL_WORLD("Real World Climate");

    public final String label;

    ClimateMode(String label) {
        this.label = label;
    }
}
