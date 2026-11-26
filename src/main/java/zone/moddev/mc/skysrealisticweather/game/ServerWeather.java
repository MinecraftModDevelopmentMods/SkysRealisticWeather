package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.WeatherMod;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import zone.moddev.mc.skysrealisticweather.api.SeasonChangedEvent;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisodeChangedEvent;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.climate.WeatherSettings;
import zone.moddev.mc.skysrealisticweather.core.HookStatus;
import zone.moddev.mc.skysrealisticweather.live.LiveLocation;
import zone.moddev.mc.skysrealisticweather.live.LiveWeather;
import zone.moddev.mc.skysrealisticweather.live.Observation;
import zone.moddev.mc.skysrealisticweather.network.WeatherMessage;

public final class ServerWeather {
    public static final ServerWeather INSTANCE = new ServerWeather();
    private static final String WEATHER_CYCLE = "doSeasonalWeather";
    private WorldServer world;
    private WeatherSettings settings;
    private WeatherSettings launchSettings;
    private String launchPreset;
    private String[] launchLabels;
    private String preset;
    private String[] phaseLabels;
    private boolean edited;
    private LiveLocation liveLocation;
    private LiveWeather provider;
    private WeatherState.Frame frame;
    private LiveWeatherControl control;
    private WeatherSchedule schedule;
    private Season lastSeason;
    private WeatherEpisode lastEpisode;
    private boolean lastActive;
    private long forcedUntil;
    private WeatherEpisode forced;

    private ServerWeather() {
    }

