package zone.moddev.mc.skysrealisticweather.game;

import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.event.world.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.*;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.TemperatureZoneIndex.Entry;
import zone.moddev.mc.skysrealisticweather.network.TemperatureZoneMessage;

/** All operations run on the owning world's logical thread; client and server stores are separate. */
public final class TemperatureZones {
    public static final TemperatureZones INSTANCE = new TemperatureZones();
    private static final Map<World,State> SERVER = new WeakHashMap<>(), CLIENT = new WeakHashMap<>();
    private static final class State {
        final TemperatureZoneIndex index = new TemperatureZoneIndex();
        final Map<ResourceLocation,Long> leases = new HashMap<>();
        boolean enabled = WeatherConfiguration.heatingEnabled();
        boolean furnaceLimitReported;
    }
    private static State state(World world,boolean create) {
        Map<World,State> map=world.isRemote?CLIENT:SERVER;
        State state=map.get(world);if(state==null&&create){state=new State();map.put(world,state);}return state;
    }
    private static void serverThread(World world) {
        if(!(world instanceof WorldServer)||!world.getMinecraftServer().isCallingFromMinecraftThread())
            throw new IllegalStateException("Temperature zones must be changed on the server world's logical thread");
    }
    public static boolean put(World world,TemperatureZone zone) {
        serverThread(world);Objects.requireNonNull(zone);
        if(zone.id.getResourceDomain().equals(BuildInfo.ID)&&zone.id.getResourcePath().startsWith("furnace/"))
            throw new IllegalArgumentException("The furnace/ zone IDs in this mod's namespace are reserved");
        return state(world,true).index.put(new Entry(zone,null));
    }
    public static boolean remove(World world,ResourceLocation id) {
        serverThread(world);State state=state(world,false);return state!=null&&state.index.remove(Objects.requireNonNull(id));
    }
    public static Optional<TemperatureSample> sample(World world,BlockPos pos) {
        if(world==null||pos==null||!world.isBlockLoaded(pos,false))return Optional.empty();
        WeatherState.Frame frame=WeatherState.frame(world);
        float ambient=frame==null?world.getBiome(pos).getFloatTemperature(pos):frame.temperature(world.getBiome(pos),pos);
        if(!Float.isFinite(ambient))return Optional.empty();
        if(frame!=null&&frame.active()&&frame.settings.farming.enabled&&frame.settings.farming.forestry) {
            Float controlled=PlantGrowthHooks.controlledTemperature(world,pos);
            if(controlled!=null)return Optional.of(new TemperatureSample(ambient,controlled,TemperatureSample.Source.FORESTRY,null));
        }
        State state=state(world,false);
        if(state!=null&&state.enabled)for(Entry entry:state.index.at(pos)) {
            TemperatureZone z=entry.zone;
            if(!z.contains(pos))continue;
            if(entry.furnace!=null&&(!world.isBlockLoaded(entry.furnace,false)||world.getBlockState(entry.furnace).getBlock()!=Blocks.LIT_FURNACE))continue;
            float value=z.mode==TemperatureZone.Mode.MINIMUM?Math.max(ambient,z.temperature):z.temperature;
            return Optional.of(new TemperatureSample(ambient,value,entry.furnace==null?TemperatureSample.Source.ZONE:TemperatureSample.Source.FURNACE,z.id));
        }
        return Optional.of(new TemperatureSample(ambient,ambient,TemperatureSample.Source.AMBIENT,null));
    }
    /** Attached only to the vanilla furnace's existing tick, not to general block changes/world generation. */
    public static void furnaceTick(TileEntityFurnace furnace) {
        World world=furnace.getWorld();BlockPos pos=furnace.getPos();
        if(world==null||world.isRemote||Math.floorMod(world.getTotalWorldTime()+pos.toLong(),20)!=0
                ||!WeatherConfiguration.heatingEnabled()||WeatherConfiguration.furnaceHeatRadius()==0)return;
        ResourceLocation id=new ResourceLocation(BuildInfo.ID,"furnace/"+pos.getX()+"/"+pos.getY()+"/"+pos.getZ());
        State state=state(world,false);
        if(!furnace.isBurning()||world.getBlockState(pos).getBlock()!=Blocks.LIT_FURNACE) { if(state!=null){state.index.remove(id);state.leases.remove(id);}return; }
        state=state(world,true);int radius=WeatherConfiguration.furnaceHeatRadius();
        BlockPos min=new BlockPos(pos.getX()-radius,Math.max(0,pos.getY()-radius),pos.getZ()-radius);
        BlockPos max=new BlockPos(pos.getX()+radius,Math.min(255,pos.getY()+radius),pos.getZ()+radius);
        try {
            state.index.put(new Entry(new TemperatureZone(id,min,max,WeatherConfiguration.furnaceHeatTemperature(),-100,TemperatureZone.Mode.MINIMUM),pos));
        } catch(IllegalArgumentException limit) {
            if(!state.furnaceLimitReported){org.apache.logging.log4j.LogManager.getLogger("Sky's Realistic Weather").warn("Furnace heating skipped because a chunk is at the temperature-zone limit.");state.furnaceLimitReported=true;}
            return;
        }
        state.leases.put(id,world.getTotalWorldTime()+40);
    }
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.world instanceof WorldServer))return;
        State state=state(event.world,false);if(state==null)return;
        if(event.world.getTotalWorldTime()%20==0) {
            Iterator<Map.Entry<ResourceLocation,Long>> leases=state.leases.entrySet().iterator();
            while(leases.hasNext()){Map.Entry<ResourceLocation,Long> lease=leases.next();if(lease.getValue()<event.world.getTotalWorldTime()){state.index.remove(lease.getKey());leases.remove();}}
        }
        WorldServer world=(WorldServer)event.world;Iterator<Long> dirty=state.index.dirty.iterator();int budget=64;
        while(dirty.hasNext()&&budget-->0){long key=dirty.next();dirty.remove();int x=(int)key,z=(int)(key>>32);
            TemperatureZoneMessage message=null;
            for(net.minecraft.entity.player.EntityPlayer player:world.playerEntities) {
                EntityPlayerMP target=(EntityPlayerMP)player;
                if(world.getPlayerChunkMap().isPlayerWatchingChunk(target,x,z)) {
                    if(message==null)message=new TemperatureZoneMessage(world.provider.getDimension(),x,z,state.enabled,state.index.snapshot(x,z));
                    WeatherMod.NETWORK.sendTo(message,target);
                }
            }
        }
    }
    @SubscribeEvent public void watch(ChunkWatchEvent.Watch event) {
        World world=event.getPlayer().world;State state=state(world,true);ChunkPos p=event.getChunk();
        WeatherMod.NETWORK.sendTo(new TemperatureZoneMessage(world.provider.getDimension(),p.chunkXPos,p.chunkZPos,state.enabled,state.index.snapshot(p.chunkXPos,p.chunkZPos)),event.getPlayer());
    }
    @SubscribeEvent public void unwatch(ChunkWatchEvent.UnWatch event) {
        World world=event.getPlayer().world;State state=state(world,false);ChunkPos p=event.getChunk();
        WeatherMod.NETWORK.sendTo(new TemperatureZoneMessage(world.provider.getDimension(),p.chunkXPos,p.chunkZPos,state==null?WeatherConfiguration.heatingEnabled():state.enabled,Collections.emptyList()),event.getPlayer());
    }
    public static void receive(World world,TemperatureZoneMessage message) {
        if(!world.isRemote||world.provider.getDimension()!=message.dimension)return;
        State state=state(world,true);state.enabled=message.enabled;state.index.receive(message.x,message.z,message.entries);
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) { (event.getWorld().isRemote?CLIENT:SERVER).remove(event.getWorld()); }
}
