package zone.moddev.mc.skysrealisticweather.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import java.util.Collections;
import org.lwjgl.input.Keyboard;
import zone.moddev.mc.skysrealisticweather.BuildInfo;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;

/** Mode is selected before showing the settings relevant to that mode. */
public final class ClimateModeScreen extends GuiScreen {
    private final GuiScreen parent;

    public ClimateModeScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        int size = Math.min(300, width - 24);
        int x = (width - size) / 2;
        for (ClimateMode mode : ClimateMode.values()) {
            buttonList.add(new GuiButton(mode.ordinal(), x, 40 + mode.ordinal() * 26, size, 20,
                    (WeatherConfiguration.mode() == mode ? "> " : "") + mode.label));
        }
        buttonList.add(new GuiButton(10, x, height - 78, size, 20, "Configure selected mode"));
        buttonList.add(new GuiButton(11, x, height - 52, size / 2 - 2, 20, "Manage presets"));
        buttonList.add(new GuiButton(13, x + size / 2 + 2, height - 52, size / 2 - 2, 20, "Display / units"));
        buttonList.add(new GuiButton(12, x, height - 26, size, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id < ClimateMode.values().length) {
            WeatherConfiguration.useMode(ClimateMode.values()[button.id]);
            buttonList.clear();
            initGui();
        } else if (button.id == 10) {
            switch (WeatherConfiguration.mode()) {
                case UNIVERSAL: mc.displayGuiScreen(new WeatherConfigScreen(this)); break;
                case PER_BIOME: mc.displayGuiScreen(WeatherConfigScreen.settings(this)); break;
                case REAL_WORLD: mc.displayGuiScreen(new RealWorldScreen(this)); break;
            }
        } else if (button.id == 11) {
            mc.displayGuiScreen(WeatherConfigScreen.manage(this));
        } else if (button.id == 13) {
            mc.displayGuiScreen(new GuiConfig(this,
                    new ConfigElement(WeatherConfiguration.file().getCategory(WeatherConfiguration.CLIENT)).getChildElements(),
                    BuildInfo.ID, false, false, "Display and temperature units"));
        } else {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, BuildInfo.NAME, width / 2, 12, 0xFFFFFF);
        String help;
        switch (WeatherConfiguration.mode()) {
            case PER_BIOME: help = "Each biome uses its mapped climate. Configure biome mappings, the shared calendar and effects."; break;
            case REAL_WORLD: help = "Track the weather at a configured real-world location. Choose a fallback climate for unavailable data."; break;
            default: help = "Use one selected climate preset throughout the Overworld, with local biome and altitude adjustments."; break;
        }
        int y = 122;
        for (String line : fontRendererObj.listFormattedStringToWidth(help, Math.min(400, width - 24))) {
            drawCenteredString(fontRendererObj, line, width / 2, y, 0xD6E5F3);
            y += 10;
        }
        drawCenteredString(fontRendererObj, "Saved changes apply after restarting Minecraft.", width / 2, height - 92, 0xBBBBBB);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static final class RealWorldScreen extends GuiScreen {
        private final GuiScreen parent;

        RealWorldScreen(GuiScreen parent) {
            this.parent = parent;
        }

        @Override
        public void initGui() {
            String[] labels = {"Location and feed", "Fallback climate", "General settings", "Done"};
            for (int i = 0; i < labels.length; i++) {
                buttonList.add(new GuiButton(i, width / 2 - 100, 54 + i * 26, 200, 20, labels[i]));
            }
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            switch (button.id) {
                case 0:
                    mc.displayGuiScreen(new GuiConfig(this, Collections.singletonList(new ConfigElement(
                            WeatherConfiguration.file().getCategory(WeatherConfiguration.LIVE))),
                            BuildInfo.ID, false, true, "Real-world location"));
                    break;
                case 1: mc.displayGuiScreen(new WeatherConfigScreen(this)); break;
                case 2: mc.displayGuiScreen(WeatherConfigScreen.settings(this)); break;
                default: mc.displayGuiScreen(parent); break;
            }
        }

        @Override
        protected void keyTyped(char character, int key) {
            if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Real World Climate", width / 2, 20, 0xFFFFFF);
            drawCenteredString(fontRendererObj, "Uses Open-Meteo weather data for the selected location.", width / 2, 36, 0xD6E5F3);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
    }
}
