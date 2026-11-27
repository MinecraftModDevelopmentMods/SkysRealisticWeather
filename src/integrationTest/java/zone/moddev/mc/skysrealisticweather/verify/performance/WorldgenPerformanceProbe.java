package zone.moddev.mc.skysrealisticweather.verify.performance;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import javax.management.Attribute;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = "worldgenperf", name = "Worldgen Performance Probe", version = "1")
public final class WorldgenPerformanceProbe {
    private static final long DAY = 24000L;
    private static final int SIZE = Integer.getInteger("perf.size", 28);
    private static final int WARM_SIZE = Integer.getInteger("perf.warmup", 24);
    private static final String PROFILE = System.getProperty("perf.profile");
    private static final String ROUND = System.getProperty("perf.round");
    private static final String TERRAIN = System.getProperty("perf.terrain");
    private static final boolean ALLOCATION = Boolean.getBoolean("perf.allocation");
    private static final boolean INTERLEAVE = Boolean.getBoolean("perf.interleave");
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private MBeanServer allocationServer;
    private ObjectName allocationBean;
    private final Map<Long, Long> frostStart = new HashMap<>();
    private List<Long> frostTimes;
    private int populationCount;
    private final HashSet<Long> decorated = new HashSet<>();
    private static final int SLICE_ROWS = Integer.getInteger("perf.rows", 2);
    private WorldServer world;
    private int ticks;
    private boolean captureBase;
    private final Map<Long,int[]> baseHeights=new HashMap<>();
    private final Map<Long,byte[]> baseBiomes=new HashMap<>();

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void loaded(net.minecraftforge.event.world.ChunkEvent.Load event) {
        if(!captureBase||event.getWorld()!=world)return;
        Chunk chunk=event.getChunk();long key=ChunkPos.asLong(chunk.x,chunk.z);
        baseHeights.put(key,chunk.getHeightMap().clone());baseBiomes.put(key,chunk.getBiomeArray().clone());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (PROFILE == null) return;
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.TERRAIN_GEN_BUS.register(this);
    }

