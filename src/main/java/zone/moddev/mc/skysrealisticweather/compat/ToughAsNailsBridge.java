package zone.moddev.mc.skysrealisticweather.compat;

import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import toughasnails.api.config.*;
import toughasnails.api.stat.capability.ITemperature;
import toughasnails.api.temperature.*;
import toughasnails.handler.PacketHandler;
import toughasnails.network.message.MessageSyncConfigs;
import toughasnails.temperature.*;
import toughasnails.temperature.modifier.*;
import zone.moddev.mc.skysrealisticweather.api.TemperatureSample;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;

/** Native TAN target corrections: never sets body temperature or changes its response rate. */
public final class ToughAsNailsBridge {
    public static final String MODIFIER = "skysrealisticweather:environment";
    private static WorldServer world;
    private static SyncedConfig.SyncedConfigEntry seasonOption;
    private static String previousSeasons;
    private static boolean failed;
    private static final TemperatureDebugger DEBUG = new TemperatureDebugger();
    private static final BiomeModifier BIOME = new BiomeModifier(DEBUG);
    private static final AltitudeModifier ALTITUDE = new AltitudeModifier(DEBUG);
    private static final TimeModifier TIME = new TimeModifier(DEBUG);
    private static final SeasonModifier SEASON = new SeasonModifier(DEBUG);

    public static void register() throws ReflectiveOperationException {
        ITemperature.class.getMethod("applyModifier",String.class,int.class,int.class,int.class);
        ITemperature.class.getMethod("getExternalModifiers");
        ITemperature.class.getMethod("setExternalModifiers",Map.class);
        ITemperature.class.getMethod("hasModifier",String.class);
        MinecraftForge.EVENT_BUS.register(new ToughAsNailsBridge());
        Integrations.weatherChanged=ToughAsNailsBridge::changed;
        Integrations.worldClosed=ToughAsNailsBridge::close;
    }
    private static boolean active(WorldServer candidate) {
        WeatherState.Frame frame=WeatherState.frame(candidate);
        return !failed&&frame!=null&&frame.active();
    }
    private static void changed(WorldServer candidate) {
        if(failed)return;
        try {
            if(!active(candidate)){close();return;}
            world=candidate;
            if(seasonOption==null) {
                SyncedConfig.SyncedConfigEntry option=SyncedConfig.optionsToSync.get(SeasonsOption.ENABLE_SEASONS.getOptionName());
                if(option==null)throw new IllegalStateException("TAN seasons option is unavailable");
                seasonOption=option;previousSeasons=option.value;option.value="false";
                synchronize();
            }
        } catch(RuntimeException|LinkageError invalid){disable(invalid);}
    }
    private static void synchronize() {
        NBTTagCompound values=new NBTTagCompound();
        values.setString(SeasonsOption.ENABLE_SEASONS.getOptionName(),seasonOption.value);
        PacketHandler.instance.sendToAll(new MessageSyncConfigs(values));
    }
    public static void close() {
        try {
            if(world!=null)for(EntityPlayer player:world.playerEntities)try{remove(player);}
                catch(RuntimeException|LinkageError invalid){failed=true;Integrations.failed("toughasnails",invalid);}
            if(seasonOption!=null) {
                seasonOption.value=previousSeasons;
                if(world!=null)synchronize();
            }
        } catch(RuntimeException|LinkageError invalid) {
            failed=true;Integrations.failed("toughasnails",invalid);
        } finally {
            seasonOption=null;previousSeasons=null;world=null;
        }
    }
    private static void disable(Throwable failure) {
        try{close();}finally{failed=true;Integrations.failed("toughasnails",failure);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.START||event.player.world.isRemote)return;
        if(failed)return;
        if(event.player.ticksExisted%20==0)update(event.player);
    }
    public static void update(EntityPlayer player) {
        try {
            if(!(player.world instanceof WorldServer)||!active((WorldServer)player.world)){remove(player);return;}
            BlockPos pos=player.getPosition();
            for(BlockPos sample:new BlockPos[]{pos,pos.north(10),pos.south(10),pos.east(10),pos.west(10)})
                if(!player.world.isBlockLoaded(sample,false)){remove(player);return;}
            Optional<TemperatureSample> local=WeatherApi.getTemperatureSample(player.world,pos);
            ITemperature data=TemperatureHelper.getTemperatureData(player);
            if(!local.isPresent()||data==null){remove(player);return;}
            Temperature baseline=new Temperature(TemperatureScale.getScaleMidpoint());
            baseline=BIOME.modifyTarget(player.world,player,baseline);
            baseline=ALTITUDE.modifyTarget(player.world,player,baseline);
            baseline=TIME.modifyTarget(player.world,player,baseline);
            // Includes TAN's MID_SUMMER fallback contribution when its seasonal feature is disabled.
            baseline=SEASON.modifyTarget(player.world,player,baseline);
            int target=ambientTarget(local.get().temperature);
            int correction=target-baseline.getRawValue();
            // TAN refreshes an existing modifier's expiry but retains its old amount/rate.
            TemperatureModifier.ExternalModifier own=data.getExternalModifiers().get(MODIFIER);
            if(own!=null&&(own.getAmount()!=correction||own.getRate()!=0))remove(player);
            data.applyModifier(MODIFIER,correction,0,40);
        } catch(RuntimeException|LinkageError invalid){disable(invalid);}
    }
    public static int ambientTarget(float minecraftTemperature) {
        if(!Float.isFinite(minecraftTemperature))throw new IllegalArgumentException("Invalid ambient temperature");
        float normalized=Math.max(0,Math.min(1.35F,minecraftTemperature))/1.35F;
        return TemperatureScale.getScaleMidpoint()+Math.round((normalized*2-1)*BiomeModifier.MAX_TEMP_OFFSET);
    }
    private static void remove(EntityPlayer player) {
        ITemperature data=TemperatureHelper.getTemperatureData(player);
        if(data==null||!data.hasModifier(MODIFIER))return;
        Map<String,TemperatureModifier.ExternalModifier> remaining=new LinkedHashMap<>(data.getExternalModifiers());
        remaining.remove(MODIFIER);data.setExternalModifiers(remaining);
    }
}
