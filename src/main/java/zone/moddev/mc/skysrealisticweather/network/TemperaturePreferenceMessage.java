package zone.moddev.mc.skysrealisticweather.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay;

/** A player's display preference changes no gameplay settings. */
public final class TemperaturePreferenceMessage implements IMessage {
    public TemperatureDisplay.Unit unit = TemperatureDisplay.Unit.CELSIUS;
    public TemperaturePreferenceMessage() { }
    public TemperaturePreferenceMessage(TemperatureDisplay.Unit unit) { this.unit = unit; }
    @Override public void toBytes(ByteBuf out) { out.writeByte(unit.ordinal()); }
    @Override public void fromBytes(ByteBuf in) {
        int value = in.readUnsignedByte();
        if (value >= TemperatureDisplay.Unit.values().length) throw new IllegalArgumentException("Unknown temperature unit");
        unit = TemperatureDisplay.Unit.values()[value];
    }
    public void apply(EntityPlayerMP player) {
        net.minecraft.nbt.NBTTagCompound persisted = player.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG);
        persisted.setInteger(TemperatureDisplay.PLAYER_KEY, unit.ordinal());
        player.getEntityData().setTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }
    public static final class Handler implements IMessageHandler<TemperaturePreferenceMessage, IMessage> {
        @Override public IMessage onMessage(TemperaturePreferenceMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            player.getServerWorld().addScheduledTask(() -> message.apply(player));
            return null;
        }
    }
}
