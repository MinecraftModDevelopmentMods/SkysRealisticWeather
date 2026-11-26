package zone.moddev.mc.skysrealisticweather.live;

public final class Observation {
    public static final long MAX_AGE_SECONDS = 45 * 60;

    public enum Condition {
        CLEAR, FOG, RAIN, SNOW, THUNDER;

        public boolean wet() {
            return this == RAIN || this == SNOW || this == THUNDER;
        }
    }

    public final long observedAt;
    public final float celsius;
    public final Condition condition;

    public Observation(long observedAt, float celsius, Condition condition) {
        if (observedAt <= 0 || !Float.isFinite(celsius) || celsius < -100 || celsius > 70 || condition == null) {
            throw new IllegalArgumentException("Invalid weather observation");
        }
        this.observedAt = observedAt;
        this.celsius = celsius;
        this.condition = condition;
    }

    public boolean fresh(long now) {
        return observedAt <= now + 120 && observedAt >= now - MAX_AGE_SECONDS;
    }

    public float referenceTemperature() {
        float delta = Math.max(-0.95F, Math.min(1.1F, 0.045F * celsius));
        if (condition == Condition.SNOW) delta = Math.min(-0.035F, delta);
        if (condition == Condition.RAIN || condition == Condition.THUNDER) delta = Math.max(0.035F, delta);
        return 0.15F + delta;
    }
}
