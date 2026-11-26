package zone.moddev.mc.skysrealisticweather.climate;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.LogManager;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.live.LiveLocation;

public final class WeatherConfiguration {
    public static final String GENERAL = "general";
    public static final String LIVE = "live_weather";
    public static final String EVENTS = "weather_events";
    public static final String CLIENT = "client";
    public static final String HEATING = "heating";
    private static boolean heatingEnabled=true;
    private static int furnaceHeatRadius=2;
    private static float furnaceHeatTemperature=1.05F;
    public static final String BIOMES = "biome_climates";
    public static final String CALENDAR = "calendar";
    private static final String F3_CLIMATE = "f3ClimateHud";
    private static final String COMPACT_CLIMATE = "compactClimateHud";
    private static final String CONFIG_VERSION = "6";
    private static final String[] LOCATION_NAMES = {"BURLINGTON", "OXFORD", "CUSTOM"};
    private static final List<String> LEGACY_PRESETS = Arrays.asList("burlington", "oxford", "winter_wonderland", "short_seasons");
    private static Configuration config;
    private static Map<String, Preset> library;
    private static WeatherSettings startupSettings;
    private static LiveLocation startupLocation;
    private static String startupPreset;
    private static String[] startupLabels;

    private WeatherConfiguration() {
    }

    public static void load(File file) {
        boolean fresh = !file.isFile() || file.length() == 0;
        config = new Configuration(file, CONFIG_VERSION);
        String previousVersion = config.getLoadedConfigVersion();
        boolean legacy = previousVersion == null || "1".equals(previousVersion);
        if (fresh || legacy) PresetCatalog.seed(config);
        if ("2".equals(previousVersion) || "3".equals(previousVersion) || "4".equals(previousVersion)) {
            PresetCatalog.upgradeWeatherDefaults(config, previousVersion);
        }
        library = PresetCatalog.read(config);

        String id = Preset.normalizeId(activeProperty().getString());
        Preset previous = PresetCatalog.defaults().getOrDefault(id, defaultPreset());
        ClimateProfile saved = readSavedProfile(previous.profile);
        String[] labels = readSavedLabels();
        Preset selected = library.get(id);
        if (legacy && LEGACY_PRESETS.contains(id) && !matchesLegacyProfile(saved, previous, previousVersion)) {
            // Older releases stored copies, which users could customize directly in the file.
            selected = null;
            id = Preset.CUSTOM_ID;
        }
        if (selected != null) {
            setActive(selected);
        } else {
            if (!Preset.CUSTOM_ID.equals(id)) {
                LogManager.getLogger("Sky's Realistic Weather").warn(
                        "Weather preset '{}' is unavailable. Keeping the saved climate as Custom.", id);
            }
            activeProperty().set(Preset.CUSTOM_ID);
            writeSnapshot(saved, labels);
            PresetCatalog.patternProperty(config, EVENTS, previous.weather);
        }
        startupSettings = read();
        startupLocation = location();
        startupPreset = activePreset();
        startupLabels = activePhaseLabels();
        config.getString("temperatureUnit", CLIENT, "CELSIUS", "Display temperatures in Celsius or Fahrenheit. Applies immediately; simulation/config-file values remain in Minecraft units.", new String[] {"CELSIUS", "FAHRENHEIT"});
        config.getBoolean(F3_CLIMATE, CLIENT, true, "Show climate details on the F3 debug screen.");
        config.getBoolean(COMPACT_CLIMATE, CLIENT, false, "Show a compact season, temperature and weather readout.");
        config.getInt("distantWeatherDistance", CLIENT, 192, 0, 256,
                "Distant rain/snow curtains in blocks; 0 disables. Limited by loaded terrain and view distance. Fast graphics caps this at 96. Client only; applies immediately.");
        heatingEnabled=config.getBoolean("enabled",HEATING,true,"Enable registered local-temperature zones. Server authoritative; restart to apply.");
        furnaceHeatRadius=config.getInt("furnaceRadius",HEATING,2,0,8,"Burning vanilla furnace zone radius in blocks, including vertically. 0 disables furnace zones. No wall/room checks.");
        furnaceHeatTemperature=0.15F+(float)config.getFloat("furnaceMinimumCelsius",HEATING,20,-50,100,"Minimum temperature near a burning furnace. Celsius regardless of display preference; never cools warmer ambient air.")*.045F;
        config.save();
    }

