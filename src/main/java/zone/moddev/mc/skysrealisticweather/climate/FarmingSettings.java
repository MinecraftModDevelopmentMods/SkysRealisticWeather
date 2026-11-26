package zone.moddev.mc.skysrealisticweather.climate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthProfile;
import zone.moddev.mc.skysrealisticweather.game.PlantProfiles;

/** Immutable server-authoritative rules. Caches contain only canonical block states, never worlds or chunks. */
public final class FarmingSettings {
    public static final String CATEGORY = "plant_growth";
    private static final JsonObject DEFAULT_DATA = load();
    public static final FarmingSettings DEFAULT = new FarmingSettings(true, true, true, true, true, defaultProfiles(), new String[0], Collections.emptyMap());
    public final boolean enabled, harvestcraft, agricraft, mystical, forestry;
    private final String[] profileRows, mappingRows;
    private final Map<String, PlantGrowthProfile> profiles = new LinkedHashMap<>();
    private final Map<String, List<Mapping>> overrides = new HashMap<>(), registrations = new HashMap<>(), defaults = new HashMap<>();
    private final Map<IBlockState, Resolved> states = Collections.synchronizedMap(new IdentityHashMap<>());

    public FarmingSettings(boolean enabled, boolean harvestcraft, boolean agricraft, boolean mystical, boolean forestry,
                           String[] profileRows, String[] mappingRows, Map<String, PlantGrowthProfile> registered) {
        if (profileRows.length > 512 || mappingRows.length > 8192 || registered.size() > 8192) throw new IllegalArgumentException("Too many plant rules");
        this.enabled = enabled; this.harvestcraft = harvestcraft; this.agricraft = agricraft;
        this.mystical = mystical; this.forestry = forestry;
        this.profileRows = profileRows.clone(); this.mappingRows = mappingRows.clone();
        profiles.put("exempt", PlantGrowthProfile.EXEMPT);
        for (String row : profileRows) {
            try {
                String[] p = row.split(",", -1);
                if (p.length != 5 || !p[0].matches("[a-z0-9_]+") || p[0].equals("exempt")) throw new IllegalArgumentException("profile format");
                profiles.put(p[0], new PlantGrowthProfile(Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4])));
            } catch (IllegalArgumentException invalid) { warn(row, invalid); }
        }
        for (Map.Entry<String, JsonElement> e : DEFAULT_DATA.getAsJsonObject("mappings").entrySet()) {
            add(defaults, new Mapping(e.getKey(), e.getValue().getAsString(), null));
        }
        registered.forEach((key, profile) -> add(registrations, new Mapping(key, "api:" + key, profile)));
        for (String row : mappingRows) {
            try {
                int split = row.lastIndexOf('=');
                if (split <= 0) throw new IllegalArgumentException("expected selector=profile");
                String id = row.substring(split + 1).trim();
                // An invalid explicit profile falls back to native, rather than silently using another rule.
                add(overrides, new Mapping(row.substring(0, split).trim(), id, null));
            } catch (IllegalArgumentException invalid) { warn(row, invalid); }
        }
    }

    private static void warn(String row, Exception problem) {
        LogManager.getLogger("Sky's Realistic Weather").warn("Invalid plant rule '{}': {}. Native growth is retained for unresolved rules.", row, problem.getMessage());
    }

    public static FarmingSettings read(Configuration config) {
        return new FarmingSettings(config.getBoolean("enabled", CATEGORY, true, "Apply temperature suitability to natural plant growth. Fertiliser keeps its native behaviour."),
                config.getBoolean("harvestcraft", CATEGORY, true, "Natural HarvestCraft field crop and fruit growth."),
                config.getBoolean("agricraft", CATEGORY, true, "Natural AgriCraft crop-stick growth; preserve fertiliser and genetics."),
                config.getBoolean("mystical", CATEGORY, true, "Allow explicit climate profiles for magical crops; unmapped magical crops remain exempt."),
                config.getBoolean("forestry", CATEGORY, true, "Connect Forestry ambient climate and recognise its controlled climates."),
                config.get(CATEGORY, "profiles", defaultProfiles(), "Rows: id,minimum,ideal minimum,ideal maximum,maximum. Minecraft temperature units. Exempt is a reserved built-in profile.").getStringList(),
                config.get(CATEGORY, "mappings", new String[0], "Overrides: minecraft:wheat=cool or minecraft:sapling[type=spruce]=cool. AgriCraft: agricraft:tomato_plant=warm. Config overrides API registrations, then bundled mappings. Unknown plants retain native growth.").getStringList(),
                PlantProfiles.registered());
    }

    public FarmingSettings withEnabled(boolean value) {
        Map<String, PlantGrowthProfile> registered = registeredProfiles();
        return new FarmingSettings(value, harvestcraft, agricraft, mystical, forestry, profileRows, mappingRows, registered);
    }

    public String[] profileRows() { return profileRows.clone(); }
    public String[] mappingRows() { return mappingRows.clone(); }
    public Map<String, PlantGrowthProfile> registeredProfiles() {
        Map<String, PlantGrowthProfile> result = new LinkedHashMap<>();
        registrations.values().forEach(list -> list.forEach(m -> result.put(m.selector.text, m.profile)));
        return result;
    }

    public Resolved resolve(String plantId, IBlockState state) {
        String block = state.getBlock().getRegistryName().toString();
        boolean cache = plantId.equals(block);
        if (cache && states.containsKey(state)) return states.get(state);
        Mapping mapping = match(overrides, plantId, block, state);
        if (mapping == null) mapping = match(registrations, plantId, block, state);
        if (mapping == null) mapping = match(defaults, plantId, block, state);
        Resolved result = mapping == null ? null : new Resolved(mapping.id, mapping.profile != null ? mapping.profile : profiles.get(mapping.id));
        if (cache) states.put(state, result);
        return result;
    }

    private static Mapping match(Map<String, List<Mapping>> map, String key, String block, IBlockState state) {
        Mapping m = match(map.get(key), state);
        return m == null && !key.equals(block) ? match(map.get(block), state) : m;
    }
    private static Mapping match(List<Mapping> list, IBlockState state) {
        if (list != null) for (Mapping m : list) if (m.selector.matches(state)) return m;
        return null;
    }
    private static void add(Map<String, List<Mapping>> map, Mapping mapping) {
        List<Mapping> list = map.computeIfAbsent(mapping.selector.id, ignored -> new ArrayList<>());
        list.add(0, mapping);
        list.sort(Comparator.comparingInt((Mapping m) -> m.selector.properties.size()).reversed());
    }

    public static final class Resolved {
        public final String id;
        public final PlantGrowthProfile profile;
        Resolved(String id, PlantGrowthProfile profile) { this.id = id; this.profile = profile; }
    }
    private static final class Mapping {
        final Selector selector;
        final String id;
        final PlantGrowthProfile profile;
        Mapping(String selector, String id, PlantGrowthProfile profile) { this.selector = new Selector(selector); this.id = id; this.profile = profile; }
    }
    public static final class Selector {
        final String id, text;
        final Map<String, String> properties = new LinkedHashMap<>();
        public Selector(String text) {
            this.text = text;
            int bracket = text.indexOf('[');
            id = bracket < 0 ? text : text.substring(0, bracket);
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid plant ID");
            if (bracket >= 0) {
                if (!text.endsWith("]")) throw new IllegalArgumentException("Unclosed state selector");
                for (String part : text.substring(bracket + 1, text.length() - 1).split(",")) {
                    String[] pair = part.split("=", -1);
                    if (pair.length != 2 || !pair[0].matches("[a-z0-9_]+") || !pair[1].matches("[a-z0-9_.-]+")
                            || properties.putIfAbsent(pair[0], pair[1]) != null) throw new IllegalArgumentException("Invalid property selector");
                }
            }
        }
        private boolean matches(IBlockState state) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                IProperty property = state.getBlock().getBlockState().getProperty(entry.getKey());
                if (property == null || !property.getName(state.getValue(property)).equals(entry.getValue())) return false;
            }
            return true;
        }
    }

    public static String[] defaultProfiles() {
        return new String[] {"cool,0.05,0.40,1.10,1.70", "temperate,0.15,0.60,1.30,2.00", "warm,0.45,0.95,1.70,2.40",
                "tropical,0.70,1.05,1.85,2.60", "desert,0.25,1.00,2.20,3.20"};
    }
    private static JsonObject load() {
        try (InputStream input = FarmingSettings.class.getResourceAsStream("/assets/skysrealisticweather/plant_growth.json")) {
            if (input == null) throw new IllegalStateException("Missing plant library");
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) { throw new IllegalStateException("Cannot read plant library", failure); }
    }
}
