package zone.moddev.mc.skysrealisticweather.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import org.lwjgl.input.Keyboard;
import zone.moddev.mc.skysrealisticweather.BuildInfo;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;

/** Preset list and selection for Forge's Mods configuration entry. */
public final class WeatherConfigScreen extends GuiScreen {
    private static final int USE = 10;
    private static final int BACK = 11;
    private static final int SETTINGS = 12;
    private static final int MANAGE = 13;
    private static final int NEW = 14;
    private static final int DUPLICATE = 15;
    private static final int EDIT = 16;
    private static final int EVENTS = 17;

    private final GuiScreen parent;
    private final boolean managing;
    private String selectedId;
    private PresetList presetList;

    public WeatherConfigScreen(GuiScreen parent) {
        this(parent, false, WeatherConfiguration.activePreset());
    }

    static GuiScreen settings(GuiScreen parent) {
        WeatherConfiguration.read();
        return new SettingsScreen(parent);
    }

    static GuiScreen manage(GuiScreen parent) {
        return new WeatherConfigScreen(parent, true, WeatherConfiguration.activePreset());
    }

    private WeatherConfigScreen(GuiScreen parent, boolean managing, String selectedId) {
        this.parent = parent;
        this.managing = managing;
        this.selectedId = selectedId;
    }

    @Override
    public void initGui() {
        int oldScroll = presetList == null ? 0 : presetList.getAmountScrolled();
        presetList = new PresetList();
        presetList.scrollBy(oldScroll);
        if (managing) {
            addButtons(new int[] {NEW, DUPLICATE, EDIT, EVENTS, BACK},
                    new int[] {42, 70, 42, 69, 44},
                    new String[] {"New", "Duplicate", "Edit", "Events", I18n.format("gui.back")});
        } else {
            addButtons(new int[] {USE, SETTINGS, MANAGE, BACK},
                    new int[] {72, 78, 98, 44},
                    new String[] {"Use Preset", "Edit Settings", "Manage Presets", I18n.format("gui.done")});
        }
        refreshButtons();
    }

    private void addButtons(int[] ids, int[] widths, String[] names) {
        int totalWidth = (widths.length - 1) * 4;
        for (int buttonWidth : widths) totalWidth += buttonWidth;
        int x = (width - totalWidth) / 2;
        for (int index = 0; index < ids.length; index++) {
            buttonList.add(new GuiButton(ids[index], x, height - 26, widths[index], 20, names[index]));
            x += widths[index] + 4;
        }
    }

    void selectPreset(String id) {
        selectedId = id;
        refreshButtons();
    }

    private Preset selected() {
        return WeatherConfiguration.preset(selectedId);
    }