    private static boolean matchesLegacyProfile(ClimateProfile saved, Preset previous, String previousVersion) {
        return sameProfile(saved, previous.profile)
                || PresetCatalog.matchesPreviousProfile(saved, previous.id, false)
                || previousVersion == null && !"short_seasons".equals(previous.id)
                        && (sameProfile(saved, previous.profile.withSeasonLength(24))
                            || PresetCatalog.matchesPreviousProfile(saved, previous.id, true));
    }

    public static WeatherSettings startupSettings() {
        return startupSettings;
    }

    public static LiveLocation startupLocation() {
        return startupLocation;
    }

    public static String startupPreset() {
        return startupPreset;
    }

    public static String[] startupLabels() {
        return startupLabels.clone();
    }

    public static Configuration file() {
        return config;
    }

    public static zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit temperatureUnit() {
        if (config == null || !config.hasKey(CLIENT, "temperatureUnit")) return zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit.CELSIUS;
        try { return zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit.valueOf(config.getCategory(CLIENT).get("temperatureUnit").getString()); }
        catch (IllegalArgumentException invalid) { return zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit.CELSIUS; }
    }

    public static int distantWeatherDistance() {
        return config == null || !config.hasKey(CLIENT, "distantWeatherDistance") ? 192
                : Math.max(0, Math.min(256, config.getCategory(CLIENT).get("distantWeatherDistance").getInt()));
    }
    public static boolean heatingEnabled() { return heatingEnabled; }
    public static int furnaceHeatRadius() { return furnaceHeatRadius; }
    public static float furnaceHeatTemperature() { return furnaceHeatTemperature; }

    public static boolean showF3Climate() {
        return config.getCategory(CLIENT).get(F3_CLIMATE).getBoolean();
    }

    public static boolean showCompactClimate() {
        return config.getCategory(CLIENT).get(COMPACT_CLIMATE).getBoolean();
    }

    public static void toggleCompactClimate() {
        config.getCategory(CLIENT).get(COMPACT_CLIMATE).set(!showCompactClimate());
        config.save();
    }

    public static String category(Season season) {
        return season.name().toLowerCase(Locale.ROOT);
    }

    public static Preset defaultPreset() {
        return PresetCatalog.defaults().get(Preset.DEFAULT_ID);
    }

    public static List<Preset> presets() {
        return new ArrayList<>(library.values());
    }

    public static Preset preset(String id) {
        return library.get(Preset.normalizeId(id));
    }

    public static String activePreset() {
        return activeProperty().getString();
    }

    private static Property activeProperty() {
        Property property = config.get(GENERAL, "activePreset", Preset.DEFAULT_ID,
                "ID of a preset in the library. Use custom to read the four top-level seasons instead.");
        property.setShowInGui(false);
        return property;
    }

    public static String[] activePhaseLabels() {
        if (mode() == ClimateMode.PER_BIOME) {
            return Arrays.stream(Season.values()).map(Preset::defaultPhaseLabel).toArray(String[]::new);
        }
        Preset active = preset(activePreset());
        return active == null ? readSavedLabels() : active.phaseLabels();
    }

    public static WeatherSettings read() {
        Preset active = preset(activePreset());
        ClimateProfile profile = active == null ? readSavedProfile(defaultPreset().profile) : active.profile;
        ClimateProfile.Period[] calendar = new ClimateProfile.Period[4];
        int[] days = {92, 92, 91, 90};
        for (Season season : Season.values()) {
            int i = season.ordinal();
            ClimateProfile.Period old = profile.period(i);
            int length = config.getInt(category(season) + "Days", CALENDAR, days[i], 1, 365,
                    "Shared calendar phase length in Minecraft days for Per Biome Climate.");
            calendar[i] = new ClimateProfile.Period(length, old.temperature, old.wetness, old.thunder, old.fog);
        }
        float biomeStrength = floatSetting("biomeStrength", GENERAL, 1, 0, 2, "Overall strength of per-biome seasonal effects.");
        config.getCategory(GENERAL).get("biomeStrength").setShowInGui(mode() == ClimateMode.PER_BIOME);
        config.getCategory(GENERAL).get("strength").setShowInGui(mode() != ClimateMode.PER_BIOME);
        if (mode() == ClimateMode.PER_BIOME) profile = new ClimateProfile(biomeStrength, calendar);
        boolean scheduled = config.getBoolean("scheduledWeather", GENERAL, true,
                "Run the climate's event schedule. Disable to use the older seasonal rain and thunder timers.");
        WeatherPattern weather = active == null ? PresetCatalog.readPattern(config, EVENTS, defaultPreset().weather)
                : active.weather;
        GenerationSettings generation = new GenerationSettings(
                config.getBoolean("seasonalWorldgen", GENERAL, true, "Use local weather for snow and ice in newly populated chunks."),
                config.get(GENERAL, "nativeSnowBiomes", GenerationSettings.defaultBiomeIds(),
                        "Biome registry IDs whose native snow and ice generation should be kept.").getStringList());
        return new WeatherSettings(profile,
                config.getBoolean("passive", GENERAL, false, "Expose the calendar and API with vanilla weather and visuals."),
                config.getBoolean("surface", GENERAL, true, "Apply seasonal snow buildup, freezing and thaw."),
                config.getBoolean("foliage", GENERAL, true, "Gradually tint biome-coloured grass and leaves."),
                config.getBoolean("fog", GENERAL, true, "Show outdoor fog."),
                weather, scheduled, generation).withMode(mode(), biomeMap()).withFarming(FarmingSettings.read(config));
    }

