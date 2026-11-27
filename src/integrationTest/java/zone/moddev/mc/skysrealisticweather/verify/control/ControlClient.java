package zone.moddev.mc.skysrealisticweather.verify.control;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
public final class ControlClient extends ControlProbe.CommonSide {
    private boolean connected,bothSeen,complete;private int ticks;
    @Override public void register(){MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void chat(ClientChatReceivedEvent event) {
        if(event.getMessage().getUnformattedText().equals("CONTROL_MEASURE_COMPLETE"))complete=true;
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null){
            if(!connected&&mc.currentScreen instanceof GuiMainMenu){connected=true;mc.displayGuiScreen(new GuiConnecting(mc.currentScreen,mc,"127.0.0.1",Integer.getInteger("skyweather.port")));}
            return;
        }
        if(mc.player==null)return;
        bothSeen|=mc.getConnection().getPlayerInfoMap().size()==2;
        if(!bothSeen)return;
        if(++ticks<100)return;
        if(mc.world.getWorldTime()!=134L*24000+6000||!mc.world.isRaining()||mc.world.getRainStrength(1)<.9F)throw new IllegalStateException("CONTROL_CLIENT incorrect server weather/date");
        if(!complete)return;
        System.out.println("CONTROL_CLIENT PASS "+mc.player.getName()+" time="+mc.world.getWorldTime()+" rain="+mc.world.getRainStrength(1));mc.shutdown();
    }
}
