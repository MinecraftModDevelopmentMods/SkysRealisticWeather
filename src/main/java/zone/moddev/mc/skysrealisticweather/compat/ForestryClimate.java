package zone.moddev.mc.skysrealisticweather.compat;

import forestry.api.climate.IClimateInfo;
import forestry.api.core.ForestryAPI;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zone.moddev.mc.skysrealisticweather.game.PlantGrowthHooks;

/** Called only on Forestry 5.3's ambient fallback; managed rooms return before this hook. */
public final class ForestryClimate {
    private ForestryClimate() { }

    public static IClimateInfo ambient(IClimateInfo nativeInfo, World world, BlockPos pos) {
        float temperature = PlantGrowthHooks.forestryTemperature(nativeInfo.getTemperature(), world, pos);
        float humidity = PlantGrowthHooks.forestryHumidity(nativeInfo.getHumidity(), world, pos);
        return temperature == nativeInfo.getTemperature() && humidity == nativeInfo.getHumidity() ? nativeInfo
                : ForestryAPI.climateManager.createInfo(temperature, humidity);
    }
}
