package zone.moddev.mc.skysrealisticweather.game;

import java.util.LinkedHashMap;
import java.util.Map;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthProfile;

/** Registrations close after mod loading, as biome registrations do. Config overrides are resolved separately. */
public final class PlantProfiles {
    private static boolean closed;
    private static final Map<String, PlantGrowthProfile> profiles = new LinkedHashMap<>();

    private PlantProfiles() { }

    public static synchronized void register(String selector, PlantGrowthProfile profile) {
        if (closed) throw new IllegalStateException("Plant registration has closed");
        new zone.moddev.mc.skysrealisticweather.climate.FarmingSettings.Selector(selector);
        if (profile == null || profiles.putIfAbsent(selector, profile) != null) {
            throw new IllegalArgumentException("Duplicate or invalid plant profile: " + selector);
        }
    }

    public static synchronized Map<String, PlantGrowthProfile> registered() {
        return new LinkedHashMap<>(profiles);
    }

    public static synchronized void freeze() { closed = true; }
}
