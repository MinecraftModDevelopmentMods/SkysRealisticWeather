package zone.moddev.mc.skysrealisticweather.game;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi.PopulationPolicy;
import zone.moddev.mc.skysrealisticweather.climate.*;

/** Headless measurements using the production scheduler, local queries and native weather timers. */
public final class ClimateEnduranceProbe {
    private static final int DAY = 24000;
    private static final long[] SEEDS = Arrays.stream(System.getProperty("endurance.seeds", "123948,70289,91173")
            .split(",")).mapToLong(Long::parseLong).toArray();
    private static final BlockPos LOW = new BlockPos(8, 81, 8);
    private static final BlockPos HIGH = new BlockPos(8, 200, 8);
    private static final WeatherKind[] KINDS = WeatherKind.values();
    private static final int RUNTIME_API = runtimeApi();
    private static final Biome[] BIOMES = {Biomes.PLAINS, Biomes.JUNGLE, Biomes.DESERT, Biomes.COLD_TAIGA,
            Biomes.ICE_PLAINS, Biomes.PLAINS, Biomes.OCEAN, Biomes.EXTREME_HILLS};
    private static final String[] SITES = {"reference", "jungle", "desert", "cold_taiga", "arctic", "highland", "coast", "mountain"};
    private final WorldServer world;
    private final TickEvent.WorldTickEvent startEvent, endEvent;
    private final WeatherSettings startup;
    private final File output = new File("endurance");
    private long nativeTicks;
    private int freezes;
    private int thaws;
    private int snow;
    private int rests;
    private long began;
    private final int[] snowMasks = new int[BIOMES.length];
    private PrintWriter yearly, dailyWeather;

