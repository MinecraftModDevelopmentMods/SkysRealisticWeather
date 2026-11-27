package zone.moddev.mc.skysrealisticweather.verify.pack;
import java.lang.reflect.Proxy;
import java.util.*;
import com.mojang.authlib.GameProfile;
import mcjty.theoneprobe.TheOneProbe;
import mcjty.theoneprobe.api.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysrealisticweather.compat.TopProvider;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay;
import static zone.moddev.mc.skysrealisticweather.verify.pack.PackSupportProbe.require;
public final class TopChecks {
    public static void run(WorldServer world) {
        TopProvider provider=(TopProvider)TheOneProbe.theOneProbeImp.getProviders().stream().filter(p->p instanceof TopProvider).findFirst().orElse(null);
        require(provider!=null,"TOP IMC registered provider");
        EntityPlayerMP player=FakePlayerFactory.get(world,new GameProfile(UUID.fromString("1cfdf478-0b96-48bf-a8e4-f627f824c593"),"ProbeChecks"));
        BlockPos pos=new BlockPos(8,81,8);world.setBlockState(pos,Blocks.WHEAT.getDefaultState(),2);player.setPosition(8,81,8);
        List<String> lines=new ArrayList<>();
        IProbeInfo info=(IProbeInfo)Proxy.newProxyInstance(TopChecks.class.getClassLoader(),new Class[]{IProbeInfo.class},(o,m,a)->{if(m.getName().equals("text"))lines.add(a[0].toString());return o;});
        IProbeHitData hit=(IProbeHitData)Proxy.newProxyInstance(TopChecks.class.getClassLoader(),new Class[]{IProbeHitData.class},(o,m,a)->m.getName().equals("getPos")?pos:null);
        provider.addProbeInfo(ProbeMode.NORMAL,info,player,world,world.getBlockState(pos),hit);
        require(lines.toString().contains("°C")&&lines.toString().contains("Here:"),"TOP crop readout Celsius");
        net.minecraft.nbt.NBTTagCompound prefs=new net.minecraft.nbt.NBTTagCompound();prefs.setInteger(TemperatureDisplay.PLAYER_KEY,1);player.getEntityData().setTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG,prefs);
        lines.clear();provider.addProbeInfo(ProbeMode.NORMAL,info,player,world,world.getBlockState(pos),hit);
        require(lines.toString().contains("°F"),"TOP uses viewer Fahrenheit preference");
    }
}
