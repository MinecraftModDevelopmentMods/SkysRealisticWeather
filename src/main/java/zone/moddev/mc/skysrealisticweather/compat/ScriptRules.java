package zone.moddev.mc.skysrealisticweather.compat;

import java.util.*;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthProfile;
import zone.moddev.mc.skysrealisticweather.climate.*;

/** Script actions own pending definitions; running worlds use an immutable snapshot. */
public final class ScriptRules {
    private static final Map<String, PlantGrowthProfile> PENDING_CROPS = new LinkedHashMap<>();
    private static final Map<String, String> PENDING_BIOMES = new LinkedHashMap<>();
    private static Map<String, PlantGrowthProfile> crops = Collections.emptyMap();
    private static Map<String, String> biomes = Collections.emptyMap();
    private static boolean active, running, pending;
    private ScriptRules() { }

    public static PlantGrowthProfile crop(String selector, PlantGrowthProfile profile) {
        new FarmingSettings.Selector(selector);
        if (profile == null) return PENDING_CROPS.remove(selector);
        if(!PENDING_CROPS.containsKey(selector)&&PENDING_CROPS.size()>=8192)throw new IllegalArgumentException("Too many script crop rules (maximum 8192)");
        return PENDING_CROPS.put(selector, profile);
    }
    public static String biome(String biome, String preset) {
        if (biome==null||!biome.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid biome registry ID");
        if (preset == null) return PENDING_BIOMES.remove(biome);
        Preset resolved=WeatherConfiguration.preset(preset);
        if (resolved == null) throw new IllegalArgumentException("Unknown climate preset: " + preset);
        Set<String> ids=new HashSet<>(PENDING_BIOMES.keySet());ids.add(biome);
        WeatherSettings defaults=WeatherConfiguration.startupSettings();
        if(defaults!=null)ids.addAll(defaults.biomes.mappings.keySet());
        if(ids.size()>8192)throw new IllegalArgumentException("Too many biome mappings (maximum 8192)");
        return PENDING_BIOMES.put(biome, resolved.id);
    }
    public static void reloaded() {
        if (!running) {
            activate();
            zone.moddev.mc.skysrealisticweather.game.ServerWeather.INSTANCE.reloadStartupRules();
        } else if (active) {
            pending = !biomes.equals(PENDING_BIOMES)||!sameCrops();
            if(pending)org.apache.logging.log4j.LogManager.getLogger("Sky's Realistic Weather").info(
                    "Weather script definitions are pending a world restart; active weather rules were retained.");
        }
    }
    private static boolean sameCrops() {
        if(!crops.keySet().equals(PENDING_CROPS.keySet()))return false;
        for(String key:crops.keySet()) {
            PlantGrowthProfile a=crops.get(key),b=PENDING_CROPS.get(key);
            if(a.exempt!=b.exempt||(!a.exempt&&(a.minimum!=b.minimum||a.idealMinimum!=b.idealMinimum||a.idealMaximum!=b.idealMaximum||a.maximum!=b.maximum)))return false;
        }
        return true;
    }
    public static void activate() {
        crops = Collections.unmodifiableMap(new LinkedHashMap<>(PENDING_CROPS));
        biomes = Collections.unmodifiableMap(new LinkedHashMap<>(PENDING_BIOMES));
        active = true; pending = false;
    }
    public static void started() { running = true; }
    public static void close() { active = running = pending = false; crops = Collections.emptyMap(); biomes = Collections.emptyMap(); }
    public static boolean pending() { return pending; }
    public static Map<String, PlantGrowthProfile> crops() { return crops; }
    public static Map<String, String> biomes() { return biomes; }
    public static WeatherSettings apply(WeatherSettings base) {
        Map<String, String> mappings = new LinkedHashMap<>(base.biomes.mappings);
        mappings.putAll(biomes);
        return base.withFarming(base.farming.withScriptProfiles(crops))
                .withMode(base.mode, new BiomeClimateMap(base.biomes.presets, mappings));
    }
}
