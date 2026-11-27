package zone.moddev.mc.skysrealisticweather.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import zone.moddev.mc.skysrealisticweather.admin.WeatherCommand;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.api.SeasonSnapshot;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.WeatherSettings;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;
import zone.moddev.mc.skysrealisticweather.network.WeatherAdminMessage;

/** Live operator view. Mutations are ordinary server commands with vanilla permission checks. */
public final class WeatherAdminScreen extends GuiScreen {
    private enum Page { WEATHER, CALENDAR, CLIMATE, EFFECTS, PRESETS }
    private static final int PAGE_SIZE = 5;
    private static final Season[] PHASES = Season.values();
    private static final WeatherKind[] EVENTS = WeatherKind.values();
    private static final String[] PHASE_FIELDS = {"Days", "Temperature", "Wetness", "Thunder", "Fog"};
    private final Map<Integer, Runnable> actions = new LinkedHashMap<>();
    private final Map<String, GuiTextField> fields = new LinkedHashMap<>();
    private final Map<String, String> draft = new LinkedHashMap<>();
    private WeatherAdminMessage snapshot;
    private Page page = Page.WEATHER;
    private Season phase = Season.SPRING;
    private WeatherKind event = WeatherKind.CONVECTIVE_SHOWER;
    private int eventPage;
    private int presetPage;
    private int panelWidth;
    private boolean dry;
    private String feedback = "";
    private boolean error;

    public WeatherAdminScreen(WeatherAdminMessage snapshot) {
        this.snapshot = snapshot;
        eventPage = event.ordinal() / PAGE_SIZE;
    }

    void receive(WeatherAdminMessage snapshot) {
        this.snapshot = snapshot;
        feedback = snapshot.feedback;
        error = snapshot.error;
        capture();
        if (!error) discardClimateDraft();
        rebuild();
    }

    @Override
    public void initGui() {
        capture();
        panelWidth = Math.min(312, width - 16);
        rebuild();
    }

    private void rebuild() {
        buttonList.clear();
        actions.clear();
        fields.clear();
        int cell = (panelWidth - 16) / 4;
        Page[] tabs = {Page.WEATHER, Page.CALENDAR, Page.CLIMATE, Page.EFFECTS};
        String[] names = {"Weather", "Calendar", "Climate", "Effects"};
        for (int index = 0; index < tabs.length; index++) {
            Page target = tabs[index];
            button(16 + cell * index, 40, cell - 3, names[index], () -> show(target))
                    .enabled = page != target && !(page == Page.PRESETS && target == Page.CLIMATE);
        }
        switch (page) {
            case WEATHER: buildWeather(cell); break;
            case CALENDAR: buildCalendar(cell); break;
            case CLIMATE: buildClimate(cell); break;
            case EFFECTS: buildEffects(); break;
            case PRESETS: buildPresets(); break;
        }
        button(16, 216, 78, "Save to world", () -> send("world save"));
        button(98, 216, 76, "Discard", () -> send("world discard"));
        button(panelWidth - 56, 216, 56, "Done", () -> mc.displayGuiScreen(null));

    }

    private void buildWeather(int cell) {
        button(16, 66, cell - 3, "Auto", () -> send("weather auto"));
        button(16 + cell, 66, cell - 3, "Clear", () -> play("clear"));
        button(16 + cell * 2, 66, cell - 3, "Rain", () -> play("rain"));
        button(16 + cell * 3, 66, cell - 3, "Thunder", () -> play("thunder"));
        int listWidth = (panelWidth - 24) * 56 / 100;
        int right = 21 + listWidth;
        int rightWidth = panelWidth - right;
        for (int row = 0; row < PAGE_SIZE && eventPage * PAGE_SIZE + row < EVENTS.length; row++) {
            WeatherKind choice = EVENTS[eventPage * PAGE_SIZE + row];
            String label = (choice == event ? "> " : "") + WeatherCommand.label(choice);
            shortButton(16, 91 + row * 18, listWidth, label, () -> {
                capture();
                event = choice;
                rebuild();
            });
        }
        pageButtons(16, 184, listWidth, eventPage, (EVENTS.length - 1) / PAGE_SIZE, direction -> eventPage += direction);
        field("seconds", right, 103, rightWidth, "90");
        field("peak", right, 134, rightWidth, "0.85");
        shortButton(right, 154, rightWidth, "Dry biomes " + onOff(dry), () -> {
            capture();
            dry = !dry;
            rebuild();
        });
        button(right, 177, rightWidth, "Start selected", () -> play(event.name().toLowerCase(Locale.ROOT)));
    }

