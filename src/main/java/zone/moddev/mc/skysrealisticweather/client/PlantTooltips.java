package zone.moddev.mc.skysrealisticweather.client;

import java.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.FarmingSettings;
import zone.moddev.mc.skysrealisticweather.game.*;

/** Client-registered Forge tooltips; common types also allow headless contract checks. */
public final class PlantTooltips {
    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        if (event.getEntityPlayer() == null) return;
        World world = event.getEntityPlayer().world;
        BlockPos pos = event.getEntityPlayer().getPosition();
        if (!world.isBlockLoaded(pos, false)) return;
        List<PlantItems.Plant> plants = PlantItems.resolve(event.getItemStack(), world, pos);
        if (plants.isEmpty()) return;
        WeatherState.Frame frame = WeatherState.frame(world);
        if (frame == null) {
            event.getToolTip().add(TextFormatting.GRAY + "Sky's Weather: local climate unavailable here");
            return;
        }
        Set<List<String>> choices = new LinkedHashSet<>();
        for (PlantItems.Plant plant : plants) {
            FarmingSettings.Resolved resolved = frame.settings.farming.resolve(plant.id, plant.state);
            PlantGrowthSample sample = PlantGrowthHooks.sample(world, pos, plant.state, plant.id, plant.magical);
            choices.add(lines(resolved == null ? null : resolved.profile, sample));
        }
        if (choices.size() == 1) event.getToolTip().addAll(choices.iterator().next());
        else event.getToolTip().add(TextFormatting.GRAY + "Sky's Weather: several crops produce this item; check their seeds");
    }

    public static List<String> lines(PlantGrowthProfile profile, PlantGrowthSample sample) {
        List<String> lines = new ArrayList<>();
        if (profile != null && !profile.exempt) {
            lines.add(TextFormatting.DARK_AQUA + "Preferred temperature: " + TemperatureDisplay.format(profile.idealMinimum) + " to " + TemperatureDisplay.format(profile.idealMaximum));
            lines.add(TextFormatting.GRAY + "Growth limits: " + TemperatureDisplay.format(profile.minimum) + " / " + TemperatureDisplay.format(profile.maximum));
        }
        if (sample == null) { lines.add(TextFormatting.GRAY + "Sky's Weather: local climate unavailable here"); return lines; }
        if (sample.plantId.startsWith("agricraft:")) lines.add(0, TextFormatting.GRAY + "AgriCraft crop-stick growth");
        TextFormatting colour = TextFormatting.GRAY;
        String status;
        switch (sample.reason) {
            case DISABLED: status = "Climate growth rules disabled; native growth"; break;
            case ADAPTER_DISABLED: status = "Plant integration disabled; native growth"; break;
            case EXEMPT: status = "No climate temperature limit (exempt)"; break;
            case UNMAPPED: status = "No temperature profile; native growth"; break;
            default:
                if (sample.naturalGrowthMultiplier <= 0) {
                    colour = TextFormatting.RED;
                    status = sample.reason == PlantGrowthSample.Reason.COLD ? "Too cold; natural growth paused" : "Too hot; natural growth paused";
                } else if (sample.naturalGrowthMultiplier >= 1) {
                    colour = TextFormatting.GREEN; status = "Suitable; normal natural growth";
                } else {
                    colour = TextFormatting.YELLOW;
                    float percent = sample.naturalGrowthMultiplier * 100;
                    String rate = percent < 1 ? "<1" : percent > 99 ? ">99" : "about " + Math.round(percent);
                    status = (sample.reason == PlantGrowthSample.Reason.COLD ? "Cool" : "Warm") + "; " + rate + "% natural growth";
                }
        }
        lines.add(colour + "Here: " + TemperatureDisplay.format(sample.temperature) + (sample.controlledClimate ? " (controlled climate)" : "") + " - " + status);
        lines.add(TextFormatting.GRAY + "Temperature check only; soil, light and native conditions still apply");
        return lines;
    }
}
