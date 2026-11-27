package zone.moddev.mc.skysrealisticweather.verify;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.ClimateSample;
import zone.moddev.mc.skysrealisticweather.api.BiomeClimate;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.api.SeasonChangedEvent;
import zone.moddev.mc.skysrealisticweather.api.SeasonSnapshot;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisodeChangedEvent;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.admin.WeatherCommand;
import zone.moddev.mc.skysrealisticweather.admin.WeatherController;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.climate.WeatherSettings;
import zone.moddev.mc.skysrealisticweather.core.HookStatus;
import zone.moddev.mc.skysrealisticweather.game.WeatherHooks;
import zone.moddev.mc.skysrealisticweather.game.ServerWeather;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;

@Mod(modid = "skyweatherprobe", name = "Sky Weather Probe", version = "1", dependencies = "required-after:skysrealisticweather")
public final class WeatherProbe {
    static final BlockPos SNOW = new BlockPos(4, 71, 5);
    static final BlockPos ICE = new BlockPos(7, 70, 5);
    static final BlockPos REPLACED = new BlockPos(9, 71, 5);
    static final BlockPos MANUAL = new BlockPos(12, 71, 5);
    static final BlockPos CHECK = new BlockPos(4, 72, 8);
    static final long DAY = 24000L;
    static final int MID_WINTER = 320;
    static final String MODE = System.getProperty("skyweather.probe", "write");

    @SidedProxy(clientSide = "zone.moddev.mc.skysrealisticweather.verify.ClientProbe",
            serverSide = "zone.moddev.mc.skysrealisticweather.verify.WeatherProbe$SideProbe")
    public static SideProbe proxy;

