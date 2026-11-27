package zone.moddev.mc.skysrealisticweather.client;

import java.util.Collections;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.BuildInfo;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;

public final class WeatherGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraft) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        return new ClimateModeScreen(parent);
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() { return ClimateModeScreen.class; }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) { return null; }

    @Override
    public boolean hasConfigGui() { return true; }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (BuildInfo.ID.equals(event.getModID())) WeatherConfiguration.saveEdits();
    }
}
