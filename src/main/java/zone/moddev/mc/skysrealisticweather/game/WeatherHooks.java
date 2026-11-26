package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather.Precipitation;

public final class WeatherHooks {
    private static final ThreadLocal<SurfaceCheck> SURFACE = ThreadLocal.withInitial(SurfaceCheck::new);

    private WeatherHooks() {
    }

    public static float localRainStrength(float vanilla, World world) {
        WeatherState.Frame frame = active(world);
        if (frame == null || !world.isRemote || !frame.regionalSchedule()) return vanilla;
        BlockPos view = zone.moddev.mc.skysrealisticweather.WeatherMod.proxy.weatherViewPosition(world);
        if (view == null || !world.isBlockLoaded(view, false)) return vanilla;
        return frame.visualRainStrength(1, view, world.getBiome(view));
    }

    public static float localThunderStrength(float vanilla, World world) {
        WeatherState.Frame frame = active(world);
        if (frame == null || !world.isRemote || !frame.regionalSchedule()) return vanilla;
        BlockPos view = zone.moddev.mc.skysrealisticweather.WeatherMod.proxy.weatherViewPosition(world);
        if (view == null || !world.isBlockLoaded(view, false)) return vanilla;
        zone.moddev.mc.skysrealisticweather.api.WeatherEpisode episode = frame.currentEpisode(world.getBiome(view));
        return episode != null && episode.kind.thunder ? vanilla : 0;
    }

    public static WeatherState.Frame active(World world) {
        WeatherState.Frame frame = WeatherState.frame(world);
        return frame != null && frame.active() ? frame : null;
    }

    public static int weatherDuration(int vanilla, World world, int transition) {
        WeatherState.Frame frame = active(world);
        return frame == null || frame.live != null ? vanilla : frame.climate.weatherDuration(vanilla, transition);
    }

    public static boolean rainingAt(boolean vanilla, World world, BlockPos position) {
        WeatherState.Frame frame = active(world);
        if (frame == null || !world.isBlockLoaded(position, false)) return vanilla;
        return frame.rainAt(position);
    }

    public static boolean lightningAt(WorldServer world, BlockPos position) {
        WeatherState.Frame frame = active(world);
        if (frame == null || frame.regions == null || !frame.scheduled
                || frame.episode != null && frame.episode.forced) return world.isRainingAt(position);
        zone.moddev.mc.skysrealisticweather.api.WeatherEpisode episode = frame.currentEpisode(world.getBiome(position));
        return episode != null && episode.kind.thunder && frame.rainAt(position);
    }

