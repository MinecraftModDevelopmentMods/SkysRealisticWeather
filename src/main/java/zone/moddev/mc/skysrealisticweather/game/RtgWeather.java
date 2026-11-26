package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import rtg.api.util.WorldUtil;

/** Called only from RTG's Forge ICE pass, after RTG checks its snow-layers setting. */
public final class RtgWeather {
    private RtgWeather() {
    }

    public static boolean snow(WorldUtil rtg, BlockPos position, boolean checkLight, World world) {
        return WeatherHooks.generationSnow(world, position, checkLight, (w, p, light) -> rtg.canSnowAt(p, light));
    }
}
