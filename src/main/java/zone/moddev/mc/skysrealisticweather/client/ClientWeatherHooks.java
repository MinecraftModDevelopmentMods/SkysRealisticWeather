package zone.moddev.mc.skysrealisticweather.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather.Precipitation;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;

public final class ClientWeatherHooks {
    private ClientWeatherHooks() {
    }

    private static WeatherState.Frame active() {
        WeatherState.Frame snapshot = WeatherState.clientFrame();
        if (snapshot == null || !snapshot.active() || snapshot.world.provider.getWeatherRenderer() != null) return null;
        return WeatherState.frame(snapshot.world);
    }

    public static boolean mayPrecipitate(Biome biome, BlockPos position) {
        WeatherState.Frame frame = active();
        if (frame == null) return biome.canRain();
        if (!frame.world.isBlockLoaded(position, false)) return false;
        BlockPos sample = position.getY() == 0
                ? frame.world.getPrecipitationHeight(position) : position;
        return frame.mayPrecipitate(biome, sample);
    }

    public static boolean vanillaSnowBiome(Biome biome) {
        return active() == null && biome.getEnableSnow();
    }

    public static float columnTemperature(Biome biome, BlockPos position) {
        WeatherState.Frame frame = active();
        if (frame == null || !frame.world.isBlockLoaded(position, false)) return biome.getFloatTemperature(position);
        return visibleTemperature(frame, biome, frame.world.getPrecipitationHeight(position));
    }

    public static float particleTemperature(Biome biome, BlockPos position) {
        WeatherState.Frame frame = active();
        return frame == null ? biome.getFloatTemperature(position) : visibleTemperature(frame, biome, position);
    }

    private static float visibleTemperature(WeatherState.Frame frame, Biome biome, BlockPos position) {
        return frame.precipitation(position, biome) == Precipitation.FREEZING_RAIN
                ? WeatherState.SNOW_LINE : frame.temperature(biome, position);
    }

    public static float precipitationStrength(World world, float partialTicks) {
        WeatherState.Frame frame = active();
        // This is only the renderer's entry gate. Sky, fog, particles and sounds retain local strength.
        if (frame == null || frame.world != world || !frame.regionalSchedule()) return world.getRainStrength(partialTicks);
        zone.moddev.mc.skysrealisticweather.api.WeatherEpisode carrier = frame.currentEpisode();
        return carrier != null && carrier.kind.wet ? 1 : 0;
    }

    public static float columnRainStrength(float strength, Biome biome, BlockPos position) {
        WeatherState.Frame frame = active();
        if (frame == null) return strength;
        if (!frame.world.isBlockLoaded(position, false)) return 0;
        return frame.columnRainStrength(strength, frame.world.getPrecipitationHeight(position), biome);
    }

    public static float visualRainStrength(World world, float partialTicks) {
        float strength = world.getRainStrength(partialTicks);
        WeatherState.Frame frame = active();
        net.minecraft.entity.Entity camera = net.minecraft.client.Minecraft.getMinecraft().getRenderViewEntity();
        if (frame == null || frame.world != world || camera == null) return strength;
        if (frame.regionalSchedule()) return strength;
        BlockPos position = new BlockPos(camera.posX, camera.posY, camera.posZ);
        return world.isBlockLoaded(position, false)
                ? frame.visualRainStrength(strength, position, world.getBiome(position)) : strength;
    }
}
