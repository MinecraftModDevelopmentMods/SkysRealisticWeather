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
            if(!Boolean.getBoolean("zones.reload"))configurationContracts();
            world.setWorldTime(320L*24000+6000);
            WeatherState.server(new WeatherState.Frame(world,WeatherConfiguration.startupSettings(),null));
            if(Boolean.getBoolean("zones.reload"))reload(world);else contracts(world);
            System.out.println("ZONES_PASS checks="+checks);
        } catch(Throwable failure){System.out.println("ZONES_FAILED "+failure);failure.printStackTrace();}
        finally{world.getMinecraftServer().initiateShutdown();}
    }
    private static void contracts(WorldServer world)throws Exception {
        require(WeatherApi.VERSION==8,"API version 8");
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
        WorldServer nether=world.getMinecraftServer().getWorld(-1);nether.getChunkFromBlockCoords(CROP);
        require(WeatherApi.getTemperatureSample(nether,CROP).get().source==TemperatureSample.Source.AMBIENT,"dimension isolation");
        WeatherApi.putTemperatureZone(nether,room);require(WeatherApi.getLocalTemperature(nether,CROP).getAsDouble()==room.temperature,"zones usable outside seasonal dimensions");
        WeatherApi.removeTemperatureZone(nether,ROOM);
        TemperatureZone moved=TemperatureZone.celsius(ROOM,SAVED,SAVED.up(3),22);
        require(WeatherApi.putTemperatureZone(world,moved),"same ID replaces bounds atomically");
        require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.AMBIENT,"replacement removes old footprint");
        BlockPos far=new BlockPos(900008,81,900008);TemperatureZone remote=TemperatureZone.celsius(new ResourceLocation("skyweatherprobe:remote"),far,far.up(),30);
        WeatherApi.putTemperatureZone(world,remote);require(!world.isBlockLoaded(far,false)&&!WeatherApi.getLocalTemperature(world,far).isPresent(),"registration/query never generate terrain");WeatherApi.removeTemperatureZone(world,remote.id);
        start(world);require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.FURNACE,"native furnace tick registers zone");
        int radius=WeatherConfiguration.furnaceHeatRadius();
        require(radius>=2,"test profile has at least a two-block heating radius");
        world.getChunkFromBlockCoords(FURNACE.add(radius,radius,radius));
        world.getChunkFromBlockCoords(FURNACE.east(radius+1));
        float bonus=WeatherConfiguration.furnaceHeatBonus(),nearBonus=bonus*(1-1F/radius),target=ambient+nearBonus;
        require(Math.abs(Thermometer.temperature(world,CROP).getAsDouble()-target)<.00001&&Math.abs(WeatherApi.getPlantGrowth(world,CROP).get().temperature-target)<.00001,"furnace adds tapered warmth to cold ambient for crops/instruments");
        require(WeatherApi.getTemperatureSample(world,FURNACE.add(radius,radius,radius)).get().source==TemperatureSample.Source.AMBIENT,"cube corners lie outside the spherical heat radius");
        require(WeatherApi.getTemperatureSample(world,FURNACE.east(radius)).get().source==TemperatureSample.Source.AMBIENT,"furnace bonus reaches zero at its radius");
        require(WeatherApi.getTemperatureSample(world,FURNACE.east(radius+1)).get().source==TemperatureSample.Source.AMBIENT,"furnace heating has bounded radius");
        require(Math.abs(WeatherApi.getLocalTemperature(world,FURNACE.east(2)).getAsDouble()-WeatherApi.getTemperature(world,FURNACE.east(2)).getAsDouble()-bonus*(1-2F/radius))<.00001,"second block follows the same linear falloff");
        playerApproach(world,radius,bonus);
        world.setBlockState(FURNACE.up(),Blocks.STONE.getDefaultState(),2);
        require(WeatherApi.getTemperatureSample(world,FURNACE.up()).get().source==TemperatureSample.Source.FURNACE,"simple radial model does not test wall insulation");
        BlockPos second=FURNACE.west();start(world,second);
        require(Math.abs(WeatherApi.getLocalTemperature(world,FURNACE).getAsDouble()-WeatherApi.getTemperature(world,FURNACE).getAsDouble()-bonus)<.00001,
                "strongest furnace wins rather than the first registry ID");
        require(Math.abs(WeatherApi.getLocalTemperature(world,FURNACE.up()).getAsDouble()-WeatherApi.getTemperature(world,FURNACE.up()).getAsDouble()-nearBonus)<.00001,
                "overlapping furnace warmth does not stack");
        WeatherApi.putTemperatureZone(world,room);
        require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.ZONE&&WeatherApi.getLocalTemperature(world,CROP).getAsDouble()==room.temperature,"explicit child zones retain priority over furnace warmth");
        WeatherApi.putTemperatureZone(world,moved);world.setBlockToAir(second);
        Arrays.fill(biomes,(byte)Biome.getIdForBiome(Biomes.JUNGLE));
        float tropical=(float)WeatherApi.getTemperature(world,CROP).getAsDouble();
        require(Math.abs(WeatherApi.getLocalTemperature(world,CROP).getAsDouble()-tropical-nearBonus)<.00001,"furnace also adds warmth to hot ambient");
        Arrays.fill(biomes,(byte)Biome.getIdForBiome(Biomes.ICE_PLAINS));
        TileEntityFurnace furnace=(TileEntityFurnace)world.getTileEntity(FURNACE);
        furnace.setInventorySlotContents(1,ItemStack.EMPTY);furnace.setField(0,1);advance(world);furnace.update();
        require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.AMBIENT,"burnout removes heat");
        start(world);world.setBlockToAir(FURNACE);
        require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.AMBIENT,"broken furnace cannot leave ghost heat");
        blockHeat(world);start(world);performance(world);
        System.out.println("ZONES_FURNACE radius="+radius+" bonusCelsius="+TemperatureDisplay.Unit.CELSIUS.adjustment(bonus));
        world.getMinecraftServer().saveAllWorlds(false);
    }
    private static final BlockPos HEAT=new BlockPos(95,127,-1);
    private static void blockHeat(WorldServer world)throws Exception {
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkFromBlockCoords(HEAT.add(x*16,0,z*16));
        net.minecraft.block.Block[] blocks={Blocks.FIRE,Blocks.LAVA,Blocks.TORCH};
        TemperatureSample.Source[] types={TemperatureSample.Source.FIRE,TemperatureSample.Source.LAVA,TemperatureSample.Source.TORCH};
        int[] radii={WeatherConfiguration.fireHeatRadius(),WeatherConfiguration.lavaHeatRadius(),WeatherConfiguration.torchHeatRadius()};
        float[] bonuses={WeatherConfiguration.fireHeatBonus(),WeatherConfiguration.lavaHeatBonus(),WeatherConfiguration.torchHeatBonus()};
        for(int x=-3;x<=3;x++)for(int z=-1;z<=1;z++)world.setBlockState(HEAT.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
        // Sources already in the terrain before the first query must be found, including across chunk/section edges.
        for(int kind=0;kind<3;kind++) {
            world.setBlockState(HEAT,blocks[kind].getDefaultState(),2);
            require(world.getBlockState(HEAT).getBlock()==blocks[kind],"native source placement accepted: "+types[kind]);
            require(WeatherApi.getTemperatureSample(world,HEAT).get().source==types[kind],"discover existing "+types[kind]);
            for(int d:new int[]{0,1,2,radii[kind]-1,radii[kind],radii[kind]+1}) {
                BlockPos pos=HEAT.add(d,0,0);
                double ambient=WeatherApi.getTemperature(world,pos).getAsDouble(),local=WeatherApi.getLocalTemperature(world,pos).getAsDouble();
                require(Math.abs(local-ambient-bonuses[kind]*Math.max(0,1-d/(double)radii[kind]))<.00001,"linear "+types[kind]+" falloff at "+d);
            }
            BlockPos diagonal=HEAT.add(1,1,1);
            require(Math.abs(WeatherApi.getLocalTemperature(world,diagonal).getAsDouble()-WeatherApi.getTemperature(world,diagonal).getAsDouble()-bonuses[kind]*(1-Math.sqrt(3)/radii[kind]))<.00001,"3D/chunk/section "+types[kind]+" falloff");
            world.setBlockToAir(HEAT);
            require(WeatherApi.getTemperatureSample(world,HEAT.east()).get().source==TemperatureSample.Source.AMBIENT,"immediate "+types[kind]+" removal");
            world.setBlockState(HEAT,blocks[kind].getDefaultState(),2);
            require(WeatherApi.getTemperatureSample(world,HEAT.east()).get().source==types[kind],"immediate "+types[kind]+" placement");
            TemperatureZone zone=TemperatureZone.celsius(ROOM,HEAT,HEAT.add(3,3,3),-10);
            WeatherApi.putTemperatureZone(world,zone);
            require(WeatherApi.getLocalTemperature(world,HEAT.east()).getAsDouble()==zone.temperature,"child zone overrides "+types[kind]);
            WeatherApi.removeTemperatureZone(world,ROOM);
            world.setBlockToAir(HEAT);
        }
        world.setBlockState(HEAT,Blocks.FLOWING_LAVA.getDefaultState(),2);
        require(WeatherApi.getTemperatureSample(world,HEAT.east()).get().source==TemperatureSample.Source.LAVA,"flowing lava heats immediately");
        world.setBlockState(HEAT,Blocks.LAVA.getDefaultState(),2);
        require(WeatherApi.getTemperatureSample(world,HEAT.east()).get().source==TemperatureSample.Source.LAVA,"flowing-to-still lava retains heat");
        world.setBlockState(HEAT,Blocks.OBSIDIAN.getDefaultState(),2);
        require(WeatherApi.getTemperatureSample(world,HEAT.east()).get().source==TemperatureSample.Source.AMBIENT,"solidifying lava removes heat immediately");
        world.setBlockState(HEAT,Blocks.TORCH.getDefaultState(),2);
        world.setBlockState(HEAT.east(2),Blocks.TORCH.getDefaultState(),2);
        require(world.getBlockState(HEAT.east(2)).getBlock()==Blocks.TORCH,"second torch has native support");
        float single=WeatherConfiguration.torchHeatBonus()*(1-1F/WeatherConfiguration.torchHeatRadius());
        require(Math.abs(WeatherApi.getLocalTemperature(world,HEAT.east()).getAsDouble()-WeatherApi.getTemperature(world,HEAT.east()).getAsDouble()-single)<.00001,"overlapping torches never stack");
        world.setBlockState(HEAT.west(),Blocks.FIRE.getDefaultState(),2);
        require(world.getBlockState(HEAT.west()).getBlock()==Blocks.FIRE,"second fire has native support");
        require(WeatherApi.getTemperatureSample(world,HEAT.east()).get().source==TemperatureSample.Source.FIRE,"stronger distant fire beats nearby torch");
        start(world,HEAT.west(2));world.setBlockToAir(HEAT.west());
        require(WeatherApi.getTemperatureSample(world,HEAT.east()).get().source==TemperatureSample.Source.FURNACE,"furnace and block sources share strongest-bonus rule");
        world.setBlockState(HEAT.west(),Blocks.LAVA.getDefaultState(),2);
        require(WeatherApi.getTemperatureSample(world,HEAT.east()).get().source==TemperatureSample.Source.LAVA,"lava can beat furnace warmth");
        world.setBlockState(HEAT.east().down(),Blocks.FARMLAND.getDefaultState(),2);world.setBlockState(HEAT.east(),Blocks.WHEAT.getDefaultState(),2);
        require(WeatherApi.getPlantGrowth(world,HEAT.east()).get().temperature==(float)Thermometer.temperature(world,HEAT.east()).getAsDouble(),"block heat shared by crops and thermometer");
        require(new BlockHeatSources().sample(world,HEAT,new BlockHeatSources.Settings(0,1,0,1,0,1))==null,"all source radii can be disabled");
        world.setBlockToAir(HEAT.west());world.setBlockToAir(HEAT.west(2));world.setBlockToAir(HEAT.east(2));world.setBlockToAir(HEAT);
        WeatherApi.getLocalTemperature(world,HEAT);
        // A mod bypassing Chunk.setBlockState still recovers within one second.
        net.minecraft.world.chunk.storage.ExtendedBlockStorage section=world.getChunkFromBlockCoords(HEAT).getBlockStorageArray()[HEAT.getY()>>4];
        section.set(HEAT.getX()&15,HEAT.getY()&15,HEAT.getZ()&15,Blocks.TORCH.getDefaultState());
        world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+20);
        require(WeatherApi.getTemperatureSample(world,HEAT).get().source==TemperatureSample.Source.TORCH,"direct section mutation has bounded cache refresh");
        world.setBlockToAir(HEAT);
        blockHeatPerformance(world);
        world.setBlockState(HEAT,Blocks.TORCH.getDefaultState(),2);
        world.setBlockState(HEAT.east(32),Blocks.LAVA.getDefaultState(),2);
    }
    private static void blockHeatPerformance(WorldServer world)throws Exception {
        BlockPos pos=HEAT.add(0,20,0);world.getChunkFromBlockCoords(pos);
        try(PrintWriter csv=new PrintWriter("temperature-zones/block-heat-queries.csv","UTF-8")) {
            csv.println("scenario,median_ns,p95_ns");
            for(int mode=0;mode<3;mode++) {
                if(mode==1){world.setBlockState(pos.west().down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(pos.west(),Blocks.TORCH.getDefaultState(),2);}
                if(mode==2)for(int y=-8;y<0;y++)for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)world.setBlockState(pos.add(x,y,z),Blocks.LAVA.getDefaultState(),2);
                long[] times=new long[30];
                for(int warm=0;warm<10000;warm++)sink=WeatherApi.getLocalTemperature(world,pos).getAsDouble();
                for(int round=0;round<30;round++) {
                    long start=System.nanoTime();double total=0;
                    for(int i=0;i<2000;i++)total+=WeatherApi.getLocalTemperature(world,pos).getAsDouble();
                    sink=total;times[round]=(System.nanoTime()-start)/2000;
                }
                Arrays.sort(times);csv.println(new String[]{"no_heat","one_torch","5000_lava_blocks"}[mode]+","+times[15]+","+times[28]);
            }
        }
        for(int y=-8;y<0;y++)for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)world.setBlockToAir(pos.add(x,y,z));
        world.setBlockToAir(pos.west());
    }
    private static void configurationContracts() throws Exception {
        java.nio.file.Path original=WeatherConfiguration.file().getConfigFile().toPath();
        String contents=new String(java.nio.file.Files.readAllBytes(original),java.nio.charset.StandardCharsets.UTF_8);
        int[] values={2,5,0,2},expected={10,5,0,2};
        new File("temperature-zones").mkdirs();
        try {
            for(int i=0;i<values.length;i++) {
                String version=i==3?"8":"7";
                File fixture=new File("temperature-zones/radius-"+i+".cfg");
                java.nio.file.Files.write(fixture.toPath(),contents.replaceFirst("~CONFIG_VERSION: [0-9]+","~CONFIG_VERSION: "+version).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                net.minecraftforge.common.config.Configuration config=new net.minecraftforge.common.config.Configuration(fixture,version);
                config.getCategory("heating").get("furnaceRadius").set(values[i]);config.save();
                WeatherConfiguration.load(fixture);
                require(WeatherConfiguration.furnaceHeatRadius()==expected[i],"radius migration preserves custom/disabled/current-schema values: "+i);
                require(new String(java.nio.file.Files.readAllBytes(fixture.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains("~CONFIG_VERSION: 8"),"radius fixture upgrades to config 8");
                WeatherConfiguration.load(fixture);
                require(WeatherConfiguration.furnaceHeatRadius()==expected[i],"radius migration is not repeated: "+i);
            }
        } finally {WeatherConfiguration.load(original.toFile());}
    }
    private static void playerApproach(WorldServer world,int radius,float peak)throws Exception {
        net.minecraftforge.common.util.FakePlayer player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        new File("temperature-zones").mkdirs();
        try(PrintWriter csv=new PrintWriter("temperature-zones/player-approach.csv","UTF-8")) {
            csv.println("horizontal_blocks,eye_height_blocks,ambient_celsius,local_celsius,bonus_celsius");
            for(int distance=Math.min(radius+1,12);distance>=1;distance--) {
                player.setPosition(FURNACE.getX()+distance+.5,FURNACE.getY(),FURNACE.getZ()+.5);
                BlockPos eye=ClimateReadout.eyes(player);world.getChunkFromBlockCoords(eye);
                require(eye.getY()==FURNACE.getY()+1,"thermometer uses actual player eye-height block");
                double ambient=WeatherApi.getTemperature(world,eye).getAsDouble();
                double local=Thermometer.temperature(world,eye).getAsDouble();
                double expected=peak*Math.max(0,1-Math.sqrt(distance*distance+1D)/radius);
                require(Math.abs(local-ambient-expected)<.00001,"held thermometer falloff at walking distance "+distance);
                double delta=TemperatureDisplay.Unit.CELSIUS.adjustment(local-ambient);
                csv.printf(Locale.ROOT,"%d,1,%.4f,%.4f,%.4f%n",distance,TemperatureDisplay.Unit.CELSIUS.temperature(ambient),TemperatureDisplay.Unit.CELSIUS.temperature(local),delta);
                if(radius==10&&distance==9)require(delta>1.8&&delta<2,"warming starts well before reaching the furnace");
                if(radius==10&&distance==1)require(delta>17&&delta<18,"adjacent head-height warmth is no longer the old six-degree jump");
            }
        }
    }
    private static void advance(WorldServer world) { world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+20+Math.floorMod(-world.getTotalWorldTime()-FURNACE.toLong(),20)); }
    private static void start(WorldServer world) {
        start(world,FURNACE);
    }
    private static void start(WorldServer world,BlockPos source) {
        if(world.getBlockState(source).getBlock()!=Blocks.FURNACE&&world.getBlockState(source).getBlock()!=Blocks.LIT_FURNACE)
            world.setBlockState(source,Blocks.FURNACE.getDefaultState(),3);
        TileEntityFurnace furnace=(TileEntityFurnace)world.getTileEntity(source);
        furnace.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,32));furnace.setInventorySlotContents(1,new ItemStack(Items.COAL,4));
        world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+20+Math.floorMod(-world.getTotalWorldTime()-source.toLong(),20));furnace.update();
    }
    private static void reload(WorldServer world) {
        require(WeatherApi.getTemperatureSample(world,SAVED).get().source==TemperatureSample.Source.AMBIENT,"child registrations are runtime-only after process reload");
        require(world.getBlockState(FURNACE).getBlock()==Blocks.LIT_FURNACE,"burning furnace block survives restart");
        require(((TileEntityFurnace)world.getTileEntity(FURNACE)).isBurning(),"furnace fuel state survives restart");
        start(world);require(WeatherApi.getTemperatureSample(world,CROP).get().source==TemperatureSample.Source.FURNACE,"furnace re-registers from native tick after restart");
        TemperatureZone room=TemperatureZone.celsius(ROOM,SAVED,SAVED.up(3),22);WeatherApi.putTemperatureZone(world,room);
        require(WeatherApi.getLocalTemperature(world,SAVED).getAsDouble()==room.temperature,"child mod can restore its zone after reload");
        world.getChunkFromBlockCoords(HEAT);world.getChunkFromBlockCoords(HEAT.east(32));
        require(WeatherApi.getTemperatureSample(world,HEAT).get().source==TemperatureSample.Source.TORCH,"saved torch discovered with a fresh process cache");
        require(WeatherApi.getTemperatureSample(world,HEAT.east(32)).get().source==TemperatureSample.Source.LAVA,"saved lava discovered with a fresh process cache");
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