    @SubscribeEvent
    public void load(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof WorldServer) || !WeatherState.supports(event.getWorld())) return;
        close();
        world = (WorldServer) event.getWorld();
        settings = launchSettings = WeatherConfiguration.startupSettings();
        preset = launchPreset = WeatherConfiguration.startupPreset();
        phaseLabels = launchLabels = WeatherConfiguration.startupLabels();
        liveLocation = WeatherConfiguration.startupLocation();
        control = new LiveWeatherControl(world.getWorldInfo());
        configureWeather();
        updateFrame();
        lastSeason = frame.climate.calendar.season;
        lastActive = frame.active();
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.getWorld() == world) close();
    }

    public void close() {
        if (provider != null) provider.close();
        provider = null;
        world = null;
        frame = null;
        control = null;
        schedule = null;
        forced = null;
        lastEpisode = null;
        forcedUntil = 0;
        edited = false;
        WeatherState.server(null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void startTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.world != world || world == null) return;
        GameRules rules = world.getGameRules();
        boolean enabled = frame.active() && (!rules.hasRule(WEATHER_CYCLE) || rules.getBoolean(WEATHER_CYCLE));
        long now = world.getTotalWorldTime();
        if (forcedUntil != 0 && now >= forcedUntil) clearPreview();
        if (forcedUntil != 0 && frame.active()) {
            control.beforeTick(now, null, true, forced);
            if (!control.scheduled()) clearPreview();
            updateAndSync(false);
            return;
        }
        Observation observation = provider != null && enabled ? provider.current() : null;
        boolean scheduled = enabled && schedule != null;
        WeatherEpisode candidate = !scheduled ? null : frame.regions == null ? schedule.at(world.getWorldTime())
                : frame.regions.carrier(world.getWorldTime());
        control.beforeTick(now, observation, scheduled, candidate);
        updateAndSync(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void endTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world != world || world == null) return;
        // Record vanilla's result before other END tick handlers can set their own weather.
        WeatherEpisode previousEpisode = control.episode();
        control.afterTick();
        if (previousEpisode != null && previousEpisode.forced && control.episode() == null) clearPreview();
        // World time is synchronized by Minecraft. Resend settings for late or recreated clients.
        updateAndSync(world.getTotalWorldTime() % 100 == 0);
        Season season = frame.climate.calendar.season;
        if (season != lastSeason) {
            Season previous = lastSeason;
            lastSeason = season;
            MinecraftForge.EVENT_BUS.post(new SeasonChangedEvent(world, previous, frame.climate.calendar));
        }
        WeatherEpisode episode = frame.currentEpisode();
        if (episode != lastEpisode) {
            WeatherEpisode previous = lastEpisode;
            lastEpisode = episode;
            MinecraftForge.EVENT_BUS.post(new WeatherEpisodeChangedEvent(world, previous, episode));
        }
    }

    private void configureWeather() {
        boolean active = !settings.passive && HookStatus.available();
        if (!active && provider != null) {
            provider.close();
            provider = null;
        } else if (active && settings.mode == ClimateMode.REAL_WORLD && provider == null && liveLocation != null) {
            provider = new LiveWeather(liveLocation);
        }
        if (settings.mode != ClimateMode.REAL_WORLD && provider != null) {
            provider.close();
            provider = null;
        }
        schedule = settings.scheduled ? new WeatherSchedule(world.getSeed(), settings.profile, settings.weather) : null;
        GameRules rules = world.getGameRules();
        if (active && !rules.hasRule(WEATHER_CYCLE)) {
            rules.addGameRule(WEATHER_CYCLE, "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
    }

    public WorldServer world() {
        return world;
    }

    public WeatherSettings settings() {
        return settings;
    }

    public String preset() {
        return preset;
    }

    public String[] phaseLabels() {
        return phaseLabels.clone();
    }

    public boolean edited() {
        return edited;
    }

    public long previewSeconds() {
        return Math.max(0, (forcedUntil - world.getTotalWorldTime() + 19) / 20);
    }

    public WeatherKind previewKind() {
        return forced == null ? null : forced.kind;
    }

    public boolean active() {
        return frame.active();
    }

    public void force(WeatherKind kind, int seconds, float peak, boolean dryBiomes) {
        long now = world.getTotalWorldTime();
        forced = kind == null ? null : new WeatherEpisode(kind, now, seconds * 20, peak, dryBiomes, true);
        forcedUntil = now + seconds * 20L;
        control.replaceOverride();
    }

    public void automatic() {
        clearPreview();
        control.replaceOverride();
    }

    private void clearPreview() {
        forced = null;
        forcedUntil = 0;
    }

    public void setDaylight(long tick) {
        world.setWorldTime(tick);
        SPacketTimeUpdate time = new SPacketTimeUpdate(world.getTotalWorldTime(), tick,
                world.getGameRules().getBoolean("doDaylightCycle"));
        for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities) {
            ((EntityPlayerMP) player).connection.sendPacket(time);
        }
        updateAndSync(true);
    }

    public void usePreset(Preset selected) {
        preset = selected.id;
        phaseLabels = selected.phaseLabels();
        apply(settings.withClimate(selected.profile, selected.weather).withMode(ClimateMode.UNIVERSAL, settings.biomes));
    }

    public void useMode(ClimateMode mode) {
        clearPreview();
        control.replaceOverride();
        apply(settings.withMode(mode, settings.biomes));
    }

    public void editClimate(ClimateProfile profile) {
        apply(settings.withClimate(profile, settings.weather));
    }

    public void editOptions(boolean passive, boolean surface, boolean foliage, boolean fog, boolean scheduled) {
        if (passive) clearPreview();
        apply(settings.withOptions(passive, surface, foliage, fog, scheduled));
    }

    public void editPlants(boolean enabled) { apply(settings.withFarming(settings.farming.withEnabled(enabled))); }

    private void apply(WeatherSettings replacement) {
        settings = replacement;
        edited = true;
        configureWeather();
        updateAndSync(true);
    }

    public void restore() {
        automatic();
        settings = launchSettings;
        preset = launchPreset;
        phaseLabels = launchLabels;
        edited = false;
        configureWeather();
        updateAndSync(true);
    }

    private void updateAndSync(boolean refresh) {
        boolean changed = frame.live != control.observation() || frame.episode != control.episode()
                || frame.scheduled != control.scheduled() || frame.active() != lastActive;
        updateFrame();
        lastActive = frame.active();
        if (refresh || changed) broadcast();
    }

    private void updateFrame() {
        if (frame != null && frame.settings == settings && frame.time == world.getWorldTime()
                && frame.live == control.observation() && frame.episode == control.episode()
                && frame.scheduled == control.scheduled()) return;
        frame = new WeatherState.Frame(world, settings, control.observation(), control.episode(), control.scheduled(),
                frame != null && frame.settings == settings ? frame.regions : null, world.getSeed(), schedule);
        WeatherState.server(frame);
    }

    private void broadcast() {
        for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities) {
            send((EntityPlayerMP) player);
        }
    }

    private void send(EntityPlayerMP player) {
        WeatherMod.NETWORK.sendTo(new WeatherMessage(player.world == world ? frame : null), player);
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) send((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) send((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) send((EntityPlayerMP) event.player);
    }
}
