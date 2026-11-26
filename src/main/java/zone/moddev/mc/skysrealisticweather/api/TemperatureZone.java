package zone.moddev.mc.skysrealisticweather.api;

import java.util.Objects;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Immutable runtime cuboid. Corners are inclusive and may be supplied in either order. */
public final class TemperatureZone {
    public enum Mode { OVERRIDE, MINIMUM }
    public static final int MAX_CHUNKS = 1024;
    public final ResourceLocation id;
    public final BlockPos minimum, maximum;
    /** Minecraft temperature units, independent of the player's display-unit preference. */
    public final float temperature;
    /** Higher wins. Equal priorities use the lexically smaller registry ID. */
    public final int priority;
    public final Mode mode;

    public TemperatureZone(ResourceLocation id, BlockPos first, BlockPos second, float temperature, int priority, Mode mode) {
        this.id = Objects.requireNonNull(id); Objects.requireNonNull(first); Objects.requireNonNull(second);
        this.mode = Objects.requireNonNull(mode);
        minimum = new BlockPos(Math.min(first.getX(),second.getX()), Math.min(first.getY(),second.getY()), Math.min(first.getZ(),second.getZ()));
        maximum = new BlockPos(Math.max(first.getX(),second.getX()), Math.max(first.getY(),second.getY()), Math.max(first.getZ(),second.getZ()));
        long chunks = ((long)(maximum.getX() >> 4)-(minimum.getX() >> 4)+1) * ((long)(maximum.getZ() >> 4)-(minimum.getZ() >> 4)+1);
        if (!Float.isFinite(temperature) || id.toString().length() > 128 || minimum.getY() < 0 || maximum.getY() > 255
                || minimum.getX() < -30000000 || maximum.getX() > 30000000 || minimum.getZ() < -30000000 || maximum.getZ() > 30000000
                || chunks > MAX_CHUNKS) throw new IllegalArgumentException("Invalid temperature zone bounds/value (maximum 1024 horizontal chunks, Y 0..255)");
        this.temperature = temperature; this.priority = priority;
    }

    /** Convenience factory for an exact Celsius override at ordinary priority. */
    public static TemperatureZone celsius(ResourceLocation id, BlockPos first, BlockPos second, float celsius) {
        return new TemperatureZone(id,first,second,WeatherApi.SNOW_LINE+celsius*.045F,0,Mode.OVERRIDE);
    }
    public boolean contains(BlockPos p) {
        return p.getX()>=minimum.getX() && p.getX()<=maximum.getX() && p.getY()>=minimum.getY() && p.getY()<=maximum.getY()
                && p.getZ()>=minimum.getZ() && p.getZ()<=maximum.getZ();
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof TemperatureZone)) return false;
        TemperatureZone z=(TemperatureZone)other;
        return id.equals(z.id) && minimum.equals(z.minimum) && maximum.equals(z.maximum) && Float.compare(temperature,z.temperature)==0 && priority==z.priority && mode==z.mode;
    }
    @Override public int hashCode() { return Objects.hash(id,minimum,maximum,temperature,priority,mode); }
}
