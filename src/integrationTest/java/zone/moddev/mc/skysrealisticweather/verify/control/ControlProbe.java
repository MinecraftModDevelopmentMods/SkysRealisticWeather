package zone.moddev.mc.skysrealisticweather.verify.control;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraft.util.text.TextComponentString;
import java.lang.management.*;
import java.util.*;
@Mod(modid="weathercontrolprobe", name="Multiplayer Control Probe", version="1")
public final class ControlProbe {
    @SidedProxy(clientSide="zone.moddev.mc.skysrealisticweather.verify.control.ControlClient",serverSide="zone.moddev.mc.skysrealisticweather.verify.control.ControlProbe$CommonSide")
    public static CommonSide proxy;
    private WorldServer world; private int ticks, samples;
    private long cpuStart,wallStart,windowCpuStart;
    private final long[] cpuTimes=new long[600],wallTimes=new long[600];
    private final ThreadMXBean timer=ManagementFactory.getThreadMXBean();
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if(!Boolean.getBoolean("skyweather.control")) return;
        MinecraftForge.EVENT_BUS.register(this); proxy.register();
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        if(!Boolean.getBoolean("skyweather.control")) return;
        if(Loader.isModLoaded("skysrealisticweather")!=Boolean.getBoolean("skyweather.controlWeather"))
            throw new IllegalStateException("Wrong multiplayer comparison profile");
        world=FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");
        world.getGameRules().setOrCreateGameRule("doWeatherCycle","false");
        world.setWorldTime(134L*24000+6000); world.getWorldInfo().setRaining(true);
        world.getMinecraftServer().getCommandManager().executeCommand(world.getMinecraftServer(),"weather rain 6000");
        world.getWorldInfo().setRainTime(120000);
        for(int i=0;i<100;i++)world.updateWeatherBody();
        timer.setThreadCpuTimeEnabled(true);
        System.out.println("CONTROL_SERVER_READY");
    }
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if(world==null||event.world!=world||event.phase!=TickEvent.Phase.END||world.playerEntities.size()!=2)return;
        ticks++;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void begin(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.START && world!=null && world.playerEntities.size()==2 && ticks>=120 && samples<600) {
            cpuStart=timer.getCurrentThreadCpuTime();wallStart=System.nanoTime();
            if(samples==0)windowCpuStart=cpuStart;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void end(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || cpuStart==0)return;
        cpuTimes[samples]=timer.getCurrentThreadCpuTime()-cpuStart;
        wallTimes[samples++]=System.nanoTime()-wallStart;cpuStart=0;
        if(samples!=600)return;
        double windowCpu=(timer.getCurrentThreadCpuTime()-windowCpuStart)/600e6;
        double cpu=Arrays.stream(cpuTimes).average().getAsDouble()/1e6;
        double wall=Arrays.stream(wallTimes).average().getAsDouble()/1e6;
        Arrays.sort(wallTimes);
        // Windows thread CPU counters are coarse; compare the entire window, not individual tick deltas.
        System.out.printf(Locale.ROOT,"MULTIPLAYER_PERF_RESULT weather=%s samples=%d cpu_window_mean_ms=%.5f cpu_inner_mean_ms=%.5f wall_mean_ms=%.5f wall_p95_ms=%.5f wall_p99_ms=%.5f%n",
                Loader.isModLoaded("skysrealisticweather"),samples,windowCpu,cpu,wall,wallTimes[569]/1e6,wallTimes[593]/1e6);
        System.out.println("CONTROL_SERVER PASS players=2 time="+world.getWorldTime()+" rain="+world.isRaining());
        for(net.minecraft.entity.player.EntityPlayer player:world.playerEntities)
            player.sendMessage(new TextComponentString("CONTROL_MEASURE_COMPLETE"));
    }
    public static class CommonSide { public void register() {} }
}
