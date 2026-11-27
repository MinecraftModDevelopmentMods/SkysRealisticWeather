package zone.moddev.mc.skysrealisticweather;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import zone.moddev.mc.skysrealisticweather.admin.WeatherCommand;
import zone.moddev.mc.skysrealisticweather.admin.WeatherController;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.BiomeClimates;
import zone.moddev.mc.skysrealisticweather.game.ServerWeather;
import zone.moddev.mc.skysrealisticweather.game.SurfaceTracker;
import zone.moddev.mc.skysrealisticweather.network.WeatherMessage;
import zone.moddev.mc.skysrealisticweather.network.WeatherAdminMessage;

@Mod(modid = BuildInfo.ID, name = BuildInfo.NAME, version = BuildInfo.VERSION,
        acceptedMinecraftVersions = "[1.11.2]", acceptableRemoteVersions = "[" + BuildInfo.VERSION + "]",
        dependencies = "required-after:forge@[13.20.1.2588,);after:crafttweaker;after:toughasnails;after:waila;after:theoneprobe;after:jei",
        guiFactory = "zone.moddev.mc.skysrealisticweather.client.WeatherGuiFactory")
public final class WeatherMod {
    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(BuildInfo.ID);

    @SidedProxy(clientSide = "zone.moddev.mc.skysrealisticweather.client.ClientProxy",
            serverSide = "zone.moddev.mc.skysrealisticweather.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        WeatherConfiguration.load(event.getSuggestedConfigurationFile());
        zone.moddev.mc.skysrealisticweather.compat.Integrations.init();
        NETWORK.registerMessage(new WeatherMessage.Handler(), WeatherMessage.class, 0, Side.CLIENT);
        NETWORK.registerMessage(new WeatherAdminMessage.Handler(), WeatherAdminMessage.class, 1, Side.CLIENT);
        NETWORK.registerMessage(zone.moddev.mc.skysrealisticweather.network.TemperaturePreferenceMessage.Handler.class,
                zone.moddev.mc.skysrealisticweather.network.TemperaturePreferenceMessage.class, 2, Side.SERVER);
        NETWORK.registerMessage(zone.moddev.mc.skysrealisticweather.network.TemperatureZoneMessage.Handler.class,
                zone.moddev.mc.skysrealisticweather.network.TemperatureZoneMessage.class, 3, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(zone.moddev.mc.skysrealisticweather.game.TemperatureZones.INSTANCE);
        WeatherController.register();
        zone.moddev.mc.skysrealisticweather.instrument.Thermometer.register();
        MinecraftForge.EVENT_BUS.register(ServerWeather.INSTANCE);
        MinecraftForge.EVENT_BUS.register(SurfaceTracker.INSTANCE);
        zone.moddev.mc.skysrealisticweather.game.PlantGrowthHooks plants = zone.moddev.mc.skysrealisticweather.game.PlantGrowthHooks.INSTANCE;
        MinecraftForge.EVENT_BUS.register(plants);
        MinecraftForge.TERRAIN_GEN_BUS.register(plants);
        proxy.register();
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        BiomeClimates.freeze();
        zone.moddev.mc.skysrealisticweather.game.PlantProfiles.freeze();
        zone.moddev.mc.skysrealisticweather.game.PlantGrowthHooks.initialize();
        WeatherConfiguration.populateBiomeMappings();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new WeatherCommand());
    }

    @Mod.EventHandler
    public void serverStarted(net.minecraftforge.fml.common.event.FMLServerStartedEvent event) {
        zone.moddev.mc.skysrealisticweather.compat.ScriptRules.started();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerWeather.INSTANCE.close();
        zone.moddev.mc.skysrealisticweather.compat.ScriptRules.close();
    }
}