    private void buildCalendar(int cell) {
        button(16, 66, 2 * cell - 3, "Previous phase", () -> send("season previous"));
        button(16 + 2 * cell, 66, 2 * cell - 3, "Next phase", () -> send("season next"));
        field("day", 16 + 3 * cell, 94, cell - 3, "1");
        for (Season season : PHASES) {
            int index = season.ordinal();
            int x = 16 + (index % 2) * 2 * cell;
            button(x, 116 + (index / 2) * 22, 2 * cell - 3, snapshot.phaseLabels[index],
                    () -> send("season set " + phaseId(season) + " " + value("day")));
        }
        field("advance", 16, 179, cell - 3, "1");
        button(16 + cell, 176, cell - 3, "Add days", () -> send("season advance " + value("advance")));
        String[] times = {"Dawn", "Noon", "Dusk", "Midnight"};
        for (int index = 0; index < times.length; index++) {
            String time = times[index];
            shortButton(16 + 2 * cell + (index % 2) * cell, 163 + (index / 2) * 19, cell - 3, time,
                    () -> send("time " + time.toLowerCase(Locale.ROOT)));
        }
    }

    private void buildClimate(int cell) {
        WeatherSettings settings = snapshot.climate.settings;
        button(16 + 2 * cell, 65, 2 * cell - 3, "Choose preset", () -> show(Page.PRESETS));
        field("strength", 16 + cell, 94, cell - 3, Float.toString(settings.profile.strength));
        button(16 + 2 * cell, 91, 2 * cell - 3, "Apply strength", () -> send("strength " + value("strength")));
        for (Season season : PHASES) {
            int index = season.ordinal();
            shortButton(16 + index * cell, 115, cell - 3, snapshot.phaseLabels[index], () -> {
                capture();
                phase = season;
                for (int value = 0; value < PHASE_FIELDS.length; value++) draft.remove(PHASE_FIELDS[value]);
                rebuild();
            }).enabled = phase != season;
        }
        ClimateProfile.Period period = settings.profile.period(phase.ordinal());
        float[] values = {period.days, (float) zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration.temperatureUnit().adjustment(period.temperature), period.wetness, period.thunder, period.fog};
        int column = (panelWidth - 16) / PHASE_FIELDS.length;
        for (int index = 0; index < values.length; index++) {
            field(PHASE_FIELDS[index], 16 + index * column, 148, column - 3,
                    index == 0 ? Integer.toString(period.days) : Float.toString(values[index]));
        }
        button(16, 176, panelWidth - 16, "Apply selected phase", () -> {
            StringBuilder command = new StringBuilder("phase ").append(phaseId(phase));
            for (String key : PHASE_FIELDS) {
                if (key.equals("Temperature")) {
                    zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit unit = zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration.temperatureUnit();
                    String input = value(key);
                    if (input.equals(Float.toString((float) unit.adjustment(period.temperature)))) command.append(' ').append(period.temperature);
                    else try {
                        float change = Float.parseFloat(input);
                        if (!Float.isFinite(change) || change < unit.adjustment(-2) || change > unit.adjustment(2)) throw new NumberFormatException();
                        command.append(' ').append(Math.max(-2, Math.min(2, unit.fromAdjustment(change))));
                    } catch (NumberFormatException invalid) {
                        feedback = "Temperature adjustment must be from " + unit.adjustment(-2) + " to " + unit.adjustment(2) + " " + unit.symbol;
                        error = true; return;
                    }
                } else command.append(' ').append(value(key));
            }
            send(command.toString());
        });
    }

    private void buildEffects() {
        button(16, 192, 140, "Reset world overrides", () -> send("world reset"));
        WeatherSettings settings = snapshot.climate.settings;
        int column = (panelWidth - 16) / 2;
        toggle(16, 67, column, "Passive mode", "passive", settings.passive);
        toggle(16 + column, 67, column, "Snow and ice", "surface", settings.surface);
        toggle(16, 93, column, "Foliage tint", "foliage", settings.foliage);
        toggle(16 + column, 93, column, "Fog", "fog", settings.fog);
        toggle(16, 119, column, "Event scheduler", "scheduled", snapshot.scheduled);
        button(16 + column, 119, column - 3, settings.mode == zone.moddev.mc.skysrealisticweather.api.ClimateMode.PER_BIOME
                ? "Per biome" : settings.mode == zone.moddev.mc.skysrealisticweather.api.ClimateMode.REAL_WORLD
                ? "Real world" : "Universal", () -> send("mode " +
                (settings.mode == zone.moddev.mc.skysrealisticweather.api.ClimateMode.PER_BIOME ? "universal" : "per_biome")));
        toggle(16, 145, column, "Plant growth", "plants", settings.farming.enabled);
        button(16, 171, panelWidth - 16, "Reset world overrides", () -> send("world reset"));
    }

