package zone.moddev.mc.skysrealisticweather.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.*;
import zone.moddev.mc.skysrealisticweather.game.*;
import zone.moddev.mc.skysrealisticweather.live.Observation;
import zone.moddev.mc.skysrealisticweather.network.WeatherMessage;

/** Tests the renderer's column policy on real world/biome data, without a graphics window. */
final class PrecipitationProbe {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new IllegalStateException(message);
    }
    static void run(WorldServer world) {
        try {
            WeatherSettings settings = WeatherConfiguration.startupSettings();
            BlockPos snow = new BlockPos(7, 81, 8), clear = new BlockPos(8, 81, 8);
            byte[] biomes = world.getChunkFromBlockCoords(snow).getBiomeArray();
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                biomes[z * 16 + x] = (byte) Biome.getIdForBiome(x < 8 ? Biomes.ICE_PLAINS : Biomes.JUNGLE);
            }
            require(world.getBiome(snow) == Biomes.ICE_PLAINS && world.getBiome(clear) == Biomes.JUNGLE, "adjacent real biome columns");
            checkBoundary(world, settings, snow, Biomes.ICE_PLAINS, clear, Biomes.JUNGLE, LocalWeather.Precipitation.SNOW);
            checkBoundary(world, settings, clear, Biomes.JUNGLE, snow, Biomes.DESERT, LocalWeather.Precipitation.RAIN);

            long time = 320L * 24000 + 6000; world.setWorldTime(time);
            WeatherSettings universal = settings.withMode(ClimateMode.UNIVERSAL, settings.biomes);
            WeatherEpisode shower = new WeatherEpisode(WeatherKind.CONVECTIVE_SHOWER, time - 1000, 10000, .8F);
            WeatherState.Frame cells = new WeatherState.Frame(world, universal, null, shower, true);
            boolean wet = false, dry = false;
            for (int x = -256; x <= 256; x += 8) for (int z = -256; z <= 256; z += 8) {
                BlockPos point = new BlockPos(x, 81, z);
                float alpha = cells.columnRainStrength(.5F, point, Biomes.JUNGLE);
                if (shower.covers(x, z, time)) { require(Math.abs(alpha - .4F) < .00001, "universal intensity applied once"); wet = true; }
                else { require(alpha == 0, "dry storm-cell gap has zero opacity"); dry = true; }
            }
            require(wet && dry, "moving shower contains wet and dry columns");
            WeatherEpisode blizzard = new WeatherEpisode(WeatherKind.BLIZZARD, world.getTotalWorldTime() - 100, 10000, .9F, false, true);
            WeatherState.Frame preview = new WeatherState.Frame(world, settings, null, blizzard, true);
            require(preview.columnRainStrength(1, snow, Biomes.ICE_PLAINS) > .8F, "admin blizzard remains snowy");
            require(preview.columnRainStrength(1, clear, Biomes.JUNGLE) == 0, "warm biome respects blizzard snow line");
            WeatherState.Frame live = new WeatherState.Frame(world, universal, new Observation(10000, 5, Observation.Condition.RAIN));
            require(Math.abs(live.columnRainStrength(.7F, clear, Biomes.JUNGLE) - .7F) < .00001, "live weather retains native carrier strength");
            WeatherState.Frame passive = new WeatherState.Frame(world, settings.withOptions(true, true, true, true, true), null);
            require(passive.columnRainStrength(.3F, clear, Biomes.DESERT) == .3F, "passive opacity is unchanged");
            BlockPos unloaded = new BlockPos(900008, 81, 900008);
            require(!world.isBlockLoaded(unloaded, false), "unloaded fixture starts absent");
            cells.columnRainStrength(.5F, unloaded, Biomes.JUNGLE);
            require(!world.isBlockLoaded(unloaded, false), "column policy never loads terrain");
            if (Boolean.getBoolean("skyweather.distant")) distant(world, settings);
            System.out.println("PRECIPITATION_PASS checks=" + checks);
        } catch (Throwable failure) {
            System.out.println("PRECIPITATION_FAILED " + failure); failure.printStackTrace();
        } finally { world.getMinecraftServer().initiateShutdown(); }
    }

    private static void distant(WorldServer world, WeatherSettings settings) throws Exception {
        for (int z = -17; z <= 17; z++) for (int x = -17; x <= 17; x++) {
            byte[] data = world.getChunkFromChunkCoords(x, z).getBiomeArray();
            java.util.Arrays.fill(data, (byte) Biome.getIdForBiome(x >= 2 ? Biomes.ICE_PLAINS : Biomes.JUNGLE));
        }
        world.setWorldTime(320L * 24000 + 6000);
        WeatherEpisode storm = new WeatherEpisode(WeatherKind.BLIZZARD, world.getTotalWorldTime() - 100, 10000, .9F, false, true);
        WeatherState.Frame frame = new WeatherState.Frame(world, settings, null, storm, true);
        DistantPrecipitationField field = new DistantPrecipitationField();
        field.sample(frame, 0, 0, 192, 32, true, 1, 128);
        require(field.count > 50 && field.count < DistantPrecipitationField.MAX_COLUMNS, "bounded distant storm visible from clear jungle");
        boolean far = false;
        for (int i = 0; i < field.count; i++) {
            DistantPrecipitationField.Column c = field.columns[i];
            require(c.x >= 32 && c.snow && c.strength > 0 && c.top >= c.bottom + 48, "snow curtain obeys local biome and surface");
            require(c.distanceSquared <= 192 * 192, "curtain respects configured range");
            far |= c.distanceSquared > 128 * 128;
        }
        require(far, "storm extends across landscape beyond 128 blocks");
        field.sample(frame, -900008, -900008, 256, 32, true, 1, 128);
        require(field.count == 0 && !world.isBlockLoaded(new BlockPos(-900008, 64, -900008), false), "distant sampling never requests absent terrain");
        field.sample(frame, 0, 0, 0, 32, true, 1, 128);
        require(field.count == 0 && field.samples == 0, "disabled field does no sampling");
        field.sample(frame, 0, 0, 256, 32, true, 0, 128);
        require(field.count == 0 && field.samples == 0, "clear carrier does no sampling");
        WeatherSettings universal = settings.withMode(ClimateMode.UNIVERSAL, settings.biomes);
        WeatherEpisode rain = new WeatherEpisode(WeatherKind.FRONTAL_PRECIPITATION, world.getTotalWorldTime() - 100, 10000, .9F, false, true);
        WeatherState.Frame mixed = new WeatherState.Frame(world, universal, null, rain, true);
        field.sample(mixed, 0, 0, 192, 32, true, 1, 128);
        boolean hasRain = false, hasSnow = false;
        for (int i = 0; i < field.count; i++) { hasSnow |= field.columns[i].snow; hasRain |= !field.columns[i].snow; }
        require(hasRain && hasSnow, "rain and snow coexist in one field");
        java.io.File output = new java.io.File("distant-weather"); output.mkdirs();
        try (java.io.PrintWriter csv = new java.io.PrintWriter(new java.io.File(output, "sampling.csv"), "UTF-8")) {
            csv.println("scenario,range,samples,columns,p50_ms,p95_ms,p99_ms");
            for (int scenario = 0; scenario < 5; scenario++) {
                int range = scenario == 1 ? 256 : 192; boolean fancy = scenario != 2;
                float carrier = scenario == 3 ? 0 : 1; double x = scenario == 4 ? 900008 : 0;
                for (int warm = 0; warm < 500; warm++) field.sample(mixed, x, 0, range, 32, fancy, carrier, 128);
                long[] elapsed = new long[200];
                for (int iteration = 0; iteration < elapsed.length; iteration++) {
                    long start = System.nanoTime(); field.sample(mixed, x, 0, range, 32, fancy, carrier, 128);
                    elapsed[iteration] = System.nanoTime() - start;
                }
                java.util.Arrays.sort(elapsed);
                String name = new String[] {"fancy_default", "fancy_maximum", "fast", "clear", "unloaded"}[scenario];
                csv.printf(java.util.Locale.ROOT, "%s,%d,%d,%d,%.6f,%.6f,%.6f%n", name, field.range, field.samples, field.count,
                        elapsed[100] / 1e6, elapsed[190] / 1e6, elapsed[198] / 1e6);
                System.out.println("DISTANT_SAMPLING " + name + " samples=" + field.samples + " columns=" + field.count + " p95_ms=" + elapsed[190] / 1e6);
                require(field.samples < DistantPrecipitationField.MAX_COLUMNS && field.count <= field.samples, "sample work bounded at every quality");
            }
        }
        field.sample(mixed, 0, 0, 256, 32, true, 1, 128);
        try (java.io.PrintWriter csv = new java.io.PrintWriter(new java.io.File(output, "columns.csv"), "UTF-8")) {
            csv.println("x,z,size,bottom,top,strength,snow,light");
            for (int i = 0; i < field.count; i++) {
                DistantPrecipitationField.Column c = field.columns[i];
                csv.printf(java.util.Locale.ROOT, "%d,%d,%d,%.1f,%.1f,%.6f,%s,%d%n", c.x,c.z,c.size,c.bottom,c.top,c.strength,c.snow,c.light);
            }
        }
    }

    private static void checkBoundary(WorldServer world, WeatherSettings settings, BlockPos wet, Biome wetBiome,
                                      BlockPos dry, Biome dryBiome, LocalWeather.Precipitation type) {
        BiomeWeather regions = new BiomeWeather(settings, world.getSeed());
        for (int day = 0; day < 365 * 3; day++) for (int hour = 0; hour < 24; hour++) {
            long time = day * 24000L + hour * 1000; world.setWorldTime(time);
            WeatherEpisode carrier = new WeatherEpisode(WeatherKind.FRONTAL_PRECIPITATION, time - 1000, 10000, .8F);
            WeatherState.Frame frame = new WeatherState.Frame(world, settings, null, carrier, true, regions, world.getSeed());
            if (frame.precipitation(wet, wetBiome) != type || frame.columnRainStrength(1, wet, wetBiome) < .2F
                    || frame.visualRainStrength(1, dry, dryBiome) != 0) continue;
            require(frame.regionalSchedule(), "per-biome schedule active");
            require(frame.columnRainStrength(1, dry, dryBiome) == 0, "clear side stays clear");
            float expected = frame.columnRainStrength(1, wet, wetBiome);
            require(expected > 0, "storm opacity is positive despite old clear-camera gate being zero");
            ByteBuf bytes = Unpooled.buffer();
            try {
                new WeatherMessage(frame).toBytes(bytes); WeatherMessage packet = new WeatherMessage(); packet.fromBytes(bytes);
                WeatherState.Frame client = new WeatherState.Frame(world, packet.settings, packet.live, packet.episode, packet.scheduled, null, packet.seed);
                require(Math.abs(client.columnRainStrength(1, wet, wetBiome) - expected) < .00001, "remote client reconstructs identical column strength");
                require(client.columnRainStrength(1, dry, dryBiome) == 0, "remote client retains dry neighbour");
            } finally { bytes.release(); }
            System.out.println("PRECIPITATION_BOUNDARY type=" + type + " day=" + day + " hour=" + hour + " wet=" + expected + " oldCameraGate=0");
            return;
        }
        throw new IllegalStateException("No independent " + type + " boundary found");
    }
}