    private WorldServer world;
    private int ticks;
    private int seasonChanges;
    private int sleepCheck = -1;
    private long beforeSleep;
    private boolean sawAdminRain;
    private boolean sawAdminStrength;
    private boolean sawAdminSeason;
    private int adminFinish;
    private int apiEpisodeStarts;
    private int apiEpisodeEnds;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        proxy.register();
        if ("thermometer".equals(MODE)) ThermometerProbe.register();
        if ("library".equals(MODE)) {
            WeatherApi.registerBiomeClimate(Biomes.MUSHROOM_ISLAND.getRegistryName(),
                    new BiomeClimate(0.15F, 0.5F, BiomeClimate.PrecipitationRule.DRY));
        }
    }

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        world = server.getWorld(0);
        require(HookStatus.available(), "required weather hooks loaded");
        switch (MODE) {
            case "metadata": metadata(server); break;
            case "temperature-zones": TemperatureZoneProbe.run(world); break;
            case "thermal-network": new TemperatureNetworkProbe(world); break;
            case "jei-client": new zone.moddev.mc.skysrealisticweather.verify.jei.JeiServerProbe(world); break;
            case "precipitation": PrecipitationProbe.run(world); break;
            case "thermometer": ThermometerProbe.run(world); break;
            case "plants":
                try { zone.moddev.mc.skysrealisticweather.game.PlantGrowthProbe.run(world); }
                catch (Exception failure) { throw new IllegalStateException("Plant verification failed", failure); }
                break;
            case "endurance":
                try {
                    zone.moddev.mc.skysrealisticweather.game.ClimateEnduranceProbe.run(world);
                } catch (Exception failure) {
                    throw new IllegalStateException("Endurance verification failed", failure);
                }
                break;
            case "biome-modes": ClimateModeProbe.run(world); break;
            case "biome-network":
                world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                world.setWorldTime(55L * DAY + 9000);
                ServerWeather.INSTANCE.automatic();
                System.out.println("SKY_WEATHER_CHECK BIOME_NETWORK_READY");
                break;
            case "calendar": calendar(); break;
            case "climates": climates(); break;
            case "write": write(); break;
            case "read": read(); break;
            case "passive": passive(); break;
            case "combined": combined(); break;
            case "network": network(); break;
            case "admin": admin(); break;
            case "library":
                fixture();
                time(MID_WINTER, 6000);
                require(command("skyweather weather winter_storm 20 0.85") == 1, "library test storm");
                break;
            case "population": new PopulationProbe(world, System.getProperty("skyweather.population", "winter")); break;
            case "biome-compat": new BiomeCompatibilityProbe(world); break;
            case "admin-network":
                fixture();
                time(MID_WINTER, 3500);
                System.out.println("SKY_WEATHER_CHECK ADMIN_NETWORK_READY");
                break;
            case "episodes":
                fixture();
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 5; z++) world.getChunkFromChunkCoords(x, z);
                }
                time(134, 5990);
                break;
            case "live": break;
            default: throw new IllegalArgumentException(MODE);
        }
    }

    private void metadata(MinecraftServer server) {
        net.minecraftforge.fml.common.Loader loader=net.minecraftforge.fml.common.Loader.instance();
        net.minecraftforge.fml.common.ModContainer mod=loader.getIndexedModList().get("skysrealisticweather");
        net.minecraftforge.fml.common.ModMetadata bound=mod.getMetadata();
        try(java.util.zip.ZipFile jar=new java.util.zip.ZipFile(mod.getSource());
                java.io.InputStream input=jar.getInputStream(jar.getEntry("mcmod.info"))) {
            com.google.gson.JsonObject declared=new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonArray().get(0).getAsJsonObject();
            java.util.Set<String> expected=new java.util.HashSet<>(),actual=new java.util.HashSet<>();
            for(com.google.gson.JsonElement e:declared.getAsJsonArray("dependencies"))expected.add(e.getAsString().split("@",2)[0]);
            for(net.minecraftforge.fml.common.versioning.ArtifactVersion v:bound.dependencies)actual.add(v.getLabel());
            require(bound.useDependencyInformation&&expected.equals(actual),"Forge uses declared optional dependency metadata");
            require(bound.requiredMods.size()==1&&bound.requiredMods.iterator().next().getLabel().equals("forge"),"only Forge is mandatory");
            java.util.List<net.minecraftforge.fml.common.ModContainer> sorted=loader.getActiveModList();
            for(net.minecraftforge.fml.common.versioning.ArtifactVersion v:bound.dependencies) {
                net.minecraftforge.fml.common.ModContainer dependency=loader.getIndexedModList().get(v.getLabel());
                if(dependency!=null)require(sorted.indexOf(dependency)<sorted.indexOf(mod),"installed integration loads before weather: "+v.getLabel());
            }
            System.out.println("SKY_WEATHER_CHECK METADATA PASS required="+bound.requiredMods+" dependencies="+actual);
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
        server.initiateShutdown();
    }

    private void calendar() {
        String expectedPreset = System.getProperty("skyweather.preset", "burlington").toLowerCase(java.util.Locale.ROOT);
        String lengths = System.getProperty("skyweather.lengths", "92,92,91,90");
        require(expectedPreset.equals(WeatherConfiguration.activePreset()), "saved preset " + expectedPreset);
        String[] expectedLengths = lengths.split(",");
        int day = 0;
        for (Season season : Season.values()) {
            int length = Integer.parseInt(expectedLengths[season.ordinal()]);
            time(day, 0);
            SeasonSnapshot start = WeatherApi.getSeason(world).get();
            require(start.season == season && start.lengthInDays == length && start.dayOfSeason == 1,
                    season + " starts on day " + day + " and lasts " + length + " days");
            day += length;
            time(day - 1, 23999);
            require(WeatherApi.getSeason(world).get().dayOfSeason == length, season + " final day");
        }
        time(day, 0);
        require(WeatherApi.getSeason(world).get().season == Season.SPRING, "calendar wraps on day " + day);
        succeed("CALENDAR_" + expectedPreset + "_" + day);
    }

    private void climates() {
        fixture();
        rain();
        selectPhase("mediterranean", Season.SUMMER);
        int dry = WeatherHooks.weatherDuration(10000, world, 3);
        selectPhase("mediterranean", Season.AUTUMN);
        require(dry > WeatherHooks.weatherDuration(10000, world, 3), "Mediterranean summer has longer dry spells");
        selectPhase("tropical_monsoon", Season.SUMMER);
        int wet = WeatherHooks.weatherDuration(10000, world, 2);
        require(weather(CHECK).precipitation == LocalWeather.Precipitation.RAIN, "monsoon peak falls as lowland rain");
        selectPhase("tropical_monsoon", Season.WINTER);
        require(wet > 3 * WeatherHooks.weatherDuration(10000, world, 2), "monsoon dry phase is distinct");
        require(WeatherApi.getSeason(world).get().lengthInDays == 210, "monsoon dry phase lasts 210 days");
        selectPhase("alpine_highlands", Season.SUMMER);
        require(weather(CHECK).precipitation == LocalWeather.Precipitation.RAIN, "alpine valley gets summer rain");
        require(weather(new BlockPos(CHECK.getX(), 210, CHECK.getZ())).precipitation
                == LocalWeather.Precipitation.SNOW, "alpine highlands get summer snow");
        selectPhase("tundra", Season.WINTER);
        require(WeatherHooks.freeze(world, ICE), "tundra freezes water in winter");
        require(WeatherHooks.placeDeposit(world, ICE, Blocks.ICE.getDefaultState()), "tundra ice tracks the source");
        selectPhase("tundra", Season.SUMMER);
        WeatherHooks.freeze(world, ICE);
        require(world.getBlockState(ICE).getBlock() == Blocks.WATER, "tundra melts ice in summer");
        selectPhase("ice_cap", Season.SUMMER);
        require(WeatherHooks.freeze(world, ICE), "ice cap freezes water even in summer");
        require(WeatherHooks.placeDeposit(world, ICE, Blocks.ICE.getDefaultState()), "ice cap forms ice");
        for (Season season : Season.values()) {
            selectPhase("ice_cap", season);
            WeatherHooks.freeze(world, ICE);
            require(world.getBlockState(ICE).getBlock() == Blocks.ICE, "ice cap stays frozen during " + season);
        }
        succeed("CLIMATE_CONTRASTS");
    }

    private void selectPhase(String id, Season phase) {
        ClimateProfile profile = WeatherConfiguration.preset(id).profile;
        long start = 0;
        for (int index = 0; index < phase.ordinal(); index++) start += profile.period(index).days;
        time(start + profile.period(phase.ordinal()).days / 2, 0);
        WeatherState.server(new WeatherState.Frame(world, new WeatherSettings(profile, false, true, true, true), null));
    }

    private void write() {
        fixture();
        time(MID_WINTER, 0);
        rain();
        require(WeatherApi.getSeason(world).get().season == Season.WINTER, "winter season");
        require(weather(CHECK).precipitation == LocalWeather.Precipitation.SNOW, "lowland winter snow");
        require(!world.isRainingAt(CHECK), "vanilla rain gate rejects winter snow");
        require(weather(CHECK.add(2, 0, 0)).precipitation == LocalWeather.Precipitation.NONE, "roof protects");
        require(WeatherHooks.snow(world, SNOW, true), "native snow placement check");
        require(WeatherHooks.freeze(world, ICE), "native source freeze check");
        require(WeatherHooks.placeDeposit(world, SNOW, Blocks.SNOW_LAYER.getDefaultState()), "tracked snow");
        require(WeatherHooks.placeDeposit(world, ICE, Blocks.ICE.getDefaultState()), "tracked ice");
        require(WeatherHooks.placeDeposit(world, REPLACED, Blocks.SNOW_LAYER.getDefaultState()), "replacement fixture");
        world.setBlockToAir(REPLACED);
        world.setBlockState(REPLACED, Blocks.SNOW_LAYER.getDefaultState());
        world.setBlockState(MANUAL, Blocks.SNOW_LAYER.getDefaultState());

        time(301, 0);
        require(weather(CHECK).precipitation == LocalWeather.Precipitation.RAIN, "lowland rain at changing snow line");
        require(weather(new BlockPos(CHECK.getX(), 210, CHECK.getZ())).precipitation
                == LocalWeather.Precipitation.SNOW, "same column has snow at higher elevation");
        Chunk dry = world.getChunkFromBlockCoords(CHECK.east(16));
        byte[] biomes = dry.getBiomeArray();
        java.util.Arrays.fill(biomes, (byte) net.minecraft.world.biome.Biome.getIdForBiome(Biomes.DESERT));
        require(weather(CHECK.east(16)).precipitation == LocalWeather.Precipitation.NONE, "desert stays dry");
        time(MID_WINTER, 0);
        require(!WeatherApi.getWeather(world, new BlockPos(180000, 70, 180000)).isPresent(), "unloaded query");
        require(!WeatherApi.getSeason(world.getMinecraftServer().getWorld(-1)).isPresent(), "Nether scope");
        succeed("WRITE");
    }

    private void read() {
        require(world.getBlockState(SNOW).getBlock() == Blocks.SNOW_LAYER, "snow survived restart");
        require(world.getBlockState(ICE).getBlock() == Blocks.ICE, "ice survived restart");
        time(138, 6000);
        WeatherHooks.freeze(world, SNOW.down());
        WeatherHooks.freeze(world, REPLACED.down());
        WeatherHooks.freeze(world, MANUAL.down());
        WeatherHooks.freeze(world, ICE);
        require(world.isAirBlock(SNOW), "tracked snow thaws after a reload");
        require(world.getBlockState(ICE).getMaterial() == net.minecraft.block.material.Material.WATER,
                "tracked ice restores water source after a reload");
        require(world.getBlockState(REPLACED).getBlock() == Blocks.SNOW_LAYER, "player-replaced layer remains");
        require(world.getBlockState(MANUAL).getBlock() == Blocks.SNOW_LAYER, "manual snow remains");
        succeed("RELOAD");
    }

    private void passive() {
        fixture();
        time(MID_WINTER, 0);
        rain();
        require(WeatherApi.getSeason(world).get().season == Season.WINTER, "passive has a calendar");
        require(weather(CHECK).source == LocalWeather.Source.VANILLA, "passive uses vanilla");
        require(world.isRainingAt(CHECK), "passive leaves vanilla rainfall");
        require(!WeatherHooks.snow(world, SNOW, true), "passive plains do not gain snow");
        succeed("PASSIVE");
    }

    private void combined() {
        require(net.minecraftforge.fml.common.Loader.isModLoaded("BiomesOPlenty"), "Biomes O' Plenty loaded");
        require(net.minecraftforge.fml.common.Loader.isModLoaded("skysgrassslabs"), "Grass Slabs loaded");
        require(net.minecraftforge.fml.common.Loader.isModLoaded("skysbuildingpieces"), "Building Pieces loaded");
        require(net.minecraftforge.fml.common.Loader.isModLoaded("skysterrainsmoother"), "Terrain Smoother loaded");
        time(MID_WINTER, 0);
        rain();
        BlockPos sample = world.getPrecipitationHeight(world.getSpawnPoint());
        require(WeatherApi.getWeather(world, sample).isPresent(), "world weather API available in combined pack");
        require(weather(sample).source == LocalWeather.Source.SEASONAL, "seasonal hooks active in combined pack");
        succeed("COMBINED");
    }

    private void network() {
        fixture();
        String expected = System.getProperty("skyweather.preset");
        if (expected != null) require(expected.equals(WeatherConfiguration.activePreset()), "server selected its configured preset");
        int[] rows = {4, 9, 12, 14};
        for (int x : rows) {
            for (int z = 16; z < 44; z += 8) {
                BlockPos trunk = new BlockPos(x, 71, z);
                world.setBlockState(trunk.down(), Blocks.GRASS.getDefaultState());
                for (int y = 0; y < 5; y++) world.setBlockState(trunk.up(y), Blocks.LOG.getDefaultState());
                for (int y = 3; y < 7; y++) {
                    int radius = y < 5 ? 2 : 1;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            BlockPos pos = trunk.add(dx, y, dz);
                            if (world.isAirBlock(pos)) {
                                world.setBlockState(pos, Blocks.LEAVES.getDefaultState()
                                        .withProperty(net.minecraft.block.BlockLeaves.DECAYABLE, false));
                            }
                        }
                    }
                }
            }
        }
        for (int x = -15; x <= 28; x++) {
            for (int z = -8; z <= 47; z++) {
                for (int y = 65; y < 70; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.DIRT.getDefaultState());
                world.setBlockState(new BlockPos(x, 70, z), Blocks.GRASS.getDefaultState());
            }
        }
        world.setBlockState(new BlockPos(4, 70, 8), Blocks.STONE.getDefaultState());
        time(46, 6000);
        world.getMinecraftServer().getCommandManager().executeCommand(world.getMinecraftServer(), "weather clear 600");
        System.out.println("SKY_WEATHER_CHECK NETWORK_READY");
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!("network".equals(MODE) || "admin-network".equals(MODE)) || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        player.connection.setPlayerLocation(player.getName().endsWith("2") ? 9 : 2, 72, 4, 0, 8);
    }

    @SubscribeEvent
    public void season(SeasonChangedEvent event) {
        if (event.world == world) seasonChanges++;
    }

    @SubscribeEvent
    public void episodeChanged(WeatherEpisodeChangedEvent event) {
        if (!"library".equals(MODE) || event.world != world) return;
        boolean wasForced = event.previous.map(episode -> episode.forced).orElse(false);
        boolean isForced = event.current.map(episode -> episode.forced).orElse(false);
        require(WeatherApi.getWeatherEpisode(world).equals(event.current), "episode listener sees the committed snapshot");
        if (isForced) apiEpisodeStarts++;
        if (wasForced && !isForced) apiEpisodeEnds++;
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (world == null || event.world != world || event.phase != TickEvent.Phase.END) return;
        if ("live".equals(MODE) && ++ticks % 20 == 0) {
            BlockPos sample = world.getPrecipitationHeight(world.getSpawnPoint());
            LocalWeather weather = weather(sample);
            if (weather.source == LocalWeather.Source.LIVE) {
                System.out.println("SKY_WEATHER_CHECK LIVE " + weather.precipitation + " temperature=" + weather.temperature);
                world.getMinecraftServer().initiateShutdown();
            } else if (ticks > 300) {
                throw new IllegalStateException("Live weather did not arrive");
            }
        } else if ("network".equals(MODE)) {
            networkTick();
        } else if ("episodes".equals(MODE)) {
            episodeTick();
        } else if ("admin".equals(MODE)) {
            adminTick();
        } else if ("admin-network".equals(MODE)) {
            adminNetworkTick();
        } else if ("library".equals(MODE)) {
            libraryTick();
        }
    }

    private void libraryTick() {
        if (++ticks == 120) {
            BlockPos covered = CHECK.add(2, 0, 0);
            ClimateSample sample = WeatherApi.getClimate(world, covered).get();
            LocalWeather local = WeatherApi.getWeather(world, covered).get();
            require(sample.openSkyWeather.precipitation == LocalWeather.Precipitation.SNOW
                    && local.precipitation == LocalWeather.Precipitation.NONE, "runtime shelter and open sky disagree correctly");
            require(sample.openSkyWeather.temperature == local.temperature
                    && local.temperature == WeatherApi.getTemperature(world, covered).getAsDouble(),
                    "runtime queries agree on temperature");
            require(sample.vanillaTemperature > sample.seasonalTemperature
                    && sample.seasonalTemperature > local.temperature, "reading distinguishes the storm and seasonal offsets");
            require(WeatherApi.getBiomeClimate(Biomes.MUSHROOM_ISLAND).precipitation == BiomeClimate.PrecipitationRule.DRY,
                    "resolved biome override is readable");
            require(WeatherApi.getPopulationPolicy(world, Biomes.ICE_PLAINS).get() == WeatherApi.PopulationPolicy.NATIVE
                    && WeatherApi.getPopulationPolicy(world, Biomes.PLAINS).get() == WeatherApi.PopulationPolicy.SEASONAL,
                    "population policy shows the protected biome");
            require(command("skyweather climate " + covered.getX() + " " + covered.getY() + " " + covered.getZ()) == 1,
                    "loaded climate diagnostic succeeds");
            require(command("skyweather climate ~ ~ ~") == 1, "relative climate diagnostic succeeds");
            require(command("skyweather climate 180000 70 180000") == 0
                    && world.getChunkProvider().getLoadedChunk(11250, 11250) == null, "diagnostic does not generate terrain");
            require(WeatherApi.getClimate(world.getMinecraftServer().getWorld(-1), covered, Biomes.PLAINS)
                    .isPresent() == false, "Nether has no seasonal climate frame");
            require(apiEpisodeStarts == 1 && apiEpisodeEnds == 0, "storm start event fires once");
            command("skyweather weather clear 4");
        } else if (ticks == 140) {
            require(apiEpisodeStarts == 1 && apiEpisodeEnds == 1, "storm end event fires once");
            succeed("LIBRARY_API");
        }
    }

    private void admin() {
        fixture();
        ServerWeather weather = ServerWeather.INSTANCE;
        require(command("skyweather time noon") == 1 && world.getWorldTime() == 6000, "console sets solar time");
        require(command("skyweather season previous") == 0 && world.getWorldTime() == 6000,
                "first-year previous phase cannot cross day zero");
        require(command("skyweather season next") == 1 && world.getWorldTime() == 92 * DAY + 6000,
                "next phase preserves time of day");
        require(command("skyweather season set winter 45") == 1 && phaseDay(Season.WINTER, 45),
                "console jumps to an exact phase day");
        require(command("skyweather season set winter 91") == 0 && phaseDay(Season.WINTER, 45),
                "invalid phase day leaves the clock in place");
        require(command("skyweather season next") == 1 && phaseDay(Season.SPRING, 1),
                "next wraps into the following year");
        require(command("skyweather season previous") == 1 && phaseDay(Season.WINTER, 1),
                "previous wraps into the preceding year");
        require(command("skyweather season advance 3") == 1 && phaseDay(Season.WINTER, 4), "advance by Minecraft days");
        require(command("skyweather preset tropical_monsoon") == 1
                && weather.settings().profile.period(3).days == 210
                && weather.phaseLabels()[1].equals("Peak Wet"), "uses the server's preset catalogue");
        require(command("skyweather strength NaN") == 0 && weather.settings().profile.strength == 1,
                "nonfinite numbers are rejected without mutation");
        require(command("skyweather strength 1.4") == 1 && weather.settings().profile.strength == 1.4F
                && command("skyweather phase summer 42 0.7 2 3 0.4") == 1
                && weather.settings().profile.period(1).days == 42, "preview settings apply immediately");
        require(command("skyweather option passive true") == 1 && !weather.active()
                && command("skyweather weather blizzard") == 0, "passive mode turns off the weather hooks");
        require(command("skyweather option passive false") == 1 && weather.active(), "can resume active weather");
        require(command("skyweather option scheduled false") == 1 && !weather.settings().scheduled,
                "schedule toggle does not clear the pattern");
        require(command("skyweather reset") == 1 && weather.settings() == WeatherConfiguration.startupSettings()
                && !weather.edited() && WeatherConfiguration.activePreset().equals("burlington"),
                "restores launch settings without writing the file");
        require(command("skyweather season set winter 45") == 1, "set winter preview");
        command("gamerule doDaylightCycle false");
        require(command("skyweather weather blizzard 8 0.9 dry") == 1, "force a named event");
    }

    private void adminTick() {
        ticks++;
        if (ticks == 15) {
            WeatherEpisode episode = WeatherApi.getWeatherEpisode(world).get();
            require(episode.kind == WeatherKind.BLIZZARD && episode.forced && episode.includesDryBiomes
                    && episode.endTick - episode.startTick == 160, "forced episode uses game time");
            require(command("skyweather time midnight") == 1, "jump the daylight clock during the preview");
        } else if (ticks == 16) {
            require(WeatherApi.getWeatherEpisode(world).get().kind == WeatherKind.BLIZZARD,
                    "daylight jump preserves the preview");
            command("weather clear 3");
        } else if (ticks == 24) {
            require(ServerWeather.INSTANCE.previewSeconds() == 0 && !WeatherApi.getWeatherEpisode(world).isPresent(),
                    "vanilla weather command cancels the preview");
            require(command("skyweather weather heatwave 2 0.8") == 1, "new preview supersedes the manual lease");
        } else if (ticks == 37) {
            WeatherEpisode episode = WeatherApi.getWeatherEpisode(world).get();
            require(episode.kind == WeatherKind.HEATWAVE && episode.intensity(world.getTotalWorldTime()) > 0.7F,
                    "dry event reaches its requested intensity on the paused daylight clock");
        } else if (ticks == 68) {
            require(ServerWeather.INSTANCE.previewSeconds() == 0
                    && !WeatherApi.getWeatherEpisode(world).map(e -> e.forced).orElse(false),
                    "preview expires with daylight paused");
            succeed("ADMIN_COMMANDS");
        }
    }

    private void adminNetworkTick() {
        if (world.playerEntities.size() < 2 && adminFinish == 0) return;
        ticks++;
        if (ticks == 40) {
            EntityPlayerMP operator = world.getMinecraftServer().getPlayerList().getPlayerByUsername("WeatherTester1");
            EntityPlayerMP guest = world.getMinecraftServer().getPlayerList().getPlayerByUsername("WeatherTester2");
            require(WeatherCommand.permitted(operator) && !WeatherCommand.permitted(guest), "fixture has one operator");
            require(world.getMinecraftServer().getCommandManager().executeCommand(guest, "skyweather preset short_seasons") == 0
                    && !WeatherCommand.open(guest), "guest cannot apply commands or request the controller");
            BlockPos foot = new BlockPos(3, 70, 6);
            world.setBlockState(foot, Blocks.STONE.getDefaultState());
            Item item = Item.getItemFromBlock(WeatherController.BLOCK);
            guest.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(item));
            require(item.onItemUse(guest, world, foot, EnumHand.MAIN_HAND,
                    EnumFacing.UP, 0.5F, 1, 0.5F) == EnumActionResult.FAIL && world.isAirBlock(foot.up()),
                    "guest creative placement is denied on the server");
            operator.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(item));
            require(item.onItemUse(operator, world, foot, EnumHand.MAIN_HAND,
                    EnumFacing.UP, 0.5F, 1, 0.5F) == EnumActionResult.SUCCESS
                    && world.getBlockState(foot.up()).getBlock() == WeatherController.BLOCK,
                    "operator places the registered controller");
            net.minecraft.block.Block placed = world.getBlockState(foot.up()).getBlock();
            for (EntityPlayerMP player : new EntityPlayerMP[] {guest, operator}) {
                placed.onBlockActivated(world, foot.up(), world.getBlockState(foot.up()), player,
                        EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1, 0.5F);
            }
        }
        ServerWeather weather = ServerWeather.INSTANCE;
        WeatherEpisode episode = WeatherApi.getWeatherEpisode(world).orElse(null);
        sawAdminRain |= episode != null && episode.forced && episode.kind == WeatherKind.FRONTAL_PRECIPITATION;
        sawAdminStrength |= weather.settings().profile.strength == 1.35F;
        sawAdminSeason |= sawAdminStrength && phaseDay(Season.SPRING, 1);
        if (adminFinish == 0 && sawAdminRain && sawAdminStrength && sawAdminSeason && !weather.edited()) {
            adminFinish = ticks + 70;
        }
        if (adminFinish > 0 && ticks >= adminFinish) succeed("ADMIN_NETWORK");
        if (ticks > 1600) throw new IllegalStateException("Admin client did not finish the controller flow");
    }

    private boolean phaseDay(Season season, int day) {
        SeasonSnapshot calendar = WeatherApi.getSeason(world).get();
        return calendar.season == season && calendar.dayOfSeason == day;
    }

    private void episodeTick() {
        ticks++;
        if (ticks == 8) {
            require(!WeatherApi.getWeatherEpisode(world).isPresent(), "storm waits for its configured afternoon onset");
        } else if (ticks == 95) {
            WeatherEpisode episode = WeatherApi.getWeatherEpisode(world).get();
            require(episode.kind == WeatherKind.CONVECTIVE_SHOWER && episode.startTick == 134 * DAY + 6000,
                    "server starts the configured convective shower");
            require(world.isRaining() && !world.isThundering(), "shower drives vanilla rain without lightning");
            BlockPos wet = CHECK;
            BlockPos dry = CHECK;
            boolean foundWet = false;
            boolean foundDry = false;
            for (int x = -100; x <= 100; x += 8) {
                for (int z = -100; z <= 100; z += 8) {
                    BlockPos sample = new BlockPos(x, CHECK.getY(), z);
                    if (!world.isBlockLoaded(sample, false)) continue;
                    if (episode.covers(x, z, world.getWorldTime())) {
                        wet = sample;
                        foundWet = true;
                    } else {
                        dry = sample;
                        foundDry = true;
                    }
                }
            }
            require(foundWet && foundDry, "loaded area contains storm cells and dry gaps");
            require(world.isRainingAt(wet) && weather(wet).precipitation == LocalWeather.Precipitation.RAIN
                    && weather(wet).intensity > 0.55F, "rain gate and local intensity agree inside the storm");
            require(!world.isRainingAt(dry) && weather(dry).precipitation == LocalWeather.Precipitation.NONE,
                    "rain gate and local query agree outside the storm");
            byte[] biomes = world.getChunkFromBlockCoords(wet).getBiomeArray();
            int column = ((wet.getZ() & 15) << 4) | (wet.getX() & 15);
            byte previous = biomes[column];
            for (net.minecraft.world.biome.Biome biome : new net.minecraft.world.biome.Biome[] {Biomes.SAVANNA, Biomes.DESERT}) {
                biomes[column] = (byte) net.minecraft.world.biome.Biome.getIdForBiome(biome);
                require(!biome.canRain() && world.isRainingAt(wet)
                        && weather(wet).precipitation == LocalWeather.Precipitation.RAIN,
                        "opt-in shower produces rain in " + biome.getRegistryName());
            }
            biomes[column] = previous;
            command("weather clear 5");
        } else if (ticks == 185) {
            require(!world.isRaining() && !WeatherApi.getWeatherEpisode(world).isPresent(), "manual clear takes precedence");
        } else if (ticks == 205) {
            require(WeatherApi.getWeatherEpisode(world).get().kind == WeatherKind.CONVECTIVE_SHOWER,
                    "the same shower resumes after the command expires");
            command("gamerule doSeasonalWeather false");
        } else if (ticks == 211) {
            require(!WeatherApi.getWeatherEpisode(world).isPresent(), "doSeasonalWeather suspends the schedule");
            command("gamerule doSeasonalWeather true");
            time(134, 7500);
        } else if (ticks == 320) {
            require(!world.isRaining() && !WeatherApi.getWeatherEpisode(world).isPresent(),
                    "shower ends at the configured time");
            episodeLocalKinds();
            succeed("EPISODES");
        }
    }

    private void episodeLocalKinds() {
        rain();
        long now = world.getWorldTime();
        WeatherSettings summer = WeatherConfiguration.startupSettings();
        WeatherEpisode hail = null;
        for (int age = 300; age < 700; age++) {
            WeatherEpisode candidate = new WeatherEpisode(WeatherKind.HAILSTORM, now - age, 1500, 0.9F);
            if (candidate.covers(CHECK.getX(), CHECK.getZ(), now)) {
                hail = candidate;
                break;
            }
        }
        require(hail != null, "hail cell fixture");
        WeatherState.server(new WeatherState.Frame(world, summer, null, hail, true));
        require(weather(CHECK).precipitation == LocalWeather.Precipitation.HAIL && world.isRainingAt(CHECK),
                "warm hail uses the rain gate and exposes a distinct precipitation type");

        ClimateProfile.Period period = new ClimateProfile.Period(90, -0.65F, 1, 1, 0.6F);
        WeatherSettings freezing = new WeatherSettings(new ClimateProfile(1, period, period, period, period),
                false, true, false, true);
        WeatherEpisode glaze = new WeatherEpisode(WeatherKind.FREEZING_RAIN, now - 2000, 6000, 0.8F);
        WeatherState.server(new WeatherState.Frame(world, freezing, null, glaze, true));
        require(weather(CHECK).precipitation == LocalWeather.Precipitation.FREEZING_RAIN && world.isRainingAt(CHECK)
                && !WeatherHooks.snow(world, SNOW, true), "freezing rain stays liquid and skips snow placement");
        require(WeatherHooks.freeze(world, ICE), "near-surface freezing still uses the native freeze check");
        WeatherEpisode blizzard = new WeatherEpisode(WeatherKind.BLIZZARD, now - 3000, 18000, 0.9F);
        WeatherState.server(new WeatherState.Frame(world, freezing, null, blizzard, true));
        require(weather(CHECK).precipitation == LocalWeather.Precipitation.SNOW
                && weather(CHECK).fog > 0.65F && weather(CHECK).wind > 0.8F, "cold blizzard brings snowfall and whiteout");
        WeatherState.server(new WeatherState.Frame(world, summer, null, blizzard, true));
        require(weather(CHECK).precipitation == LocalWeather.Precipitation.NONE, "blizzard has a local snow line");
    }

    private void networkTick() {
        if (world.playerEntities.size() < 2) return;
        ticks++;
        if (ticks == 100) {
            time(229, 6000);
            world.getMinecraftServer().getCommandManager().executeCommand(world.getMinecraftServer(), "weather clear 600");
        } else if (ticks == 200) {
            time(MID_WINTER, 600);
            command("weather rain 2");
        } else if (ticks == 235) {
            require(world.isRaining(), "vanilla weather command kept its duration");
            require(!WeatherApi.getWeatherEpisode(world).isPresent(), "manual rain takes precedence");
        } else if (ticks == 400) {
            require(WeatherApi.getWeatherEpisode(world).get().kind == WeatherKind.WINTER_STORM, "server is running a winter storm");
            require(weather(CHECK).precipitation == LocalWeather.Precipitation.SNOW, "server network phase is snow");
            require(seasonChanges >= 2, "calendar change events");
            time(MID_WINTER + 1, 14500);
        } else if (ticks == 407) {
            beforeSleep = world.getWorldTime();
            for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities) {
                // Exercise vanilla's normal sleep path, using one bed per client.
                BlockPos bed = new BlockPos(player.getName().endsWith("2") ? 7 : 3, 71, 8);
                world.setBlockState(bed.down(), Blocks.STONE.getDefaultState());
                net.minecraft.block.state.IBlockState bedState = Blocks.BED.getDefaultState();
                world.setBlockState(bed, bedState, 2);
                BlockPos pillow = bed.offset(bedState.getValue(net.minecraft.block.BlockBed.FACING));
                world.setBlockState(pillow,
                        bedState.withProperty(net.minecraft.block.BlockBed.PART, net.minecraft.block.BlockBed.EnumPartType.HEAD), 2);
                player.setPositionAndUpdate(bed.getX() + 0.5, 72, 7.5);
                net.minecraft.entity.player.EntityPlayer.SleepResult result = player.trySleep(pillow);
                require(result == net.minecraft.entity.player.EntityPlayer.SleepResult.OK, "player entered bed: " + result);
            }
            sleepCheck = ticks + 130;
        } else if (ticks == sleepCheck) {
            require(world.getWorldTime() >= (MID_WINTER + 2) * DAY && world.getWorldTime() > beforeSleep + 1000,
                    "sleep advanced world clock");
            require(WeatherApi.getSeason(world).get().dayOfSeason == 48, "sleep advanced calendar day");
            require(!WeatherApi.getWeatherEpisode(world).isPresent(), "sleep ends the storm before the next daily onset");
            succeed("NETWORK_AND_SLEEP");
        }
    }

    private int command(String value) {
        MinecraftServer server = world.getMinecraftServer();
        return server.getCommandManager().executeCommand(server, value);
    }

    private void fixture() {
        world.getChunkFromChunkCoords(0, 0);
        world.getChunkFromChunkCoords(1, 0);
        world.setBlockState(SNOW.down(), Blocks.GRASS.getDefaultState());
        world.setBlockState(ICE, Blocks.WATER.getDefaultState());
        world.setBlockState(REPLACED.down(), Blocks.GRASS.getDefaultState());
        world.setBlockState(MANUAL.down(), Blocks.GRASS.getDefaultState());
        world.setBlockState(CHECK.add(2, 3, 0), Blocks.STONE.getDefaultState());
    }

    private void time(long day, int tickOfDay) {
        world.setWorldTime(day * DAY + tickOfDay);
    }

    private void rain() {
        world.getWorldInfo().setRaining(true);
        world.getWorldInfo().setRainTime(12000);
        for (int tick = 0; tick < 100; tick++) world.updateWeatherBody();
    }

    private LocalWeather weather(BlockPos pos) {
        return WeatherApi.getWeather(world, pos).get();
    }

    static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("SKY_WEATHER_CHECK failed: " + description);
    }

    private void succeed(String scenario) {
        System.out.println("SKY_WEATHER_CHECK " + scenario + " PASS");
        world.getMinecraftServer().initiateShutdown();
    }

    public static class SideProbe {
        public void register() {
        }
    }
}
