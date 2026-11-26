package zone.moddev.mc.skysrealisticweather.network;

import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import zone.moddev.mc.skysrealisticweather.WeatherMod;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import zone.moddev.mc.skysrealisticweather.climate.BiomeClimateMap;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.WeatherPattern;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.WeatherSettings;
import zone.moddev.mc.skysrealisticweather.climate.FarmingSettings;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthProfile;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;
import zone.moddev.mc.skysrealisticweather.live.Observation;

public final class WeatherMessage implements IMessage {
    public WeatherSettings settings;
    public Observation live;
    public boolean scheduled;
    public WeatherEpisode episode;
    public long seed;

    public WeatherMessage() {
    }

    public WeatherMessage(WeatherState.Frame frame) {
        if (frame != null) {
            WeatherSettings configured = frame.settings;
            settings = configured.withOptions(!frame.active(), configured.surface, configured.foliage,
                    configured.fog, configured.scheduled);
            seed = frame.seed;
            live = frame.active() ? frame.live : null;
            scheduled = frame.active() && frame.scheduled;
            episode = frame.currentEpisode();
        }
    }

    @Override
    public void toBytes(ByteBuf out) {
        out.writeBoolean(settings != null);
        if (settings == null) return;
        out.writeBoolean(settings.passive);
        out.writeBoolean(settings.surface);
        out.writeBoolean(settings.foliage);
        out.writeBoolean(settings.fog);
        out.writeFloat(settings.profile.strength);
        for (int index = 0; index < ClimateProfile.SEASON_COUNT; index++) {
            ClimateProfile.Period period = settings.profile.period(index);
            out.writeShort(period.days);
            out.writeFloat(period.temperature);
            out.writeFloat(period.wetness);
            out.writeFloat(period.thunder);
            out.writeFloat(period.fog);
        }
        out.writeBoolean(live != null);
        if (live != null) {
            out.writeLong(live.observedAt);
            out.writeFloat(live.celsius);
            out.writeByte(live.condition.ordinal());
        }
        out.writeBoolean(scheduled);
        out.writeBoolean(episode != null);
        if (episode != null) {
            out.writeByte(episode.kind.ordinal());
            out.writeLong(episode.startTick);
            out.writeShort((int) (episode.endTick - episode.startTick));
            out.writeFloat(episode.peakIntensity);
            out.writeBoolean(episode.includesDryBiomes);
            out.writeBoolean(episode.forced);
        }
        out.writeByte(settings.mode.ordinal());
        out.writeLong(seed);
        String[] nativeBiomes = settings.nativeSnowBiomes();
        out.writeInt(nativeBiomes.length);
        for (String id : nativeBiomes) ByteBufUtils.writeUTF8String(out, id);
        String[] weather = settings.weather == null ? new String[0] : settings.weather.rows();
        out.writeInt(weather.length);
        for (String row : weather) ByteBufUtils.writeUTF8String(out, row);
        FarmingSettings farms = settings.farming;
        out.writeBoolean(farms.enabled); out.writeBoolean(farms.harvestcraft); out.writeBoolean(farms.agricraft);
        out.writeBoolean(farms.mystical); out.writeBoolean(farms.forestry);
        String[] farmProfiles = farms.profileRows(), farmMappings = farms.mappingRows();
        out.writeInt(farmProfiles.length);
        for (String row : farmProfiles) ByteBufUtils.writeUTF8String(out, row);
        out.writeInt(farmMappings.length);
        for (String row : farmMappings) ByteBufUtils.writeUTF8String(out, row);
        Map<String, PlantGrowthProfile> registered = farms.registeredProfiles();
        out.writeInt(registered.size());
        registered.forEach((key, profile) -> {
            ByteBufUtils.writeUTF8String(out, key); out.writeBoolean(profile.exempt);
            if (!profile.exempt) {
                out.writeFloat(profile.minimum); out.writeFloat(profile.idealMinimum);
                out.writeFloat(profile.idealMaximum); out.writeFloat(profile.maximum);
            }
        });
        if (settings.mode == ClimateMode.PER_BIOME) {
            out.writeInt(settings.biomes.presets.size());
            for (Preset preset : settings.biomes.presets.values()) {
                ByteBufUtils.writeUTF8String(out, preset.id);
                ByteBufUtils.writeUTF8String(out, preset.label);
                writeProfile(out, preset.profile);
                for (String label : preset.phaseLabels()) ByteBufUtils.writeUTF8String(out, label);
                String[] rows = preset.weather.rows();
                out.writeInt(rows.length);
                for (String row : rows) ByteBufUtils.writeUTF8String(out, row);
            }
            out.writeInt(settings.biomes.mappings.size());
            settings.biomes.mappings.forEach((id, preset) -> {
                ByteBufUtils.writeUTF8String(out, id);
                ByteBufUtils.writeUTF8String(out, preset);
            });
        }
    }

