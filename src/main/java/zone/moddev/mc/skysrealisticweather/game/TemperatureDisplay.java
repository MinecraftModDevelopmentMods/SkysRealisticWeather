package zone.moddev.mc.skysrealisticweather.game;

import java.util.Locale;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;

/** Presentation only. Snow at 0.15 MC is 0 Celsius, using the live feed's 0.045 MC/C scale. */
public final class TemperatureDisplay {
    public static final String PLAYER_KEY = "SkysWeatherTemperatureUnit";
    public enum Unit {
        CELSIUS("°C"), FAHRENHEIT("°F");
        public final String symbol;
        Unit(String symbol) { this.symbol = symbol; }
        public double adjustment(double mc) { return mc / 0.045 * (this == FAHRENHEIT ? 1.8 : 1); }
        public double temperature(double mc) { return adjustment(mc - (double) zone.moddev.mc.skysrealisticweather.api.WeatherApi.SNOW_LINE) + (this == FAHRENHEIT ? 32 : 0); }
        public float fromTemperature(double degrees) { return (float) (fromAdjustment(degrees - (this == FAHRENHEIT ? 32 : 0)) + zone.moddev.mc.skysrealisticweather.api.WeatherApi.SNOW_LINE); }
        public float fromAdjustment(double degrees) { return (float) (degrees * 0.045 / (this == FAHRENHEIT ? 1.8 : 1)); }
        public String format(float mc) { return String.format(Locale.ROOT, "%.1f %s", temperature(mc), symbol); }
    }
    private TemperatureDisplay() { }
    public static String format(float mc) { return WeatherConfiguration.temperatureUnit().format(mc); }
    public static Unit forSender(ICommandSender sender) {
        if (sender.getCommandSenderEntity() instanceof EntityPlayer) {
            int value = sender.getCommandSenderEntity().getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getInteger(PLAYER_KEY);
            return value == Unit.FAHRENHEIT.ordinal() ? Unit.FAHRENHEIT : Unit.CELSIUS;
        }
        return WeatherConfiguration.temperatureUnit();
    }
}
