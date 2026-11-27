package zone.moddev.mc.skysrealisticweather.verify;

import java.util.*;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay;
import zone.moddev.mc.skysrealisticweather.instrument.Thermometer;

/** Coordinates assertions in two real clients using ordinary chat commands as acknowledgements. */
public final class TemperatureNetworkProbe {
    private final WorldServer world;
    private final Set<String> acknowledgements = new HashSet<>();
    private final ResourceLocation room = new ResourceLocation("networkprobe:room"), remote = new ResourceLocation("networkprobe:remote");
    private final BlockPos origin = new BlockPos(8,81,8), torch = origin.east(), furnace = origin.east(2);
    private int stage=-1, elapsed, total;
    TemperatureNetworkProbe(WorldServer world) {
        this.world=world; MinecraftForge.EVENT_BUS.register(this);
        ((CommandHandler)world.getMinecraftServer().getCommandManager()).registerCommand(new CommandBase() {
            public String getName(){return "weatherprobeack";}
            public String getUsage(ICommandSender sender){return "weatherprobeack <stage>";}
            public int getRequiredPermissionLevel(){return 0;}
            @Override public boolean checkPermission(MinecraftServer server,ICommandSender sender) {
                return sender instanceof EntityPlayerMP && (sender.getName().equals("WeatherTester1")||sender.getName().equals("WeatherTester2"));
            }
            public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException {
                System.out.println("THERMAL_ACK sender="+sender.getName()+" stage="+stage+" args="+Arrays.toString(args));
                if(sender instanceof EntityPlayerMP && args.length==1 && parseInt(args[0],0,10)==stage)
                    acknowledgements.add(sender.getName());
            }
        });
        world.getGameRules().setOrCreateGameRule("doDaylightCycle","false"); world.setWorldTime(134L*24000+6000);
        for(int x=5;x<=12;x++)for(int z=5;z<=11;z++) {
            BlockPos p=new BlockPos(x,80,z); world.setBlockToAir(p.up()); world.setBlockState(p,Blocks.GRASS.getDefaultState(),3);
        }
        BlockPos crop=origin.south();world.setBlockState(crop.down(),Blocks.FARMLAND.getDefaultState(),3);world.setBlockState(crop,Blocks.WHEAT.getDefaultState(),3);
        world.setBlockState(origin.west(),((net.minecraft.block.Block)Thermometer.BLOCK).getDefaultState(),3);
        put(room,origin,25);
        System.out.println("THERMAL_SERVER_READY");
    }
    private void put(ResourceLocation id,BlockPos center,float celsius) {
        WeatherApi.putTemperatureZone(world,TemperatureZone.celsius(id,center.add(-3,-2,-3),center.add(3,2,3),celsius));
    }
    private EntityPlayerMP player(int n){return world.getMinecraftServer().getPlayerList().getPlayerByUsername("WeatherTester"+n);}
    private void announce(EntityPlayerMP p){p.sendMessage(new TextComponentString("WEATHER_THERMAL_STAGE:"+stage));}
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if(event.world!=world||event.phase!=TickEvent.Phase.END)return;
        if(++total>5000)throw new IllegalStateException("THERMAL_NETWORK timeout stage="+stage+" ack="+acknowledgements);
        if(stage<0) {
            if(player(1)==null||player(2)==null)return;
            for(int n=1;n<=2;n++) {
                EntityPlayerMP p=player(n);p.connection.setPlayerLocation(8.25,81,8.25,0,0);
                p.inventory.setInventorySlotContents(0,new ItemStack(Thermometer.BLOCK));p.inventoryContainer.detectAndSendChanges();
            }
            advance();return;
        }
        if(++elapsed%20==0)for(int n=1;n<=2;n++)if(player(n)!=null)announce(player(n));
        if(acknowledgements.size()!=2)return;
        if(stage==0 || stage==9)for(int n=1;n<=2;n++)
            WeatherProbe.require(TemperatureDisplay.forSender(player(n))==(n==1?TemperatureDisplay.Unit.CELSIUS:TemperatureDisplay.Unit.FAHRENHEIT),"independent server unit preferences");
        System.out.println("THERMAL_SERVER_STAGE PASS "+stage+" players="+acknowledgements);
        if(stage==10) {
            WeatherApi.removeTemperatureZone(world,room);WeatherApi.removeTemperatureZone(world,remote);
            for(int n=1;n<=2;n++)player(n).sendMessage(new TextComponentString("WEATHER_THERMAL_COMPLETE"));
            System.out.println("SKY_WEATHER_CHECK THERMAL_NETWORK PASS");
            world.getMinecraftServer().initiateShutdown(); stage=11;return;
        }
        advance();
    }
    private void advance() {
        stage++;elapsed=0;acknowledgements.clear();
        switch(stage) {
            case 1:put(room,origin,-15);break;
            case 2:WeatherApi.removeTemperatureZone(world,room);break;
            case 3:world.setBlockState(torch,Blocks.TORCH.getDefaultState(),3);break;
            case 4:
                world.setBlockState(furnace,Blocks.LIT_FURNACE.getDefaultState(),3);
                TileEntityFurnace tile=(TileEntityFurnace)world.getTileEntity(furnace);
                tile.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE,64));tile.setInventorySlotContents(1,new ItemStack(Items.COAL,64));tile.setField(0,1200);break;
            case 5:
                TileEntityFurnace cold=(TileEntityFurnace)world.getTileEntity(furnace);
                cold.setInventorySlotContents(0,ItemStack.EMPTY);cold.setInventorySlotContents(1,ItemStack.EMPTY);cold.setField(0,0);break;
            case 6:world.setBlockToAir(torch);world.setBlockToAir(furnace);break;
            case 7:put(remote,new BlockPos(168,81,168),17);player(1).connection.setPlayerLocation(168.25,81,168.25,0,0);break;
            case 8:player(1).connection.setPlayerLocation(8.25,81,8.25,0,0);break;
            case 9:put(room,origin,17);player(2).connection.disconnect("Thermal reconnect check");break;
            case 10:WeatherApi.removeTemperatureZone(world,room);WeatherApi.removeTemperatureZone(world,remote);break;
            default:break;
        }
        for(int n=1;n<=2;n++)if(player(n)!=null)announce(player(n));
    }
}
