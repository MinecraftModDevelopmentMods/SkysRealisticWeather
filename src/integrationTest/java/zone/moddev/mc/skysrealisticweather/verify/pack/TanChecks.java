package zone.moddev.mc.skysrealisticweather.verify.pack;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import toughasnails.api.config.*;
import toughasnails.api.stat.capability.ITemperature;
import toughasnails.api.temperature.*;
import toughasnails.handler.season.SeasonHandler;
import toughasnails.season.SeasonTime;
import toughasnails.temperature.TemperatureHandler;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.compat.*;
import zone.moddev.mc.skysrealisticweather.game.*;
import static zone.moddev.mc.skysrealisticweather.verify.pack.PackSupportProbe.require;

public final class TanChecks {
    public static void run(WorldServer world) {
        require(Integrations.status().get("toughasnails").equals("active"),"TAN adapter initialized");
        require(!SyncedConfig.getBooleanValue(GameplayOption.ENABLE_SEASONS),"Sky owns TAN seasonal effects");
        EntityPlayerMP player=FakePlayerFactory.get(world,new GameProfile(UUID.fromString("1cfdf478-0b96-48bf-a8e4-f627f824c592"),"ClimateChecks"));
        BlockPos pos=new BlockPos(8,81,8);player.setPosition(pos.getX(),pos.getY(),pos.getZ());
        for(BlockPos p:new BlockPos[]{pos,pos.north(10),pos.south(10),pos.east(10),pos.west(10)})world.getChunkFromBlockCoords(p);
        ITemperature data=TemperatureHelper.getTemperatureData(player);
        require(data!=null,"native TAN capability attached");
        int body=TemperatureHandler.TEMPERATURE_SCALE_MIDPOINT;
        data.setTemperature(new Temperature(body));data.setChangeTime(37);data.applyModifier("other:heat",2,5,500);
        ResourceLocation zone=new ResourceLocation("packchecks","tan");
        WeatherApi.putTemperatureZone(world,TemperatureZone.celsius(zone,pos.add(-1,-1,-1),pos.add(1,1,1),-35));
        ToughAsNailsBridge.update(player);
        require(data.hasModifier(ToughAsNailsBridge.MODIFIER),"TAN climate modifier applied");
        int cold=data.getExternalModifiers().get(ToughAsNailsBridge.MODIFIER).getAmount();
        WeatherApi.putTemperatureZone(world,TemperatureZone.celsius(zone,pos.add(-1,-1,-1),pos.add(1,1,1),35));
        ToughAsNailsBridge.update(player);
        int warm=data.getExternalModifiers().get(ToughAsNailsBridge.MODIFIER).getAmount();
        require(warm>cold,"heated zones affect TAN target");
        require(data.getTemperature().getRawValue()==body&&data.getChangeTime()==37,"body temperature and response timer preserved");
        require(data.hasModifier("other:heat")&&data.getExternalModifiers().get("other:heat").getRate()==5,"other TAN modifiers preserved");
        require(data.getExternalModifiers().get(ToughAsNailsBridge.MODIFIER).getRate()==0,"ambient bridge adds no response-rate modifier");
        WeatherApi.removeTemperatureZone(world,zone);
        net.minecraft.world.World previousWorld=player.world;
        player.world=world.getMinecraftServer().worldServerForDimension(-1);ToughAsNailsBridge.update(player);
        require(!data.hasModifier(ToughAsNailsBridge.MODIFIER)&&data.hasModifier("other:heat"),"unsupported dimensions remove only Sky's ambient correction");
        player.world=previousWorld;ToughAsNailsBridge.update(player);
        require(data.hasModifier(ToughAsNailsBridge.MODIFIER),"returning to supported world restores correction");
        int previousSeason=SeasonHandler.getSeasonSavedData(world).seasonCycleTicks;
        SeasonHandler.getSeasonSavedData(world).seasonCycleTicks=SeasonTime.TOTAL_CYCLE_TICKS-1000;
        world.setBlockState(pos.down(),Blocks.FARMLAND.getDefaultState(),2);world.setBlockState(pos,Blocks.WHEAT.getDefaultState(),2);
        for(int i=0;i<100;i++)Blocks.WHEAT.updateTick(world,pos,world.getBlockState(pos),world.rand);
        require(world.getBlockState(pos).getBlock()==Blocks.WHEAT,"TAN winter does not destroy crops under Sky seasons");
        SeasonHandler.getSeasonSavedData(world).seasonCycleTicks=previousSeason;
        WeatherState.Frame frame=WeatherState.frame(world);
        WeatherState.server(new WeatherState.Frame(world,frame.settings.withOptions(true,true,true,true,true),null));
        Integrations.weatherChanged.accept(world);ToughAsNailsBridge.update(player);
        require(!data.hasModifier(ToughAsNailsBridge.MODIFIER)&&data.hasModifier("other:heat"),"passive mode removes only Sky modifier");
        require(SyncedConfig.getBooleanValue(GameplayOption.ENABLE_SEASONS)==Boolean.parseBoolean(
                SyncedConfig.optionsToSync.get(GameplayOption.ENABLE_SEASONS.getOptionName()).defaultValue),"native TAN seasons restored");
        WeatherState.server(frame);Integrations.weatherChanged.accept(world);
        require(!SyncedConfig.getBooleanValue(GameplayOption.ENABLE_SEASONS),"season ownership resumes when Sky resumes");
    }
}
