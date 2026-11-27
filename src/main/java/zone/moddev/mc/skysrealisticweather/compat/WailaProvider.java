package zone.moddev.mc.skysrealisticweather.compat;

import java.util.List;
import mcp.mobius.waila.api.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.CropPresentation;

public final class WailaProvider implements IWailaDataProvider {
    public static void register(){FMLInterModComms.sendMessage("Waila","register",WailaProvider.class.getName()+".register");}
    public static void register(IWailaRegistrar registrar) {
        WailaProvider provider=new WailaProvider();registrar.registerBodyProvider(provider,Block.class);registrar.registerNBTProvider(provider,Block.class);
        Integrations.ready("waila");
    }
    public ItemStack getWailaStack(IWailaDataAccessor accessor,IWailaConfigHandler config){return null;}
    public List<String> getWailaHead(ItemStack stack,List<String> text,IWailaDataAccessor accessor,IWailaConfigHandler config){return text;}
    public List<String> getWailaTail(ItemStack stack,List<String> text,IWailaDataAccessor accessor,IWailaConfigHandler config){return text;}
    public List<String> getWailaBody(ItemStack stack,List<String> text,IWailaDataAccessor accessor,IWailaConfigHandler config) {
        NBTTagCompound root=accessor.getNBTData();
        // Legacy Hwyla only requests server NBT for tile entities. Vanilla crops use
        // the same synchronized settings/temperature zones as inventory tooltips.
        NBTTagCompound sample=root!=null&&root.hasKey(CropPresentation.KEY,10)?root.getCompoundTag(CropPresentation.KEY)
                :CropPresentation.block(accessor.getWorld(),accessor.getPosition());
        text.addAll(CropPresentation.lines(sample,WeatherConfiguration.temperatureUnit()));return text;
    }
    public NBTTagCompound getNBTData(EntityPlayerMP player,TileEntity tile,NBTTagCompound tag,World world,BlockPos pos) {
        tag.setTag(CropPresentation.KEY,CropPresentation.block(world,pos));return tag;
    }
}