    public static ClimateMode mode() {
        String fallback = config.hasKey(LIVE, "enabled") && config.get(LIVE, "enabled", false).getBoolean()
                ? ClimateMode.REAL_WORLD.name() : ClimateMode.PER_BIOME.name();
        Property property = config.get(GENERAL, "climateMode", fallback,
                "PER_BIOME assigns a climate to each biome. UNIVERSAL uses the selected preset everywhere. REAL_WORLD tracks the configured location.");
        property.setShowInGui(false);
        try {
            return ClimateMode.valueOf(property.getString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return ClimateMode.PER_BIOME;
        }
    }

    public static void useMode(ClimateMode mode) {
        config.getCategory(GENERAL).get("climateMode").set(mode.name());
        config.get(LIVE, "enabled", false).set(mode == ClimateMode.REAL_WORLD);
        config.save();
    }

    private static BiomeClimateMap biomeMap() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (Map.Entry<String, Property> entry : config.getCategory(BIOMES).entrySet()) {
            entry.getValue().setValidValues(library.keySet().toArray(new String[0]));
            String id = Preset.normalizeId(entry.getValue().getString());
            if (library.containsKey(id)) mappings.put(entry.getKey(), id);
            else LogManager.getLogger("Sky's Realistic Weather").warn("Unknown biome climate preset '{}' for '{}'. Using automatic classification.", id, entry.getKey());
        }
        return new BiomeClimateMap(library, mappings);
    }

    /** Run after biome registration so optional mod IDs become editable alongside vanilla biomes. */
    public static void populateBiomeMappings() {
        config.getCategory(BIOMES).setComment("Biome registry ID to preset ID. Edit a value to override automatic classification, including fantasy presets. Newly registered biomes get defaults on startup.");
        for (Biome biome : Biome.REGISTRY) {
            if (biome.getRegistryName() != null) {
                config.get(BIOMES, biome.getRegistryName().toString(), BiomeClimateMap.classify(biome),
                        "Climate preset ID for " + biome.getRegistryName()).setValidValues(library.keySet().toArray(new String[0]));
            }
        }
        startupSettings = read();
        config.save();
    }

    public static Property eventRows(String id) {
        Preset preset = preset(id);
        return preset == null ? PresetCatalog.patternProperty(config, EVENTS, defaultPreset().weather)
                : PresetCatalog.patternProperty(config, PresetCatalog.path(preset.id), preset.weather);
    }

    private static ClimateProfile readSavedProfile(ClimateProfile fallback) {
        ClimateProfile.Period[] periods = new ClimateProfile.Period[ClimateProfile.SEASON_COUNT];
        for (Season season : Season.values()) {
            int index = season.ordinal();
            String category = category(season);
            ClimateProfile.Period defaults = fallback.period(index);
            periods[index] = new ClimateProfile.Period(
                    config.getInt("days", category, defaults.days, 1, 365, "Length in Minecraft days."),
                    floatSetting("temperature", category, defaults.temperature, -2, 2, "Temperature offset in Minecraft biome units."),
                    floatSetting("wetness", category, defaults.wetness, 0.25F, 4, "Precipitation multiplier."),
                    floatSetting("thunder", category, defaults.thunder, 0.25F, 4, "Thunder multiplier."),
                    floatSetting("fog", category, defaults.fog, 0, 1, "Strength of outdoor mist."));
        }
        float strength = floatSetting("strength", GENERAL, fallback.strength, 0, 2, "Overall strength of seasonal effects.");
        return new ClimateProfile(strength, periods);
    }

