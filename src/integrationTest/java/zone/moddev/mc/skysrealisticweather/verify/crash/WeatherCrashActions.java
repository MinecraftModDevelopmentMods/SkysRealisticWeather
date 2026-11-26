package zone.moddev.mc.skysrealisticweather.verify.crash;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.*;
import zone.moddev.mc.skysrealisticweather.game.*;

/** Loaded only for the weather-enabled half of a matched test. */
public final class WeatherCrashActions {
    public static void run(String action,WorldServer world,long value)throws Exception {
        if(action.equals("edge")){edge(world);return;}
        if(action.equals("query")) {
            BlockPos pos=BlockPos.fromLong(value);WeatherApi.getPlantGrowth(world,pos);WeatherApi.getTemperatureSample(world,pos);return;
        }
        List<Preset> presets=new ArrayList<>(WeatherConfiguration.presets());
        ServerWeather weather=ServerWeather.INSTANCE;
        if(value%5==0)weather.useMode(ClimateMode.PER_BIOME);
        else{weather.useMode(ClimateMode.UNIVERSAL);weather.usePreset(presets.get((int)(value%presets.size())));}
        boolean on=value%7!=0;weather.editPlants(on);
        weather.editOptions(value%11==0,true,true,true,value%3!=0);
    }
    private static void edge(WorldServer world)throws Exception {
        BlockPos pos=new BlockPos(8,81,8);world.getChunkFromBlockCoords(pos);
        Arrays.fill(world.getChunkFromBlockCoords(pos).getBiomeArray(),(byte)Biome.getIdForBiome(Biomes.PLAINS));
        world.setBlockState(pos.down(),Blocks.FARMLAND.getDefaultState(),2);world.setBlockState(pos,Blocks.WHEAT.getDefaultState(),2);
        ServerWeather.INSTANCE.editPlants(true);ServerWeather.INSTANCE.editOptions(false,true,true,true,true);
        Field temperature=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(Biome.class,"temperature","field_76750_F");
        float saved=temperature.getFloat(Biomes.PLAINS);
        try {
            temperature.setFloat(Biomes.PLAINS,Float.NaN);
            if(WeatherApi.getTemperatureSample(world,pos).isPresent())throw new IllegalStateException("Invalid provider temperature should be unavailable");
            if(WeatherApi.getPlantGrowth(world,pos).isPresent())throw new IllegalStateException("Invalid crop climate should be unavailable");
            if(!PlantGrowthHooks.allow(world,pos,new Random(1)))throw new IllegalStateException("Unavailable climate should retain native growth");
        } finally {temperature.setFloat(Biomes.PLAINS,saved);}
        if(!WeatherApi.getPlantGrowth(world,pos).isPresent())throw new IllegalStateException("Valid climate should recover after transient invalid data");
        System.out.println("QUALIFY_EDGE invalid-climate-fallback PASS");
    }
}
