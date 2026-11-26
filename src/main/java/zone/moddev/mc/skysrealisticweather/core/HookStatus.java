package zone.moddev.mc.skysrealisticweather.core;

import org.apache.logging.log4j.LogManager;

public final class HookStatus {
    private static volatile boolean available = true;

    private HookStatus() {
    }

    public static boolean available() {
        return available;
    }

    static void failed(String target, RuntimeException problem) {
        available = false;
        LogManager.getLogger("Sky's Realistic Weather").error(
                "Weather hooks did not match {}. This instance will use passive weather.", target, problem);
    }

    static void generationFailed(String target, RuntimeException problem) {
        LogManager.getLogger("Sky's Realistic Weather").warn(
                "Seasonal chunk decoration could not be attached to {}. Its native snow and ice generation will run.",
                target, problem);
    }
}
