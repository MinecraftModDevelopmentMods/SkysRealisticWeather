package zone.moddev.mc.skysrealisticweather.api;

import java.util.Optional;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Posted on the Forge event bus when a scheduled or forced episode starts, changes or ends. */
public final class WeatherEpisodeChangedEvent extends Event {
    public final WorldServer world;
    public final Optional<WeatherEpisode> previous;
    public final Optional<WeatherEpisode> current;

    public WeatherEpisodeChangedEvent(WorldServer world, WeatherEpisode previous, WeatherEpisode current) {
        this.world = world;
        this.previous = Optional.ofNullable(previous);
        this.current = Optional.ofNullable(current);
    }
}
