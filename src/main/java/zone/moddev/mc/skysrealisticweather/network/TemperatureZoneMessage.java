package zone.moddev.mc.skysrealisticweather.network;

import io.netty.buffer.ByteBuf;
import java.util.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import zone.moddev.mc.skysrealisticweather.WeatherMod;
import zone.moddev.mc.skysrealisticweather.api.TemperatureZone;
import zone.moddev.mc.skysrealisticweather.game.TemperatureZoneIndex.Entry;
import zone.moddev.mc.skysrealisticweather.game.BlockHeatSources;

/** Authoritative snapshot for one watched chunk, including an empty snapshot to clear it. */
public final class TemperatureZoneMessage implements IMessage {
    public int dimension,x,z;
    public boolean enabled;
    public BlockHeatSources.Settings blockHeat=BlockHeatSources.Settings.configured();
    public List<Entry> entries=Collections.emptyList();
    public TemperatureZoneMessage() { }
    public TemperatureZoneMessage(int dimension,int x,int z,boolean enabled,List<Entry> entries) {
        this.dimension=dimension;this.x=x;this.z=z;this.enabled=enabled;this.entries=entries;
    }
    @Override public void toBytes(ByteBuf out) {
        if(entries.size()>4096)throw new IllegalArgumentException("Too many temperature zones in one chunk");
        out.writeInt(dimension);out.writeInt(x);out.writeInt(z);out.writeBoolean(enabled);out.writeInt(entries.size());
        blockHeat.write(out);
        for(Entry e:entries){TemperatureZone zone=e.zone;ByteBufUtils.writeUTF8String(out,zone.id.toString());
            out.writeLong(zone.minimum.toLong());out.writeLong(zone.maximum.toLong());out.writeFloat(zone.temperature);
            out.writeInt(zone.priority);out.writeByte(zone.mode.ordinal());out.writeBoolean(e.furnace!=null);if(e.furnace!=null)out.writeLong(e.furnace.toLong());}
    }
    @Override public void fromBytes(ByteBuf in) {
        dimension=in.readInt();x=in.readInt();z=in.readInt();enabled=in.readBoolean();int count=in.readInt();
        if(count<0||count>4096)throw new IllegalArgumentException("Invalid temperature zone count");
        blockHeat=BlockHeatSources.Settings.read(in);
        List<Entry> decoded=new ArrayList<>(count);
        for(int i=0;i<count;i++) {
            String id=ByteBufUtils.readUTF8String(in);if(id.length()>128)throw new IllegalArgumentException("Zone ID too long");
            BlockPos min=BlockPos.fromLong(in.readLong()),max=BlockPos.fromLong(in.readLong());float value=in.readFloat();int priority=in.readInt(),mode=in.readUnsignedByte();
            if(mode>=TemperatureZone.Mode.values().length)throw new IllegalArgumentException("Unknown zone mode");
            TemperatureZone zone=new TemperatureZone(new ResourceLocation(id),min,max,value,priority,TemperatureZone.Mode.values()[mode]);
            if(x<(min.getX()>>4)||x>(max.getX()>>4)||z<(min.getZ()>>4)||z>(max.getZ()>>4))throw new IllegalArgumentException("Zone outside snapshot chunk");
            decoded.add(new Entry(zone,in.readBoolean()?BlockPos.fromLong(in.readLong()):null));
        }
        entries=Collections.unmodifiableList(decoded);
    }
    public static final class Handler implements IMessageHandler<TemperatureZoneMessage,IMessage> {
        @Override public IMessage onMessage(TemperatureZoneMessage message,MessageContext context) { WeatherMod.proxy.receiveTemperatureZones(message,context);return null; }
    }
}
