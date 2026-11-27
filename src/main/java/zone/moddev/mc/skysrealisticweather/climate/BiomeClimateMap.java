package zone.moddev.mc.skysrealisticweather.climate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

/** Immutable preset definitions and registry mappings, with a cache for generator-supplied biomes. */
public final class BiomeClimateMap {
    private static final Map<String, String> STARTER_MAPPINGS = loadMappings();
    public final Map<String, Preset> presets;
    public final Map<String, String> mappings;
    public final Set<String> scheduledPresets;
    private final Map<Biome, Preset> resolved = new ConcurrentHashMap<>();

    public BiomeClimateMap(Map<String, Preset> presets, Map<String, String> mappings) {
        Map<String, Preset> complete = new LinkedHashMap<>(PresetCatalog.defaults());
        complete.putAll(presets);
        this.presets = Collections.unmodifiableMap(complete);
        this.mappings = Collections.unmodifiableMap(new LinkedHashMap<>(mappings));
        Set<String> scheduled = new LinkedHashSet<>(mappings.values());
        PresetCatalog.defaults().values().stream().filter(p -> p.group.ordinal() <= Preset.Group.HIGHLANDS.ordinal())
                .forEach(p -> scheduled.add(p.id));
        scheduledPresets = Collections.unmodifiableSet(scheduled);
    }

    public Preset preset(Biome biome) {
        return resolved.computeIfAbsent(biome, value -> {
            String id = value.getRegistryName() == null ? null : mappings.get(value.getRegistryName().toString());
            return presets.getOrDefault(id == null ? classify(value) : id, PresetCatalog.defaults().get("central_european"));
        });
    }

    public static String classify(Biome biome) {
        String known = biome.getRegistryName() == null ? null : STARTER_MAPPINGS.get(biome.getRegistryName().toString());
        if (known != null) return known;
        String name = (biome.getRegistryName() == null ? biome.getBiomeName() : biome.getRegistryName().toString())
                .toLowerCase(java.util.Locale.ROOT);
        float temperature = biome.getTemperature();
        float rainfall = biome.getRainfall();
        if (name.contains("glacier") || name.contains("ice_cap")) return "ice_cap";
        if (name.contains("tundra") || temperature <= 0.05F && !is(biome, BiomeDictionary.Type.CONIFEROUS)) return "tundra";
        if (is(biome, BiomeDictionary.Type.CONIFEROUS) && temperature < 0.3F) return "subarctic";
        if (is(biome, BiomeDictionary.Type.JUNGLE) || name.contains("tropical") || name.contains("rainforest")) {
            return rainfall >= 0.8F ? "tropical_rainforest" : "tropical_monsoon";
        }
        if (is(biome, BiomeDictionary.Type.SAVANNA)) return "tropical_savanna";
        if (is(biome, BiomeDictionary.Type.SANDY) || is(biome, BiomeDictionary.Type.MESA)) {
            return temperature >= 1 ? "hot_desert" : "cold_desert";
        }
        if (name.contains("chaparral") || name.contains("mediterranean")) return "mediterranean";
        if (is(biome, BiomeDictionary.Type.DRY) || rainfall < 0.2F) return "semi_arid_steppe";
        if (is(biome, BiomeDictionary.Type.MOUNTAIN)) {
            return temperature >= 0.8F ? "subtropical_highland" : "alpine_highlands";
        }
        if (temperature >= 0.9F) return "humid_subtropical";
        if (is(biome, BiomeDictionary.Type.SWAMP)) return "oxford";
        if (is(biome, BiomeDictionary.Type.OCEAN) || is(biome, BiomeDictionary.Type.BEACH)) {
            return temperature < 0.3F ? "cool_maritime" : "oxford";
        }
        if (temperature < 0.4F) return "snowy_continental";
        if (is(biome, BiomeDictionary.Type.CONIFEROUS)) return "burlington";
        return "central_european";
    }

    private static boolean is(Biome biome, BiomeDictionary.Type type) {
        return BiomeDictionary.hasType(biome, type);
    }

    public static BiomeClimateMap defaults() {
        return new BiomeClimateMap(PresetCatalog.defaults(), Collections.emptyMap());
    }

    private static Map<String, String> loadMappings() {
        try (java.io.InputStream input = BiomeClimateMap.class.getResourceAsStream("/assets/skysrealisticweather/biome_climates.json")) {
            if (input == null) throw new IllegalStateException("Missing biome climate mappings");
            com.google.gson.JsonObject values = new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(
                    input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : values.entrySet()) {
                String id = entry.getValue().getAsString();
                if (!PresetCatalog.defaults().containsKey(id)) throw new IllegalStateException("Invalid starter climate " + id);
                result.put(entry.getKey(), id);
            }
            return Collections.unmodifiableMap(result);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read biome climate mappings", failure);
        }
    }
}
