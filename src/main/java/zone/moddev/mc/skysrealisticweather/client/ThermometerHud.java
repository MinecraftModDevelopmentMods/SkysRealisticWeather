package zone.moddev.mc.skysrealisticweather.client;

import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.game.ClimateReadout;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay;
import zone.moddev.mc.skysrealisticweather.instrument.Thermometer;

/** Uses vanilla GUI primitives and models; no extra texture or rendering dependency. */
public final class ThermometerHud {
    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        Minecraft game = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || game.player == null || game.world == null || game.gameSettings.hideGUI) return;
        boolean held = Thermometer.heldBy(game.player);
        BlockPos pos = held ? ClimateReadout.eyes(game.player) : null;
        RayTraceResult target = game.objectMouseOver;
        if (!held && target != null && target.typeOfHit == RayTraceResult.Type.BLOCK && target.getBlockPos() != null
                && game.player.getDistanceSq(target.getBlockPos()) <= 36 && game.world.isBlockLoaded(target.getBlockPos(), false)
                && game.world.getBlockState(target.getBlockPos()).getBlock() == Thermometer.BLOCK) pos = target.getBlockPos();
        if (pos == null) return;
        OptionalDouble reading = Thermometer.temperature(game.world, pos);
        int x = event.getResolution().getScaledWidth() - 98, y = event.getResolution().getScaledHeight() - 86;
        Gui.drawRect(x, y, x + 90, y + 38, 0xC0101822);
        Gui.drawRect(x + 5, y + 4, x + 15, y + 34, 0xFF72838E);
        Gui.drawRect(x + 7, y + 6, x + 13, y + 28, 0xFF17232D);
        Gui.drawRect(x + 6, y + 27, x + 14, y + 33, 0xFFD5E4ED);
        if (reading.isPresent()) {
            int level = Math.round(Thermometer.gauge((float) reading.getAsDouble()) * 22);
            Gui.drawRect(x + 8, y + 28 - level, x + 12, y + 28, 0xFFD5E4ED);
        }
        for (int mark = 0; mark <= 4; mark++) Gui.drawRect(x + 14, y + 6 + mark * 5, x + 17, y + 7 + mark * 5, 0xFFA9BBC7);
        game.fontRenderer.drawStringWithShadow(I18n.format("hud.skysrealisticweather.temperature"), x + 21, y + 4, 0xC9D8E2);
        game.fontRenderer.drawStringWithShadow(reading.isPresent() ? TemperatureDisplay.format((float) reading.getAsDouble()) : "--", x + 21, y + 16, 0xFFFFFF);
        game.fontRenderer.drawStringWithShadow(I18n.format(held ? "hud.skysrealisticweather.here" : "hud.skysrealisticweather.placed"), x + 21, y + 27, 0x91A5B4);
        GlStateManager.color(1, 1, 1, 1);
    }
    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack() == null || event.getItemStack().getItem() != Item.getItemFromBlock(Thermometer.BLOCK)) return;
        event.getToolTip().add(TextFormatting.GRAY + I18n.format("tooltip.skysrealisticweather.thermometer"));
        if (event.getEntityPlayer() == null) return;
        OptionalDouble value = Thermometer.temperature(event.getEntityPlayer().world, ClimateReadout.eyes(event.getEntityPlayer()));
        if (value.isPresent()) event.getToolTip().add(TextFormatting.AQUA + TemperatureDisplay.format((float) value.getAsDouble()));
    }
}