    private static String[] readSavedLabels() {
        String[] names = new String[ClimateProfile.SEASON_COUNT];
        for (Season season : Season.values()) {
            String fallback = Preset.defaultPhaseLabel(season);
            String name = config.get(category(season), "label", fallback, "Name of this calendar phase.").getString();
            try {
                names[season.ordinal()] = Preset.text(name, "Phase label", false, 32);
            } catch (IllegalArgumentException invalid) {
                names[season.ordinal()] = fallback;
            }
        }
        return names;
    }

    private static void writeSnapshot(ClimateProfile profile, String[] labels) {
        PresetCatalog.writeStrength(config, GENERAL, profile.strength);
        for (Season season : Season.values()) {
            PresetCatalog.writePeriod(config, category(season), profile.period(season.ordinal()), labels[season.ordinal()]);
        }
    }

    public static void usePreset(String id) {
        Preset preset = preset(id);
        if (preset == null) throw new IllegalArgumentException("Unknown preset " + id);
        setActive(preset);
        config.save();
    }

    private static void setActive(Preset preset) {
        activeProperty().set(preset.id);
        writeSnapshot(preset.profile, preset.phaseLabels());
        PresetCatalog.patternProperty(config, EVENTS, preset.weather).set(preset.weather.rows());
    }

    public static String newPresetId(String name) {
        String base = Preset.normalizeId(name).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty() || Preset.CUSTOM_ID.equals(base)) base = "preset";
        if (base.length() > 48) base = base.substring(0, 48);
        String id = base;
        for (int suffix = 2; config.hasCategory(PresetCatalog.path(id)); suffix++) id = base + "_" + suffix;
        return id;
    }

    public static void savePreset(Preset preset) {
        PresetCatalog.write(config, PresetCatalog.path(preset.id), preset);
        library = PresetCatalog.read(config);
        if (preset.id.equals(activePreset())) setActive(preset);
        config.save();
    }

    public static void saveEdits() {
        Preset active = preset(activePreset());
        ClimateProfile stored = readSavedProfile(active == null ? defaultPreset().profile : active.profile);
        String[] labels = readSavedLabels();
        if (active != null && (!sameProfile(stored, active.profile) || !Arrays.equals(labels, active.phaseLabels())
                || !Arrays.equals(eventRows(Preset.CUSTOM_ID).getStringList(), active.weather.rows()))) {
            activeProperty().set(Preset.CUSTOM_ID);
        }
        library = PresetCatalog.read(config);
        Preset selected = preset(activePreset());
        if (selected != null) setActive(selected);
        else writeSnapshot(stored, labels);
        config.save();
    }

    public static LiveLocation location() {
        config.get(LIVE, "enabled", false, "Legacy live-feed flag. Select REAL_WORLD with general.climateMode.").setShowInGui(false);
        String site = config.getString("location", LIVE, "BURLINGTON",
                "Burlington, Oxford or the coordinates below.", LOCATION_NAMES);
        double latitude = coordinate("latitude", LiveLocation.BURLINGTON.latitude, 90);
        double longitude = coordinate("longitude", LiveLocation.BURLINGTON.longitude, 180);
        if (mode() != ClimateMode.REAL_WORLD) return null;
        if ("CUSTOM".equals(site)) {
            try {
                return new LiveLocation(latitude, longitude);
            } catch (IllegalArgumentException invalid) {
                LogManager.getLogger("Sky's Realistic Weather").warn(
                        "Custom weather coordinates are invalid. Using seasonal weather.");
                return null;
            }
        }
        return "OXFORD".equals(site) ? LiveLocation.OXFORD : LiveLocation.BURLINGTON;
    }

    private static double coordinate(String name, double fallback, double limit) {
        return config.get(LIVE, name, fallback, "Custom location in decimal degrees.", -limit, limit).getDouble();
    }

    private static float floatSetting(String name, String category, float fallback, float min, float max, String help) {
        double value = config.get(category, name, fallback, help, min, max).getDouble(fallback);
        return Double.isFinite(value) ? (float) Math.max(min, Math.min(max, value)) : fallback;
    }

    private static boolean sameProfile(ClimateProfile a, ClimateProfile b) {
        if (a.strength != b.strength) return false;
        for (int index = 0; index < ClimateProfile.SEASON_COUNT; index++) {
            ClimateProfile.Period x = a.period(index);
            ClimateProfile.Period y = b.period(index);
            if (x.days != y.days || x.temperature != y.temperature || x.wetness != y.wetness
                    || x.thunder != y.thunder || x.fog != y.fog) return false;
        }
        return true;
    }
}
