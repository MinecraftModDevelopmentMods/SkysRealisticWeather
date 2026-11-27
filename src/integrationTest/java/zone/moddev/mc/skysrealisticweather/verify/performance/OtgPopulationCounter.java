package zone.moddev.mc.skysrealisticweather.verify.performance;

import com.pg85.otg.LocalWorld;
import com.pg85.otg.OTG;
import com.pg85.otg.events.EventHandler;
import com.pg85.otg.events.EventPriority;
import com.pg85.otg.forge.ForgeWorld;
import java.util.Random;
import net.minecraft.world.WorldServer;

public final class OtgPopulationCounter extends EventHandler {
    private final WorldgenPerformanceProbe probe;
    private OtgPopulationCounter(WorldgenPerformanceProbe probe) { this.probe = probe; }
    static void attach(WorldgenPerformanceProbe probe) {
        OTG.registerEventHandler(new OtgPopulationCounter(probe), EventPriority.MONITOR);
    }
    @Override
    public void onPopulateEnd(LocalWorld world, Random random, boolean village, int x, int z) {
        probe.otgDecorated((WorldServer) ((ForgeWorld) world).getWorld(), x, z);
    }
}
