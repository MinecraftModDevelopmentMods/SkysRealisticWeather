package zone.moddev.mc.skysrealisticweather.game;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthSample;
import zone.moddev.mc.skysrealisticweather.climate.*;

/** Native block updates with independently seeded geometric random-tick selection. Light and soil stay controlled. */
final class PlantEnduranceProbe {
    private static final int DAY = 24000, BASELINE_ATTEMPTS = 12000;
    private static final double TICK_PROBABILITY = 3.0 / 4096;
    private static final String[] PAM = {"pamtomatocrop", "pampineapplecrop", "pamcactusfruitcrop", "pamapple"};
    private static final class Plot {
        final Block block;
        final BlockPos pos;
        final IProperty age;
        final int maxAge;
        final Random random;
        long next, attempts, successes;
        double expected, multiplierSum, tempSum;
        double nativeChance;
        Plot(Block block, BlockPos pos, long seed) {
            this.block = block; this.pos = pos; random = new Random(seed);
            age = block.getBlockState().getProperty("age");
            if (age == null) throw new IllegalArgumentException("No age property " + block.getRegistryName());
            maxAge = (Integer) Collections.max(age.getAllowedValues());
        }
    }

    static void run(WorldServer world) throws Exception {
        int years = Integer.getInteger("plants.years", 5);
        long[] seeds = Arrays.stream(System.getProperty("plants.seeds", "123948,70289,91173").split(",")).mapToLong(Long::parseLong).toArray();
        String selected = System.getProperty("plants.climates", "all");
        List<Plot> plots = plots(world, seeds[0]);
        FarmingSettings farming = WeatherConfiguration.startupSettings().farming;
        try (PrintWriter csv = writer("endurance.csv")) {
            csv.println("climate,seed,days,plant,y,attempts,stages,expected_stages,mean_multiplier,mean_temp,tolerance");
            for (Preset preset : WeatherConfiguration.presets()) {
                if (!selected.equals("all") && !Arrays.asList(selected.split(",")).contains(preset.id)) continue;
                for (long seed : seeds) {
                    world.rand.setSeed(seed);
                    for (int index = 0; index < plots.size(); index++) {
                        Plot p = plots.get(index);
                        prepare(world, p);
                        constantFrame(world, 0.8F, farming.withEnabled(false));
                        long grown = 0;
                        Random nativeRandom = new Random(seed + index);
                        for (int n = 0; n < BASELINE_ATTEMPTS; n++) if (tick(world, p, nativeRandom)) grown++;
                        p.nativeChance = grown / (double) BASELINE_ATTEMPTS;
                        p.random.setSeed(seed ^ index * 0x9E3779B97F4A7C15L);
                        p.attempts = p.successes = 0; p.expected = p.multiplierSum = p.tempSum = 0;
                        p.next = waitTicks(p.random);
                    }
                    WeatherSettings settings = new WeatherSettings(preset.profile, false, true, true, false, preset.weather, true).withFarming(farming);
                    WeatherSchedule schedule = new WeatherSchedule(seed, preset.profile, preset.weather);
                    long warmup = preset.profile.daysInYear() * (long) DAY;
                    long duration = years * 365L * DAY;
                    for (;;) {
                        Plot next = Collections.min(plots, Comparator.comparingLong(p -> p.next));
                        if (next.next >= duration) break;
                        long time = warmup + next.next;
                        world.setWorldTime(time);
                        WeatherState.server(new WeatherState.Frame(world, settings, null, schedule.at(time), true, null, seed, schedule));
                        PlantGrowthSample suitability = PlantGrowthHooks.sample(world, next.pos);
                        next.attempts++; next.multiplierSum += suitability.naturalGrowthMultiplier; next.tempSum += suitability.temperature;
                        next.expected += suitability.naturalGrowthMultiplier * next.nativeChance;
                        if (tick(world, next, next.random)) next.successes++;
                        next.next += waitTicks(next.random);
                    }
                    for (Plot p : plots) {
                        double tolerance = 6 * Math.sqrt(p.expected + p.attempts * (double) p.attempts * p.nativeChance * (1 - p.nativeChance) / BASELINE_ATTEMPTS) + 20;
                        if (Math.abs(p.successes - p.expected) > tolerance) throw new IllegalStateException("Native growth rate mismatch " + preset.id + " " + p.block.getRegistryName() + ": " + p.successes + " vs " + p.expected);
                        csv.printf(Locale.ROOT, "%s,%d,%d,%s,%d,%d,%d,%.3f,%.5f,%.5f,%.3f%n", preset.id, seed, years * 365,
                                p.block.getRegistryName(), p.pos.getY(), p.attempts, p.successes, p.expected, p.multiplierSum / p.attempts, p.tempSum / p.attempts, tolerance);
                    }
                    csv.flush();
                }
                System.out.println("PLANT_ENDURANCE " + preset.id + " seeds=" + seeds.length + " years=" + years + " plots=" + plots.size());
            }
        }
    }

