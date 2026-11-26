package zone.moddev.mc.skysrealisticweather.api;

import java.util.Objects;

/** An Overworld weather event. The clock is given by {@link #forced}. Times are in Minecraft ticks. */
public final class WeatherEpisode {
    public static final int MAX_DURATION = 48000;
    public final WeatherKind kind;
    public final long startTick;
    public final long endTick;
    public final float peakIntensity;
    public final boolean includesDryBiomes;
    /** Admin preview episodes use total game time. Scheduled episodes use the daylight clock. */
    public final boolean forced;
    private final int rampTicks;
    private final int fadeTicks;
    private final double driftX;
    private final double driftZ;

    public WeatherEpisode(WeatherKind kind, long startTick, int duration, float peakIntensity) {
        this(kind, startTick, duration, peakIntensity, false);
    }

    public WeatherEpisode(WeatherKind kind, long startTick, int duration, float peakIntensity, boolean includesDryBiomes) {
        this(kind, startTick, duration, peakIntensity, includesDryBiomes, false);
    }

    public WeatherEpisode(WeatherKind kind, long startTick, int duration, float peakIntensity,
                          boolean includesDryBiomes, boolean forced) {
        if (duration < 1 || duration > MAX_DURATION || !Float.isFinite(peakIntensity)
                || peakIntensity <= 0 || peakIntensity > 1 || startTick > Long.MAX_VALUE - duration) {
            throw new IllegalArgumentException("Invalid weather episode");
        }
        this.kind = Objects.requireNonNull(kind);
        this.startTick = startTick;
        this.endTick = startTick + duration;
        this.peakIntensity = peakIntensity;
        this.includesDryBiomes = includesDryBiomes;
        this.forced = forced;
        boolean sudden = kind == WeatherKind.CONVECTIVE_SHOWER || kind == WeatherKind.THUNDERSTORM
                || kind == WeatherKind.SEVERE_THUNDERSTORM || kind == WeatherKind.HAILSTORM || kind == WeatherKind.SNOW_SQUALL
                || kind == WeatherKind.DUST_STORM;
        rampTicks = Math.max(1, Math.min(forced ? 40 : sudden ? 110 : 2200, duration / (sudden ? 10 : 5)));
        fadeTicks = Math.max(1, Math.min(forced ? 60 : 3000, duration / 4));
        double bearing = Math.toRadians(windBearing());
        driftX = Math.sin(bearing) * kind.wind / 18;
        driftZ = -Math.cos(bearing) * kind.wind / 18;
    }

    public boolean contains(long time) {
        return time >= startTick && time < endTick;
    }

    /** Selects the clock to use with the episode's timing and coverage methods. */
    public long time(long daylight, long total) {
        return forced ? total : daylight;
    }

    /** Instantaneous intensity on a zero-to-one scale, including onset and dissipation. */
    public float intensity(long time) {
        if (!contains(time)) return 0;
        float ramp = Math.min(1, (time - startTick + 1) / (float) rampTicks);
        float fade = Math.min(1, (endTick - time) / (float) fadeTicks);
        return peakIntensity * ramp * fade;
    }

    /** Prevailing horizontal bearing in degrees, clockwise from north. */
    public int windBearing() {
        return (int) Math.floorMod((startTick ^ (startTick >>> 17)) * 0x9E3779B97F4A7C15L, 360L);
    }

    /** Convective storms and squalls pass through moving bands with dry gaps. */
    public boolean covers(int x, int z, long time) {
        if (forced) return true;
        switch (kind) {
            case CONVECTIVE_SHOWER:
            case THUNDERSTORM:
            case SEVERE_THUNDERSTORM:
            case HAILSTORM:
            case SNOW_SQUALL:
                long elapsed = Math.max(startTick, Math.min(endTick, time)) - startTick;
                double sampleX = (x - elapsed * driftX) / 128;
                double sampleZ = (z - elapsed * driftZ) / 128;
                int cellX = (int) Math.floor(sampleX);
                int cellZ = (int) Math.floor(sampleZ);
                double fx = smooth(sampleX - cellX);
                double fz = smooth(sampleZ - cellZ);
                double north = lerp(cell(cellX, cellZ), cell(cellX + 1, cellZ), fx);
                double south = lerp(cell(cellX, cellZ + 1), cell(cellX + 1, cellZ + 1), fx);
                return lerp(north, south, fz) >= 0.40;
            default:
                return true;
        }
    }

    private double cell(int x, int z) {
        long hash = startTick ^ x * 0x9E3779B97F4A7C15L ^ z * 0xD1B54A32D192ED03L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        return ((hash ^ (hash >>> 31)) >>> 40) / 16777216.0;
    }

    private static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }
}