    public static boolean freeze(WorldServer world, BlockPos position) {
        WeatherState.Frame frame = active(world);
        if (frame == null || !frame.settings.surface) return world.canBlockFreezeNoWater(position);
        if (!world.isBlockLoaded(position, false) || !world.getWorldBorder().contains(position)) return false;
        SurfaceTracker.INSTANCE.thaw(world, position, frame);
        SurfaceTracker.INSTANCE.thaw(world, position.up(), frame);
        if (!world.canSeeSky(position.up())) return false;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            if (!world.isBlockLoaded(position.offset(direction), false)) return false;
        }
        return checkFreeze(frame, world, position, true);
    }

    private static boolean checkFreeze(WeatherState.Frame frame, World world, BlockPos position, boolean edgeOnly) {
        SurfaceCheck check = beginSurfaceCheck(frame, world, position);
        try {
            return edgeOnly ? world.canBlockFreezeNoWater(position) : world.canBlockFreezeWater(position);
        } finally {
            endSurfaceCheck(check);
        }
    }

    public static boolean snow(WorldServer world, BlockPos position, boolean checkLight) {
        WeatherState.Frame frame = active(world);
        if (frame == null || !frame.settings.surface) return world.canSnowAt(position, checkLight);
        if (!world.canSeeSky(position) || !world.getWorldBorder().contains(position)
                || frame.precipitation(position, world.getBiome(position)) != Precipitation.SNOW) return false;
        return checkSnow(frame, world, position, checkLight, null, 0, World::canSnowAt);
    }

    static boolean checkSnow(WeatherState.Frame frame, World world, BlockPos position, boolean checkLight,
                             Biome sampledBiome, float temperature, SnowCheck eligibility) {
        SurfaceCheck check = beginSurfaceCheck(frame, world, position);
        check.sampledBiome = sampledBiome;
        check.temperature = temperature;
        try {
            return eligibility.test(world, position, checkLight);
        } finally {
            endSurfaceCheck(check);
        }
    }

    public static boolean generationFreeze(World world, BlockPos position) {
        WeatherState.Frame frame = generationFrame(world);
        if (frame == null) return world.canBlockFreezeWater(position);
        if (!world.isBlockLoaded(position, false)) return false;
        if (keepsNativeSurface(frame, position)) return world.canBlockFreezeWater(position);
        return world.getWorldBorder().contains(position) && checkFreeze(frame, world, position, false)
                && world.canSeeSky(position.up());
    }

    public static boolean generationSnow(World world, BlockPos position, boolean checkLight) {
        return generationSnow(world, position, checkLight, World::canSnowAt);
    }

    interface SnowCheck {
        boolean test(World world, BlockPos position, boolean checkLight);
    }

    static boolean generationSnow(World world, BlockPos position, boolean checkLight, SnowCheck eligibility) {
        WeatherState.Frame frame = generationFrame(world);
        if (frame == null) return eligibility.test(world, position, checkLight);
        if (!world.isBlockLoaded(position, false)) return false;
        Biome biome = world.getBiome(position);
        if (frame.settings.keepsNativeSurface(biome.getRegistryName())) return eligibility.test(world, position, checkLight);
        boolean raining = world.isRaining();
        boolean naturallyWet = BiomeClimates.allowsPrecipitation(biome);
        if (!raining && !naturallyWet) return false;
        float original = biome.getFloatTemperature(position);
        // Naturally snowy surfaces can retain existing cover between storms while it is still cold.
        boolean coldCover = naturallyWet && original <= WeatherState.SNOW_LINE;
        if (!raining && !coldCover) return false;
        float temperature = frame.temperature(biome, position, original);
        return (coldCover || frame.snowfallAt(position, biome, temperature))
                && world.canSeeSky(position) && world.getWorldBorder().contains(position)
                && checkSnow(frame, world, position, checkLight, biome, temperature, eligibility);
    }

    // The Lost Cities calls the provider's two-argument freeze check directly.
    public static boolean generationFreeze(World world, BlockPos position, boolean edgeOnly) {
        WeatherState.Frame frame = generationFrame(world);
        if (frame == null) return world.canBlockFreeze(position, edgeOnly);
        if (!world.isBlockLoaded(position, false)) return false;
        if (keepsNativeSurface(frame, position)) return world.canBlockFreeze(position, edgeOnly);
        return world.getWorldBorder().contains(position) && world.canSeeSky(position.up())
                && checkGenerationWater(frame, world, position, edgeOnly, null, 0);
    }

    static boolean checkGenerationWater(WeatherState.Frame frame, World world, BlockPos position, boolean edgeOnly,
                                        Biome sampledBiome, float temperature) {
        SurfaceCheck check = beginSurfaceCheck(frame, world, position);
        check.sampledBiome = sampledBiome;
        check.temperature = temperature;
        try {
            return world.canBlockFreeze(position, edgeOnly);
        } finally {
            endSurfaceCheck(check);
        }
    }

    public static boolean placeGeneratedDeposit(World world, BlockPos position, IBlockState state, int flags) {
        WeatherState.Frame frame = generationFrame(world);
        if (frame == null) return world.setBlockState(position, state, flags);
        if (!world.isBlockLoaded(position, false)) return false;
        if (keepsNativeSurface(frame, position)) return world.setBlockState(position, state, flags);
        return SurfaceTracker.INSTANCE.place((WorldServer) world, position, state, flags);
    }

    static WeatherState.Frame generationFrame(World world) {
        WeatherState.Frame frame = world instanceof WorldServer ? active(world) : null;
        return frame != null && frame.settings.seasonalWorldgen() ? frame : null;
    }

    private static boolean keepsNativeSurface(WeatherState.Frame frame, BlockPos position) {
        return frame.settings.keepsNativeSurface(frame.world.getBiome(position).getRegistryName());
    }

    private static SurfaceCheck beginSurfaceCheck(WeatherState.Frame frame, World world, BlockPos position) {
        SurfaceCheck check = SURFACE.get();
        if (check.frame != null) {
            // A world provider can call another weather check while handling this one.
            SurfaceCheck nested = new SurfaceCheck();
            nested.previous = check;
            check = nested;
            SURFACE.set(check);
        }
        check.frame = frame;
        check.world = world;
        check.position = position;
        return check;
    }

    private static void endSurfaceCheck(SurfaceCheck check) {
        check.frame = null;
        check.world = null;
        check.position = null;
        check.sampledBiome = null;
        if (check.previous != null) SURFACE.set(check.previous);
    }

    public static float surfaceTemperature(Biome biome, BlockPos position, World world) {
        SurfaceCheck check = SURFACE.get();
        if (check.world != world || !check.position.equals(position)) return biome.getFloatTemperature(position);
        return check.sampledBiome == biome ? check.temperature : check.frame.temperature(biome, position);
    }

    public static boolean placeDeposit(WorldServer world, BlockPos position, IBlockState state) {
        WeatherState.Frame frame = active(world);
        return frame == null || !frame.settings.surface ? world.setBlockState(position, state)
                : SurfaceTracker.INSTANCE.place(world, position, state);
    }

    public static void blockChanged(IBlockState previous, Chunk chunk, BlockPos position) {
        // Only replacing a deposit can invalidate its thaw record.
        if (previous == null) return;
        Block block = previous.getBlock();
        if (block != Blocks.ICE && block != Blocks.SNOW_LAYER) return;
        World world = chunk.getWorld();
        if (!world.isRemote && world.provider.getDimension() == 0) {
            SurfaceTracker.INSTANCE.changed(chunk, position);
        }
    }

    public static boolean sampleRain(Biome biome, WorldServer world) {
        WeatherState.Frame frame = active(world);
        return frame == null ? biome.canRain() : frame.wetBiome(biome);
    }

    public static void fillRain(Block block, World world, BlockPos position) {
        WeatherState.Frame frame = active(world);
        BlockPos surface = position.up();
        Precipitation falling = frame == null ? Precipitation.RAIN
                : frame.precipitation(surface, world.getBiome(surface));
        if (falling == Precipitation.RAIN || falling == Precipitation.HAIL) {
            block.fillWithRain(world, position);
        }
    }

    public static float rainTemperature(Biome biome, BlockPos position, World world) {
        WeatherState.Frame frame = active(world);
        return frame == null ? biome.getFloatTemperature(position) : frame.temperature(biome, position.up());
    }

    private static final class SurfaceCheck {
        SurfaceCheck previous;
        WeatherState.Frame frame;
        World world;
        BlockPos position;
        Biome sampledBiome;
        float temperature;
    }
}
