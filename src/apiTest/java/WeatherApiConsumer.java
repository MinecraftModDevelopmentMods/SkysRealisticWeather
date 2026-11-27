import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zone.moddev.mc.skysrealisticweather.api.*;

/** Compiled against the API artifact, with no implementation classes on its classpath. */
final class WeatherApiConsumer {
    void sample(World world, BlockPos pos) {
        WeatherApi.getLocalTemperature(world, pos);
        WeatherApi.getTemperatureSample(world, pos);
        WeatherApi.getWeather(world, pos);
        WeatherApi.getClimate(world, pos);
        WeatherApi.getPlantGrowth(world, pos);
        TemperatureZone zone = TemperatureZone.celsius(
                new net.minecraft.util.ResourceLocation("consumer", "greenhouse"), pos, pos.add(8, 4, 8), 20);
        WeatherApi.putTemperatureZone(world, zone);
        WeatherApi.removeTemperatureZone(world, zone.id);
    }
}