    private void refreshButtons() {
        boolean hasSelection = selected() != null;
        for (GuiButton button : buttonList) {
            if (button.id == USE || button.id == DUPLICATE || button.id == EDIT || button.id == EVENTS) {
                button.enabled = hasSelection;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case USE:
                if (selected() != null) WeatherConfiguration.usePreset(selectedId);
                break;
            case BACK:
                mc.displayGuiScreen(parent);
                break;
            case SETTINGS:
                mc.displayGuiScreen(settings(this));
                break;
            case MANAGE:
                mc.displayGuiScreen(new WeatherConfigScreen(this, true, selectedId));
                break;
            case NEW:
                mc.displayGuiScreen(new PresetEditorScreen(this, WeatherConfiguration.defaultPreset(), false, "New Preset"));
                break;
            case DUPLICATE:
                Preset duplicate = selected();
                if (duplicate != null) {
                    String name = duplicate.label.substring(0, Math.min(59, duplicate.label.length())) + " Copy";
                    mc.displayGuiScreen(new PresetEditorScreen(this, duplicate, false, name));
                }
                break;
            case EDIT:
                Preset edit = selected();
                if (edit != null) mc.displayGuiScreen(new PresetEditorScreen(this, edit, true, edit.label));
                break;
            case EVENTS:
                Preset preset = selected();
                if (preset != null) {
                    mc.displayGuiScreen(new GuiConfig(this, Collections.singletonList(
                            new ConfigElement(WeatherConfiguration.eventRows(preset.id))),
                            BuildInfo.ID, false, true, preset.label + " Weather Events"));
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        presetList.handleMouseInput();
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            presetList.moveSelection(key == Keyboard.KEY_UP ? -1 : 1);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        presetList.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, managing ? "Manage Weather Presets" : BuildInfo.NAME,
                width / 2, 12, 0xFFFFFF);
        Preset active = WeatherConfiguration.preset(WeatherConfiguration.activePreset());
        String current = active == null ? "Custom" : active.label;
        drawCenteredString(fontRendererObj, fit("Active  " + current, width - 24),
                width / 2, 29, 0xD6E5F3);
        drawPreview();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPreview() {
        Preset preview = selected();
        int center = width / 2;
        int top = presetList.bottom + 7;
        if (preview != null) {
            drawCenteredString(fontRendererObj, fit(preview.label + "  |  " + preview.profile.daysInYear()
                    + " Minecraft days", width - 24), center, top, 0xFFFFFF);
            List<String> lines = fontRendererObj.listFormattedStringToWidth(preview.description, width - 28);
            for (int index = 0; index < Math.min(lines.size(), 2); index++) {
                String line = lines.get(index);
                if (index == 1 && lines.size() > 2) line = fit(line + "...", width - 28);
                drawCenteredString(fontRendererObj, line, center, top + 13 + index * 10, 0xD6E5F3);
            }
            drawPhasePair(preview, Season.SPRING, Season.SUMMER, top + 37);
            drawPhasePair(preview, Season.AUTUMN, Season.WINTER, top + 48);
        } else {
            String text = presetList.rows.isEmpty() ? "Create a preset in Manage Presets." : "Select a preset to preview it.";
            drawCenteredString(fontRendererObj, text, center, top + 18, 0xD6E5F3);
        }
        drawCenteredString(fontRendererObj, "Changes take effect after restarting Minecraft.",
                center, height - 38, 0xBBBBBB);
    }

    private void drawPhasePair(Preset preset, Season first, Season second, int y) {
        int column = Math.min((width - 24) / 2, 215);
        for (Season season : new Season[] {first, second}) {
            String phase = preset.phaseLabel(season) + " (" + preset.profile.period(season.ordinal()).days + " days)";
            int center = width / 2 + (season == first ? -column / 2 : column / 2);
            drawCenteredString(fontRendererObj, fit(phase, column - 4), center, y, 0xD6E5F3);
        }
    }

    private String fit(String text, int pixels) {
        if (fontRendererObj.getStringWidth(text) <= pixels) return text;
        return fontRendererObj.trimStringToWidth(text, pixels - fontRendererObj.getStringWidth("...")) + "...";
    }

    private final class PresetList extends GuiSlot {
        private final List<Row> rows = new ArrayList<>();

        PresetList() {
            super(WeatherConfigScreen.this.mc, WeatherConfigScreen.this.width, WeatherConfigScreen.this.height,
                    44, WeatherConfigScreen.this.height - 102, 20);
            Preset.Group previous = null;
            for (Preset preset : WeatherConfiguration.presets()) {
                if (preset.group != previous) {
                    rows.add(new Row(preset.group.label, null));
                    previous = preset.group;
                }
                rows.add(new Row(preset.label, preset));
            }
        }

        @Override
        protected int getSize() {
            return rows.size();
        }

        @Override
        public int getListWidth() {
            return Math.min(width - 38, 460);
        }

        @Override
        protected int getScrollBarX() {
            return width / 2 + getListWidth() / 2 + 6;
        }

        @Override
        protected void elementClicked(int row, boolean doubleClick, int mouseX, int mouseY) {
            Preset preset = rows.get(row).preset;
            if (preset != null) selectPreset(preset.id);
        }

        @Override
        protected boolean isSelected(int row) {
            Preset preset = rows.get(row).preset;
            return preset != null && preset.id.equals(selectedId);
        }

        @Override
        protected void drawBackground() {
        }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            Row row = rows.get(index);
            if (row.preset == null) {
                drawString(fontRendererObj, row.label, x + 4, y + 4, 0x9DC4E1);
                return;
            }
            boolean active = row.preset.id.equals(WeatherConfiguration.activePreset());
            int reserve = active ? fontRendererObj.getStringWidth("Active") + 18 : 12;
            drawString(fontRendererObj, fit(row.label, getListWidth() - reserve), x + 9, y + 4, 0xFFFFFF);
            if (active) drawString(fontRendererObj, "Active", x + getListWidth() - reserve + 8, y + 4, 0xB7DB98);
        }

        void moveSelection(int direction) {
            int selectedRow = direction > 0 ? -1 : rows.size();
            for (int index = 0; index < rows.size(); index++) {
                if (isSelected(index)) {
                    selectedRow = index;
                    break;
                }
            }
            for (int index = selectedRow + direction; index >= 0 && index < rows.size(); index += direction) {
                Preset preset = rows.get(index).preset;
                if (preset != null) {
                    selectPreset(preset.id);
                    int start = index * slotHeight + 4;
                    int view = getAmountScrolled();
                    int end = start + slotHeight;
                    if (start < view) scrollBy(start - view);
                    if (end > view + bottom - top) scrollBy(end - view - bottom + top);
                    break;
                }
            }
        }
    }

    private static final class Row {
        final String label;
        final Preset preset;

        Row(String label, Preset preset) {
            this.label = label;
            this.preset = preset;
        }
    }

    private static final class SettingsScreen extends GuiConfig {
        SettingsScreen(GuiScreen parent) {
            super(parent, elements(), BuildInfo.ID, false, true, BuildInfo.NAME);
        }

        private static List<IConfigElement> elements() {
            List<IConfigElement> elements = new ArrayList<>();
            add(elements, WeatherConfiguration.GENERAL, "General");
            if (WeatherConfiguration.mode() == ClimateMode.PER_BIOME) {
                add(elements, WeatherConfiguration.BIOMES, "Biome to climate mappings");
                add(elements, WeatherConfiguration.CALENDAR, "Shared calendar");
            }
            String[] labels = WeatherConfiguration.activePhaseLabels();
            for (Season season : Season.values()) {
                if (WeatherConfiguration.mode() != ClimateMode.PER_BIOME) {
                    add(elements, WeatherConfiguration.category(season), labels[season.ordinal()]);
                }
            }
            if (WeatherConfiguration.mode() != ClimateMode.PER_BIOME) add(elements, WeatherConfiguration.EVENTS, "Weather Events");
            add(elements, WeatherConfiguration.CLIENT, "Display and Climate HUD");
            add(elements, WeatherConfiguration.HEATING, "Local temperature zones and furnaces");
            add(elements, zone.moddev.mc.skysrealisticweather.climate.FarmingSettings.CATEGORY, "Plant Growth");
            return elements;
        }

        private static void add(List<IConfigElement> elements, String name, String title) {
            elements.add(new ConfigElement(WeatherConfiguration.file().getCategory(name)) {
                @Override public List<IConfigElement> getChildElements() {
                    List<IConfigElement> children = super.getChildElements();
                    for (int i = 0; i < children.size(); i++) {
                        IConfigElement child = children.get(i);
                        boolean profile = name.equals(zone.moddev.mc.skysrealisticweather.climate.FarmingSettings.CATEGORY) && child.getName().equals("profiles");
                        if (child.isProperty() && (profile || child.getName().equals("temperature"))) children.set(i,
                                new TemperatureConfigElement(WeatherConfiguration.file().getCategory(name).get(child.getName()), WeatherConfiguration.temperatureUnit(), profile));
                        else if(child.isProperty()&&name.equals(WeatherConfiguration.HEATING)&&child.getName().equals("furnaceMinimumCelsius"))
                            children.set(i,new TemperatureConfigElement(WeatherConfiguration.file().getCategory(name).get(child.getName()),WeatherConfiguration.temperatureUnit(),false,true));
                    }
                    return children;
                }
                @Override public String getName() { return title; }
                @Override public String getLanguageKey() { return title; }
            });
        }
    }
}
