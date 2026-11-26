package zone.moddev.mc.skysrealisticweather.api;

/** Temperature suitability for natural growth, in Minecraft biome units. */
public final class PlantGrowthProfile {
    public static final PlantGrowthProfile EXEMPT = new PlantGrowthProfile();
    public final float minimum, idealMinimum, idealMaximum, maximum;
    public final boolean exempt;

    private PlantGrowthProfile() {
        minimum = idealMinimum = idealMaximum = maximum = 0;
        exempt = true;
    }

    public PlantGrowthProfile(float minimum, float idealMinimum, float idealMaximum, float maximum) {
        if (!Float.isFinite(minimum) || !Float.isFinite(idealMinimum) || !Float.isFinite(idealMaximum)
                || !Float.isFinite(maximum) || !(minimum < idealMinimum && idealMinimum <= idealMaximum && idealMaximum < maximum)) {
            throw new IllegalArgumentException("Expected finite minimum < ideal minimum <= ideal maximum < maximum");
        }
        this.minimum = minimum;
        this.idealMinimum = idealMinimum;
        this.idealMaximum = idealMaximum;
        this.maximum = maximum;
        this.exempt = false;
    }

    public float multiplier(float temperature) {
        if (exempt || !Float.isFinite(temperature)) return 1;
        if (temperature <= minimum || temperature >= maximum) return 0;
        if (temperature < idealMinimum) return (float) (((double) temperature - minimum) / ((double) idealMinimum - minimum));
        if (temperature > idealMaximum) return (float) (((double) maximum - temperature) / ((double) maximum - idealMaximum));
        return 1;
    }
}