    private ClimateEnduranceProbe(WorldServer world) {
        this.world = world;
        startEvent = new TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER, TickEvent.Phase.START, world);
        endEvent = new TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER, TickEvent.Phase.END, world);
        startup = WeatherConfiguration.startupSettings();
    }

    public static void run(WorldServer world) throws Exception {
        new ClimateEnduranceProbe(world).run();
    }

    private void run() throws Exception {
        began = System.nanoTime();
        if (!output.mkdirs() && !output.isDirectory()) throw new IOException("Cannot create endurance output");
        try (PrintWriter metadata = writer("assumptions.json")) {
            com.google.gson.JsonObject values = new com.google.gson.JsonObject();
            values.addProperty("api", RUNTIME_API);
            values.addProperty("worldSeed", world.getSeed());
            values.addProperty("civilDayStartsAtMinecraftTick", 18000);
            values.addProperty("canonicalWarmupPresetYears", 1);
            values.addProperty("sampleStep", Integer.getInteger("endurance.sampleStep", 100));
            values.add("nativeSites", new com.google.gson.Gson().toJsonTree(SITES));
            values.addProperty("coverClock", "09:00; more than half of a nine-column patch");
            values.addProperty("surfaceColumnProbabilityPerTick", 1.0 / 4096);
            values.addProperty("workload", "weather handlers, native weather timers, and sampled surface hooks; no entity or render loop");
            metadata.println(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(values));
        }
        if (Boolean.getBoolean("endurance.reload")) {
            reload();
            return;
        }
        String phase = System.getProperty("endurance.phase", "all");
        if (phase.equals("all") || phase.equals("sweep")) {
            if (RUNTIME_API >= 5) climateContracts();
            sweep();
        }
        if (phase.equals("all") || phase.equals("runtime")) {
            temperatureReferences();
            int days = Integer.getInteger("endurance.nativeDays", 365 * 5);
            String climates = System.getProperty("endurance.nativeClimates", "all");
            if (climates.equals("all")) {
                nativeRun(null, days);
                for (Preset preset : WeatherConfiguration.presets()) nativeRun(preset.id, days);
            } else for (String id : climates.split(",")) nativeRun(id.equals("per_biome") ? null : id, days);
            liveReplay();
            saveFixture();
        }
        System.out.printf(Locale.ROOT, "ENDURANCE_PASS nativeTicks=%d freezes=%d thaws=%d snow=%d restarts=%d elapsed=%.1fs%n",
                nativeTicks, freezes, thaws, snow, rests, (System.nanoTime() - began) / 1e9);
        System.out.println("ENDURANCE_MEMORY heapUsed=" + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()
                + " heapMax=" + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax());
        world.getMinecraftServer().initiateShutdown();
    }

    private void sweep() throws Exception {
        try (PrintWriter totals = writer("climates.csv"); PrintWriter kinds = writer("events.csv");
             PrintWriter timing = writer("onsets.csv"); PrintWriter encounters = writer("encounters.csv");
             PrintWriter yearOutput = writer("years.csv"); PrintWriter daily = writer("daily-weather.csv")) {
            yearly = yearOutput;
            dailyWeather = daily;
            yearOutput.println("climate,seed,strength,year,wet_days,snow_days,thunder_days,fog_days,cold_days,heat_days");
            daily.println("climate,seed,day,phase,condition_mask,wet_seconds,snow_seconds,min_temp,max_temp");
            totals.println("climate,seed,strength,phase,days,wet_days,wet_pct,thunder_pct,snow_pct,fog_pct,min_temp,max_temp,mean_temp,max_dry_days,max_wet_days,events,mean_event_seconds,max_event_seconds,wet_days_02mm,wet_days_1mm,liquid_mm,snow_days,high_snow_days,thunder_days,fog_days,dust_days,cold_days,heat_days,blowing_snow_days,blizzard_days,strong_days,obscured_pct,samples");
            kinds.println("climate,seed,strength,kind,phase,events");
            timing.println("climate,seed,strength,kind,clock_hour,events");
            encounters.println("climate,seed,strength,kind,days,segments,mean_seconds,max_seconds,visible_seconds");
            int years = Integer.getInteger("endurance.years", 20);
            String selected = System.getProperty("endurance.sweepClimates", "all");
            for (Preset preset : WeatherConfiguration.presets()) {
                if (!selected.equals("all") && !Arrays.asList(selected.split(",")).contains(preset.id)) continue;
                for (long seed : SEEDS) {
                    measure(preset, seed, 1, years, totals, kinds, timing, encounters);
                }
                int extremeYears = Integer.getInteger("endurance.extremeYears", 5);
                if (extremeYears > 0) {
                    measure(preset, SEEDS[0], 0, extremeYears, totals, kinds, timing, encounters);
                    measure(preset, SEEDS[0], 2, extremeYears, totals, kinds, timing, encounters);
                }
                totals.flush();
                kinds.flush();
                timing.flush();
                encounters.flush();
                yearOutput.flush(); daily.flush();
                System.out.println("ENDURANCE_CLIMATE " + preset.id + " complete");
                pause();
            }
        }
    }

    private void measure(Preset preset, long seed, float strength, int years, PrintWriter totals, PrintWriter kinds, PrintWriter timing, PrintWriter encounters) {
        ClimateProfile.Period[] periods = new ClimateProfile.Period[4];
        for (int i = 0; i < 4; i++) periods[i] = preset.profile.period(i);
        ClimateProfile profile = new ClimateProfile(strength, periods);
        WeatherSettings settings = new WeatherSettings(profile, false, true, true, true, preset.weather, true);
        WeatherSchedule schedule = new WeatherSchedule(seed, profile, preset.weather);
        Stats[] stats = stats();
        Map<WeatherKind, int[]> events = new EnumMap<>(WeatherKind.class);
        Map<WeatherKind, int[]> onsets = new EnumMap<>(WeatherKind.class);
        KindStats[] experienced = new KindStats[KINDS.length];
        Arrays.setAll(experienced, i -> new KindStats());
        Segment weatherSegment = new Segment(), thermalSegment = new Segment();
        long[] previous = new long[KINDS.length];
        Arrays.fill(previous, Long.MIN_VALUE);
        int step = Integer.getInteger("endurance.sampleStep", 100);
        int days = profile.daysInYear() * years;
        long firstMidnight = profile.daysInYear() * (long) DAY - 6000;
        int dryRun = 0, wetRun = 0;
        int[] annual = new int[6];
        for (int day = 0; day < days; day++) {
            boolean wetDay = false;
            boolean snowDay = false;
            boolean thunderDay = false;
            boolean highSnowDay = false;
            int seen = 0;
            double liquidDay = 0;
            int wetTicks = 0, snowTicks = 0;
            float minimumDay = Float.POSITIVE_INFINITY, maximumDay = Float.NEGATIVE_INFINITY;
            int phase = profile.at(firstMidnight + day * (long) DAY + DAY / 2).calendar.season.ordinal();
            Stats current = stats[phase];
            current.days++;
            for (int tod = 0; tod < DAY; tod += step) {
                long time = firstMidnight + day * (long) DAY + tod;
                world.setWorldTime(time);
                WeatherEpisode episode = schedule.at(time);
                world.getWorldInfo().setRaining(episode != null && episode.kind.wet);
                world.getWorldInfo().setThundering(episode != null && episode.kind.thunder);
                WeatherEpisode thermal = RUNTIME_API >= 5 ? schedule.thermalAt(time)
                        : episode != null && thermalKind(episode.kind) ? episode : null;
                WeatherState.Frame frame = RUNTIME_API >= 5
                        ? new WeatherState.Frame(world, settings, null, episode, true, null, seed, schedule)
                        : new WeatherState.Frame(world, settings, null, episode, true);
                LocalWeather local = frame.openSky(LOW, Biomes.PLAINS);
                float high = frame.temperature(Biomes.PLAINS, HIGH);
                require(Float.isFinite(local.temperature) && Float.isFinite(high) && high <= local.temperature + 0.02F, "altitude / finite temperatures");
                require(local.intensity >= 0 && local.intensity <= 1 && local.fog >= 0 && local.fog <= 1, "bounded weather");
                current.samples++;
                minimumDay = Math.min(minimumDay, local.temperature);
                maximumDay = Math.max(maximumDay, local.temperature);
                current.temperature += local.temperature;
                current.minimum = Math.min(current.minimum, local.temperature);
                current.maximum = Math.max(current.maximum, local.temperature);
                // A sampled calendar sweep has no native rain interpolation ticks.
                LocalWeather.Precipitation falling = world.getWorldInfo().isRaining()
                        ? frame.precipitation(LOW, Biomes.PLAINS) : LocalWeather.Precipitation.NONE;
                if (falling != LocalWeather.Precipitation.NONE) {
                    wetDay = true;
                    current.wet++;
                    wetTicks += step;
                    liquidDay += liquidRate(episode.kind) * episode.intensity(time) * step / 1000.0;
                }
                if ((falling == LocalWeather.Precipitation.RAIN || falling == LocalWeather.Precipitation.HAIL)
                        && episode != null && episode.kind.thunder) {
                    current.thunder++;
                    if (episode.intensity(time) >= 0.3F) thunderDay = true;
                }
                if (falling == LocalWeather.Precipitation.SNOW) {
                    current.snow++;
                    snowDay = true;
                    snowTicks += step;
                }
                if (world.getWorldInfo().isRaining() && frame.precipitation(HIGH, Biomes.PLAINS) == LocalWeather.Precipitation.SNOW) highSnowDay = true;
                if (local.fog > 0.15F) current.obscured++;
                boolean fog = episode != null && (episode.kind == WeatherKind.RADIATION_FOG || episode.kind == WeatherKind.ADVECTION_FOG);
                if (fog && local.fog > 0.15F) current.fog++;
                boolean visible = episode != null && !thermalKind(episode.kind) && (episode.kind.wet
                        ? falling != LocalWeather.Precipitation.NONE && episode.intensity(time) >= (episode.kind == WeatherKind.DRIZZLE ? 0.1F : 0.3F)
                        : fog ? local.fog > 0.15F : local.wind > 0.2F);
                if (visible) seen |= 1 << episode.kind.ordinal();
                weatherSegment.sample(visible ? episode : null, step, experienced);
                boolean felt = thermal != null && Math.abs(thermal.kind.temperatureOffset * thermal.intensity(time) * strength) >= 0.12F;
                if (felt) seen |= 1 << thermal.kind.ordinal();
                thermalSegment.sample(felt ? thermal : null, step, experienced);
                episodeOnset(preset, seed, profile, episode, time, current, events, onsets, previous);
                episodeOnset(preset, seed, profile, thermal, time, current, events, onsets, previous);
            }
            for (WeatherKind kind : KINDS) if ((seen & (1 << kind.ordinal())) != 0) experienced[kind.ordinal()].days++;
            if (thunderDay) current.thunderDays++;
            current.fogDays += has(seen, WeatherKind.RADIATION_FOG, WeatherKind.ADVECTION_FOG) ? 1 : 0;
            current.dustDays += has(seen, WeatherKind.DUST_STORM) ? 1 : 0;
            current.coldDays += has(seen, WeatherKind.COLD_SNAP) ? 1 : 0;
            current.heatDays += has(seen, WeatherKind.HEATWAVE) ? 1 : 0;
            current.blowingDays += has(seen, WeatherKind.BLOWING_SNOW) ? 1 : 0;
            current.blizzardDays += has(seen, WeatherKind.BLIZZARD) ? 1 : 0;
            current.strongDays += has(seen, WeatherKind.SEVERE_THUNDERSTORM, WeatherKind.MONSOON_BURST, WeatherKind.TROPICAL_STORM, WeatherKind.BLIZZARD) ? 1 : 0;
            if (highSnowDay) current.highSnowDays++;
            if (wetDay) {
                current.wetDays++;
                wetRun++;
                dryRun = 0;
            } else {
                dryRun++;
                wetRun = 0;
            }
            current.maxDryDays = Math.max(current.maxDryDays, dryRun);
            current.maxWetDays = Math.max(current.maxWetDays, wetRun);
            if (liquidDay >= 0.2) current.wetDays02++;
            if (liquidDay >= 1) current.wetDays1++;
            current.liquidMm += liquidDay;
            if (snowDay) current.snowDays++;
            if (wetDay) annual[0]++;
            if (snowDay) annual[1]++;
            if (thunderDay) annual[2]++;
            if (has(seen, WeatherKind.RADIATION_FOG, WeatherKind.ADVECTION_FOG)) annual[3]++;
            if (has(seen, WeatherKind.COLD_SNAP)) annual[4]++;
            if (has(seen, WeatherKind.HEATWAVE)) annual[5]++;
            if (strength == 1 && seed == SEEDS[0] && day < 365) {
                dailyWeather.printf(Locale.ROOT, "%s,%d,%d,%d,%d,%.1f,%.1f,%.4f,%.4f%n", preset.id,seed,day,phase,seen,
                        wetTicks/20.0,snowTicks/20.0,minimumDay,maximumDay);
            }
            if ((day + 1) % profile.daysInYear() == 0) {
                yearly.printf(Locale.ROOT, "%s,%d,%.1f,%d,%d,%d,%d,%d,%d,%d%n",preset.id,seed,strength,
                        (day+1)/profile.daysInYear(),annual[0],annual[1],annual[2],annual[3],annual[4],annual[5]);
                Arrays.fill(annual,0);
            }
        }
        weatherSegment.sample(null, 0, experienced);
        thermalSegment.sample(null, 0, experienced);
        for (WeatherKind kind : KINDS) {
            KindStats e = experienced[kind.ordinal()];
            encounters.printf(Locale.ROOT, "%s,%d,%.1f,%s,%d,%d,%.2f,%.2f,%.2f%n", preset.id, seed, strength, kind,
                    e.days, e.segments, e.segments == 0 ? 0 : e.ticks / (20.0 * e.segments), e.longest / 20.0, e.ticks / 20.0);
        }
        for (int phase = 0; phase < 4; phase++) stats[phase].write(totals, preset.id, seed, strength, phase);
        events.forEach((kind, counts) -> {
            for (int phase = 0; phase < 4; phase++) {
                kinds.printf(Locale.ROOT, "%s,%d,%.1f,%s,%d,%d%n", preset.id, seed, strength, kind, phase, counts[phase]);
            }
        });
        onsets.forEach((kind, counts) -> {
            for (int hour = 0; hour < 24; hour++) {
                timing.printf(Locale.ROOT, "%s,%d,%.1f,%s,%d,%d%n", preset.id, seed, strength, kind, hour, counts[hour]);
            }
        });
    }

    private void episodeOnset(Preset preset, long seed, ClimateProfile profile, WeatherEpisode episode, long time,
                               Stats current, Map<WeatherKind,int[]> events, Map<WeatherKind,int[]> onsets, long[] previous) {
        if (episode == null || episode.startTick == previous[episode.kind.ordinal()]) return;
        previous[episode.kind.ordinal()] = episode.startTick;
                    int onsetPhase = profile.at(episode.startTick).calendar.season.ordinal();
                    events.computeIfAbsent(episode.kind, ignored -> new int[4])[onsetPhase]++;
                    int hour = (int) (episode.startTick % DAY) / 1000;
                    onsets.computeIfAbsent(episode.kind, ignored -> new int[24])[(hour + 6) % 24]++;
                    int duration = (int) (episode.endTick - episode.startTick);
                    current.events++;
                    current.eventTicks += duration;
                    current.maxEventTicks = Math.max(current.maxEventTicks, duration);
                    require(episode.contains(time) && episode.intensity(time) > 0, "active episode timing");
                    WeatherSchedule restart = new WeatherSchedule(seed, profile, preset.weather);
                    WeatherEpisode resumed = RUNTIME_API >= 5 && thermalKind(episode.kind) ? restart.thermalAt(time) : restart.at(time);
                    require(same(episode, resumed), "scheduler restart parity");
    }

    private static boolean thermalKind(WeatherKind kind) { return kind == WeatherKind.COLD_SNAP || kind == WeatherKind.HEATWAVE; }
    private static boolean has(int seen, WeatherKind... kinds) {
        for (WeatherKind kind : kinds) if ((seen & (1 << kind.ordinal())) != 0) return true;
        return false;
    }

    private static int runtimeApi() {
        try { return WeatherApi.class.getField("VERSION").getInt(null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private void climateContracts() {
        BlockPos future = new BlockPos(900008, 81, -900008);
        require(!world.isBlockLoaded(future, false), "contract starts outside generated terrain");
        int snapshots = 0, overlaps = 0;
        for (Preset preset : WeatherConfiguration.presets()) {
            WeatherSettings global = new WeatherSettings(preset.profile, false, true, true, true, preset.weather, true);
            WeatherSettings mapped = global.withMode(ClimateMode.PER_BIOME, new BiomeClimateMap(
                    Collections.singletonMap(preset.id, preset), Collections.singletonMap("minecraft:plains", preset.id)));
            WeatherSchedule schedule = new WeatherSchedule(SEEDS[0], preset.profile, preset.weather);
            BiomeWeather regions = new BiomeWeather(mapped, SEEDS[0]);
            for (int month = 0; month < 12; month++) {
                long time = (preset.profile.daysInYear() + preset.profile.daysInYear() * month / 12L) * DAY + 9000;
                world.setWorldTime(time);
                WeatherEpisode episode = schedule.at(time);
                WeatherState.Frame a = new WeatherState.Frame(world, global, null, episode, true, null, SEEDS[0], schedule);
                WeatherState.Frame b = new WeatherState.Frame(world, mapped, null, episode, true, regions, SEEDS[0]);
                require(Math.abs(a.temperature(Biomes.PLAINS, future)-b.temperature(Biomes.PLAINS, future)) < 0.00001F,
                        "mapped and universal reference parity: " + preset.id);
                WeatherState.server(a);
                ClimateSample sample = WeatherApi.getClimate(world, future, Biomes.PLAINS).get();
                require(sample.openSkyWeather.temperature == a.temperature(Biomes.PLAINS, future), "supplied biome generation temperature parity");
                require(!WeatherApi.getClimate(world, future).isPresent(), "loaded-only climate query declines future terrain");
                require(same(a.temperatureEpisode(Biomes.PLAINS), WeatherApi.getTemperatureEpisode(world, Biomes.PLAINS).orElse(null)), "background API parity");
                io.netty.buffer.ByteBuf packet = io.netty.buffer.Unpooled.buffer();
                try {
                    new zone.moddev.mc.skysrealisticweather.network.WeatherMessage(a).toBytes(packet);
                    zone.moddev.mc.skysrealisticweather.network.WeatherMessage received = new zone.moddev.mc.skysrealisticweather.network.WeatherMessage();
                    received.fromBytes(packet);
                    WeatherState.Frame client = new WeatherState.Frame(world, received.settings, null, received.episode, received.scheduled, null, received.seed);
                    require(Math.abs(client.temperature(Biomes.PLAINS, future)-a.temperature(Biomes.PLAINS, future)) < 0.00001F,
                            "universal packet temperature parity: " + preset.id);
                } finally { packet.release(); }
                WeatherEpisode thermal = a.temperatureEpisode(Biomes.PLAINS);
                if (episode != null && episode.kind.wet && thermal != null) overlaps++;
                float delta = a.temperature(Biomes.PLAINS, future)-a.seasonalTemperature(Biomes.PLAINS, Biomes.PLAINS.getFloatTemperature(future));
                require(delta >= -0.77 && delta <= 0.55, "cold air and cold fronts do not double-count extremes");
                snapshots++;
            }
        }
        require(overlaps > 0 && !world.isBlockLoaded(future, false), "cold/warm air overlaps weather without generating chunks");
        System.out.println("ENDURANCE_CLIMATE_CONTRACTS PASS snapshots=" + snapshots + " overlaps=" + overlaps);
    }

    /** Calibration proxy: mm/hour at intensity 1; 1,000 Minecraft ticks represent one climate hour. */
    private static float liquidRate(WeatherKind kind) {
        switch (kind) {
            case DRIZZLE: return 0.3F;
            case FRONTAL_PRECIPITATION: return 0.9F;
            case CONVECTIVE_SHOWER: return 12;
            case THUNDERSTORM: return 18;
            case SEVERE_THUNDERSTORM: return 30;
            case HAILSTORM: return 12;
            case MONSOON_BURST: return 4;
            case TROPICAL_STORM: return 6;
            case SNOW_SQUALL: return 3;
            case BLIZZARD: return 1;
            case WINTER_STORM:
            case FREEZING_RAIN: return 0.8F;
            default: return 0;
        }
    }

    private void temperatureReferences() {
        WeatherSettings settings = startup.withMode(ClimateMode.PER_BIOME, startup.biomes);
        world.setWorldTime(87L * DAY + 6000);
        WeatherState.Frame summer = new WeatherState.Frame(world, settings, null);
        require(summer.temperature(Biomes.COLD_TAIGA, LOW) > WeatherState.SNOW_LINE, "subarctic summer thaw");
        require(summer.temperature(Biomes.COLD_TAIGA, HIGH) < summer.temperature(Biomes.COLD_TAIGA, LOW), "mapped altitude departure retained");
        List<String> exceptions = new ArrayList<>(Arrays.asList(settings.nativeSnowBiomes()));
        exceptions.add("minecraft:taiga_cold");
        WeatherSettings protectedTaiga = settings.withNativeSnowBiomes(exceptions.toArray(new String[0]));
        WeatherState.Frame protectedFrame = new WeatherState.Frame(world, protectedTaiga, null);
        require(protectedFrame.temperature(Biomes.COLD_TAIGA, LOW) < WeatherState.SNOW_LINE, "custom native exception retained");
        io.netty.buffer.ByteBuf packet = io.netty.buffer.Unpooled.buffer();
        try {
            new zone.moddev.mc.skysrealisticweather.network.WeatherMessage(protectedFrame).toBytes(packet);
            zone.moddev.mc.skysrealisticweather.network.WeatherMessage received = new zone.moddev.mc.skysrealisticweather.network.WeatherMessage();
            received.fromBytes(packet);
            require(Arrays.equals(protectedTaiga.nativeSnowBiomes(), received.settings.nativeSnowBiomes()), "server native exceptions synchronized");
            WeatherState.Frame client = new WeatherState.Frame(world, received.settings, null);
            require(client.temperature(Biomes.COLD_TAIGA, LOW) == protectedFrame.temperature(Biomes.COLD_TAIGA, LOW), "client native temperature parity");
        } finally { packet.release(); }
        world.setWorldTime(67L * DAY + 6000);
        WeatherState.Frame arctic = new WeatherState.Frame(world, settings, null);
        int checked = 0;
        for (String id : settings.nativeSnowBiomes()) {
            net.minecraft.util.ResourceLocation key = new net.minecraft.util.ResourceLocation(id);
            if (!Biome.REGISTRY.containsKey(key)) continue;
            Biome biome = Biome.REGISTRY.getObject(key);
            require(arctic.temperature(biome, LOW) < WeatherState.SNOW_LINE, "Arctic summer remains cold: " + id);
            WeatherState.server(arctic);
            require(WeatherApi.getPopulationPolicy(world, biome).get() == PopulationPolicy.NATIVE, "Arctic generation policy: " + id);
            checked++;
        }
        List<String> unprotected = new ArrayList<>(Arrays.asList(settings.nativeSnowBiomes()));
        unprotected.remove("minecraft:ice_flats");
        WeatherState.Frame optedOut = new WeatherState.Frame(world,
                settings.withNativeSnowBiomes(unprotected.toArray(new String[0])), null);
        require(optedOut.temperature(Biomes.ICE_PLAINS, LOW) > WeatherState.SNOW_LINE, "Arctic exception opt-out works");
        ClimateProfile p = settings.profile;
        WeatherState.Frame disabled = new WeatherState.Frame(world, settings.withClimate(
                new ClimateProfile(0, p.period(0), p.period(1), p.period(2), p.period(3)), settings.weather), null);
        require(disabled.temperature(Biomes.COLD_TAIGA, LOW) == Biomes.COLD_TAIGA.getFloatTemperature(LOW), "zero strength keeps vanilla temperature");
        System.out.println("ENDURANCE_TEMPERATURE_REFERENCES PASS nativeBiomes=" + checked);
    }

    private void nativeRun(String presetId, int days) throws Exception {
        ServerWeather server = ServerWeather.INSTANCE;
        server.restore();
        if (presetId != null) server.usePreset(WeatherConfiguration.preset(presetId));
        else server.useMode(ClimateMode.PER_BIOME);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "true");
        world.getGameRules().setOrCreateGameRule("doSeasonalWeather", "true");
        server.automatic();
        for (int i = 0; i < BIOMES.length; i++) pad(i, BIOMES[i]);
        String name = presetId == null ? "per_biome" : presetId;
        int oldFreezes = freezes, oldThaws = thaws, oldSnow = snow;
        try (PrintWriter daily = writer("native-" + name + ".csv")) {
            daily.println("site,day,biome,climate,wet_samples,samples,min_temp,max_temp,ice,snow,cover_fraction,cover_at_0900,ice_at_0900,scored");
            Random cadence = new Random(world.getSeed() ^ name.hashCode() ^ 0x5DEECE66DL);
            long[] nextColumn = new long[BIOMES.length * 10];
            Arrays.setAll(nextColumn, i -> weatherWait(cadence));
            long nextPass = Arrays.stream(nextColumn).min().getAsLong();
            for (int day = 0; day < days; day++) {
                int[] wet = new int[BIOMES.length];
                float[] min = new float[BIOMES.length], max = new float[BIOMES.length];
                Arrays.fill(min, Float.POSITIVE_INFINITY);
                Arrays.fill(max, Float.NEGATIVE_INFINITY);
                double[] morning = new double[BIOMES.length];
                boolean[] morningIce = new boolean[BIOMES.length];
                for (int tod = 0; tod < DAY; tod++) {
                    long tick = day * (long) DAY + tod;
                    world.getWorldInfo().setWorldTotalTime(nativeTicks++);
                    world.setWorldTime(tick);
                    server.startTick(startEvent);
                    world.updateWeatherBody();
                    server.endTick(endEvent);
                    WeatherState.Frame frame = WeatherState.frame(world);
                    require(frame.active() && frame.scheduled, "automatic native control remains active");
                    if (tod % 200 == 0) {
                        for (int i = 0; i < BIOMES.length; i++) {
                            BlockPos sample = plot(i).up();
                            LocalWeather local = frame.openSky(sample, BIOMES[i]);
                            require(Float.isFinite(local.temperature) && local.intensity >= 0 && local.intensity <= 1
                                    && local.fog >= 0 && local.fog <= 1 && local.wind >= 0 && local.wind <= 1, "native local value bounds");
                            min[i] = Math.min(min[i], local.temperature);
                            max[i] = Math.max(max[i], local.temperature);
                            if (local.precipitation != LocalWeather.Precipitation.NONE) wet[i]++;
                            WeatherEpisode episode = frame.currentEpisode(BIOMES[i]);
                            require(episode == null || episode.contains(episode.time(tick, world.getTotalWorldTime())), "local episode expiry");
                            if (presetId == null && (i == 1 || i == 2)) require(local.precipitation != LocalWeather.Precipitation.SNOW, "warm biomes have no snow");
                        }
                    }
                    if (tick >= nextPass) {
                        for (int column = 0; column < nextColumn.length; column++) if (tick >= nextColumn[column]) {
                            surfaceColumn(column / 10, column % 10);
                            nextColumn[column] = tick + weatherWait(cadence);
                        }
                        nextPass = Arrays.stream(nextColumn).min().getAsLong();
                    }
                    if (tod == 3000) for (int i = 0; i < BIOMES.length; i++) {
                        morning[i] = Integer.bitCount(snowMasks[i]) / 9.0;
                        morningIce[i] = world.getBlockState(plot(i)).getBlock() == Blocks.ICE;
                    }
                }
                WeatherState.Frame frame = WeatherState.frame(world);
                for (int i = 0; i < BIOMES.length; i++) {
                    daily.printf(Locale.ROOT, "%s,%d,%s,%s,%d,120,%.4f,%.4f,%s,%s,%.4f,%s,%s,%s%n", SITES[i], day,
                            BIOMES[i].getRegistryName(), frame.climateId(BIOMES[i]), wet[i], min[i], max[i],
                            world.getBlockState(plot(i)).getBlock() == Blocks.ICE,
                            snowMasks[i] != 0, morning[i], morning[i] > 0.5, morningIce[i], day >= Integer.getInteger("endurance.warmupDays", 365));
                }
                if (day % 30 == 0) {
                    daily.flush();
                    System.out.println("ENDURANCE_NATIVE " + name + " day=" + day);
                    pause();
                }
                int dayInYear = day % 365;
                if (dayInYear == 90 || dayInYear == 181 || dayInYear == 272) restart(presetId);
            }
        }
        System.out.println("ENDURANCE_SURFACES " + name + " freezes=" + (freezes-oldFreezes)
                + " thaws=" + (thaws-oldThaws) + " snow=" + (snow-oldSnow));
    }

    private static long weatherWait(Random random) {
        // Native weather samples one of 256 columns on a 1-in-16 chunk tick: p = 1/4096 per column.
        return 1 + (long) (Math.log1p(-random.nextDouble()) / Math.log1p(-1.0 / 4096));
    }

    private void surfaceColumn(int i, int column) {
            BlockPos water = plot(i);
            if (column == 0) {
            boolean hadIce = world.getBlockState(water).getBlock() == Blocks.ICE;
            if (WeatherHooks.freeze(world, water)) {
                if (WeatherHooks.placeDeposit(world, water, Blocks.ICE.getDefaultState())) freezes++;
            }
            if (hadIce && world.getBlockState(water).getBlock() == Blocks.WATER) thaws++;
            return;
            }
            BlockPos snowPos = snowPlot(i, column - 1);
            WeatherHooks.freeze(world, snowPos.down());
            if (world.isRaining() && WeatherHooks.snow(world, snowPos, true)) {
                if (WeatherHooks.placeDeposit(world, snowPos, Blocks.SNOW_LAYER.getDefaultState())) snow++;
            }
            int bit = 1 << (column - 1);
            if (world.getBlockState(snowPos).getBlock() == Blocks.SNOW_LAYER) snowMasks[i] |= bit;
            else snowMasks[i] &= ~bit;
    }

    private static BlockPos plot(int index) {
        return new BlockPos(8 + index * 32, index == 5 || index == 7 ? 199 : 80, 8);
    }

    private static BlockPos snowPlot(int index, int cell) {
        return plot(index).add(2 + cell % 3 - 1, 1, cell / 3 - 1);
    }

    private void pad(int index, Biome biome) {
        BlockPos position = plot(index);
        byte id = (byte) Biome.getIdForBiome(biome);
        Arrays.fill(world.getChunkFromBlockCoords(position).getBiomeArray(), id);
        world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(position, Blocks.WATER.getDefaultState(), 2);
        world.setBlockState(position.up(), Blocks.AIR.getDefaultState(), 2);
        boolean nativeArctic = index == 4 && WeatherState.frame(world).settings.keepsNativeSurface(biome.getRegistryName());
        if (nativeArctic) world.setBlockState(position, Blocks.ICE.getDefaultState(), 2);
        snowMasks[index] = nativeArctic ? 511 : 0;
        for (int cell = 0; cell < 9; cell++) {
            BlockPos top = snowPlot(index, cell);
            world.setBlockState(top.down(), Blocks.GRASS.getDefaultState(), 2);
            world.setBlockState(top, nativeArctic ? Blocks.SNOW_LAYER.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
        }
    }

    private void restart(String presetId) throws Exception {
        long time = world.getWorldTime();
        NBTTagCompound saved = world.getWorldInfo().cloneNBTCompound(null);
        File file = new File(output, "weather-state-" + (++rests) + ".dat");
        try (OutputStream stream = new FileOutputStream(file)) { CompressedStreamTools.writeCompressed(saved, stream); }
        NBTTagCompound loaded;
        try (InputStream stream = new FileInputStream(file)) { loaded = CompressedStreamTools.readCompressed(stream); }
        require(loaded.getLong("DayTime") == time, "calendar persisted through NBT");
        WeatherEpisode[] before = new WeatherEpisode[BIOMES.length];
        WeatherEpisode[] thermal = new WeatherEpisode[BIOMES.length];
        float[] temperatures = new float[BIOMES.length];
        WeatherState.Frame old = WeatherState.frame(world);
        for (int i = 0; i < before.length; i++) {
            before[i] = old.currentEpisode(BIOMES[i]);
            thermal[i] = old.temperatureEpisode(BIOMES[i]);
            temperatures[i] = old.temperature(BIOMES[i], plot(i).up());
        }
        ServerWeather server = ServerWeather.INSTANCE;
        server.close();
        server.load(new WorldEvent.Load(world));
        if (presetId != null) server.usePreset(WeatherConfiguration.preset(presetId));
        server.automatic();
        server.startTick(startEvent);
        world.updateWeatherBody();
        server.endTick(endEvent);
        for (int i = 0; i < before.length; i++) {
            require(same(before[i], WeatherState.frame(world).currentEpisode(BIOMES[i])), "runtime restart parity");
            require(same(thermal[i], WeatherState.frame(world).temperatureEpisode(BIOMES[i])), "thermal restart parity");
            require(Math.abs(temperatures[i] - WeatherState.frame(world).temperature(BIOMES[i], plot(i).up())) < 0.00001F,
                    "temperature restart parity");
        }
    }

    private void liveReplay() {
        LiveWeatherControl control = new LiveWeatherControl(world.getWorldInfo());
        control.replaceOverride();
        zone.moddev.mc.skysrealisticweather.live.Observation.Condition[] states =
                zone.moddev.mc.skysrealisticweather.live.Observation.Condition.values();
        for (int hour = 0; hour < 24 * 30; hour++) {
            zone.moddev.mc.skysrealisticweather.live.Observation value =
                    new zone.moddev.mc.skysrealisticweather.live.Observation(1700000000L + hour * 3600L,
                            hour % 24 < 8 ? -4 : 15, states[hour % states.length]);
            long tick = nativeTicks + hour * 1000L;
            require(control.beforeTick(tick, value) == value, "live observation accepted during replay");
            for (int elapsed = 0; elapsed < 1000; elapsed++) {
                control.beforeTick(tick + elapsed, value);
                world.updateWeatherBody();
                control.afterTick();
            }
            require(world.isRaining() == value.condition.wet(), "live weather replay condition");
        }
        System.out.println("ENDURANCE_LIVE_REPLAY days=30 PASS");
    }

    private void saveFixture() {
        ServerWeather server = ServerWeather.INSTANCE;
        server.usePreset(WeatherConfiguration.preset("winter_wonderland"));
        server.setDaylight(320L * DAY + 6000);
        server.force(WeatherKind.WINTER_STORM, 600, 0.85F, false);
        server.startTick(startEvent);
        world.updateWeatherBody();
        pad(0, Biomes.PLAINS);
        require(WeatherHooks.freeze(world, plot(0)), "cold persisted water fixture");
        require(WeatherHooks.placeDeposit(world, plot(0), Blocks.ICE.getDefaultState()), "persist ice fixture");
        require(WeatherHooks.placeDeposit(world, plot(0).east(2).up(), Blocks.SNOW_LAYER.getDefaultState()), "persist snow fixture");
        server.endTick(endEvent);
    }

    private void reload() {
        world.getChunkFromBlockCoords(plot(0));
        require(world.getBlockState(plot(0)).getBlock() == Blocks.ICE, "ice survived process restart");
        require(world.getBlockState(plot(0).east(2).up()).getBlock() == Blocks.SNOW_LAYER, "snow survived process restart");
        ServerWeather.INSTANCE.setDaylight(134L * DAY + 6000);
        WeatherHooks.freeze(world, plot(0));
        WeatherHooks.freeze(world, plot(0).east(2));
        require(world.getBlockState(plot(0)).getBlock() == Blocks.WATER, "persisted ice thawed after restart");
        require(world.getBlockState(plot(0).east(2).up()).getBlock() == Blocks.AIR, "persisted snow thawed after restart");
        System.out.println("ENDURANCE_RELOAD PASS");
        world.getMinecraftServer().initiateShutdown();
    }

    private PrintWriter writer(String name) throws IOException {
        return new PrintWriter(new BufferedWriter(new FileWriter(new File(output, name))));
    }

    private static Stats[] stats() {
        return new Stats[]{new Stats(),new Stats(),new Stats(),new Stats()};
    }

    private static boolean same(WeatherEpisode a, WeatherEpisode b) {
        return a == null ? b == null : b != null && a.kind == b.kind && a.startTick == b.startTick
                && a.endTick == b.endTick && a.peakIntensity == b.peakIntensity;
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("ENDURANCE_FAILED " + description);
    }

    private static void pause() throws InterruptedException {
        Thread.sleep(10);
    }

    private static final class Stats {
        long samples, wet, thunder, snow, fog, obscured, eventTicks;
        int days, wetDays, events, maxEventTicks, maxDryDays, maxWetDays, wetDays02, wetDays1, snowDays;
        int highSnowDays, thunderDays, fogDays, dustDays, coldDays, heatDays, blowingDays, blizzardDays, strongDays;
        float minimum = Float.POSITIVE_INFINITY, maximum = Float.NEGATIVE_INFINITY;
        double temperature, liquidMm;

        void write(PrintWriter out, String id, long seed, float strength, int phase) {
            out.printf(Locale.ROOT, "%s,%d,%.1f,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.2f,%.2f,%d,%d,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%d%n",
                    id,seed,strength,phase,days,wetDays,wet*100.0/samples,thunder*100.0/samples,snow*100.0/samples,
                    fog*100.0/samples,minimum,maximum,temperature/samples,maxDryDays,maxWetDays,events,
                    events == 0 ? 0 : eventTicks/(20.0*events),maxEventTicks/20.0,wetDays02,wetDays1,liquidMm,snowDays,
                    highSnowDays,thunderDays,fogDays,dustDays,coldDays,heatDays,blowingDays,blizzardDays,strongDays,obscured*100.0/samples,samples);
        }
    }

    private static final class KindStats {
        long ticks;
        int days, segments, longest;
    }

    private static final class Segment {
        private WeatherEpisode previous;
        private int ticks;
        void sample(WeatherEpisode current, int step, KindStats[] stats) {
            if (previous != null && (current == null || current.kind != previous.kind || current.startTick != previous.startTick)) {
                KindStats old = stats[previous.kind.ordinal()];
                old.longest = Math.max(old.longest, ticks);
                previous = null;
                ticks = 0;
            }
            if (current == null) return;
            KindStats value = stats[current.kind.ordinal()];
            if (previous == null) value.segments++;
            previous = current;
            ticks += step;
            value.ticks += step;
        }
    }
}
