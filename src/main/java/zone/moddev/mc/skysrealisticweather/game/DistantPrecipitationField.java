package zone.moddev.mc.skysrealisticweather.game;

import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.EnumSkyBlock;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather.Precipitation;

/** Bounded, reusable samples of loaded terrain. No particles, chunk requests or per-frame biome lookups. */
public final class DistantPrecipitationField {
    public static final int MAX_COLUMNS = 2048;
    public final Column[] columns = new Column[MAX_COLUMNS];
    public int count, samples, range;
    private static final Comparator<Column> FAR_FIRST = (a, b) -> Double.compare(b.distanceSquared, a.distanceSquared);

    public static int range(int requested, int viewChunks, boolean fancy) {
        return Math.max(0, Math.min(Math.min(requested, fancy ? 256 : 96), Math.max(0, viewChunks * 16 - 16)));
    }

    public void clear() { count = samples = range = 0; }

    public void sample(WeatherState.Frame frame, double cameraX, double cameraZ, int requested, int viewChunks,
                       boolean fancy, float carrier, float cloudHeight) {
        count = samples = 0;
        range = range(requested, viewChunks, fancy);
        if (range <= 10 || !frame.active() || carrier <= 0) return;
        int anchorX = Math.floorDiv((int) Math.floor(cameraX), 16) * 16;
        int anchorZ = Math.floorDiv((int) Math.floor(cameraZ), 16) * 16;
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        // Aligned nested rings: four-block cells near the player, eight at medium range, sixteen far away.
        int inner = 0;
        bands: for (int band = 0; band < 3; band++) {
            int outer = Math.min((range + 15) / 16 * 16, band == 0 ? 32 : band == 1 ? 96 : (range + 15) / 16 * 16);
            int step = (fancy ? 4 : 8) << band;
            for (int z = -outer; z < outer; z += step) for (int x = -outer; x < outer; x += step) {
                double dx = x + step * .5, dz = z + step * .5;
                if (Math.max(Math.abs(dx), Math.abs(dz)) < inner) continue;
                int sx = anchorX + x + step / 2, sz = anchorZ + z + step / 2;
                double distanceSquared = (sx - cameraX) * (sx - cameraX) + (sz - cameraZ) * (sz - cameraZ);
                if (distanceSquared > range * range || distanceSquared < 16) continue;
                samples++;
                point.setPos(sx, 0, sz);
                if (!frame.world.isBlockLoaded(point, false)) continue;
                BlockPos surface = frame.world.getPrecipitationHeight(point);
                Biome biome = frame.world.getBiome(surface);
                float strength = frame.columnRainStrength(carrier, surface, biome);
                if (strength <= .005F) continue;
                Precipitation falling = frame.precipitation(surface, biome);
                if (falling == Precipitation.NONE) continue;
                Column column = columns[count];
                if (column == null) columns[count] = column = new Column();
                column.x = sx; column.z = sz; column.size = step;
                column.bottom = surface.getY();
                column.top = Math.max(surface.getY() + 48, cloudHeight + 8);
                column.strength = strength; column.snow = falling == Precipitation.SNOW;
                // The precipitation surface is open air; use its propagated light directly.
                column.light = frame.world.getLightFor(EnumSkyBlock.SKY, surface) << 20
                        | frame.world.getLightFor(EnumSkyBlock.BLOCK, surface) << 4;
                column.distanceSquared = distanceSquared;
                column.bounds = new AxisAlignedBB(sx - step, column.bottom, sz - step, sx + step, column.top, sz + step);
                if (++count == MAX_COLUMNS) break bands;
            }
            if (outer >= range) break;
            inner = outer;
        }
        Arrays.sort(columns, 0, count, FAR_FIRST);
    }

    public static float fade(double distance, int range, boolean fancy) {
        float near = (float) Math.max(0, Math.min(1, (distance - (fancy ? 7 : 4)) / 12));
        float far = (float) Math.max(0, Math.min(1, (range - distance) / 24));
        return near * far;
    }

    public static final class Column {
        public int x, z, size, light;
        public float bottom, top, strength;
        public boolean snow;
        public double distanceSquared;
        public AxisAlignedBB bounds;
    }
}
