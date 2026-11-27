package zone.moddev.mc.skysrealisticweather.verify.pack;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import io.netty.buffer.*;
import net.minecraft.init.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import zone.moddev.mc.skysrealisticweather.compat.*;
import zone.moddev.mc.skysrealisticweather.game.*;
import zone.moddev.mc.skysrealisticweather.network.WeatherMessage;
import zone.moddev.mc.skysrealisticweather.climate.*;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;

@Mod(modid="weatherpackprobe",name="Weather Pack Checks",version="1",dependencies="required-after:skysrealisticweather;after:MineTweaker3")
public final class PackSupportProbe {
    private static int checks;
    public static void require(boolean value,String name) { checks++;if(!value)throw new IllegalStateException(name); }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        if(!Boolean.getBoolean("pack.probe"))return;
        MinecraftServer server=FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world=server.worldServerForDimension(0);
        try {
            boolean reload=Boolean.getBoolean("pack.reload");
            String profile=System.getProperty("pack.profile","core");
            boolean combined=profile.equals("combined")||profile.equals("full");
            boolean disabled=Boolean.getBoolean("pack.disabled");
            if(profile.equals("tan")||combined)require(Loader.isModLoaded("ToughAsNails"),"requested legacy TAN is loaded");
            if(profile.equals("crafttweaker")||combined)require(Loader.isModLoaded("MineTweaker3"),"requested CraftTweaker is loaded");
            if(profile.equals("jei")||combined)require(Loader.isModLoaded("JEI"),"requested JEI is loaded");
            if(profile.equals("hwyla")||combined)require(Loader.isModLoaded("Waila"),"requested Hwyla is loaded");
            if(profile.equals("top")||combined)require(Loader.isModLoaded("theoneprobe"),"requested TOP is loaded");
            if(profile.equals("full"))for(String mod:new String[]{"harvestcraft","agricraft","mysticalagriculture","forestry"})require(Loader.isModLoaded(mod),"requested farming mod "+mod+" is loaded");
            if(disabled) {
                require(Integrations.status().values().stream().allMatch(value->value.equals("disabled")||value.equals("absent")),"optional bridges disabled independently of installed mods");
                require(ScriptRules.crops().isEmpty()&&ScriptRules.biomes().isEmpty(),"disabled script bridge leaves native rules");
            }
            if(!disabled&&Loader.isModLoaded("Waila"))Class.forName("zone.moddev.mc.skysrealisticweather.verify.pack.WailaChecks").getMethod("run",WorldServer.class).invoke(null,world);
            if(!disabled&&Loader.isModLoaded("theoneprobe"))Class.forName("zone.moddev.mc.skysrealisticweather.verify.pack.TopChecks").getMethod("run",WorldServer.class).invoke(null,world);
            if(!disabled&&Loader.isModLoaded("ToughAsNails"))Class.forName("zone.moddev.mc.skysrealisticweather.verify.pack.TanChecks").getMethod("run",WorldServer.class).invoke(null,world);
            if(!disabled&&Loader.isModLoaded("MineTweaker3")) {
                require(!ScriptRules.crops().isEmpty(),"startup scripts captured before gameplay");
                require(ServerWeather.INSTANCE.settings().farming.resolve("minecraft:wheat",Blocks.WHEAT.getDefaultState()).profile.exempt!=reload,"active script crop policy");
                require(ServerWeather.INSTANCE.settings().biomes.preset(Biomes.PLAINS).id.equals("tropical_monsoon"),"scripts override generated biome config");
                ByteBuf bytes=Unpooled.buffer();WeatherMessage decoded=new WeatherMessage();
                new WeatherMessage(WeatherState.frame(world)).toBytes(bytes);decoded.fromBytes(bytes);bytes.release();
                require(decoded.settings.farming.scriptProfiles().containsKey("minecraft:wheat"),"script crop policy reaches clients");
                if(!reload) {
                    Files.write(Paths.get("scripts/weather.zs"),("import mods.skysrealisticweather.Climate;\nClimate.setCropTemperatureCelsius(\"minecraft:wheat\",-10,0,10,20);\nClimate.mapBiome(\"minecraft:plains\",\"tropical_monsoon\");\n").getBytes(StandardCharsets.UTF_8));
                    Class.forName("minetweaker.MineTweakerImplementationAPI").getMethod("reload").invoke(null);
                    require(ScriptRules.pending(),"reload reported pending restart");
                    require(ServerWeather.INSTANCE.settings().farming.resolve("minecraft:wheat",Blocks.WHEAT.getDefaultState()).profile.exempt,"reload retained active rules");
                }
            }
            ServerWeather weather=ServerWeather.INSTANCE;
            require(weather.active(),"weather hooks remain active");
            customModeDefaults();
            java.util.Map<String,Object> report=zone.moddev.mc.skysrealisticweather.admin.PackDiagnostics.collect(weather);
            require(!((java.util.List<?>)report.get("biomes")).isEmpty(),"diagnostics enumerate biome registry");
            require(!((java.util.List<?>)report.get("resolvedCropStates")).isEmpty(),"diagnostics resolve crop rules");
            require(Files.size(zone.moddev.mc.skysrealisticweather.admin.PackDiagnostics.dump(weather,report))>100,"diagnostic JSON is written");
            if(reload) {
                require(weather.hasWorldOverrides(),"world overrides survive process reload");
                require(!weather.settings().fog&&weather.settings().profile.strength==.4F,"saved flags and strength restored");
                require(weather.settings().calendarOffset==92L*24000,"saved calendar offset restored");
                require(weather.previewKind()==null,"forced test weather is not persisted");
                weather.resetWorld();
                require(!weather.hasWorldOverrides()&&weather.settings().calendarOffset==0,"reset removes saved overrides");
                require(weather.settings().fog,"reset restores inherited pack flags");
            } else {
                long daylight=world.getWorldTime();
                weather.editOptions(weather.settings().passive,weather.settings().surface,weather.settings().foliage,false,weather.settings().scheduled);
                ClimateProfile p=weather.settings().profile;
                weather.editClimate(new ClimateProfile(.4F,p.period(0),p.period(1),p.period(2),p.period(3)));
                weather.setCalendar(weather.calendarTime()+92L*24000);
                require(world.getWorldTime()==daylight,"season adjustment preserves daylight clock");
                weather.force(WeatherKind.BLIZZARD,120,1,false);weather.saveWorld();
                require(weather.worldData().overrides().getKeySet().size()==3,"save stores only edited fields");
                require(!weather.worldData().overrides().hasKey("preset"),"unchanged preset remains inherited");
                weather.editOptions(false,true,true,true,true);weather.restore();
                require(!weather.settings().fog&&weather.settings().calendarOffset==92L*24000,"discard restores saved state");
                ByteBuf bytes=Unpooled.buffer();WeatherMessage decoded=new WeatherMessage();new WeatherMessage(WeatherState.frame(world)).toBytes(bytes);decoded.fromBytes(bytes);bytes.release();
                require(decoded.settings.calendarOffset==92L*24000,"calendar offset reaches clients");
                require(java.util.Arrays.equals(decoded.settings.phaseLabels(),weather.settings().phaseLabels()),"phase names reach clients");
            }
            server.saveAllWorlds(false);
            System.out.println("PACK_PASS checks="+checks);
        } catch(Throwable failure){System.out.println("PACK_FAIL "+failure);failure.printStackTrace();}
        finally {server.initiateShutdown();}
    }
    private static void customModeDefaults() throws Exception {
        java.io.File original=WeatherConfiguration.file().getConfigFile(),fixture=new java.io.File("custom-mode.cfg");
        Files.copy(original.toPath(),fixture.toPath(),StandardCopyOption.REPLACE_EXISTING);
        net.minecraftforge.common.config.Configuration custom=new net.minecraftforge.common.config.Configuration(fixture,"7");
        custom.getCategory("general").get("activePreset").set("custom");
        custom.getCategory("general").get("climateMode").set("PER_BIOME");
        custom.getCategory("general").get("strength").set("0.7");
        custom.getCategory("winter").get("temperature").set("-1.6");custom.getCategory("winter").get("days").set("123");custom.save();
        try {
            WeatherConfiguration.load(fixture);
            net.minecraft.nbt.NBTTagCompound fields=new net.minecraft.nbt.NBTTagCompound();fields.setString("mode","UNIVERSAL");
            WeatherSettings settings=WorldWeatherData.merge(WeatherConfiguration.startupSettings(),fields);
            require(settings.profile.period(3).days==123&&settings.profile.period(3).temperature==-1.6F&&settings.profile.strength==.7F,"mode switch restores unnamed Custom definition");
        } finally {WeatherConfiguration.load(original);}
    }
}