    private void buildPresets() {
        List<Map.Entry<String, String>> choices = new ArrayList<>(snapshot.presets.entrySet());
        presetPage = Math.min(presetPage, Math.max(0, (choices.size() - 1) / PAGE_SIZE));
        for (int row = 0; row < PAGE_SIZE && presetPage * PAGE_SIZE + row < choices.size(); row++) {
            Map.Entry<String, String> choice = choices.get(presetPage * PAGE_SIZE + row);
            String label = (choice.getKey().equals(snapshot.preset) ? "> " : "") + choice.getValue();
            button(16, 66 + row * 22, panelWidth - 16, label, () -> {
                show(Page.CLIMATE);
                send("preset " + choice.getKey());
            });
        }
        int listWidth = (panelWidth - 24) * 2 / 3;
        pageButtons(16, 179, listWidth, presetPage, Math.max(0, (choices.size() - 1) / PAGE_SIZE),
                direction -> presetPage += direction);
        shortButton(22 + listWidth, 179, panelWidth - 22 - listWidth, "Back", () -> show(Page.CLIMATE));
    }

    private void pageButtons(int x, int y, int w, int pageNumber, int last, java.util.function.IntConsumer change) {
        shortButton(x, y, 26, "<", () -> flip(change, -1)).enabled = pageNumber > 0;
        shortButton(x + 29, y, Math.max(29, w - 58), (pageNumber + 1) + " / " + (last + 1), () -> {}).enabled = false;
        shortButton(x + w - 26, y, 26, ">", () -> flip(change, 1)).enabled = pageNumber < last;
    }

    private void flip(java.util.function.IntConsumer change, int direction) {
        capture();
        change.accept(direction);
        rebuild();
    }

    private void toggle(int x, int y, int w, String title, String key, boolean checked) {
        button(x, y, w - 3, title + " " + onOff(checked), () -> send("option " + key + " " + !checked));
    }

    private static String onOff(boolean checked) {
        return checked ? "on" : "off";
    }

    private static String phaseId(Season season) {
        return season.name().toLowerCase(Locale.ROOT);
    }

    private void show(Page next) {
        capture();
        page = next;
        rebuild();
    }

    private void play(String id) {
        String command = "weather " + id + " " + value("seconds");
        if (!"clear".equals(id)) command += " " + value("peak") + (dry ? " dry" : "");
        send(command);
    }

    private void send(String command) {
        if (mc.player == null || !WeatherCommand.permitted(mc.player)) {
            mc.displayGuiScreen(null);
            return;
        }
        feedback = "Applying...";
        error = false;
        mc.player.sendChatMessage("/" + WeatherCommand.NAME + " " + command);
    }

    private void capture() {
        fields.forEach((key, value) -> draft.put(key, value.getText()));
    }

    private void discardClimateDraft() {
        draft.remove("strength");
        for (String key : PHASE_FIELDS) draft.remove(key);
    }

    private String value(String key) {
        String value = fields.get(key).getText().trim();
        // GUI fields contain one numeric argument each. Keep missing or invalid values on the normal error path.
        return value.isEmpty() || value.indexOf(' ') >= 0 ? "?" : value;
    }

    private void field(String key, int x, int y, int width, String initial) {
        GuiTextField field = new GuiTextField(fields.size(), fontRenderer, x, y, width, 16);
        field.setMaxStringLength(16);
        field.setText(draft.getOrDefault(key, initial));
        fields.put(key, field);
    }

    private GuiButton button(int x, int y, int w, String label, Runnable action) {
        return button(x, y, w, 20, label, action);
    }

    private GuiButton shortButton(int x, int y, int w, String label, Runnable action) {
        return button(x, y, w, 17, label, action);
    }

