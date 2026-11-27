package zone.moddev.mc.skysrealisticweather.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;

/** A draft stays in this screen until the player saves it. */
final class PresetEditorScreen extends GuiScreen {
    private static final int SAVE = 10;
    private static final int CANCEL = 11;
    private static final String[] OVERVIEW_FIELDS = {"Name", "Description", "Strength"};
    private static final int[] OVERVIEW_LIMITS = {64, 240, 18};
    private static final String[] PHASE_FIELDS = {
            "Phase name", "Length (days)", "Temperature offset", "Precipitation multiplier", "Thunder multiplier", "Fog strength"
    };
    private static final int[] PHASE_LIMITS = {32, 4, 18, 18, 18, 18};
    private static final Season[] SEASONS = Season.values();

    private final zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit unit = WeatherConfiguration.temperatureUnit();
    private final WeatherConfigScreen parent;
    private final Preset template;
    private final boolean editing;
    private final String[][] draft = new String[SEASONS.length + 1][];
    private final List<GuiTextField> inputs = new ArrayList<>();
    private int page;
    private String error;

    PresetEditorScreen(WeatherConfigScreen parent, Preset template, boolean editing, String name) {
        this.parent = parent;
        this.template = template;
        this.editing = editing;
        draft[0] = new String[] {name, template.description, Float.toString(template.profile.strength)};
        for (Season season : SEASONS) {
            ClimateProfile.Period period = template.profile.period(season.ordinal());
            draft[season.ordinal() + 1] = new String[] {
                    template.phaseLabel(season), Integer.toString(period.days), Float.toString((float) unit.adjustment(period.temperature)),
                    Float.toString(period.wetness), Float.toString(period.thunder), Float.toString(period.fog)
            };
        }
    }

    @Override
    public void initGui() {
        capturePage();
        buildControls();
    }

    private void buildControls() {
        buttonList.clear();
        inputs.clear();
        int tab = Math.min(76, (width - 20) / draft.length);
        int start = (width - tab * draft.length) / 2;
        for (int index = 0; index < draft.length; index++) {
            String label = index == 0 ? "Overview" : draft[index][0].trim();
            if (label.isEmpty()) label = Preset.defaultPhaseLabel(SEASONS[index - 1]);
            if (fontRenderer.getStringWidth(label) > tab - 9) {
                label = fontRenderer.trimStringToWidth(label, tab - 9 - fontRenderer.getStringWidth("...")) + "...";
            }
            GuiButton button = new GuiButton(index, start + index * tab, 29, tab - 2, 20, label);
            button.enabled = index != page;
            buttonList.add(button);
        }
        int[] limits = page == 0 ? OVERVIEW_LIMITS : PHASE_LIMITS;
        int fieldWidth = Math.min(width / 2, 270);
        for (int index = 0; index < draft[page].length; index++) {
            GuiTextField field = new GuiTextField(index, fontRenderer, width / 2, 59 + index * 21, fieldWidth - 10, 16);
            field.setMaxStringLength(limits[index]);
            field.setText(draft[page][index]);
            inputs.add(field);
        }
        buttonList.add(new GuiButton(SAVE, width / 2 - 94, height - 26, 90, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(CANCEL, width / 2 + 4, height - 26, 90, 20, I18n.format("gui.cancel")));
    }

    private void capturePage() {
        for (int index = 0; index < inputs.size(); index++) draft[page][index] = inputs.get(index).getText();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id < draft.length) {
            capturePage();
            page = button.id;
            error = null;
            buildControls();
        } else if (button.id == SAVE) {
            capturePage();
            try {
                Preset updated = presetFromDraft();
                WeatherConfiguration.savePreset(updated);
                parent.selectPreset(updated.id);
                mc.displayGuiScreen(parent);
            } catch (IllegalArgumentException invalid) {
                error = invalid.getMessage();
            }
        } else if (button.id == CANCEL) {
            mc.displayGuiScreen(parent);
        }
    }

    private Preset presetFromDraft() {
        ClimateProfile.Period[] periods = new ClimateProfile.Period[SEASONS.length];
        String[] phaseLabels = new String[periods.length];
        for (Season season : SEASONS) {
            int index = season.ordinal();
            String[] values = draft[index + 1];
            String phase = Preset.defaultPhaseLabel(season) + " ";
            int days = (int) number(values[1], phase + "length", 1, 365, true);
            periods[index] = new ClimateProfile.Period(days,
                    values[2].equals(Float.toString((float) unit.adjustment(template.profile.period(index).temperature)))
                            ? template.profile.period(index).temperature
                            : Math.max(-2, Math.min(2, unit.fromAdjustment(number(values[2], phase + "temperature adjustment (" + unit.symbol + ")",
                                    (float) unit.adjustment(-2), (float) unit.adjustment(2), false)))),
                    number(values[3], phase + "precipitation", 0.25F, 4, false),
                    number(values[4], phase + "thunder", 0.25F, 4, false),
                    number(values[5], phase + "fog", 0, 1, false));
            phaseLabels[index] = values[0];
            if (phaseLabels[index].trim().isEmpty()) throw new IllegalArgumentException(phase + "phase name is required.");
        }
        String name = draft[0][0];
        String id = editing ? template.id : WeatherConfiguration.newPresetId(name);
        return new Preset(id, name, draft[0][1], editing ? template.group : Preset.Group.USER,
                new ClimateProfile(number(draft[0][2], "Strength", 0, 2, false), periods), phaseLabels, template.weather);
    }

    private static float number(String text, String label, float min, float max, boolean whole) {
        try {
            float value = whole ? Integer.parseInt(text.trim()) : Float.parseFloat(text.trim());
            if (Float.isFinite(value) && value >= min && value <= max) return value;
        } catch (NumberFormatException invalid) {
            // A blank or malformed input gets the same range hint as an out-of-range value.
        }
        throw new IllegalArgumentException(label + " must be " + (whole ? "a whole number " : "")
                + "from " + min + " to " + max + ".");
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : inputs) field.updateCursorCounter();
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        super.mouseClicked(x, y, button);
        for (GuiTextField field : inputs) field.mouseClicked(x, y, button);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (key == Keyboard.KEY_TAB) {
            int focused = -1;
            for (int index = 0; index < inputs.size(); index++) {
                if (inputs.get(index).isFocused()) {
                    focused = index;
                    inputs.get(index).setFocused(false);
                    break;
                }
            }
            inputs.get((focused + 1) % inputs.size()).setFocused(true);
        } else {
            for (GuiTextField field : inputs) field.textboxKeyTyped(character, key);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, editing ? "Edit Weather Preset" : "New Weather Preset", width / 2, 12, 0xFFFFFF);
        String[] labels = page == 0 ? OVERVIEW_FIELDS : PHASE_FIELDS;
        for (int index = 0; index < inputs.size(); index++) {
            String label = labels[index] + (page != 0 && index == 2 ? " (" + unit.symbol + ")" : "");
            drawString(fontRenderer, label, width / 2 - fontRenderer.getStringWidth(label) - 10, 63 + index * 21, 0xD6E5F3);
            inputs.get(index).drawTextBox();
        }
        if (error != null) drawCenteredString(fontRenderer, error, width / 2, height - 39, 0xFFAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
