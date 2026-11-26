package zone.moddev.mc.skysrealisticweather.core;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;

/** Farming compatibility failures never disable weather or another adapter. */
public final class PlantHookStatus {
    private static final Map<String, String> states = new LinkedHashMap<>();
    private PlantHookStatus() { }
    public static synchronized void attached(String name) { states.put(name, "attached"); }
    public static synchronized void failed(String name, Exception problem) {
        if (states.getOrDefault(name, "").startsWith("native fallback:")) return;
        states.put(name, "native fallback: " + problem.getMessage());
        LogManager.getLogger("Sky's Realistic Weather").warn("Plant adapter {} is unavailable; its native behaviour is retained.", name, problem);
    }
    public static synchronized boolean fallback(String name) {
        return states.getOrDefault(name, "").startsWith("native fallback:");
    }
    public static synchronized Map<String, String> states() { return new LinkedHashMap<>(states); }
}
