package zone.moddev.mc.skysrealisticweather.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import zone.moddev.mc.skysrealisticweather.api.ClimateSample;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.ClimateReadout;

final class ClimateHud {
    private final KeyBinding toggle = new KeyBinding("key.skysrealisticweather.climateHud",
            Keyboard.KEY_NONE, "key.categories.skysrealisticweather");

    ClimateHud() {
        ClientRegistry.registerKeyBinding(toggle);
    }

    @SubscribeEvent
    public void keyInput(InputEvent.KeyInputEvent event) {
        while (toggle.isPressed()) WeatherConfiguration.toggleCompactClimate();
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Text event) {
        Minecraft game = Minecraft.getMinecraft();
        if (game.world == null || game.player == null || game.gameSettings.hideGUI || game.isReducedDebug()) return;
        boolean full = game.gameSettings.showDebugInfo;
        if (full ? !WeatherConfiguration.showF3Climate() : !WeatherConfiguration.showCompactClimate()) return;
        Entity camera = game.getRenderViewEntity();
        if (camera == null) return;
        BlockPos position = ClimateReadout.eyes(camera);
        ClimateSample climate = WeatherApi.getClimate(game.world, position).orElse(null);
        if (climate == null) return;
        LocalWeather local = WeatherApi.getWeather(game.world, position).get();
        zone.moddev.mc.skysrealisticweather.api.TemperatureSample temperature=WeatherApi.getTemperatureSample(game.world,position).orElse(null);
        if(temperature==null){event.getRight().add("Sky's Climate: local temperature unavailable");return;}
        if (full) {
            event.getRight().add("");
            event.getRight().add("Sky's Climate");
            for (String line : ClimateReadout.details(climate, local, WeatherApi.getWeatherEpisode(game.world, position).orElse(null))) {
                int width = Math.max(0, event.getResolution().getScaledWidth() / 2 - 8);
                event.getRight().add(game.fontRendererObj.trimStringToWidth(line, width));
            }
            event.getRight().add(game.fontRendererObj.trimStringToWidth(ClimateReadout.temperature(temperature,WeatherConfiguration.temperatureUnit()),
                    Math.max(0,event.getResolution().getScaledWidth()/2-8)));
        } else {
            event.getRight().add(ClimateReadout.compact(climate,new LocalWeather(local.precipitation,local.source,temperature.temperature,
                    local.fog,local.thunder,local.intensity,local.wind)));
        }
    }
}
