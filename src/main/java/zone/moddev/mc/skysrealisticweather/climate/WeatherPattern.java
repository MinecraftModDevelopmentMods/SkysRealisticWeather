package zone.moddev.mc.skysrealisticweather.climate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;

/** The editable event rows for one climate and the built-in defaults. */
public final class WeatherPattern {
    private static final String ROOT = "/assets/skysrealisticweather/";
    private static final Map<WeatherKind, String> TIMING = loadTiming();
    private static final Map<String, WeatherPattern> DEFAULTS = loadDefaults();
    public final List<Rule> rules;
    private final String[] rows;

    public WeatherPattern(String[] rows) {
        Map<WeatherKind, Rule> byKind = new EnumMap<>(WeatherKind.class);
        for (String row : rows) {
            Rule rule = parse(row);
            if (byKind.putIfAbsent(rule.kind, rule) != null) {
                throw new IllegalArgumentException("Duplicate weather event " + rule.kind);
            }
        }
        rules = Collections.unmodifiableList(new ArrayList<>(byKind.values()));
        this.rows = rows.clone();
    }

    public String[] rows() {
        return rows.clone();
    }

    public static WeatherPattern defaults(String preset) {
        return DEFAULTS.getOrDefault(preset, DEFAULTS.get(Preset.DEFAULT_ID));
    }

    static boolean hasDefaults(String preset) {
        return DEFAULTS.containsKey(preset);
    }

    private static Map<WeatherKind, String> loadTiming() {
        Map<WeatherKind, String> result = new EnumMap<>(WeatherKind.class);
        for (Map.Entry<String, JsonElement> entry : resource("weather_timing.json").entrySet()) {
            result.put(kind(entry.getKey()), entry.getValue().getAsString());
        }
        if (result.size() != WeatherKind.values().length) throw new IllegalArgumentException("Incomplete weather defaults");
        return result;
    }

    private static Map<String, WeatherPattern> loadDefaults() {
        Map<String, WeatherPattern> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : resource("weather_weights.json").entrySet()) {
            List<String> rows = new ArrayList<>();
            for (JsonElement row : entry.getValue().getAsJsonArray()) rows.add(row.getAsString());
            result.put(entry.getKey(), new WeatherPattern(rows.toArray(new String[0])));
        }
        if (!result.containsKey(Preset.DEFAULT_ID)) throw new IllegalArgumentException("Missing Burlington weather defaults");
        return Collections.unmodifiableMap(result);
    }

    static JsonObject resource(String name) {
        try (InputStream input = WeatherPattern.class.getResourceAsStream(ROOT + name)) {
            if (input == null) throw new IllegalStateException("Missing " + name);
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read " + name, failure);
        }
    }

    private static WeatherKind kind(String value) {
        return WeatherKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static Rule parse(String row) {
        try {
            String[] values = row.split(",", -1);
            WeatherKind event = kind(values[0]);
            boolean includesDryBiomes = "include_dry_biomes".equalsIgnoreCase(values[values.length - 1].trim());
            if (includesDryBiomes) values = Arrays.copyOf(values, values.length - 1);
            if (values.length == 5) values = (String.join(",", values) + "," + TIMING.get(event)).split(",", -1);
            if (values.length != 11) throw new IllegalArgumentException("Expected 5 or 11 fields before the optional biome flag");
            float[] rates = new float[4];
            for (int index = 0; index < rates.length; index++) rates[index] = number(values[index + 1], 0, 100);
            int first = integer(values[5], 0, ClimateProfile.TICKS_PER_DAY - 1);
            int last = integer(values[6], first, ClimateProfile.TICKS_PER_DAY - 1);
            int shortest = integer(values[7], 200, WeatherEpisode.MAX_DURATION);
            int longest = integer(values[8], shortest, WeatherEpisode.MAX_DURATION);
            float low = number(values[9], 0.01F, 1);
            return new Rule(event, rates, first, last, shortest, longest, low, number(values[10], low, 1),
                    includesDryBiomes);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid weather row '" + row + "'. " + invalid.getMessage(), invalid);
        }
    }

    private static int integer(String value, int min, int max) {
        int number = Integer.parseInt(value.trim());
        if (number < min || number > max) throw new IllegalArgumentException("Expected an integer from " + min + " to " + max);
        return number;
    }

    private static float number(String value, float min, float max) {
        float number = Float.parseFloat(value.trim());
        if (!Float.isFinite(number) || number < min || number > max) {
            throw new IllegalArgumentException("Expected a number from " + min + " to " + max);
        }
        return number;
    }

    public static final class Rule {
        public final WeatherKind kind;
        public final int firstStart;
        public final int lastStart;
        public final int minDuration;
        public final int maxDuration;
        public final float minPeak;
        public final float maxPeak;
        public final boolean includesDryBiomes;
        private final float[] rates;

        private Rule(WeatherKind kind, float[] rates, int firstStart, int lastStart, int minDuration,
                     int maxDuration, float minPeak, float maxPeak, boolean includesDryBiomes) {
            this.kind = kind;
            this.rates = rates;
            this.firstStart = firstStart;
            this.lastStart = lastStart;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
            this.minPeak = minPeak;
            this.maxPeak = maxPeak;
            this.includesDryBiomes = includesDryBiomes;
        }

        public double weight(ClimateProfile.Reading reading, float strength) {
            return weight(reading, strength, (rates[0] + rates[1] + rates[2] + rates[3]) / 4);
        }

        public double weight(ClimateProfile.Reading reading, ClimateProfile profile) {
            float mean = 0;
            for (int i = 0; i < 4; i++) mean += rates[i] * profile.period(i).days / profile.daysInYear();
            return weight(reading, profile.strength, mean);
        }

        private double weight(ClimateProfile.Reading reading, float strength, float mean) {
            int phase = reading.calendar.season.ordinal();
            double progress = reading.calendar.progress;
            int neighbour = (phase + (progress < 0.5 ? 3 : 1)) % 4;
            double blend = Math.max(0, Math.abs(progress - 0.5) - 0.25) * 2;
            float seasonal = ClimateProfile.mix(rates[phase], rates[neighbour], blend);
            float rate = ClimateProfile.mix(mean, seasonal, Math.min(1, strength));
            if (kind.thunder) return rate * Math.sqrt(reading.thunder) * Math.sqrt(reading.wetness);
            if (kind.wet) return rate * Math.sqrt(reading.wetness);
            if (kind == WeatherKind.RADIATION_FOG || kind == WeatherKind.ADVECTION_FOG) {
                return rate * Math.min(1.6, reading.fog * 2);
            }
            return rate;
        }
    }
}
