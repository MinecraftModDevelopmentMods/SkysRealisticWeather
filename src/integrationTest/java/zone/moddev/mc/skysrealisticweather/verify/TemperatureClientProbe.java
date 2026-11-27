package zone.moddev.mc.skysrealisticweather.verify;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.WeatherMod;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay;
import zone.moddev.mc.skysrealisticweather.instrument.Thermometer;
import zone.moddev.mc.skysrealisticweather.network.TemperaturePreferenceMessage;

public final class TemperatureClientProbe {
    private int stage=-1,ack=-1,ready,attempts,reconnects;
    private String reading="";private boolean connecting,complete,clicked;
    TemperatureClientProbe(){MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void chat(ClientChatReceivedEvent event) {
        String text=event.getMessage().getUnformattedText();
        if(text.startsWith("WEATHER_THERMAL_STAGE:")) {
            int next=Integer.parseInt(text.substring(text.indexOf(':')+1));
            if(next!=stage){stage=next;ready=0;clicked=false;reading="";}
        } else if(text.startsWith("Temperature:"))reading=text;
        else if(text.equals("WEATHER_THERMAL_COMPLETE"))complete=true;
    }
    void tick(Minecraft game) {
        if(++attempts%100==0)System.out.println("THERMAL_STATE stage="+stage+" ready="+ready+" screen="+(game.currentScreen==null?"none":game.currentScreen.getClass().getSimpleName())+" pos="+(game.player==null?"none":game.player.getPosition()));
        if(complete){System.out.println("THERMAL_CLIENT PASS "+game.getSession().getUsername()+" reconnects="+reconnects);game.shutdown();return;}
        if(game.world==null) {
            if(game.currentScreen instanceof GuiMainMenu || game.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected) {
                if(!connecting){connecting=true;reconnects++;game.displayGuiScreen(new GuiConnecting(new GuiMainMenu(),game,"127.0.0.1",Integer.getInteger("skyweather.port")));}
            }
            return;
        }
        connecting=false;if(game.player==null||stage<0||stage==ack||game.currentScreen!=null)return;
        boolean guest=game.player.getName().endsWith("2");TemperatureDisplay.Unit unit=guest?TemperatureDisplay.Unit.FAHRENHEIT:TemperatureDisplay.Unit.CELSIUS;
        if(++ready==1){WeatherConfiguration.file().getCategory(WeatherConfiguration.CLIENT).get("temperatureUnit").set(unit.name());WeatherMod.NETWORK.sendToServer(new TemperaturePreferenceMessage(unit));}
        if(ready<20)return;
        BlockPos pos=game.player.getPosition();Optional<TemperatureSample> sample=WeatherApi.getTemperatureSample(game.world,pos);
        if(!sample.isPresent())return;TemperatureSample value=sample.get();
        if(ready%100==0)System.out.println("THERMAL_WAIT stage="+stage+" pos="+pos+" source="+value.source+" value="+value.temperature+" ambient="+value.ambientTemperature+" reading="+reading);
        TemperatureSample.Source expected=stage==0||stage==1||stage==9||stage==7&&!guest?TemperatureSample.Source.ZONE:
                stage==3||stage==5?TemperatureSample.Source.TORCH:stage==4?TemperatureSample.Source.FURNACE:TemperatureSample.Source.AMBIENT;
        if(value.source!=expected)return;
        if(expected==TemperatureSample.Source.ZONE) {
            float target=stage==0?25:stage==1?-15:17;
            if(Math.abs(TemperatureDisplay.Unit.CELSIUS.temperature(value.temperature)-target)>.01)return;
        }
        if(expected==TemperatureSample.Source.TORCH && Math.abs(TemperatureDisplay.Unit.CELSIUS.adjustment(value.temperature-value.ambientTemperature)-3)>.02)return;
        if(expected==TemperatureSample.Source.FURNACE && Math.abs(TemperatureDisplay.Unit.CELSIUS.adjustment(value.temperature-value.ambientTemperature)-16)>.02)return;
        WeatherProbe.require(Thermometer.heldBy(game.player),"ordinary player holds instrument");
        WeatherProbe.require(Math.abs(Thermometer.temperature(game.world,pos).getAsDouble()-value.temperature)<.00001,"thermometer matches synchronized local sample");
        if(stage==1) {
            Optional<PlantGrowthSample> crop=WeatherApi.getPlantGrowth(game.world,new BlockPos(8,81,9));if(!crop.isPresent()||crop.get().naturalGrowthMultiplier!=0)return;
            List<String> lines=new ArrayList<>();MinecraftForge.EVENT_BUS.post(new ItemTooltipEvent(new ItemStack(Items.WHEAT_SEEDS),game.player,lines,false));
            WeatherProbe.require(lines.toString().contains(unit.symbol)&&lines.toString().toLowerCase(Locale.ROOT).contains("paused"),"crop tooltip uses cold policy and personal display units");
            if(!clicked){clicked=true;game.playerController.processRightClickBlock(game.player,game.world,new BlockPos(7,81,8),EnumFacing.UP,new Vec3d(7.5,81.5,8.5),EnumHand.MAIN_HAND);return;}
            if(!reading.contains(guest?"5.0 °F":"-15.0 °C"))return;
        }
        if(stage==7 && (guest?pos.getX()>20:pos.getX()<160))return;
        if(stage==8 && (!guest && game.world.isBlockLoaded(new BlockPos(168,81,168),false)))return;
        if(stage==9 && guest && reconnects<2)return;
        System.out.println("THERMAL_CLIENT_STAGE PASS "+game.player.getName()+" stage="+stage+" source="+value.source+" reading="+unit.format(value.temperature));
        game.player.sendChatMessage("/weatherprobeack "+stage);ack=stage;
    }
}
