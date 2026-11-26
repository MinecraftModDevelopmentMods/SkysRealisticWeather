package zone.moddev.mc.skysrealisticweather.climate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile.Period;

public final class PresetCatalog {
    private static final String ROOT = "presets";
    private static final String RESOURCE = "/assets/skysrealisticweather/presets.json";
    private static final Logger LOG = LogManager.getLogger("Sky's Realistic Weather");
    private static final Map<String, Preset> DEFAULTS = loadDefaults();

    private PresetCatalog() {
    }

    public static Map<String, Preset> defaults() {
        return DEFAULTS;
    }

    private static Map<String, Preset> loadDefaults() {
        Map<String, Preset> results = new LinkedHashMap<>();
        try (InputStream input = PresetCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing starter preset library");
            JsonArray rows = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement row : rows) {
                JsonObject entry = row.getAsJsonObject();
                JsonArray periods = entry.getAsJsonArray("periods");
                JsonArray names = entry.getAsJsonArray("phaseLabels");
                List<Period> climate = new ArrayList<>();
                String[] labels = new String[ClimateProfile.SEASON_COUNT];
                if (periods.size() != labels.length || (names != null && names.size() != labels.length)) {
                    throw new IllegalArgumentException("Expected four seasons for " + entry.get("id"));
                }
                for (Season season : Season.values()) {
                    int index = season.ordinal();
                    JsonArray values = periods.get(index).getAsJsonArray();
                    if (values.size() != 5) throw new IllegalArgumentException("Expected five values for " + entry.get("id"));
                    climate.add(new Period(values.get(0).getAsInt(), values.get(1).getAsFloat(),
                            values.get(2).getAsFloat(), values.get(3).getAsFloat(), values.get(4).getAsFloat()));
                    labels[index] = names == null ? Preset.defaultPhaseLabel(season) : names.get(index).getAsString();
                }
                Preset preset = new Preset(entry.get("id").getAsString(), entry.get("name").getAsString(),
                        entry.get("description").getAsString(), Preset.Group.parse(entry.get("group").getAsString()),
                        new ClimateProfile(1, climate.toArray(new Period[0])), labels);
                if (!WeatherPattern.hasDefaults(preset.id)) throw new IllegalArgumentException("Missing events for " + preset.id);
                if (results.putIfAbsent(preset.id, preset) != null) throw new IllegalArgumentException("Duplicate preset " + preset.id);
            }
            if (!results.containsKey(Preset.DEFAULT_ID)) throw new IllegalArgumentException("Missing Burlington preset");
            return Collections.unmodifiableMap(results);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot read " + RESOURCE, failure);
        }
    }

    static void seed(Configuration configuration) {
        configuration.getCategory(ROOT).setComment("Saved weather presets. Copy a section with a unique ID to add one.");
        for (Preset preset : DEFAULTS.values()) {
            if (!configuration.hasCategory(path(preset.id))) write(configuration, path(preset.id), preset);
        }
    }

    static void upgradeWeatherDefaults(Configuration configuration, String version) {
        // Only untouched stock fields adopt new defaults; custom tables and profiles remain authoritative.
        if (!"4".equals(version)) upgradeWeather(configuration, WeatherPattern.resource("weather_weights_v3.json"));
        upgradeWeather(configuration, WeatherPattern.resource("weather_weights_v4.json"));
        JsonArray previous = previousDefaults();
        for (JsonElement value : previous) {
            JsonObject old = value.getAsJsonObject();
            String id = old.get("id").getAsString(), category = path(id);
            if (!configuration.hasCategory(category)) continue;
            Preset current = DEFAULTS.get(id);
            try {
                Preset saved = read(configuration, category, id);
                if (matchesPreviousProfile(saved.profile, old, false)) for (Season season : Season.values()) {
                    writePeriod(configuration, category + "." + WeatherConfiguration.category(season),
                            current.profile.period(season.ordinal()), saved.phaseLabel(season));
                }
                if (saved.description.equals(old.get("description").getAsString())) {
                    configuration.getCategory(category).get("description").set(current.description);
                }
            } catch (IllegalArgumentException invalid) {
                // The normal catalog read retains and reports invalid user entries.
            }
        }
    }

    private static JsonArray previousDefaults() {
        try (InputStream input = PresetCatalog.class.getResourceAsStream("/assets/skysrealisticweather/presets_v4.json")) {
            if (input == null) throw new IllegalStateException("Missing previous preset library");
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read previous preset library", failure);
        }
    }

    static boolean matchesPreviousProfile(ClimateProfile profile, String id, boolean shortCalendar) {
        for (JsonElement value : previousDefaults()) {
            JsonObject old = value.getAsJsonObject();
            if (id.equals(old.get("id").getAsString())) return matchesPreviousProfile(profile, old, shortCalendar);
        }
        return false;
    }

    private static boolean matchesPreviousProfile(ClimateProfile profile, JsonObject old, boolean shortCalendar) {
        if (profile.strength != 1) return false;
        for (int i = 0; i < 4; i++) {
            JsonArray p = old.getAsJsonArray("periods").get(i).getAsJsonArray();
            Period saved = profile.period(i);
            if (saved.days != (shortCalendar ? 24 : p.get(0).getAsInt()) || saved.temperature != p.get(1).getAsFloat()
                    || saved.wetness != p.get(2).getAsFloat() || saved.thunder != p.get(3).getAsFloat()
                    || saved.fog != p.get(4).getAsFloat()) return false;
        }
        return true;
    }

    private static void upgradeWeather(Configuration configuration, JsonObject previous) {
        for (Map.Entry<String, JsonElement> entry : previous.entrySet()) {
            String category = path(entry.getKey());
            if (!configuration.hasCategory(category)) continue;
            Property property = configuration.getCategory(category).get("weatherEvents");
            String[] rows = new com.google.gson.Gson().fromJson(entry.getValue(), String[].class);
            if (property != null && property.isList() && java.util.Arrays.equals(rows, property.getStringList())) {
                property.set(WeatherPattern.defaults(entry.getKey()).rows());
            }
        }
    }

    static Map<String, Preset> read(Configuration configuration) {
        List<Preset> valid = new ArrayList<>();
        if (configuration.hasCategory(ROOT)) {
            for (ConfigCategory category : configuration.getCategory(ROOT).getChildren()) {
                try {
                    valid.add(read(configuration, category.getQualifiedName(), category.getName()));
                } catch (IllegalArgumentException invalid) {
                    LOG.warn("Ignoring weather preset '{}'. {}", category.getName(), invalid.getMessage());
                }
            }
        }
        valid.sort(Comparator.comparing((Preset preset) -> preset.group)
                .thenComparing(preset -> preset.label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(preset -> preset.id));
        Map<String, Preset> result = new LinkedHashMap<>();
        for (Preset preset : valid) result.put(preset.id, preset);
        return result;
    }

    static String path(String id) {
        return ROOT + Configuration.CATEGORY_SPLITTER + id;
    }

    private static Preset read(Configuration source, String category, String id) {
        ConfigCategory values = requiredCategory(source, category);
        Period[] periods = new Period[ClimateProfile.SEASON_COUNT];
        String[] labels = new String[periods.length];
        for (Season season : Season.values()) {
            int index = season.ordinal();
            ConfigCategory phase = requiredCategory(source, category + "." + WeatherConfiguration.category(season));
            try {
                periods[index] = new Period(integer(phase, "days"), decimal(phase, "temperature"), decimal(phase, "wetness"),
                        decimal(phase, "thunder"), decimal(phase, "fog"));
                labels[index] = Preset.text(optional(phase, "label", Preset.defaultPhaseLabel(season)), "Phase label", false, 32);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(Preset.defaultPhaseLabel(season) + ": " + invalid.getMessage());
            }
        }
        return new Preset(id, required(values, "name"), optional(values, "description", ""),
                Preset.Group.parse(optional(values, "group", "user")),
                new ClimateProfile(decimal(values, "strength"), periods), labels,
                readPattern(source, category, WeatherPattern.defaults(id)));
    }

    private static ConfigCategory requiredCategory(Configuration configuration, String path) {
        if (!configuration.hasCategory(path)) throw new IllegalArgumentException("Missing section " + path);
        return configuration.getCategory(path);
    }

    private static String optional(ConfigCategory category, String key, String fallback) {
        return category.containsKey(key) ? required(category, key) : fallback;
    }

    private static String required(ConfigCategory category, String key) {
        Property property = category.get(key);
        if (property == null || property.isList()) throw new IllegalArgumentException("Expected a value for " + key);
        return property.getString();
    }

    private static int integer(ConfigCategory category, String key) {
        try {
            return Integer.parseInt(required(category, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(key + " must be a whole number");
        }
    }

    private static float decimal(ConfigCategory category, String key) {
        try {
            return Float.parseFloat(required(category, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(key + " must be a number");
        }
    }

    static void write(Configuration target, String category, Preset preset) {
        target.get(category, "name", preset.label, "Display name in the preset picker.").set(preset.label);
        target.get(category, "description", preset.description, "Description shown in the preset picker.").set(preset.description);
        target.get(category, "group", preset.group.key(), "Section in the preset picker.").set(preset.group.key());
        writeStrength(target, category, preset.profile.strength);
        patternProperty(target, category, preset.weather).set(preset.weather.rows());
        for (Season season : Season.values()) {
            writePeriod(target, category + "." + WeatherConfiguration.category(season),
                    preset.profile.period(season.ordinal()), preset.phaseLabel(season));
        }
    }

    static Property patternProperty(Configuration target, String category, WeatherPattern fallback) {
        ConfigCategory values = target.getCategory(category);
        if (values.containsKey("weatherEvents")) return values.get("weatherEvents");
        return target.get(category, "weatherEvents", fallback.rows(),
                "Rows: event, spring, summer, autumn, winter. Optional: first onset tick, last onset tick, "
                        + "shortest duration, longest duration, lowest peak, highest peak. Optional last field: include_dry_biomes. "
                        + "See docs/WEATHER.md for event names, units and defaults.");
    }

    static WeatherPattern readPattern(Configuration source, String category, WeatherPattern fallback) {
        Property value = patternProperty(source, category, fallback);
        try {
            if (!value.isList()) throw new IllegalArgumentException("Expected a list");
            String[] rows = value.getStringList();
            return java.util.Arrays.equals(rows, fallback.rows()) ? fallback : new WeatherPattern(rows);
        } catch (IllegalArgumentException invalid) {
            LOG.warn("Invalid {}.weatherEvents. Using the default event rows. {}", category, invalid.getMessage());
            return fallback;
        }
    }

    static void writeStrength(Configuration target, String category, float strength) {
        writeDecimal(target, category, "strength", strength, 0, 2, "Overall strength of the seasonal effects.");
    }

    static void writePeriod(Configuration target, String category, Period period, String label) {
        target.get(category, "label", label, "Name shown for this phase in the preset picker.").set(label);
        target.get(category, "days", period.days, "Length in Minecraft days.", 1, 365).set(period.days);
        writeDecimal(target, category, "temperature", period.temperature, -2, 2,
                "Temperature offset in Minecraft biome units.");
        writeDecimal(target, category, "wetness", period.wetness, 0.25, 4,
                "Precipitation multiplier. Higher values increase the frequency of wet weather.");
        writeDecimal(target, category, "thunder", period.thunder, 0.25, 4,
                "Thunder multiplier. Higher values increase the frequency of thunder.");
        writeDecimal(target, category, "fog", period.fog, 0, 1,
                "Frequency and strength of the climate's fog.");
    }

    private static void writeDecimal(Configuration target, String category, String key, float value,
                                     double minimum, double maximum, String comment) {
        target.get(category, key, value, comment, minimum, maximum).set(Float.toString(value));
    }
}
