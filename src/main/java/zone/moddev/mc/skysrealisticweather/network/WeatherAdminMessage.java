package zone.moddev.mc.skysrealisticweather.network;

import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import zone.moddev.mc.skysrealisticweather.WeatherMod;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.ServerWeather;

/** A server-authorized snapshot, delivered only to the requesting operator. */
public final class WeatherAdminMessage implements IMessage {
    public boolean open;
    public boolean error;
    public String feedback;
    public String preset;
    public boolean edited;
    public boolean scheduled;
    public final String[] phaseLabels = new String[ClimateProfile.SEASON_COUNT];
    public final Map<String, String> presets = new LinkedHashMap<>();
    public final WeatherMessage climate = new WeatherMessage();

    public WeatherAdminMessage() {
    }

    public WeatherAdminMessage(ServerWeather server, boolean open, boolean error, String feedback) {
        this.open = open;
        this.error = error;
        this.feedback = feedback;
        this.preset = server.preset();
        this.edited = server.edited();
        this.scheduled = server.settings().scheduled;
        System.arraycopy(server.phaseLabels(), 0, phaseLabels, 0, phaseLabels.length);
        for (Preset entry : WeatherConfiguration.presets()) presets.put(entry.id, entry.label);
        climate.settings = server.settings();
    }

    @Override
    public void toBytes(ByteBuf out) {
        out.writeBoolean(open);
        out.writeBoolean(error);
        ByteBufUtils.writeUTF8String(out, feedback);
        ByteBufUtils.writeUTF8String(out, preset);
        out.writeBoolean(edited);
        out.writeBoolean(scheduled);
        for (String label : phaseLabels) ByteBufUtils.writeUTF8String(out, label);
        out.writeInt(presets.size());
        presets.forEach((id, label) -> {
            ByteBufUtils.writeUTF8String(out, id);
            ByteBufUtils.writeUTF8String(out, label);
        });
        climate.toBytes(out);
    }

    @Override
    public void fromBytes(ByteBuf in) {
        open = in.readBoolean();
        error = in.readBoolean();
        feedback = ByteBufUtils.readUTF8String(in);
        preset = ByteBufUtils.readUTF8String(in);
        edited = in.readBoolean();
        scheduled = in.readBoolean();
        for (int index = 0; index < phaseLabels.length; index++) phaseLabels[index] = ByteBufUtils.readUTF8String(in);
        int count = in.readInt();
        if (count < 0 || count > in.readableBytes() / 2) throw new IllegalArgumentException("Invalid preset list");
        for (int index = 0; index < count; index++) {
            presets.put(ByteBufUtils.readUTF8String(in), ByteBufUtils.readUTF8String(in));
        }
        climate.fromBytes(in);
    }

    public static final class Handler implements IMessageHandler<WeatherAdminMessage, IMessage> {
        @Override
        public IMessage onMessage(WeatherAdminMessage message, MessageContext context) {
            WeatherMod.proxy.receiveAdmin(message, context);
            return null;
        }
    }
}
