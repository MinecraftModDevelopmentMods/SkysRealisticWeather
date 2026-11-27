package zone.moddev.mc.skysrealisticweather.compat;

import java.util.*;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;

/** Optional entry points are loaded only after the relevant mod is present. */
public final class Integrations {
    private static final Map<String, String> STATUS = new LinkedHashMap<>();
    public static java.util.function.Consumer<net.minecraft.world.WorldServer> weatherChanged = world -> { };
    public static Runnable worldClosed = () -> { };
    private Integrations() { }
    public static void init() {
        load("crafttweaker", "crafttweaker", "CraftTweakerBridge");
        load("toughasnails", "toughasnails", "ToughAsNailsBridge");
        load("waila", "waila", "WailaProvider");
        load("theoneprobe", "theoneprobe", "TopProvider");
        STATUS.put("jei",!Loader.isModLoaded("jei")?"absent":WeatherConfiguration.integration("jei")?"available on clients":"disabled");
    }
    public static void load(String key, String mod, String entry) {
        if (!Loader.isModLoaded(mod)) { STATUS.put(key, "absent"); return; }
        if (!WeatherConfiguration.integration(key)) { STATUS.put(key, "disabled"); return; }
        try {
            Class.forName("zone.moddev.mc.skysrealisticweather.compat." + entry).getMethod("register").invoke(null);
            STATUS.put(key, key.equals("waila")||key.equals("theoneprobe")?"awaiting callback":"active");
        } catch (ReflectiveOperationException | LinkageError failure) { failed(key, failure); }
    }
    public static void ready(String key) { STATUS.put(key,"active"); }
    public static void failed(String key, Throwable failure) {
        STATUS.put(key, "unavailable: " + failure.getClass().getSimpleName());
        org.apache.logging.log4j.LogManager.getLogger("Sky's Realistic Weather").warn("Optional {} integration unavailable", key, failure);
    }
    public static Map<String, String> status() { return Collections.unmodifiableMap(new LinkedHashMap<>(STATUS)); }
}
