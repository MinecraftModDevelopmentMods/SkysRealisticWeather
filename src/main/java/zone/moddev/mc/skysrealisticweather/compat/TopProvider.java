package zone.moddev.mc.skysrealisticweather.compat;

import com.google.common.base.Function;
import mcjty.theoneprobe.api.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import zone.moddev.mc.skysrealisticweather.game.*;

public final class TopProvider implements Function<ITheOneProbe,Void>,IProbeInfoProvider {
    public static void register(){FMLInterModComms.sendFunctionMessage("theoneprobe","getTheOneProbe",TopProvider.class.getName());}
    @Override public Void apply(ITheOneProbe probe){probe.registerProvider(this);Integrations.ready("theoneprobe");return null;}
    public String getID(){return "skysrealisticweather:crop";}
    public void addProbeInfo(ProbeMode mode,IProbeInfo info,EntityPlayer player,World world,IBlockState state,IProbeHitData hit) {
        if(hit==null||player==null)return;
        for(String line:CropPresentation.lines(CropPresentation.block(world,hit.getPos()),TemperatureDisplay.forSender(player)))info.text(line);
    }
}
