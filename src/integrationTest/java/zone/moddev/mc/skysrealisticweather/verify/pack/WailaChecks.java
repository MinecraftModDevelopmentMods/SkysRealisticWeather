package zone.moddev.mc.skysrealisticweather.verify.pack;
import java.lang.reflect.Proxy;
import java.util.*;
import mcp.mobius.waila.api.*;
import mcp.mobius.waila.api.impl.ModuleRegistrar;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysrealisticweather.compat.WailaProvider;
import static zone.moddev.mc.skysrealisticweather.verify.pack.PackSupportProbe.require;
public final class WailaChecks {
    public static void run(WorldServer world) {
        List<IWailaDataProvider> providers=ModuleRegistrar.instance().bodyBlockProviders.get(Block.class);
        WailaProvider provider=providers==null?null:(WailaProvider)providers.stream().filter(p->p instanceof WailaProvider).findFirst().orElse(null);
        require(provider!=null,"Hwyla IMC registered provider");
        require(ModuleRegistrar.instance().NBTDataProviders.get(Block.class).contains(provider),"Hwyla server NBT provider registered");
        BlockPos pos=new BlockPos(8,81,8);world.setBlockState(pos,Blocks.WHEAT.getDefaultState(),2);
        NBTTagCompound tag=provider.getNBTData(null,null,new NBTTagCompound(),world,pos);
        IWailaDataAccessor accessor=(IWailaDataAccessor)Proxy.newProxyInstance(WailaChecks.class.getClassLoader(),new Class[]{IWailaDataAccessor.class},(o,m,a)->m.getName().equals("getNBTData")?tag:null);
        List<String> lines=provider.getWailaBody(null,new ArrayList<>(),accessor,null);
        require(lines.toString().contains("Here:"),"Hwyla uses server crop readout");
        require(world.getTileEntity(pos)==null,"ordinary wheat has no tile entity");
        IWailaDataAccessor ordinary=(IWailaDataAccessor)Proxy.newProxyInstance(WailaChecks.class.getClassLoader(),new Class[]{IWailaDataAccessor.class},(o,m,a)->{
            if(m.getName().equals("getNBTData"))return new NBTTagCompound();
            if(m.getName().equals("getWorld"))return world;
            if(m.getName().equals("getPosition"))return pos;return null;
        });
        require(provider.getWailaBody(null,new ArrayList<>(),ordinary,null).toString().contains("Here:"),"Hwyla reads ordinary crops without tile NBT");
        require(provider.getNBTData(null,null,new NBTTagCompound(),world,new BlockPos(1000000,80,1000000)).getCompoundTag("skysrealisticweather").hasNoTags(),"Hwyla unloaded target is unavailable");
    }
}
