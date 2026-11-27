package zone.moddev.mc.skysrealisticweather.game;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSavedData;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import zone.moddev.mc.skysrealisticweather.climate.*;

/** Only explicitly edited controller fields are persisted; pack definitions remain inherited. */
public final class WorldWeatherData extends WorldSavedData {
    public static final String KEY = "skysrealisticweather_settings";
    private NBTTagCompound overrides = new NBTTagCompound();
    private NBTTagCompound original;
    private String problem;
    public boolean initialized;
    public WorldWeatherData() { super(KEY); }
    public WorldWeatherData(String name) { super(name); }
    public static WorldWeatherData get(WorldServer world) {
        WorldWeatherData data = (WorldWeatherData) world.getPerWorldStorage().getOrLoadData(WorldWeatherData.class, KEY);
        if (data == null) { data = new WorldWeatherData(); world.getPerWorldStorage().setData(KEY, data); }
        return data;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        initialized = true;
        original = tag.copy();
        if (tag.getInteger("schema") != 1 || !tag.hasKey("overrides", 10)) {
            problem = "Unsupported or malformed world weather data; original retained";
            return;
        }
        overrides = tag.getCompoundTag("overrides").copy();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if (problem != null && original != null) return original.copy();
        tag.setInteger("schema", 1); tag.setTag("overrides", overrides.copy()); return tag;
    }
    public NBTTagCompound overrides() { return overrides.copy(); }
    public String problem() { return problem; }
    public void invalid(String description) {
        problem = description;
        org.apache.logging.log4j.LogManager.getLogger("Sky's Realistic Weather").warn("{}; using pack defaults without replacing saved data", description);
    }
    public void save(NBTTagCompound values) {
        if (problem != null) throw new IllegalStateException("World weather data is invalid; explicitly reset world overrides first");
        overrides = values.copy(); markDirty();
    }
    public void reset() { problem = null; original = null; overrides = new NBTTagCompound(); markDirty(); }

    public static WeatherSettings merge(WeatherSettings base, NBTTagCompound fields) {
        ClimateMode mode = fields.hasKey("mode") ? ClimateMode.valueOf(fields.getString("mode")) : base.mode;
        ClimateProfile universal=WeatherConfiguration.startupUniversalProfile();
        ClimateProfile profile = mode == ClimateMode.PER_BIOME ? WeatherConfiguration.sharedCalendar()
                : mode!=base.mode&&universal!=null?universal:base.profile;
        String[] labels=mode==ClimateMode.PER_BIOME?java.util.Arrays.stream(zone.moddev.mc.skysrealisticweather.api.Season.values()).map(Preset::defaultPhaseLabel).toArray(String[]::new):base.phaseLabels();
        if(mode!=base.mode&&mode!=ClimateMode.PER_BIOME&&universal!=null)labels=WeatherConfiguration.startupUniversalLabels();
        WeatherPattern pattern = base.weather;
        if (fields.hasKey("preset")) {
            Preset preset = WeatherConfiguration.preset(fields.getString("preset"));
            if (preset == null) throw new IllegalArgumentException("Saved world preset is unavailable: " + fields.getString("preset"));
            if (mode != ClimateMode.PER_BIOME) profile = preset.profile;
            pattern = preset.weather;
            if(mode!=ClimateMode.PER_BIOME)labels=preset.phaseLabels();
        }
        ClimateProfile.Period[] phases = new ClimateProfile.Period[4];
        for (int i = 0; i < phases.length; i++) {
            ClimateProfile.Period p = profile.period(i); String prefix = "phase." + i + ".";
            if(fields.hasKey(prefix+"days")&&!fields.hasKey(prefix+"days",3))throw new IllegalArgumentException("Invalid phase length: "+i);
            phases[i] = new ClimateProfile.Period(fields.hasKey(prefix + "days") ? fields.getInteger(prefix + "days") : p.days,
                    number(fields,prefix+"temperature",p.temperature),number(fields,prefix+"wetness",p.wetness),
                    number(fields,prefix+"thunder",p.thunder),number(fields,prefix+"fog",p.fog));
        }
        Set<String> allowed = snapshot(base).getKeySet();
        for (String key : fields.getKeySet()) if (!allowed.contains(key) && !key.equals("preset")) throw new IllegalArgumentException("Unknown saved weather field: " + key);
        if(fields.hasKey("offset")&&!fields.hasKey("offset",4))throw new IllegalArgumentException("Invalid calendar offset");
        return base.withPhaseLabels(labels).withClimate(new ClimateProfile(number(fields,"strength",profile.strength),phases),pattern)
                .withMode(mode,base.biomes)
                .withOptions(flag(fields,"passive",base.passive),flag(fields,"surface",base.surface),flag(fields,"foliage",base.foliage),
                        flag(fields,"fog",base.fog),flag(fields,"scheduled",base.scheduled))
                .withFarming(base.farming.withEnabled(flag(fields,"plants",base.farming.enabled)))
                .withCalendarOffset(fields.hasKey("offset") ? fields.getLong("offset") : base.calendarOffset);
    }
    private static boolean flag(NBTTagCompound fields,String key,boolean fallback) {
        if (fields.hasKey(key) && !fields.hasKey(key,1)) throw new IllegalArgumentException("Invalid boolean world override: " + key);
        return fields.hasKey(key) ? fields.getBoolean(key) : fallback;
    }
    private static float number(NBTTagCompound fields,String key,float fallback) {
        if (fields.hasKey(key) && !fields.hasKey(key,99)) throw new IllegalArgumentException("Invalid numeric world override: " + key);
        return fields.hasKey(key) ? fields.getFloat(key) : fallback;
    }
    public static void record(NBTTagCompound fields,WeatherSettings before,WeatherSettings after) {
        NBTTagCompound a=snapshot(before),b=snapshot(after);
        for(String key:b.getKeySet()) if(!Objects.equals(a.getTag(key),b.getTag(key))) fields.setTag(key,b.getTag(key).copy());
    }
    public static void clearProfile(NBTTagCompound fields) {
        for (String key:new ArrayList<>(fields.getKeySet())) if(key.startsWith("phase.")||key.equals("strength")) fields.removeTag(key);
    }
    public static NBTTagCompound snapshot(WeatherSettings settings) {
        NBTTagCompound tag=new NBTTagCompound();
        tag.setString("mode",settings.mode.name()); tag.setFloat("strength",settings.profile.strength);
        tag.setLong("offset",settings.calendarOffset);
        tag.setBoolean("passive",settings.passive);tag.setBoolean("surface",settings.surface);tag.setBoolean("foliage",settings.foliage);
        tag.setBoolean("fog",settings.fog);tag.setBoolean("scheduled",settings.scheduled);tag.setBoolean("plants",settings.farming.enabled);
        for(int i=0;i<4;i++) {
            ClimateProfile.Period p=settings.profile.period(i);String key="phase."+i+".";
            tag.setInteger(key+"days",p.days);tag.setFloat(key+"temperature",p.temperature);tag.setFloat(key+"wetness",p.wetness);
            tag.setFloat(key+"thunder",p.thunder);tag.setFloat(key+"fog",p.fog);
        }
        return tag;
    }
}
