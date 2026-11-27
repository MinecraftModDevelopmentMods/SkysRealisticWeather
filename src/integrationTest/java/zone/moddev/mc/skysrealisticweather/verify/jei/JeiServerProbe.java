package zone.moddev.mc.skysrealisticweather.verify.jei;

import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.api.*;

/** Ordinary guest client, controlled temperatures, and an actual disconnect/rejoin. */
public final class JeiServerProbe {
    private final WorldServer world;
    private final ResourceLocation zone=new ResourceLocation("jeiprobe:room");
    private EntityPlayerMP joined;
    private int stage,elapsed;
    private boolean reconnect,complete;
    public JeiServerProbe(WorldServer world) {
        this.world=world;MinecraftForge.EVENT_BUS.register(this);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.setWorldTime(134L*24000+6000);
        put(20);
        ((CommandHandler)world.getMinecraftServer().getCommandManager()).registerCommand(new CommandBase() {
            public String getName(){return "jeiprobeack";}
            public String getUsage(ICommandSender s){return "jeiprobeack <stage>";}
            public int getRequiredPermissionLevel(){return 0;}
            @Override public boolean checkPermission(MinecraftServer s,ICommandSender p){return p instanceof EntityPlayerMP&&p.getName().equals("JeiTester");}
            public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException {
                if(args.length!=1||parseInt(args[0],0,4)!=stage)return;
                System.out.println("JEI_SERVER_STAGE PASS "+stage);
                if(stage==3){reconnect=true;sender.sendMessage(new TextComponentString("JEI_RECONNECT"));return;}
                if(stage==4){complete=true;sender.sendMessage(new TextComponentString("JEI_COMPLETE"));System.out.println("JEI_SERVER PASS");return;}
                stage++;put(stage==1?-15:stage==2?45:20);announce((EntityPlayerMP)sender);
            }
        });
        System.out.println("JEI_SERVER_READY");
    }
    private void put(float c){WeatherApi.putTemperatureZone(world,TemperatureZone.celsius(zone,new BlockPos(5,79,5),new BlockPos(11,84,11),c));}
    private void announce(EntityPlayerMP p){p.sendMessage(new TextComponentString("JEI_STAGE:"+stage));}
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent e) {
        if(e.world!=world||e.phase!=TickEvent.Phase.END||complete)return;
        if(++elapsed>8000)throw new IllegalStateException("JEI server timeout at "+stage);
        EntityPlayerMP p=world.getMinecraftServer().getPlayerList().getPlayerByUsername("JeiTester");
        if(p==null||p==joined)return;
        if(reconnect)stage=4;
        joined=p;p.connection.setPlayerLocation(8.25,81,8.25,0,0);announce(p);
    }
}
