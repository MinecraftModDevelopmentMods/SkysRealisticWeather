package zone.moddev.mc.skysrealisticweather.api;

import net.minecraft.util.ResourceLocation;

/** Effective loaded-position temperature. Atmospheric weather/generation queries remain independent. */
public final class TemperatureSample {
    public enum Source { AMBIENT, ZONE, FURNACE, FORESTRY, FIRE, LAVA, TORCH }
    public final float ambientTemperature, temperature;
    public final Source source;
    /** Null for ambient/Forestry; otherwise identifies the winning zone or vanilla heat source. */
    public final ResourceLocation zoneId;
    public TemperatureSample(float ambient, float temperature, Source source, ResourceLocation zoneId) {
        this.ambientTemperature=ambient;this.temperature=temperature;this.source=source;this.zoneId=zoneId;
    }
}
