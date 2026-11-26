package zone.moddev.mc.skysrealisticweather.api;

import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Posted on the Forge event bus after a server changes season. */
public final class SeasonChangedEvent extends Event {
    public final WorldServer world;
    public final Season previous;
    public final SeasonSnapshot current;

    public SeasonChangedEvent(WorldServer world, Season previous, SeasonSnapshot current) {
        this.world = world;
        this.previous = previous;
        this.current = current;
    }
}
