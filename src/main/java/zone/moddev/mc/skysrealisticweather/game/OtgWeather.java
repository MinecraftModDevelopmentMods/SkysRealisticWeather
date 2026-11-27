package zone.moddev.mc.skysrealisticweather.game;

import com.pg85.otg.LocalBiome;
import com.pg85.otg.LocalMaterialData;
import com.pg85.otg.LocalWorld;
import com.pg85.otg.forge.ForgeBiome;
import com.pg85.otg.forge.ForgeWorld;
import com.pg85.otg.util.minecraftTypes.DefaultMaterial;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;

/** Called at OTG v9's existing surface height and virtual biome lookup. */
public final class OtgWeather {
    private OtgWeather() {
    }

    public static float frostColumn(LocalBiome selected, int x, int y, int z, LocalWorld terrain) {
        float nativeTemperature = selected.getTemperatureAt(x, y, z);
        if (!(terrain instanceof ForgeWorld) || !(selected instanceof ForgeBiome) || y <= 0 || y >= 256) return nativeTemperature;
        World world = ((ForgeWorld) terrain).getWorld();
        WeatherState.Frame frame = WeatherHooks.generationFrame(world);
        if (frame == null) return nativeTemperature;
        BlockPos surface = new BlockPos(x, y, z);
        if (!world.isBlockLoaded(surface, false)) return nativeTemperature;
        Biome biome = ((ForgeBiome) selected).getHandle();
        Biome saved = world.getBiome(surface);
        if (frame.settings.keepsNativeSurface(biome.getRegistryName())
                || frame.settings.keepsNativeSurface(saved.getRegistryName())) return nativeTemperature;
        float temperature = frame.temperature(biome, surface, nativeTemperature);
        // OTG owns its naturally cold terrain, including snow depth, cooled lava and custom ice materials.
        if (nativeTemperature < WeatherState.SNOW_LINE) return temperature;
        if (temperature >= WeatherState.SNOW_LINE || !world.getWorldBorder().contains(surface) || !world.canSeeSky(surface)) {
            return nativeTemperature;
        }
        LocalMaterialData support = terrain.getMaterial(x, y - 1, z, false);
        BlockPos water = surface.down();
        if (support.isLiquid()) {
            if (selected.getBiomeConfig().iceBlock.isMaterial(DefaultMaterial.ICE)
                    && WeatherHooks.checkGenerationWater(frame, world, water, false, saved, temperature)) {
                SurfaceTracker.INSTANCE.place((WorldServer) world, water, Blocks.ICE.getDefaultState(), 2);
            }
        } else if (world.isRaining() && support.canSnowFallOn() && frame.snowfallAt(surface, biome, temperature)
                && WeatherHooks.checkSnow(frame, world, surface, true, saved, temperature, World::canSnowAt)) {
            SurfaceTracker.INSTANCE.place((WorldServer) world, surface, Blocks.SNOW_LAYER.getDefaultState(), 2);
        }
        // Added seasonal cover is complete. A warm native value skips OTG's permanent frost pass.
        return nativeTemperature;
    }
}
