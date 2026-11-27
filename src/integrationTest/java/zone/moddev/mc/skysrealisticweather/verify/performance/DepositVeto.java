package zone.moddev.mc.skysrealisticweather.verify.performance;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.api.SeasonalSurfaceEvent;

/** Diagnostic only: isolate climate queries from the physical effect of generated cover. */
public final class DepositVeto {
    private final WorldServer world;
    private static int count;
    private DepositVeto(WorldServer world){this.world=world;}
    public static void attach(WorldServer world){MinecraftForge.EVENT_BUS.register(new DepositVeto(world));}
    public static int count(){return count;}
    @SubscribeEvent public void surface(SeasonalSurfaceEvent event) {
        if(event.world==world && (event.action==SeasonalSurfaceEvent.Action.ACCUMULATE_SNOW||event.action==SeasonalSurfaceEvent.Action.FREEZE_WATER)) {
            event.setCanceled(true);count++;
        }
    }
}