    private GuiButton button(int x, int y, int w, int h, String label, Runnable action) {
        int id = actions.size();
        actions.put(id, action);
        GuiButton button = new GuiButton(id, x, y, w, h, fit(label, w - 7));
        buttonList.add(button);
        return button;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.enabled) actions.get(button.id).run();
    }

    @Override
    public void updateScreen() {
        if (!WeatherState.supports(mc.world) || mc.player == null || !WeatherCommand.permitted(mc.player)) {
            mc.displayGuiScreen(null);
            return;
        }
        fields.values().forEach(GuiTextField::updateCursorCounter);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        for (GuiTextField field : fields.values()) field.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_TAB && !fields.isEmpty()) {
            List<GuiTextField> entries = new ArrayList<>(fields.values());
            int focused = -1;
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index).isFocused()) focused = index;
                entries.get(index).setFocused(false);
            }
            entries.get((focused + 1) % entries.size()).setFocused(true);
            return;
        }
        for (GuiTextField field : fields.values()) {
            if (field.textboxKeyTyped(character, key)) return;
        }
        super.keyTyped(character, key);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(8, 7, 8 + panelWidth, 238, 0xED15202D);
        drawRect(8, 7, 8 + panelWidth, 9, 0xFF6DAAC7);
        drawString(fontRenderer, "Weather Controller", 16, 14, 0xFFFFFF);
        drawString(fontRenderer, fit(worldStatus(), panelWidth - 16), 16, 27, 0xC1DEEB);
        int cell = (panelWidth - 16) / 4;
        switch (page) {
            case WEATHER:
                int right = 21 + (panelWidth - 24) * 56 / 100;
                drawString(fontRenderer, "Seconds", right, 93, 0xC1DEEB);
                drawString(fontRenderer, "Intensity (0.01-1)", right, 124, 0xC1DEEB);
                break;
            case CALENDAR:
                drawString(fontRenderer, "Jump to a phase on day", 16, 98, 0xC1DEEB);
                break;
            case CLIMATE:
                String preset = snapshot.presets.getOrDefault(snapshot.preset, "Custom");
                drawString(fontRenderer, fit(preset, cell * 2 - 6), 16, 71, 0xC1DEEB);
                drawString(fontRenderer, "Strength", 16, 98, 0xC1DEEB);
                int column = (panelWidth - 16) / PHASE_FIELDS.length;
                for (int index = 0; index < PHASE_FIELDS.length; index++) {
                    drawString(fontRenderer, fit(index == 1 ? "Î”" + zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration.temperatureUnit().symbol : PHASE_FIELDS[index], column - 3), 16 + index * column, 137, 0xC1DEEB);
                }
                break;
            case EFFECTS:
                drawString(fontRenderer, "Changes apply to the whole Overworld.", 16, 148, 0xC1DEEB);
                break;
            default: break;
        }
        String visibleFeedback = fit(feedback, panelWidth - 16);
        drawString(fontRenderer, visibleFeedback, 16, 204, error ? 0xFFAAAA : 0xBDDDA8);
        fields.values().forEach(GuiTextField::drawTextBox);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (!visibleFeedback.equals(feedback) && mouseX >= 16 && mouseX <= panelWidth
                && mouseY >= 204 && mouseY < 213) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(feedback, panelWidth - 16), mouseX, mouseY);
        }
    }

    private String worldStatus() {
        if (mc.world == null || mc.player == null) return "";
        SeasonSnapshot season = WeatherApi.getSeason(mc.world).orElse(null);
        if (season == null) return "Waiting for world weather";
        WeatherEpisode episode = WeatherApi.getWeatherEpisode(mc.world).orElse(null);
        LocalWeather local = WeatherApi.getWeather(mc.world,
                new BlockPos(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ)).orElse(null);
        String condition = episode == null ? local == null || local.precipitation == LocalWeather.Precipitation.NONE
                ? "Clear" : local.precipitation.name().toLowerCase(Locale.ROOT) : WeatherCommand.label(episode.kind);
        if (episode != null && episode.forced) {
            long now = episode.time(mc.world.getWorldTime(), mc.world.getTotalWorldTime());
            condition += " " + (episode.endTick - now + 19) / 20 + "s";
        }
        return snapshot.phaseLabels[season.season.ordinal()] + " " + season.dayOfSeason + "/" + season.lengthInDays
                + "  |  " + (local == null ? "" : zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.format(local.temperature) + " | ") + condition;
    }

    private String fit(String value, int pixels) {
        if (fontRenderer.getStringWidth(value) <= pixels) return value;
        if (pixels < fontRenderer.getStringWidth("...")) return "";
        return fontRenderer.trimStringToWidth(value, pixels - fontRenderer.getStringWidth("...")) + "...";
    }
}
