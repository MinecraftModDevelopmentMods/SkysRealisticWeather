package zone.moddev.mc.skysrealisticweather.api;

/** Immutable natural-growth policy; fertiliser and native plant eligibility remain separate. */
public final class PlantGrowthSample {
    public enum Reason { SUITABLE, COLD, HEAT, EXEMPT, UNMAPPED, DISABLED, ADAPTER_DISABLED }
    public final String plantId, profileId;
    public final float temperature, naturalGrowthMultiplier;
    public final boolean controlledClimate;
    public final Reason reason;

    public PlantGrowthSample(String plantId, String profileId, float temperature, float multiplier,
                             boolean controlledClimate, Reason reason) {
        this.plantId = plantId;
        this.profileId = profileId;
        this.temperature = temperature;
        this.naturalGrowthMultiplier = multiplier;
        this.controlledClimate = controlledClimate;
        this.reason = reason;
    }
}
