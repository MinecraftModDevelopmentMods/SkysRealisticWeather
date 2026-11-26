package zone.moddev.mc.skysrealisticweather.verify;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.*;
import zone.moddev.mc.skysrealisticweather.instrument.Thermometer;

/** Native API, crop, instrument, furnace and process-reload contracts in disposable worlds. */
final class TemperatureZoneProbe {
    private static final BlockPos FURNACE=new BlockPos(8,81,8), CROP=FURNACE.east(), SAVED=new BlockPos(40,81,8);
    private static final ResourceLocation ROOM=new ResourceLocation("skyweatherprobe:greenhouse");
    private static int checks;
    private static volatile double sink;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException(message);}
    static void run(WorldServer world) {
        try {
            world.getChunkFromBlockCoords(FURNACE);world.getChunkFromBlockCoords(SAVED);
            world.setWorldTime(320L*24000+6000);
            WeatherState.server(new WeatherState.Frame(world,WeatherConfiguration.startupSettings(),null));
            if(Boolean.getBoolean("zones.reload"))reload(world);else contracts(world);
            System.out.println("ZONES_PASS checks="+checks);
        } catch(Throwable failure){System.out.println("ZONES_FAILED "+failure);failure.printStackTrace();}
        finally{world.getMinecraftServer().initiateShutdown();}
    }
    private static void contracts(WorldServer world)throws Exception {
        require(WeatherApi.VERSION==7,"API version 7");
        byte[] biomes=world.getChunkFromBlockCoords(FURNACE).getBiomeArray();Arrays.fill(biomes,(byte)Biome.getIdForBiome(Biomes.ICE_PLAINS));
        float ambient=(float)WeatherApi.getTemperature(world,CROP).getAsDouble();
        TemperatureZone room=TemperatureZone.celsius(ROOM,new BlockPos(4,80,4),new BlockPos(15,85,15),18);
        require(WeatherApi.putTemperatureZone(world,room),"child mod registers exact Celsius zone");
        require(!WeatherApi.putTemperatureZone(world,room),"identical update is a no-op");
        require(WeatherApi.getTemperatureSample(world,CROP).get().zoneId.equals(ROOM),"winning zone identified");
        require(WeatherApi.getLocalTemperature(world,new BlockPos(4,80,4)).getAsDouble()==room.temperature
                &&WeatherApi.getLocalTemperature(world,new BlockPos(15,85,15)).getAsDouble()==room.temperature,"inclusive 3D corners");
        require(WeatherApi.getTemperatureSample(world,new BlockPos(8,86,8)).get().source==TemperatureSample.Source.AMBIENT,"vertical boundary respected");
        require(WeatherApi.getTemperature(world,CROP).getAsDouble()==ambient,"outdoor API unchanged inside zone");
        require(WeatherApi.getClimate(world,CROP).get().openSkyWeather.temperature==ambient,"generation climate unchanged");
        require(Thermometer.temperature(world,CROP).getAsDouble()==room.temperature,"thermometer reads registered temperature");
        world.setBlockState(CROP.down(),Blocks.FARMLAND.getDefaultState(),2);world.setBlockState(CROP,Blocks.WHEAT.getDefaultState(),2);
        PlantGrowthSample crop=WeatherApi.getPlantGrowth(world,CROP).get();
        require(crop.controlledClimate&&crop.temperature==room.temperature,"crop growth shares effective temperature");
        require(TemperatureDisplay.Unit.CELSIUS.format(room.temperature).equals("18.0 °C")&&TemperatureDisplay.Unit.FAHRENHEIT.format(room.temperature).equals("64.4 °F"),"display units preserved");
        TemperatureZone high=new TemperatureZone(new ResourceLocation("skyweatherprobe:priority"),room.minimum,room.maximum,.15F,10,TemperatureZone.Mode.OVERRIDE);
        WeatherApi.putTemperatureZone(world,high);require(WeatherApi.getLocalTemperature(world,CROP).getAsDouble()==.15F,"priority can supply cooling override");
        WeatherApi.removeTemperatureZone(world,high.id);require(WeatherApi.getLocalTemperature(world,CROP).getAsDouble()==room.temperature,"removing winner restores lower zone");
        AtomicReference<Throwable> wrongThread=new AtomicReference<>();Thread worker=new Thread(()->{try{WeatherApi.putTemperatureZone(world,room);}catch(Throwable e){wrongThread.set(e);}});
        worker.start();worker.join();require(wrongThread.get() instanceof IllegalStateException,"off-thread mutations rejected");
        WorldServer nether=world.getMinecraftServer().worldServerForDimension(-1);nether.getChunkFromBlockCoords(CROP);
        require(WeatherApi.getTemperatureSample(nether,CROP).get().source==TemperatureSample.Source.AMBIENT,"dimension isolation");
        WeatherApi.putTemperatureZone(nether,room);require(WeatherApi.getLocalTemperature(nether,CROP).getAsDouble()==room.temperature,"zones usable outside seasonal dimensions");
        WeatherApi.removeTemperatureZone(nether,ROOM);
        TemperatureZone moved=TemperatureZone.celsius(ROOM,SAVED,SAVED.up(3),22);
        require(WeatherApi.putTemperatureZone(world,moved),"same ID replaces bounds atomically");
        require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.AMBIENT,"replacement removes old footprint");
        BlockPos far=new BlockPos(900008,81,900008);TemperatureZone remote=TemperatureZone.celsius(new ResourceLocation("skyweatherprobe:remote"),far,far.up(),30);
        WeatherApi.putTemperatureZone(world,remote);require(!world.isBlockLoaded(far,false)&&!WeatherApi.getLocalTemperature(world,far).isPresent(),"registration/query never generate terrain");WeatherApi.removeTemperatureZone(world,remote.id);
        start(world);require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.FURNACE,"native furnace tick registers zone");
        float target=WeatherConfiguration.furnaceHeatTemperature();
        require(Thermometer.temperature(world,CROP).getAsDouble()==target&&WeatherApi.getPlantGrowth(world,CROP).get().temperature==target,"furnace crop/instrument agreement");
        require(WeatherApi.getTemperatureSample(world,FURNACE.add(2,2,2)).get().source==TemperatureSample.Source.FURNACE,"furnace default 5x5x5 inclusive extent");
        require(WeatherApi.getTemperatureSample(world,FURNACE.add(3,0,0)).get().source==TemperatureSample.Source.AMBIENT,"furnace heating has bounded radius");
        world.setBlockState(FURNACE.up(),Blocks.STONE.getDefaultState(),2);
        require(WeatherApi.getTemperatureSample(world,FURNACE.up(2)).get().source==TemperatureSample.Source.FURNACE,"simple cuboid does not test wall insulation");
        Arrays.fill(biomes,(byte)Biome.getIdForBiome(Biomes.JUNGLE));
        float tropical=(float)WeatherApi.getTemperature(world,CROP).getAsDouble();
        require(tropical>target&&WeatherApi.getLocalTemperature(world,CROP).getAsDouble()==tropical,"furnace never cools tropical ambient");
        Arrays.fill(biomes,(byte)Biome.getIdForBiome(Biomes.ICE_PLAINS));
        TileEntityFurnace furnace=(TileEntityFurnace)world.getTileEntity(FURNACE);
        furnace.setInventorySlotContents(1,null);furnace.setField(0,1);advance(world);furnace.update();
        require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.AMBIENT,"burnout removes heat");
        start(world);world.setBlockToAir(FURNACE);
        require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.AMBIENT,"broken furnace cannot leave ghost heat");
        start(world);performance(world);
        world.getMinecraftServer().saveAllWorlds(false);
    }
    private static void advance(WorldServer world) { world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+20+Math.floorMod(-world.getTotalWorldTime()-FURNACE.toLong(),20)); }
    private static void start(WorldServer world) {
        if(world.getBlockState(FURNACE).getBlock()!=Blocks.FURNACE&&world.getBlockState(FURNACE).getBlock()!=Blocks.LIT_FURNACE)
            world.setBlockState(FURNACE,Blocks.FURNACE.getDefaultState(),3);
        TileEntityFurnace furnace=(TileEntityFurnace)world.getTileEntity(FURNACE);
        furnace.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,32));furnace.setInventorySlotContents(1,new ItemStack(Items.COAL,4));advance(world);furnace.update();
    }
    private static void reload(WorldServer world) {
        require(WeatherApi.getTemperatureSample(world,SAVED).get().source==TemperatureSample.Source.AMBIENT,"child registrations are runtime-only after process reload");
        require(world.getBlockState(FURNACE).getBlock()==Blocks.LIT_FURNACE,"burning furnace block survives restart");
        require(((TileEntityFurnace)world.getTileEntity(FURNACE)).isBurning(),"furnace fuel state survives restart");
        start(world);require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.FURNACE,"furnace re-registers from native tick after restart");
        TemperatureZone room=TemperatureZone.celsius(ROOM,SAVED,SAVED.up(3),22);WeatherApi.putTemperatureZone(world,room);
        require(WeatherApi.getLocalTemperature(world,SAVED).getAsDouble()==room.temperature,"child mod can restore its zone after reload");
    }
    private static void performance(WorldServer world)throws Exception {
        world.getChunkFromBlockCoords(new BlockPos(24,81,8));
        for(int i=0;i<2000;i++)WeatherApi.putTemperatureZone(world,TemperatureZone.celsius(new ResourceLocation("skyweatherprobe:far/"+i),new BlockPos(10000+i*16,81,0),new BlockPos(10000+i*16,85,3),25));
        new File("temperature-zones").mkdirs();
        try(PrintWriter csv=new PrintWriter(new File("temperature-zones/queries.csv"),"UTF-8")) {
            csv.println("query,median_ns,p95_ns");
            for(int mode=0;mode<3;mode++) {
                final int selected=mode;BlockPos p=mode==2?CROP:new BlockPos(24,81,8);long[] times=new long[30];
                for(int warm=0;warm<10000;warm++)sink=selected==0?WeatherApi.getTemperature(world,p).getAsDouble():WeatherApi.getLocalTemperature(world,p).getAsDouble();
                for(int round=0;round<30;round++){long start=System.nanoTime();double total=0;for(int i=0;i<5000;i++)total+=selected==0?WeatherApi.getTemperature(world,p).getAsDouble():WeatherApi.getLocalTemperature(world,p).getAsDouble();sink=total;times[round]=(System.nanoTime()-start)/5000;}
                Arrays.sort(times);csv.println(new String[]{"ambient_api","local_outside_2000_other_zones","local_furnace_2000_other_zones"}[mode]+","+times[15]+","+times[28]);
            }
        }
    }
}
