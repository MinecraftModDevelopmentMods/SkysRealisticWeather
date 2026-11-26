package zone.moddev.mc.skysrealisticweather.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import zone.moddev.mc.skysrealisticweather.CommonProxy;
import zone.moddev.mc.skysrealisticweather.admin.WeatherCommand;
import zone.moddev.mc.skysrealisticweather.admin.WeatherController;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;
import zone.moddev.mc.skysrealisticweather.network.WeatherMessage;
import zone.moddev.mc.skysrealisticweather.network.WeatherAdminMessage;

public final class ClientProxy extends CommonProxy {
    private NetHandlerPlayClient unitConnection;
    private zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit sentUnit;
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void unitPreference(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        Minecraft game = Minecraft.getMinecraft();
        if (game.getConnection() == null) { unitConnection = null; sentUnit = null; return; }
        if (game.player == null) return;
        zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit unit = zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration.temperatureUnit();
        if (unitConnection != game.getConnection() || sentUnit != unit) {
            zone.moddev.mc.skysrealisticweather.WeatherMod.NETWORK.sendToServer(new zone.moddev.mc.skysrealisticweather.network.TemperaturePreferenceMessage(unit));
            unitConnection = game.getConnection(); sentUnit = unit;
        }
    }

    @Override
    public net.minecraft.util.math.BlockPos weatherViewPosition(net.minecraft.world.World world) {
        Minecraft game = Minecraft.getMinecraft();
        net.minecraft.entity.Entity camera = game.getRenderViewEntity();
        return game.world != world || camera == null ? null : camera.getPosition();
    }

    @Override
    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new WeatherVisuals());
        MinecraftForge.EVENT_BUS.register(new DistantPrecipitationRenderer());
        MinecraftForge.EVENT_BUS.register(new ClimateHud());
        MinecraftForge.EVENT_BUS.register(new PlantTooltips());
        MinecraftForge.EVENT_BUS.register(new ThermometerHud());
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(zone.moddev.mc.skysrealisticweather.instrument.Thermometer.BLOCK), 0,
                new ModelResourceLocation(zone.moddev.mc.skysrealisticweather.instrument.Thermometer.BLOCK.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(WeatherController.BLOCK), 0,
                new ModelResourceLocation(WeatherController.BLOCK.getRegistryName(), "inventory"));
    }

    @Override
    public boolean showAdminItem() {
        net.minecraft.client.entity.EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.capabilities.isCreativeMode && WeatherCommand.permitted(player);
    }

    @Override
    public void receiveAdmin(WeatherAdminMessage message, MessageContext context) {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetHandlerPlayClient connection = context.getClientHandler();
        minecraft.addScheduledTask(() -> {
            if (minecraft.getConnection() != connection || !WeatherState.supports(minecraft.world)
                    || minecraft.player == null) return;
            if (minecraft.currentScreen instanceof WeatherAdminScreen) {
                ((WeatherAdminScreen) minecraft.currentScreen).receive(message);
            } else if (message.open) {
                minecraft.displayGuiScreen(new WeatherAdminScreen(message));
            }
        });
    }

    @Override public void receiveTemperatureZones(zone.moddev.mc.skysrealisticweather.network.TemperatureZoneMessage message, MessageContext context) {
        Minecraft game=Minecraft.getMinecraft();NetHandlerPlayClient connection=context.getClientHandler();
        game.addScheduledTask(() -> {
            if(game.getConnection()==connection&&game.world!=null&&game.world.provider.getDimension()==message.dimension)
                zone.moddev.mc.skysrealisticweather.game.TemperatureZones.receive(game.world,message);
        });
    }

    @Override
    public void receive(WeatherMessage message, MessageContext context) {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetHandlerPlayClient connection = context.getClientHandler();
        minecraft.addScheduledTask(() -> {
            if (minecraft.getConnection() != connection) return;
            WeatherState.client(message.settings != null && WeatherState.supports(minecraft.world)
                    ? new WeatherState.Frame(minecraft.world, message.settings, message.live,
                            message.episode, message.scheduled, null, message.seed) : null);
        });
    }
}
