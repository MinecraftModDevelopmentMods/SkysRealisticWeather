package zone.moddev.mc.skysrealisticweather.verify.crash;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.*;
import net.minecraftforge.event.terraingen.SaplingGrowTreeEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Optional-dependency probe: the same native schedule can run with and without the weather jar. */
@Mod(modid="weatherqualification",name="Weather Qualification",version="1",dependencies="after:skysrealisticweather")
public final class CrashQualificationProbe {
    @Mod.Instance("weatherqualification") public static CrashQualificationProbe INSTANCE;
    private long cropEvents,treeEvents,updates,queries,ticks;
    private String stage="startup";
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if(!Boolean.getBoolean("crash.probe"))return;
        MinecraftForge.EVENT_BUS.register(this);MinecraftForge.TERRAIN_GEN_BUS.register(this);
        ForgeChunkManager.setForcedChunkLoadingCallback(this,(ForgeChunkManager.LoadingCallback)(tickets,world)->{});
    }
    @SubscribeEvent public void crop(BlockEvent.CropGrowEvent.Pre event){cropEvents++;}
    @SubscribeEvent public void tree(SaplingGrowTreeEvent event){treeEvents++;}
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        if(!Boolean.getBoolean("crash.probe"))return;
        MinecraftServer server=FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world=server.getWorld(0);long start=System.nanoTime();
        List<ForgeChunkManager.Ticket> tickets=new ArrayList<>();
        try {
            String phase=System.getProperty("crash.phase","soak");
            if(phase.equals("edge")){stage="invalid climate data";weather("edge",world,0);}
            else if(phase.equals("stress")){stage="plant matrix";stress(world);}
            else {
                stage="loading saved crop chunks";
                List<String> targets=Files.readAllLines(Paths.get("target-chunks.tsv"),StandardCharsets.UTF_8);
                ForgeChunkManager.Ticket ticket=ForgeChunkManager.requestTicket(this,world,ForgeChunkManager.Type.NORMAL);
                if(ticket==null)throw new IllegalStateException("Cannot obtain native random-tick chunk ticket");tickets.add(ticket);
                List<ChunkPos> locations=new ArrayList<>();
                for(String line:targets){if(line.trim().isEmpty())continue;String[] p=line.split("\\t");locations.add(new ChunkPos(Integer.parseInt(p[0]),Integer.parseInt(p[1])));}
                Set<ChunkPos> activeChunks=new HashSet<>();List<BlockPos> activeCrops=new ArrayList<>();
                System.out.println("QUALIFY_TARGETS unique="+locations.size()+" ticketDepth="+ticket.getChunkListDepth());
                List<BlockPos> crops=points();
                long initial=world.getWorldTime();int steps=Integer.getInteger("crash.ticks",6000);
                for(int tick=0;tick<steps;tick++) {
                    stage="native server tick "+tick;
                    if(tick%200==0){
                        for(ChunkPos pos:activeChunks)ForgeChunkManager.unforceChunk(ticket,pos);activeChunks.clear();
                        for(int index=0;index<Math.min(24,locations.size());index++) {
                            ChunkPos pos=locations.get((tick/200*24+index)%locations.size());world.getChunkFromChunkCoords(pos.x,pos.z);ForgeChunkManager.forceChunk(ticket,pos);activeChunks.add(pos);
                        }
                        activeCrops.clear();for(BlockPos pos:crops)if(activeChunks.contains(new ChunkPos(pos)))activeCrops.add(pos);
                        weather("schedule",world,tick/200);System.out.println("QUALIFY_PROGRESS tick="+tick+" activeCropPoints="+activeCrops.size()+" cropEvents="+cropEvents+" updates="+updates);
                    }
                    world.setWorldTime(initial+(long)tick*24000);server.tick();ticks++;
                    for(int index=0;index<Math.min(64,activeCrops.size());index++) {
                        BlockPos pos=activeCrops.get((tick*61+index)%activeCrops.size());
                        if(!world.isBlockLoaded(pos,false))continue;
                        IBlockState state=world.getBlockState(pos);Block block=state.getBlock();
                        if(block instanceof IPlantable||block instanceof IGrowable){block.updateTick(world,pos,state,world.rand);updates++;if(tick%20==0){weather("query",world,pos.toLong());queries++;}}
                    }
                }
                stage="saved tile entity round trip";
                for(Chunk chunk:world.getChunkProvider().getLoadedChunks())for(TileEntity tile:new ArrayList<>(chunk.getTileEntityMap().values())) {
                    NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());
                    if(tag==null)throw new IllegalStateException("Null saved tile NBT: "+tile.getClass().getName());
                }
            }
            stage="save";server.saveAllWorlds(false);
            try(PrintWriter report=new PrintWriter("qualification-summary.csv","UTF-8")) {
                report.println("weather,native_server_ticks,native_crop_updates,crop_events,tree_events,queries,seconds");
                report.printf(Locale.ROOT,"%s,%d,%d,%d,%d,%d,%.3f%n",Loader.isModLoaded("skysrealisticweather"),ticks,updates,cropEvents,treeEvents,queries,(System.nanoTime()-start)/1e9);
            }
            System.out.println("QUALIFY_PASS stage="+stage+" ticks="+ticks+" updates="+updates+" cropEvents="+cropEvents+" trees="+treeEvents);
        } catch(Throwable failure) {
            System.out.println("QUALIFY_FAIL stage="+stage+" "+failure);failure.printStackTrace();
        } finally {
            for(ForgeChunkManager.Ticket ticket:tickets)ForgeChunkManager.releaseTicket(ticket);
            server.initiateShutdown();
        }
    }
    private static void weather(String action,WorldServer world,long value)throws Exception {
        if(!Loader.isModLoaded("skysrealisticweather"))return;
        try{Class.forName("zone.moddev.mc.skysrealisticweather.verify.crash.WeatherCrashActions").getMethod("run",String.class,WorldServer.class,long.class).invoke(null,action,world,value);}
        catch(java.lang.reflect.InvocationTargetException failed){Throwable cause=failed.getCause();if(cause instanceof Error)throw(Error)cause;throw new IllegalStateException(cause);}
    }
    private static List<BlockPos> points()throws IOException {
        List<BlockPos> result=new ArrayList<>();for(String line:Files.readAllLines(Paths.get("crop-points.tsv"),StandardCharsets.UTF_8)) {
            String[] p=line.split("\\t");if(p.length>=3)result.add(new BlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])));
        }
        return result;
    }
    private void stress(WorldServer world)throws Exception {
        BlockPos pos=new BlockPos(8,81,8);world.getChunkFromBlockCoords(pos);Random random=new Random(913570);
        List<Block> plants=new ArrayList<>();for(Block block:Block.REGISTRY)if(block instanceof IPlantable||block instanceof IGrowable)plants.add(block);
        int states=0,items=0;
        for(int climate=0;climate<4;climate++) {
            weather("schedule",world,climate+1);world.setWorldTime(climate*92L*24000+6000);
            for(Block block:plants) {
                String name=block.getRegistryName().toString();if(!name.startsWith("minecraft:")&&!name.startsWith("harvestcraft:")&&!name.startsWith("mysticalagriculture:"))continue;
                if(block instanceof BlockSapling)continue;
                List<IBlockState> valid=block.getBlockState().getValidStates();
                for(int stateIndex=0;stateIndex<valid.size();stateIndex++) {
                    IBlockState state=valid.get(stateIndex);stage="plant matrix "+name+" "+state;
                    world.setBlockToAir(pos);world.setBlockState(pos.down(),Blocks.FARMLAND.getDefaultState().withProperty(BlockFarmland.MOISTURE,7),2);
                    world.setBlockToAir(pos.up());world.setBlockToAir(pos.east());world.setBlockToAir(pos.west());
                    if(block==Blocks.CACTUS||block==Blocks.REEDS)world.setBlockState(pos.down(),Blocks.SAND.getDefaultState(),2);
                    if(block==Blocks.REEDS)world.setBlockState(pos.down().west(),Blocks.WATER.getDefaultState(),2);
                    world.setBlockState(pos.east(2),Blocks.GLOWSTONE.getDefaultState(),2);world.setBlockState(pos,state,2);
                    if(world.getBlockState(pos).getBlock()!=block)continue;
                    for(int iteration=0;iteration<40;iteration++){IBlockState current=world.getBlockState(pos);if(current.getBlock()!=block)break;block.updateTick(world,pos,current,random);updates++;weather("query",world,pos.toLong());queries++;}
                    states++;
                }
            }
        }
        stage="inventory metadata and NBT matrix";
        for(Item item:Item.REGISTRY)for(int meta:new int[]{0,1,15,32767,-1})for(int nbt=0;nbt<3;nbt++) {
            ItemStack stack=new ItemStack(item,1,meta);
            if(nbt>0){
                NBTTagCompound tag=new NBTTagCompound();
                if(nbt==1)tag.setString("agri_seed","missing:removed_plant");else tag.setInteger("agri_seed",-1);
                tag.setInteger("agri_growth",-1);stack.setTagCompound(tag);
            }
            if(Loader.isModLoaded("skysrealisticweather")){
                Class.forName("zone.moddev.mc.skysrealisticweather.game.PlantItems").getMethod("resolve",ItemStack.class,net.minecraft.world.World.class,BlockPos.class).invoke(null,stack,world,pos);items++;
            }
        }
        System.out.println("QUALIFY_MATRIX plants="+plants.size()+" states="+states+" itemVariants="+items);
    }
}
