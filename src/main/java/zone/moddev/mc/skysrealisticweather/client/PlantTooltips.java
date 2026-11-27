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
        return CropPresentation.lines(profile,sample,zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration.temperatureUnit());
    }
}