    private static List<Plot> plots(WorldServer world, long seed) {
        List<Block> blocks = new ArrayList<>(Arrays.asList(Blocks.WHEAT, Blocks.CARROTS));
        for (String name : PAM) {
            Block b = Block.REGISTRY.getObject(new ResourceLocation("harvestcraft", name));
            if (b != null && b != Blocks.AIR) blocks.add(b);
        }
        blocks.add(Blocks.WHEAT); blocks.add(Blocks.CARROTS);
        List<Plot> result = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) result.add(new Plot(blocks.get(index),
                new BlockPos(8 + index * 32, index >= blocks.size() - 2 ? 200 : 81, 8), seed + index));
        return result;
    }

    private static long waitTicks(Random random) { return 1 + (long) (Math.log1p(-random.nextDouble()) / Math.log1p(-TICK_PROBABILITY)); }

    private static void prepare(WorldServer world, Plot p) {
        world.getChunkFromBlockCoords(p.pos);
        Arrays.fill(world.getChunkFromBlockCoords(p.pos).getBiomeArray(), (byte) net.minecraft.world.biome.Biome.getIdForBiome(Biomes.PLAINS));
        world.setBlockState(p.pos.down(), Blocks.FARMLAND.getDefaultState().withProperty(BlockFarmland.MOISTURE, 7), 2);
        world.setBlockState(p.pos, p.block.getDefaultState(), 2);
        world.setBlockState(p.pos.up(), Blocks.LEAVES.getDefaultState(), 2);
        world.setBlockState(p.pos.east(2), Blocks.GLOWSTONE.getDefaultState(), 2);
        world.getChunkFromBlockCoords(p.pos).generateSkylightMap();
    }
    private static boolean tick(WorldServer world, Plot p, Random random) {
        IBlockState before = world.getBlockState(p.pos);
        p.block.updateTick(world, p.pos, before, random);
        IBlockState after = world.getBlockState(p.pos);
        if (after.getBlock() != p.block) throw new IllegalStateException("Plant lost during endurance: " + p.block.getRegistryName());
        boolean changed = before != after;
        if (((Number) after.getValue(p.age)).intValue() >= p.maxAge) world.setBlockState(p.pos, p.block.getDefaultState(), 2);
        return changed;
    }

    private static void constantFrame(WorldServer world, float temperature, FarmingSettings farming) {
        world.setWorldTime(6000);
        BlockPos ref = new BlockPos(8, 81, 8);
        float offset = temperature - Biomes.PLAINS.getFloatTemperature(ref) - ClimateProfile.daylightTemperature(6000) * ClimateProfile.temperatureRange(1);
        ClimateProfile.Period p = new ClimateProfile.Period(90, offset, 1, 1, 0);
        WeatherState.server(new WeatherState.Frame(world, new WeatherSettings(new ClimateProfile(1, p, p, p, p), false, true, true, false).withFarming(farming), null));
    }

    static void performance(WorldServer world) throws Exception {
        FarmingSettings farming = WeatherConfiguration.startupSettings().farming;
        try (PrintWriter csv = writer("performance.csv")) {
            csv.println("plants,trial,enabled,nanoseconds,updates,estimated_crop_ms_per_game_tick");
            for (int count : new int[] {1000, 10000}) {
                List<Plot> plots = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Plot p = new Plot(Blocks.WHEAT, new BlockPos(8 + (i % 100) * 3, 81, 40 + (i / 100) * 3), i);
                    prepare(world, p); plots.add(p);
                }
                long[] enabled = new long[5], disabled = new long[5];
                long nativeTrace = 0;
                for (int trial = -1; trial < 5; trial++) for (boolean on : new boolean[] {false, true}) {
                    for (Plot p : plots) world.setBlockState(p.pos, p.block.getDefaultState(), 2);
                    constantFrame(world, 0.8F, farming.withEnabled(on));
                    world.rand.setSeed(1123); Random selection = new Random(714);
                    // Remove this listener for a native Forge growth baseline, including event dispatch cost.
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(PlantGrowthHooks.INSTANCE);
                    if (on) net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(PlantGrowthHooks.INSTANCE);
                    long start = System.nanoTime();
                    for (int n = 0; n < 150000; n++) tick(world, plots.get(selection.nextInt(count)), world.rand);
                    long elapsed = System.nanoTime() - start;
                    long trace = 1;
                    for (Plot p : plots) trace = trace * 31 + ((Number) world.getBlockState(p.pos).getValue(p.age)).intValue();
                    if (!on) nativeTrace = trace;
                    else if (nativeTrace != trace) throw new IllegalStateException("Suitable crop growth changed native RNG trace");
                    if (trial >= 0) {
                        (on ? enabled : disabled)[trial] = elapsed;
                        csv.printf(Locale.ROOT, "%d,%d,%s,%d,150000,%.6f%n", count, trial, on, elapsed, elapsed / 150000.0 * count * TICK_PROBABILITY / 1e6);
                    }
                }
                Arrays.sort(enabled); Arrays.sort(disabled);
                double extra = (enabled[2] - disabled[2]) / 150000.0 * count * TICK_PROBABILITY / 1e6;
                if (extra > 1) throw new IllegalStateException("Crop overhead exceeds budget: " + extra + " ms/tick");
                System.out.printf(Locale.ROOT, "PLANT_PERFORMANCE count=%d extraCropMsPerTick=%.6f PASS%n", count, extra);
            }
        }
    }
    private static PrintWriter writer(String name) throws Exception {
        new File("plants").mkdirs();
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File("plants", name)), StandardCharsets.UTF_8));
    }
}
