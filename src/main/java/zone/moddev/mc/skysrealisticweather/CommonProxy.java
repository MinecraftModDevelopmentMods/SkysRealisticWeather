package zone.moddev.mc.skysrealisticweather;

import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import zone.moddev.mc.skysrealisticweather.network.WeatherAdminMessage;
import zone.moddev.mc.skysrealisticweather.network.WeatherMessage;

public class CommonProxy {
    public net.minecraft.util.math.BlockPos weatherViewPosition(net.minecraft.world.World world) {
        return null;
    }

    public void register() {
    }

    public void receive(WeatherMessage message, MessageContext context) {
    }

    public void receiveAdmin(WeatherAdminMessage message, MessageContext context) {
    }
    public void receiveTemperatureZones(zone.moddev.mc.skysrealisticweather.network.TemperatureZoneMessage message, MessageContext context) { }

    public boolean showAdminItem() {
        return false;
    }
}
