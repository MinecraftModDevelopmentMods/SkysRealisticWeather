package zone.moddev.mc.skysrealisticweather.verify;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.admin.WeatherController;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.ClimateSample;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.api.SeasonSnapshot;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.client.ClientWeatherHooks;
import zone.moddev.mc.skysrealisticweather.client.WeatherAdminScreen;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.core.HookStatus;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;

public final class ClientProbe extends WeatherProbe.SideProbe {
    private final EnumSet<Season> captured = EnumSet.noneOf(Season.class);
    private int menu;
    private int readyTicks;
    private Season lastSeason;
    private boolean pendingPicture;
    private int adminStage;
    private int adminTicks;
    private int climateStage;
    private String climatePicture;
    private int modeUiStage;
    private GuiScreen modeRoot;

    @Override
    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) throws ReflectiveOperationException {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft game = Minecraft.getMinecraft();
        if (Boolean.getBoolean("skyweather.biomeNetwork")) {
            biomeNetwork(game);
            return;
        }
        if (Boolean.getBoolean("skyweather.modeUi")) {
            modeUi(game);
            return;
        }
        if (game.world == null) {
            if (menu == 0 && game.currentScreen instanceof GuiMainMenu) {
                verifyConfig(game);
                menu++;
            } else if (menu == 1 && !pendingPicture) {
                menu++;
                int port = Integer.getInteger("skyweather.port", 0);
                if (port == 0) {
                    System.out.println("SKY_WEATHER_CHECK CLIENT_MENU PASS");
                    game.shutdown();
                } else {
                    game.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), game, "127.0.0.1", port));
                }
            } else if (captured.contains(Season.WINTER) && game.currentScreen != null) {
                System.out.println("SKY_WEATHER_CHECK CLIENT_NETWORK PASS");
                game.shutdown();
            } else if (adminStage == 6 && game.currentScreen != null) {
                System.out.println("SKY_WEATHER_CHECK CLIENT_ADMIN PASS");
                game.shutdown();
            }
            return;
        }
        if (Boolean.getBoolean("skyweather.adminProbe")) {
            admin(game);
            return;
        }
        SeasonSnapshot snapshot = WeatherApi.getSeason(game.world).orElse(null);
        if (snapshot == null) return;
        if (Boolean.getBoolean("skyweather.climateHudProbe") && game.currentScreen == null
                && game.player != null && game.world.isBlockLoaded(game.player.getPosition(), false)) {
            climateHudTick(game);
        }
        if (snapshot.season != lastSeason) {
            readyTicks = 0;
            lastSeason = snapshot.season;
        }
        int stableTicks = snapshot.season == Season.WINTER ? 126 : 34;
        if (++readyTicks < stableTicks || captured.contains(snapshot.season) || game.currentScreen != null) return;
        LocalWeather weather = WeatherApi.getWeather(game.world, WeatherProbe.CHECK).orElse(null);
        if (weather == null) return;
        if (snapshot.season == Season.WINTER) {
            if (weather.precipitation != LocalWeather.Precipitation.SNOW) return;
            if (!WeatherApi.getWeatherEpisode(game.world).isPresent()) return;
            WeatherProbe.require(WeatherApi.getWeatherEpisode(game.world).get().kind == WeatherKind.WINTER_STORM,
                    "client synced the server's scheduled winter storm");
            float rain = game.world.getRainStrength(1);
            float rendered = ClientWeatherHooks.visualRainStrength(game.world, 1);
            WeatherProbe.require(rendered > 0 && rendered < rain, "scheduled intensity drives client precipitation");
            WeatherProbe.require(snapshot.lengthInDays == Integer.getInteger("skyweather.winterDays", 90),
                    "client synced the server's winter length");
            WeatherProbe.require(!game.world.isRainingAt(WeatherProbe.CHECK), "client vanilla rain gate uses snow");
        }
        WeatherProbe.require(HookStatus.available(), "client weather hooks loaded");
        System.out.println("SKY_WEATHER_CHECK CLIENT " + game.player.getName() + " " + snapshot.season
                + " " + weather.precipitation + " " + weather.source);
        captured.add(snapshot.season);
        pendingPicture = true;
    }

    private void climateHudTick(Minecraft game) {
        if (climateStage == 0) {
            WeatherProbe.require(WeatherConfiguration.showF3Climate() && !WeatherConfiguration.showCompactClimate(),
                    "debug climate defaults");
            game.gameSettings.showDebugInfo = true;
            climateStage = 1;
        } else if (climateStage == 2 && climatePicture == null) {
            game.gameSettings.showDebugInfo = false;
            WeatherConfiguration.toggleCompactClimate();
            climateStage = 3;
        } else if (climateStage == 4 && climatePicture == null) {
            game.player.setReducedDebug(true);
            climateStage = 5;
        } else if (climateStage == 6) {
            game.player.setReducedDebug(false);
            WeatherConfiguration.toggleCompactClimate();
            climateStage = 7;
            System.out.println("SKY_WEATHER_CHECK CLIMATE_HUD PASS");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void climateHud(RenderGameOverlayEvent.Text event) {
        if (climateStage == 1) {
            WeatherProbe.require(event.getRight().contains("Sky's Climate")
                    && event.getRight().stream().anyMatch(line -> line.startsWith("Temp ") && line.contains("vanilla")),
                    "F3 climate details are in Forge's text overlay");
            climatePicture = "climate-f3.png";
            climateStage = 2;
        } else if (climateStage == 3) {
            WeatherProbe.require(!event.getRight().contains("Sky's Climate")
                    && event.getRight().stream().anyMatch(line -> line.contains(WeatherConfiguration.temperatureUnit().symbol + " | ")), "compact climate readout renders");
            climatePicture = "climate-compact.png";
            climateStage = 4;
        } else if (climateStage == 5) {
            WeatherProbe.require(event.getRight().stream().noneMatch(line -> line.contains(WeatherConfiguration.temperatureUnit().symbol + " | ")),
                    "reduced debug info hides the compact readout");
            climateStage = 6;
        }
    }

    private void admin(Minecraft game) throws ReflectiveOperationException {
        if (game.player == null || WeatherState.frame(game.world) == null) return;
        adminTicks++;
        WeatherEpisode episode = WeatherApi.getWeatherEpisode(game.world).orElse(null);
        boolean forcedRain = episode != null && episode.forced && episode.kind == WeatherKind.FRONTAL_PRECIPITATION;
        if (game.player.getName().endsWith("2")) {
            if (adminTicks == 20) {
                verifyCreativeEntry(false);
                game.player.sendChatMessage("/skyweather gui");
                game.player.sendChatMessage("/skyweather strength 0");
            }
            WeatherProbe.require(!(game.currentScreen instanceof WeatherAdminScreen), "guest never receives the admin screen");
            if (adminTicks > 20 && forcedRain) adminStage = 6;
            return;
        }
        if (!(game.currentScreen instanceof WeatherAdminScreen)) return;
        GuiScreen screen = game.currentScreen;
        int panel = (Integer) field(screen, "panelWidth");
        int cell = (panel - 16) / 4;
        switch (adminStage) {
            case 0:
                WeatherProbe.require(!screen.doesGuiPauseGame(), "admin controller stays live");
                verifyCreativeEntry(true);
                click(screen, 16 + cell * 2 + 5, 72);
                adminStage = 1;
                break;
            case 1:
                if (!forcedRain || game.world.getRainStrength(1) < 0.75F) return;
                pendingPicture = true;
                adminStage = 2;
                break;
            case 2:
                if (pendingPicture) return;
                click(screen, 16 + cell * 2 + 5, 45);
                Map<?, ?> fields = (Map<?, ?>) field(screen, "fields");
                ((GuiTextField) fields.get("strength")).setText("1.35");
                click(screen, 16 + cell * 2 + 5, 96);
                adminStage = 3;
                break;
            case 3:
                if (WeatherState.frame(game.world).settings.profile.strength != 1.35F) return;
                click(screen, 16 + cell + 5, 45);
                click(screen, 16 + cell * 2 + 5, 72);
                adminStage = 4;
                break;
            case 4:
                if (WeatherApi.getSeason(game.world).get().season != Season.SPRING) return;
                click(screen, 16 + cell * 3 + 5, 45);
                click(screen, 24, 179);
                adminStage = 5;
                break;
            case 5:
                if (forcedRain || WeatherState.frame(game.world).settings.profile.strength != 1F) return;
                adminStage = 6;
                break;
            default: break;
        }
    }

    private static void verifyCreativeEntry(boolean operator) {
        net.minecraft.block.Block block = WeatherController.BLOCK;
        Item item = Item.getItemFromBlock(block);
        net.minecraft.util.NonNullList<ItemStack> entries = net.minecraft.util.NonNullList.create();
        CreativeTabs tab = block.getCreativeTabToDisplayOn();
        item.getSubItems(item, tab, entries);
        WeatherProbe.require(entries.size() == (operator ? 1 : 0), "controller is listed only for creative operators");
    }

    private static void click(GuiScreen screen, int x, int y) throws ReflectiveOperationException {
        method(screen, "mouseClicked", "func_73864_a", int.class, int.class, int.class).invoke(screen, x, y, 0);
    }

    private void verifyConfig(Minecraft game) throws ReflectiveOperationException {
        GuiScreen parent = new GuiModList(game.currentScreen);
        Class<? extends GuiScreen> factory = FMLClientHandler.instance()
                .getGuiFactoryFor(Loader.instance().getIndexedModList().get("skysrealisticweather")).mainConfigGuiClass();
        GuiScreen screen = factory.getConstructor(GuiScreen.class).newInstance(parent);
        game.displayGuiScreen(screen);
        WeatherProbe.require(screen instanceof zone.moddev.mc.skysrealisticweather.client.ClimateModeScreen,
                "Mods config opens mode selection first");
        press(screen, zone.moddev.mc.skysrealisticweather.api.ClimateMode.UNIVERSAL.ordinal());
        press(screen, 10);
        screen = game.currentScreen;
        List<Preset> initial = WeatherConfiguration.presets();
        WeatherProbe.require(initial.size() == 28, "fresh library has 28 choices");
        pick(screen, "short_seasons");
        press(screen, 10);
        WeatherProbe.require("short_seasons".equals(WeatherConfiguration.activePreset())
                && WeatherConfiguration.read().profile.daysInYear() == 96, "load Short Seasons from the Mods config screen");
        press(screen, 13);
        GuiScreen manage = game.currentScreen;
        for (Preset preset : initial) {
            pick(manage, preset.id);
            press(manage, 16);
            WeatherProbe.require(game.currentScreen != manage, "open editor for " + preset.id);
            WeatherProbe.require(preset.label.equals(input(game.currentScreen, 0).getText()), "editor opened selected preset");
            press(game.currentScreen, 11);
        }
        press(manage, 14);
        GuiScreen editor = game.currentScreen;
        input(editor, 0).setText("");
        press(editor, 10);
        WeatherProbe.require(game.currentScreen == editor && WeatherConfiguration.presets().size() == 28,
                "invalid draft stays in editor without writing a preset");
        input(editor, 0).setText("Harbour Test");
        input(editor, 1).setText("A climate created through the preset editor.");
        press(editor, 1);
        input(editor, 0).setText("Thaw");
        input(editor, 1).setText("0");
        press(editor, 10);
        WeatherProbe.require(game.currentScreen == editor && WeatherConfiguration.preset("harbour_test") == null,
                "invalid season length cannot save a draft");
        input(editor, 1).setText("100");
        press(editor, 10);
        Preset harbour = WeatherConfiguration.preset("harbour_test");
        WeatherProbe.require(harbour != null && harbour.profile.daysInYear() == 373
                && "Thaw".equals(harbour.phaseLabel(Season.SPRING)), "create and label a new preset");
        WeatherProbe.require("short_seasons".equals(WeatherConfiguration.activePreset()), "creating an inactive preset keeps the active choice");

        pick(manage, harbour.id);
        press(manage, 17);
        WeatherProbe.require(game.currentScreen instanceof net.minecraftforge.fml.client.config.GuiConfig,
                "opens event settings for the selected preset");
        String[] harbourWeather = {"DRIZZLE,25,10,30,26"};
        WeatherConfiguration.eventRows(harbour.id).set(harbourWeather);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent(
                "skysrealisticweather", null, false, true));
        WeatherProbe.require(java.util.Arrays.equals(harbourWeather, WeatherConfiguration.preset(harbour.id).weather.rows()),
                "event settings save to the named preset");
        game.displayGuiScreen(manage);
        press(manage, 15);
        press(game.currentScreen, 10);
        Preset copy = WeatherConfiguration.preset("harbour_test_copy");
        WeatherProbe.require(copy != null && copy.profile.daysInYear() == 373
                && "Thaw".equals(copy.phaseLabel(Season.SPRING))
                && java.util.Arrays.equals(harbourWeather, copy.weather.rows()), "duplicate keeps phase and event settings");
        pick(manage, copy.id);
        press(manage, 16);
        input(game.currentScreen, 1).setText("An independent copy.");
        press(game.currentScreen, 10);
        WeatherProbe.require("An independent copy.".equals(WeatherConfiguration.preset(copy.id).description)
                && "short_seasons".equals(WeatherConfiguration.activePreset())
                && WeatherConfiguration.read().profile.daysInYear() == 96, "editing an inactive preset is independent");
        press(manage, 11);
        pick(screen, harbour.id);
        press(screen, 10);
        press(screen, 13);
        manage = game.currentScreen;
        pick(manage, harbour.id);
        press(manage, 16);
        editor = game.currentScreen;
        input(editor, 0).setText("Harbour Renamed");
        press(editor, 1);
        input(editor, 1).setText("103");
        press(editor, 10);
        WeatherProbe.require("Harbour Renamed".equals(WeatherConfiguration.preset(harbour.id).label)
                && harbour.id.equals(WeatherConfiguration.activePreset())
                && WeatherConfiguration.read().profile.daysInYear() == 376, "editing active preset follows stable ID");
        press(manage, 16);
        input(game.currentScreen, 0).setText("This change is cancelled");
        press(game.currentScreen, 11);
        WeatherProbe.require("Harbour Renamed".equals(WeatherConfiguration.preset(harbour.id).label), "cancel discards draft");

        WeatherConfiguration.load(new File(game.mcDataDir, "config/skysrealisticweather.cfg"));
        WeatherProbe.require(harbour.id.equals(WeatherConfiguration.activePreset())
                && WeatherConfiguration.startupSettings().profile.daysInYear() == 376
                && WeatherConfiguration.preset(copy.id) != null, "new, edited and duplicate presets survive config reload");
        press(manage, 11);
        pick(screen, "oxford");
        press(screen, 10);
        WeatherProbe.require("oxford".equals(WeatherConfiguration.activePreset())
                && WeatherConfiguration.read().profile.daysInYear() == 365, "load Oxford from the Mod config screen");
        press(screen, 12);
        WeatherProbe.require(game.currentScreen instanceof net.minecraftforge.fml.client.config.GuiConfig,
                "opens direct settings editor");
        WeatherConfiguration.file().getCategory("spring").get("days").set(77);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent(
                "skysrealisticweather", null, false, true));
        WeatherProbe.require("custom".equals(WeatherConfiguration.activePreset())
                && WeatherConfiguration.read().profile.period(0).days == 77
                && WeatherConfiguration.preset("oxford").profile.period(0).days == 92,
                "ordinary settings edits detach without editing the saved preset");
        game.displayGuiScreen(screen);
        press(screen, 10);
        pick(screen, "tropical_monsoon");
        System.out.println("SKY_WEATHER_CHECK PRESET_EDITOR PASS");
        pendingPicture = true;
    }

    private void modeUi(Minecraft game) throws ReflectiveOperationException {
        if (modeUiStage == 0 && !(game.currentScreen instanceof GuiMainMenu)) return;
        if (++readyTicks % 12 != 0) return;
        switch (modeUiStage++) {
            case 0:
                WeatherProbe.require(WeatherConfiguration.mode() == zone.moddev.mc.skysrealisticweather.api.ClimateMode.PER_BIOME,
                        "fresh menu defaults to biome mode");
                modeRoot = new zone.moddev.mc.skysrealisticweather.client.ClimateModeScreen(game.currentScreen);
                game.displayGuiScreen(modeRoot);
                climatePicture = "mode-selection.png";
                break;
            case 1:
                press(modeRoot, 10);
                WeatherProbe.require(game.currentScreen instanceof net.minecraftforge.fml.client.config.GuiConfig,
                        "biome mode opens its settings");
                climatePicture = "biome-mappings.png";
                break;
            case 2:
                game.displayGuiScreen(modeRoot);
                press(modeRoot, zone.moddev.mc.skysrealisticweather.api.ClimateMode.UNIVERSAL.ordinal());
                press(modeRoot, 10);
                WeatherProbe.require(game.currentScreen instanceof zone.moddev.mc.skysrealisticweather.client.WeatherConfigScreen,
                        "universal mode opens climate picker");
                climatePicture = "universal-climate.png";
                break;
            case 3:
                game.displayGuiScreen(modeRoot);
                press(modeRoot, zone.moddev.mc.skysrealisticweather.api.ClimateMode.REAL_WORLD.ordinal());
                press(modeRoot, 10);
                climatePicture = "real-world-mode.png";
                break;
            case 4:
                press(game.currentScreen, 0);
                WeatherProbe.require(game.currentScreen instanceof net.minecraftforge.fml.client.config.GuiConfig,
                        "real world mode opens location settings");
                climatePicture = "real-world-location.png";
                break;
            case 5:
                WeatherConfiguration.useMode(zone.moddev.mc.skysrealisticweather.api.ClimateMode.PER_BIOME);
                System.out.println("SKY_WEATHER_CHECK MODE_UI PASS");
                game.shutdown();
                break;
        }
    }

    private void biomeNetwork(Minecraft game) {
        if (game.world == null) {
            if (menu == 0 && game.currentScreen instanceof GuiMainMenu) {
                menu++;
                game.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), game, "127.0.0.1",
                        Integer.getInteger("skyweather.port")));
            }
            return;
        }
        WeatherState.Frame frame = WeatherState.frame(game.world);
        if (frame == null || game.player == null || !game.world.isBlockLoaded(game.player.getPosition(), false)) return;
        if (++readyTicks != 100) return;
        WeatherProbe.require(frame.settings.mode == zone.moddev.mc.skysrealisticweather.api.ClimateMode.PER_BIOME,
                "server selected biome mode");
        WeatherProbe.require(frame.seed == 123948 && frame.scheduled, "server schedule seed and control synchronized");
        WeatherProbe.require(frame.settings.biomes.mappings.get("minecraft:jungle").equals("tropical_monsoon"),
                "server mapping replaces client default");
        BlockPos eye = game.player.getPosition();
        ClimateSample jungle = WeatherApi.getClimate(game.world, eye, net.minecraft.init.Biomes.JUNGLE).get();
        ClimateSample desert = WeatherApi.getClimate(game.world, eye, net.minecraft.init.Biomes.DESERT).get();
        WeatherProbe.require(jungle.climateId.equals("tropical_monsoon") && desert.climateId.equals("hot_desert"),
                "remote supplied-biome climates follow server");
        net.minecraft.world.biome.Biome biome = game.world.getBiome(eye);
        float expected = frame.visualRainStrength(1, eye, biome);
        WeatherProbe.require(Math.abs(game.world.getRainStrength(1) - expected) < 0.0001F,
                "cloud darkening follows the camera's local episode");
        WeatherProbe.require(Math.abs(ClientWeatherHooks.visualRainStrength(game.world, 1) - expected) < 0.0001F,
                "rain intensity is applied once");
        WeatherEpisode episode = frame.currentEpisode(biome);
        if (episode == null || !episode.kind.thunder) {
            WeatherProbe.require(game.world.getThunderStrength(1) == 0, "local non-thunder weather has no thunder darkening");
        }
        System.out.println("SKY_WEATHER_CHECK BIOME_NETWORK PASS");
        game.shutdown();
    }

    private static void press(GuiScreen screen, int id) throws ReflectiveOperationException {
        method(screen, "actionPerformed", "func_146284_a", GuiButton.class)
                .invoke(screen, new GuiButton(id, 0, 0, ""));
    }

    private static GuiTextField input(GuiScreen screen, int index) throws ReflectiveOperationException {
        return (GuiTextField) ((List<?>) field(screen, "inputs")).get(index);
    }

    private static void pick(GuiScreen screen, String id) throws ReflectiveOperationException {
        GuiSlot list = (GuiSlot) field(screen, "presetList");
        List<?> rows = (List<?>) field(list, "rows");
        WeatherProbe.require(list.getMaxScroll() > 0, "large catalog scrolls");
        for (int index = 0; index < rows.size(); index++) {
            Preset preset = (Preset) field(rows.get(index), "preset");
            if (preset == null || !id.equals(preset.id)) continue;
            list.scrollBy(index * list.slotHeight - list.getAmountScrolled());
            int mouseY = list.top + 8 + index * list.slotHeight - list.getAmountScrolled();
            int row = list.getSlotIndexFromScreenCoords(list.width / 2, mouseY);
            WeatherProbe.require(row == index && mouseY < list.bottom, "scrolled row is selectable " + id);
            method(list, "elementClicked", "func_148144_a", int.class, boolean.class, int.class, int.class)
                    .invoke(list, row, false, list.width / 2, mouseY);
            return;
        }
        throw new IllegalStateException("Preset is absent from the picker: " + id);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Method method(Object target, String mapped, String packaged, Class<?>... arguments)
            throws ReflectiveOperationException {
        Method method;
        try {
            method = target.getClass().getDeclaredMethod(mapped, arguments);
        } catch (NoSuchMethodException absent) {
            method = target.getClass().getDeclaredMethod(packaged, arguments);
        }
        method.setAccessible(true);
        return method;
    }

    @SubscribeEvent
    public void picture(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft game = Minecraft.getMinecraft();
        if (climatePicture != null) {
            ScreenShotHelper.saveScreenshot(new File("."), climatePicture, game.displayWidth, game.displayHeight, game.getFramebuffer());
            climatePicture = null;
        }
        if (!pendingPicture) return;
        pendingPicture = false;
        String name = game.world == null ? "weather-config.png" : Boolean.getBoolean("skyweather.adminProbe")
                ? "weather-controller.png" : lastSeason.name().toLowerCase(java.util.Locale.ROOT) + ".png";
        ScreenShotHelper.saveScreenshot(new File("."), name, game.displayWidth, game.displayHeight, game.getFramebuffer());
    }
}
