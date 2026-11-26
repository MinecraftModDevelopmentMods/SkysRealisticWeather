package zone.moddev.mc.skysrealisticweather.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Cancel on the Forge event bus to veto a seasonal surface change. */
@Cancelable
public final class SeasonalSurfaceEvent extends Event {
    public enum Action { ACCUMULATE_SNOW, FREEZE_WATER, THAW_SNOW, THAW_ICE }

    public final WorldServer world;
    public final BlockPos position;
    public final IBlockState original;
    public final IBlockState proposed;
    public final Action action;

    public SeasonalSurfaceEvent(WorldServer world, BlockPos position, IBlockState original,
                                IBlockState proposed, Action action) {
        this.world = world;
        this.position = position.toImmutable();
        this.original = original;
        this.proposed = proposed;
        this.action = action;
    }
}