    @Mod.EventHandler
    public void start(FMLServerStartedEvent event) {
        if (PROFILE == null) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        world = server.getWorld(0);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.setWorldTime(320 * DAY + 6000);
        boolean expected = !"vanilla".equals(PROFILE);
        if(expected&&Boolean.getBoolean("perf.vetoDeposits"))DepositVeto.attach(world);
        require(Loader.isModLoaded("skysrealisticweather") == expected, "wrong mod profile");
        require(world.getWorldType().getName().equalsIgnoreCase(System.getProperty("perf.worldType")), "wrong world type");
        if (!System.getProperty("perf.expectedMod", "").isEmpty()) require(Loader.isModLoaded(System.getProperty("perf.expectedMod")), "generator mod missing");
        if ("bundle".equals(TERRAIN)) {
            require(Loader.isModLoaded("biomebundle") && world.getWorldInfo().getWorldName().equals("Biome Bundle"), "wrong OTG preset");
            OtgPopulationCounter.attach(this);
        }
        require(server.getCommandManager().executeCommand(server, "weather rain 6000") == 1, "weather set");
        require(threads.isCurrentThreadCpuTimeSupported(), "server thread CPU timer available");
        threads.setThreadCpuTimeEnabled(true);
        if (ALLOCATION) {
            enableAllocationCounter();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void frostStart(PopulateChunkEvent.Populate event) {
        if (frostTimes != null && event.getWorld() == world
                && event.getType() == PopulateChunkEvent.Populate.EventType.ICE) {
            frostStart.put(ChunkPos.asLong(event.getChunkX(), event.getChunkZ()), System.nanoTime());
            if (!"bundle".equals(TERRAIN)) decorated.add(ChunkPos.asLong(event.getChunkX(), event.getChunkZ()));
        }
    }

    void otgDecorated(WorldServer eventWorld, int x, int z) {
        if (eventWorld == world && frostTimes != null) decorated.add(ChunkPos.asLong(x,z));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void populationEnd(PopulateChunkEvent.Post event) {
        if (frostTimes == null || event.getWorld() != world) return;
        long end = System.nanoTime();
        populationCount++;
        Long start = frostStart.remove(ChunkPos.asLong(event.getChunkX(), event.getChunkZ()));
        if (start != null) frostTimes.add(end - start);
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (world == null || event.world != world || event.phase != TickEvent.Phase.END || ++ticks != 32) return;
        require(world.isRaining(), "forced rain reached steady state");
        if (!"vanilla".equals(PROFILE)) verifySetting(world);
        world.disableLevelSaving = true;
        ChunkProviderServer provider = world.getChunkProvider();
        // Warm the same code using a separate region before either timed batch.
        world.rand.setSeed(123948L ^ 0x5741524DL);
        generate(provider, 740, -540, WARM_SIZE, null);
        if (Boolean.getBoolean("perf.reverse")) {
            benchmark("summer", -360, 550, 134);
            benchmark("winter", 320, 320, 320);
        } else {
            benchmark("winter", 320, 320, 320);
            benchmark("summer", -360, 550, 134);
        }
        verifyPropertyHash();
        fingerprint("winter", 320, 320);
        fingerprint("summer", -360, 550);
        System.out.println("WORLDGEN_PERF PASS");
        world.getMinecraftServer().initiateShutdown();
    }

    private void benchmark(String phase, int x, int z, long day) {
        world.setWorldTime(day * DAY + 6000);
        // A frame captures its date: advance the production controller before each timed phase.
        if (!"vanilla".equals(PROFILE)) {
            require(world.getMinecraftServer().getCommandManager().executeCommand(world.getMinecraftServer(),
                    "skyweather weather frontal_precipitation 1800") == 1, "controlled precipitation");
        }
        world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime() + 80);
        if (!"vanilla".equals(PROFILE)) {
            try {
                Class<?> type = Class.forName("zone.moddev.mc.skysrealisticweather.game.ServerWeather");
                type.getMethod("startTick", TickEvent.WorldTickEvent.class).invoke(type.getField("INSTANCE").get(null),
                        new TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER, TickEvent.Phase.START, world));
                Class<?> state = Class.forName("zone.moddev.mc.skysrealisticweather.game.WeatherState");
                Object frame = state.getMethod("frame", net.minecraft.world.World.class).invoke(null, world);
                java.lang.reflect.Field time = frame.getClass().getDeclaredField("time"); time.setAccessible(true);
                require(time.getLong(frame) == world.getWorldTime(), "fresh phase snapshot");
            } catch (ReflectiveOperationException invalid) { throw new IllegalStateException(invalid); }
        }
        world.getWorldInfo().setRaining(true); world.getWorldInfo().setRainTime(120000);
        world.getWorldInfo().setThundering(false); world.getWorldInfo().setThunderTime(120000);
        for (int i=0;i<100;i++) world.updateWeatherBody();
        require(world.isRaining(), "both batches use the same rain state");
        // Some biome decorators use World.rand rather than the generator's seeded chunk RNG.
        world.rand.setSeed(123948L ^ day * 0x9E3779B97F4A7C15L);
        ChunkProviderServer provider = world.getChunkProvider();
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                require(provider.getLoadedChunk(x + dx, z + dz) == null, "target is fresh");
            }
        }
        long[] timings = new long[SIZE * SIZE];
        long[] groups = new long[(timings.length + 15) / 16];
        populationCount = 0;
        decorated.clear();
        frostStart.clear();
        frostTimes = new ArrayList<>(timings.length);
        int before = provider.getLoadedChunkCount();
        long gcCount = gc(false);
        long gcTime = gc(true);
        long allocatedBytes = allocated();
        long cpu = threads.getCurrentThreadCpuTime();
        baseHeights.clear();baseBiomes.clear();captureBase=true;
        long start = System.nanoTime();
        long elapsed;
        if (INTERLEAVE) {
            elapsed = 0;
            cpu = 0;
            for (int row = 0; row < SIZE; row += SLICE_ROWS) {
                String slice = phase + "-" + row;
                awaitTurn(slice);
                long sliceCpu = threads.getCurrentThreadCpuTime();
                long sliceStart = System.nanoTime();
                for (int dz = row; dz < Math.min(row + SLICE_ROWS, SIZE); dz++) {
                    for (int dx = 0; dx < SIZE; dx++) {
                        long request = System.nanoTime();
                        provider.provideChunk(x + dx, z + dz);
                        timings[dz * SIZE + dx] = System.nanoTime() - request;
                    }
                }
                long wall = System.nanoTime() - sliceStart;
                long thread = threads.getCurrentThreadCpuTime() - sliceCpu;
                elapsed += wall;
                cpu += thread;
                System.out.printf(Locale.ROOT, "SLICE %s,%s,%d,%.4f,%.4f%n", PROFILE, phase, row, ms(wall), ms(thread));
                signal(slice + ".done");
            }
            awaitTurn(phase + "-cover");
        } else {
            generate(provider, x, z, SIZE, timings);
            elapsed = System.nanoTime() - start;
            cpu = threads.getCurrentThreadCpuTime() - cpu;
        }
        allocatedBytes = allocated() - allocatedBytes;
        captureBase=false;
        gcCount = gc(false) - gcCount;
        gcTime = gc(true) - gcTime;
        List<Long> frost = frostTimes;
        frostTimes = null;
        for (int dz = 2; dz < SIZE-2; dz++) for (int dx=2; dx<SIZE-2; dx++) {
            require(decorated.contains(ChunkPos.asLong(x+dx,z+dz)), "missing interior decoration " + (x+dx) + "," + (z+dz) + " total " + decorated.size());
        }
        System.out.println("POPULATION_EVIDENCE " + phase + " " + decorated.size() + " " + world.getBiomeProvider().getClass().getName());
        for (int index = 0; index < timings.length; index++) groups[index / 16] += timings[index];
        long[] ice = new long[frost.size()];
        for (int index = 0; index < ice.length; index++) ice[index] = frost.get(index);
        Arrays.sort(timings);
        Arrays.sort(groups);
        Arrays.sort(ice);
        long[] cover = cover(x, z);
        if(!"vanilla".equals(PROFILE)&&Boolean.getBoolean("perf.vetoDeposits"))System.out.println("VETOED_SURFACE_CHANGES "+DepositVeto.count());
        MessageDigest baseDigest=sha256();byte[] baseBytes=new byte[2048];int low=256,high=0;
        for(int dz=0;dz<SIZE;dz++)for(int dx=0;dx<SIZE;dx++) {
            long key=ChunkPos.asLong(x+dx,z+dz);int[] heights=baseHeights.get(key);byte[] biomes=baseBiomes.get(key);
            require(heights!=null&&biomes!=null,"captured pre-population terrain");
            for(int i=0;i<256;i++){putInt(baseBytes,i*8,heights[i]);putInt(baseBytes,i*8+4,biomes[i]&255);low=Math.min(low,heights[i]);high=Math.max(high,heights[i]);}
            baseDigest.update(baseBytes);
        }
        require(high>0,"base height maps are initialized");
        System.out.println("BASE_TERRAIN_PARITY "+PROFILE+" "+phase+" "+hex(baseDigest.digest())+" heights="+low+".."+high);
        System.out.printf(Locale.ROOT, "WORLDGEN_PERF_RESULT %s,%s,%s,%s,%d,%d,%d,%.3f,%.3f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%016x%n",
                ROUND, TERRAIN, PROFILE, phase, timings.length, provider.getLoadedChunkCount() - before, decorated.size(),
                ms(elapsed), ms(cpu), gcCount, gcTime, p(timings, .5), p(timings, .95), p(timings, .99),
                p(groups, .95), p(ice, .5), p(ice, .95), cover[0], cover[1], cover[2]);
        if (ALLOCATION) System.out.printf(Locale.ROOT, "WORLDGEN_PERF_ALLOCATION %s,%s,%d,%d%n",
                PROFILE, phase, timings.length, allocatedBytes);
    }

    private long allocated() {
        if (!ALLOCATION) return 0;
        try {
            return (Long) allocationServer.invoke(allocationBean, "getThreadAllocatedBytes",
                    new Object[] {Thread.currentThread().getId()}, new String[] {"long"});
        } catch (JMException failure) {
            throw new IllegalStateException("Cannot read the server thread allocation counter", failure);
        }
    }

    private void enableAllocationCounter() {
        try {
            allocationServer = ManagementFactory.getPlatformMBeanServer();
            allocationBean = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
            require(Boolean.TRUE.equals(allocationServer.getAttribute(allocationBean, "ThreadAllocatedMemorySupported")),
                    "server thread allocation counter available");
            allocationServer.setAttribute(allocationBean, new Attribute("ThreadAllocatedMemoryEnabled", true));
        } catch (JMException failure) {
            throw new IllegalStateException("Cannot enable the server thread allocation counter", failure);
        }
    }

    private static void generate(ChunkProviderServer provider, int x, int z, int size, long[] timings) {
        int index = 0;
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                if (timings == null) {
                    provider.provideChunk(x + dx, z + dz);
                } else {
                    long start = System.nanoTime();
                    provider.provideChunk(x + dx, z + dz);
                    timings[index++] = System.nanoTime() - start;
                }
            }
        }
    }

    private static void awaitTurn(String slice) {
        signal(slice + ".ready");
        Path gate = Paths.get(slice + ".go");
        long deadline = System.nanoTime() + 600_000_000_000L;
        try {
            while (!Files.exists(gate)) {
                if (System.nanoTime() > deadline) throw new IllegalStateException("Timed out waiting for " + gate);
                Thread.sleep(5);
            }
        } catch (InterruptedException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void signal(String marker) {
        try {
            Files.createFile(Paths.get(marker));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void verifyPropertyHash() {
        PropertyEnum<BlockStone.EnumType> first = BlockStone.VARIANT;
        PropertyEnum<BlockStone.EnumType> equal = PropertyEnum.create(first.getName(), BlockStone.EnumType.class, first.getAllowedValues());
        Map<String, BlockStone.EnumType> names = new HashMap<>();
        for (BlockStone.EnumType kind : first.getAllowedValues()) names.put(kind.getName(), kind);
        int base = 31 * BlockStone.EnumType.class.hashCode() + first.getName().hashCode();
        int expected = 31 * (31 * base + first.getAllowedValues().hashCode()) + names.hashCode();
        require(first.equals(equal) && first.hashCode() == expected && equal.hashCode() == expected, "original enum hash and equality");
        boolean installed = Arrays.stream(PropertyEnum.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("skysrealisticweather$nameTableHash"));
        require(installed == !"vanilla".equals(PROFILE), "expected hash optimization in packaged runtime");
        if (Boolean.getBoolean("perf.fourBit")) verifySectionPalette();
    }

    private static void verifySectionPalette() {
        net.minecraft.world.chunk.BlockStateContainer palette = new net.minecraft.world.chunk.BlockStateContainer();
        try {
            boolean specialized = false;
            for (java.lang.reflect.Field field : palette.getClass().getDeclaredFields()) {
                if (field.getType() != net.minecraft.util.BitArray.class) continue;
                field.setAccessible(true);
                specialized = field.get(palette).getClass().getName().endsWith(".FourBitSectionStorage");
            }
            require(specialized == !"vanilla".equals(PROFILE), "expected four-bit storage in packaged runtime");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        List<net.minecraft.block.state.IBlockState> states = new ArrayList<>();
        for (Block block : Block.REGISTRY) {
            for (net.minecraft.block.state.IBlockState state : block.getBlockState().getValidStates()) {
                int id = Block.BLOCK_STATE_IDS.get(state);
                if (id >= 0 && Block.BLOCK_STATE_IDS.getByValue(id) == state) states.add(state);
            }
            if (states.size() >= 300) break;
        }
        for (int i = 0; i < states.size(); i++) palette.set(i & 15, i >> 8, (i >> 4) & 15, states.get(i));
        for (int i = 0; i < states.size(); i++) require(palette.get(i & 15, i >> 8, (i >> 4) & 15) == states.get(i), "palette resize at " + i);
    }

    private void fingerprint(String phase, int startX, int startZ) {
        // Registry numbers can move when another mod registers blocks. Compare named states.
        java.util.SortedMap<String, net.minecraft.block.state.IBlockState> named = new java.util.TreeMap<>();
        for (Block block : Block.REGISTRY) {
            if (block.getRegistryName().getResourceDomain().equals("skysrealisticweather")) continue;
            for (net.minecraft.block.state.IBlockState state : block.getBlockState().getValidStates()) named.put(state.toString(),state);
        }
        java.util.IdentityHashMap<net.minecraft.block.state.IBlockState,Integer> ids = new java.util.IdentityHashMap<>();
        List<String> table = new ArrayList<>(); int next = 0;
        for (Map.Entry<String,net.minecraft.block.state.IBlockState> row : named.entrySet()) {
            ids.put(row.getValue(),next);table.add(next++ + "\t" + Block.getStateId(row.getValue()) + "\t" + row.getKey());
        }
        java.util.SortedMap<String,Biome> biomes = new java.util.TreeMap<>();
        for (Biome biome : Biome.REGISTRY) biomes.put(biome.getRegistryName().toString(),biome);
        int[] biomeIds = new int[256]; List<String> biomeTable = new ArrayList<>();next=0;
        for (Map.Entry<String,Biome> row : biomes.entrySet()) {
            int raw=Biome.getIdForBiome(row.getValue());biomeIds[raw]=next;biomeTable.add(next++ + "\t" + raw + "\t" + row.getKey());
        }
        MessageDigest digest = sha256(); byte[] states = new byte[16384], biomeBytes = new byte[1024];
        List<String> chunks = new ArrayList<>(); long[] counts = new long[ids.size()];
        for (int z=0;z<SIZE;z++) for (int x=0;x<SIZE;x++) {
            Chunk chunk=world.getChunkProvider().getLoadedChunk(startX+x,startZ+z);
            require(chunk!=null,"chunk present during parity scan"); MessageDigest local=sha256();
            byte[] rawBiomes=chunk.getBiomeArray();
            for(int i=0;i<256;i++)putInt(biomeBytes,i*4,biomeIds[rawBiomes[i]&255]);
            digest.update(biomeBytes);local.update(biomeBytes);
            for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
                for (int i=0;i<4096;i++) {
                    net.minecraft.block.state.IBlockState state=section==null?Blocks.AIR.getDefaultState():section.get(i&15,i>>8,(i>>4)&15);
                    // Seasonal cover changes are intentional. Both source-water forms share a name here.
                    if(state.getBlock()==Blocks.SNOW_LAYER)state=Blocks.AIR.getDefaultState();
                    else if(state.getBlock()==Blocks.ICE || state.getBlock()==Blocks.FLOWING_WATER && state.getValue(net.minecraft.block.BlockLiquid.LEVEL)==0)state=Blocks.WATER.getDefaultState();
                    Integer id=ids.get(state);if(id==null)throw new IllegalStateException("unexpected generated state "+state);counts[id]++;putInt(states,i*4,id);
                }
                digest.update(states);local.update(states);
            }
            chunks.add((startX+x)+"\t"+(startZ+z)+"\t"+hex(local.digest()));
        }
        List<String> histogram = new ArrayList<>();next=0;
        for(String key:named.keySet()) {if(counts[next]!=0)histogram.add(counts[next]+"\t"+key);next++;}
        try {
            Files.write(Paths.get("canonical-states.tsv"),table,java.nio.charset.StandardCharsets.UTF_8);
            Files.write(Paths.get("canonical-biomes.tsv"),biomeTable,java.nio.charset.StandardCharsets.UTF_8);
            Files.write(Paths.get(phase+"-chunks.tsv"),chunks,java.nio.charset.StandardCharsets.UTF_8);
            Files.write(Paths.get(phase+"-states.tsv"),histogram,java.nio.charset.StandardCharsets.UTF_8);
        } catch(java.io.IOException invalid) {throw new IllegalStateException(invalid);}
        System.out.println("TERRAIN_PARITY " + PROFILE + " " + phase + " " + hex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try {return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException invalid){throw new AssertionError(invalid);}
    }
    private static void putInt(byte[] target,int offset,int value) {
        target[offset]=(byte)value;target[offset+1]=(byte)(value>>8);target[offset+2]=(byte)(value>>16);target[offset+3]=(byte)(value>>24);
    }
    private static String hex(byte[] value) {
        char[] chars=new char[value.length*2];String digits="0123456789abcdef";
        for(int i=0;i<value.length;i++){chars[i*2]=digits.charAt((value[i]>>4)&15);chars[i*2+1]=digits.charAt(value[i]&15);}return new String(chars);
    }

    private long[] cover(int x, int z) {
        int snow = 0;
        int ice = 0;
        long fingerprint = 0xcbf29ce484222325L;
        BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
        // Count the exactly populated 16-column offsets, outside the measured interval.
        for (int dz = 2 * 16 + 8; dz < (SIZE - 2) * 16 + 8; dz++) {
            for (int dx = 2 * 16 + 8; dx < (SIZE - 2) * 16 + 8; dx++) {
                BlockPos height = world.getPrecipitationHeight(column.setPos(x * 16 + dx, 0, z * 16 + dz));
                BlockPos below = height.down();
                int snowY = world.getBlockState(height).getBlock() == Blocks.SNOW_LAYER ? height.getY()
                        : world.getBlockState(below).getBlock() == Blocks.SNOW_LAYER ? below.getY() : -1;
                int iceY = world.getBlockState(below).getBlock() == Blocks.ICE ? below.getY()
                        : world.getBlockState(below.down()).getBlock() == Blocks.ICE ? below.getY() - 1 : -1;
                if (snowY >= 0) snow++;
                if (iceY >= 0) ice++;
                fingerprint = (fingerprint ^ (snowY + 1L) ^ (iceY + 1L) << 9) * 0x100000001b3L;
            }
        }
        return new long[]{snow, ice, fingerprint};
    }

    private static long gc(boolean time) {
        long sum = 0;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            sum += Math.max(0, time ? collector.getCollectionTime() : collector.getCollectionCount());
        }
        return sum;
    }

    private static double p(long[] sorted, double quantile) {
        return sorted.length == 0 ? Double.NaN : ms(sorted[(int) Math.ceil(sorted.length * quantile) - 1]);
    }

    private static double ms(long ns) {
        return ns / 1_000_000.0;
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("WORLDGEN_PERF FAILED: " + description);
    }

    private static void verifySetting(WorldServer world) {
        try {
            Class<?> state = Class.forName("zone.moddev.mc.skysrealisticweather.game.WeatherState");
            Object frame = state.getMethod("frame", net.minecraft.world.World.class).invoke(null, world);
            Object settings = frame.getClass().getField("settings").get(frame);
            require((Boolean) settings.getClass().getMethod("seasonalWorldgen").invoke(settings) == "on".equals(PROFILE), "generation switch");
            require((Boolean) frame.getClass().getMethod("active").invoke(frame), "active weather");
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