    @Override
    public void fromBytes(ByteBuf in) {
        if (!in.readBoolean()) return;
        boolean passive = in.readBoolean();
        boolean surface = in.readBoolean();
        boolean foliage = in.readBoolean();
        boolean fog = in.readBoolean();
        float strength = in.readFloat();
        ClimateProfile.Period[] periods = new ClimateProfile.Period[ClimateProfile.SEASON_COUNT];
        for (int index = 0; index < periods.length; index++) {
            periods[index] = new ClimateProfile.Period(in.readUnsignedShort(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat());
        }
        settings = new WeatherSettings(new ClimateProfile(strength, periods), passive, surface, foliage, fog);
        if (in.readBoolean()) {
            long observedAt = in.readLong();
            float temp = in.readFloat();
            Observation.Condition[] conditions = Observation.Condition.values();
            int kind = in.readUnsignedByte();
            if (kind >= conditions.length) throw new IllegalArgumentException("Invalid weather condition");
            live = new Observation(observedAt, temp, conditions[kind]);
        }
        scheduled = in.readBoolean();
        if (in.readBoolean()) {
            WeatherKind[] kinds = WeatherKind.values();
            int kind = in.readUnsignedByte();
            if (kind >= kinds.length || !scheduled || live != null) throw new IllegalArgumentException("Invalid weather event");
            episode = new WeatherEpisode(kinds[kind], in.readLong(), in.readUnsignedShort(),
                    in.readFloat(), in.readBoolean(), in.readBoolean());
        }
        int ordinal = in.readUnsignedByte();
        if (ordinal >= ClimateMode.values().length) throw new IllegalArgumentException("Invalid climate mode");
        ClimateMode mode = ClimateMode.values()[ordinal];
        seed = in.readLong();
        String[] nativeBiomes = new String[count(in, 8192)];
        for (int i = 0; i < nativeBiomes.length; i++) nativeBiomes[i] = ByteBufUtils.readUTF8String(in);
        String[] weather = new String[count(in, WeatherKind.values().length)];
        for (int i = 0; i < weather.length; i++) weather[i] = ByteBufUtils.readUTF8String(in);
        settings = settings.withClimate(settings.profile, new WeatherPattern(weather));
        boolean plants = in.readBoolean(), pam = in.readBoolean(), agri = in.readBoolean(), magic = in.readBoolean(), forest = in.readBoolean();
        String[] plantProfiles = new String[count(in, 512)];
        for (int i = 0; i < plantProfiles.length; i++) plantProfiles[i] = ByteBufUtils.readUTF8String(in);
        String[] plantMappings = new String[count(in, 8192)];
        for (int i = 0; i < plantMappings.length; i++) plantMappings[i] = ByteBufUtils.readUTF8String(in);
        Map<String, PlantGrowthProfile> registered = new LinkedHashMap<>();
        int plantCount = count(in, 8192);
        for (int i = 0; i < plantCount; i++) {
            String key = ByteBufUtils.readUTF8String(in);
            registered.put(key, in.readBoolean() ? PlantGrowthProfile.EXEMPT
                    : new PlantGrowthProfile(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
        }
        settings = settings.withFarming(new FarmingSettings(plants, pam, agri, magic, forest, plantProfiles, plantMappings, registered));
        BiomeClimateMap biomes = BiomeClimateMap.defaults();
        if (mode == ClimateMode.PER_BIOME) {
            Map<String, Preset> presets = new LinkedHashMap<>();
            int count = count(in, 512);
            for (int i = 0; i < count; i++) {
                String id = ByteBufUtils.readUTF8String(in);
                String label = ByteBufUtils.readUTF8String(in);
                ClimateProfile profile = readProfile(in);
                String[] labels = new String[4];
                for (int j = 0; j < 4; j++) labels[j] = ByteBufUtils.readUTF8String(in);
                String[] rows = new String[count(in, WeatherKind.values().length)];
                for (int j = 0; j < rows.length; j++) rows[j] = ByteBufUtils.readUTF8String(in);
                presets.put(id, new Preset(id, label, "", Preset.Group.USER, profile, labels, new WeatherPattern(rows)));
            }
            if (presets.isEmpty()) throw new IllegalArgumentException("Empty biome climate library");
            Map<String, String> mappings = new LinkedHashMap<>();
            count = count(in, 8192);
            for (int i = 0; i < count; i++) mappings.put(ByteBufUtils.readUTF8String(in), ByteBufUtils.readUTF8String(in));
            biomes = new BiomeClimateMap(presets, mappings);
        }
        settings = settings.withMode(mode, biomes).withNativeSnowBiomes(nativeBiomes);
    }

    private static int count(ByteBuf in, int limit) {
        int value = in.readInt();
        if (value < 0 || value > limit) throw new IllegalArgumentException("Invalid climate data count");
        return value;
    }

    private static void writeProfile(ByteBuf out, ClimateProfile profile) {
        out.writeFloat(profile.strength);
        for (int i = 0; i < 4; i++) {
            ClimateProfile.Period period = profile.period(i);
            out.writeShort(period.days);
            out.writeFloat(period.temperature);
            out.writeFloat(period.wetness);
            out.writeFloat(period.thunder);
            out.writeFloat(period.fog);
        }
    }

    private static ClimateProfile readProfile(ByteBuf in) {
        float strength = in.readFloat();
        ClimateProfile.Period[] periods = new ClimateProfile.Period[4];
        for (int i = 0; i < 4; i++) {
            periods[i] = new ClimateProfile.Period(in.readUnsignedShort(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
        }
        return new ClimateProfile(strength, periods);
    }

    public static final class Handler implements IMessageHandler<WeatherMessage, IMessage> {
        @Override
        public IMessage onMessage(WeatherMessage message, MessageContext context) {
            WeatherMod.proxy.receive(message, context);
            return null;
        }
    }
}
