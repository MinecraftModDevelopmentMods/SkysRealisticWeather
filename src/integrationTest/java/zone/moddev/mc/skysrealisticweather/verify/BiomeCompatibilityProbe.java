package zone.moddev.mc.skysrealisticweather.verify;

import java.util.Objects;
import java.util.Random;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.api.ClimateSample;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.SeasonalSurfaceEvent;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.game.SurfaceTracker;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;

/** Exercises naturally selected biomes and measures cover inside the generation call, before weather ticks. */
public final class BiomeCompatibilityProbe {
    private final WorldServer world;
    private final String prefix = System.getProperty("skyweather.biomePrefix");
    private final String biomeName = System.getProperty("skyweather.biomeName", "");
    private final boolean seasonalCover = Boolean.parseBoolean(System.getProperty("skyweather.biomeCover", "true"));
    private final Random positions = new Random(123948);
    private final boolean waterSample = Boolean.getBoolean("skyweather.testWater");
    private final boolean checkVeto = Boolean.getBoolean("skyweather.testVeto");
    private final boolean clear = Boolean.getBoolean("skyweather.testClear");
    private BlockPos veto;
    private BlockPos owned;
    private int targetX, targetZ;
    private boolean sawIcePass;
    private int ticks;
    private int snowEvents;
    private boolean generating;
    private Biome winterBiome;

    BiomeCompatibilityProbe(WorldServer world) {
        this.world = world;
        for (String id : System.getProperty("skyweather.expectedMods").split(",")) {
            WeatherProbe.require(Loader.isModLoaded(id), "loaded " + id);
        }
        WeatherProbe.require(System.getProperty("skyweather.worldType").equalsIgnoreCase(world.getWorldType().getName()),
                "selected world type " + world.getWorldType().getName());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.TERRAIN_GEN_BUS.register(this);
        command("gamerule doDaylightCycle false");
        command("skyweather season set winter 45");
        command("skyweather strength 2");
        command("skyweather weather " + (clear ? "clear" : "frontal_precipitation") + " 180");
    }

    private void command(String command) {
        WeatherProbe.require(world.getMinecraftServer().getCommandManager().executeCommand(world.getMinecraftServer(), command) == 1, command);
    }

    @SubscribeEvent
    public void surface(SeasonalSurfaceEvent event) {
        if (generating && event.world == world && event.action == SeasonalSurfaceEvent.Action.ACCUMULATE_SNOW) snowEvents++;
        if (generating && event.world == world && event.action == (waterSample ? SeasonalSurfaceEvent.Action.FREEZE_WATER
                : SeasonalSurfaceEvent.Action.ACCUMULATE_SNOW)) {
            if (checkVeto && (veto == null || veto.equals(event.position))) {
                veto = event.position;
                event.setCanceled(true);
            } else if (owned == null) owned = event.position;
        }
    }

    @SubscribeEvent
    public void icePass(PopulateChunkEvent.Populate event) {
        if (!generating || event.getWorld() != world || event.getType() != PopulateChunkEvent.Populate.EventType.ICE) return;
        if (event.getChunkX() == targetX && event.getChunkZ() == targetZ) sawIcePass = true;
        if (Boolean.getBoolean("skyweather.denyIcePass")) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.world != world || event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks == 120) {
            if (Loader.isModLoaded("biomesoplenty")) verifyBopClimates();
            sample(true);
            command("skyweather season set summer 45");
        } else if (ticks == 160) {
            if (seasonalCover && checkVeto) {
                WeatherProbe.require(owned != null && veto != null, "seasonal placement and veto observed");
                WeatherProbe.require(hasDeposit(owned) && !hasDeposit(veto), "only permitted deposit exists");
                SurfaceTracker.INSTANCE.thaw(world, owned, WeatherState.frame(world));
                WeatherProbe.require(waterSample ? world.getBlockState(owned).getBlock() instanceof BlockLiquid
                        && world.getBlockState(owned).getValue(BlockLiquid.LEVEL) == 0
                        : world.isAirBlock(owned), "seasonal deposit thaws in summer");
            }
            sample(false);
            System.out.println("SKY_WEATHER_CHECK BIOME_COMPAT PASS");
            world.getMinecraftServer().initiateShutdown();
        }
    }

    private boolean hasDeposit(BlockPos pos) {
        return world.getBlockState(pos).getBlock() == (waterSample ? Blocks.ICE : Blocks.SNOW_LAYER);
    }

    private void verifyBopClimates() {
        BlockPos low = new BlockPos(24000, 64, 24000);
        Biome outback = Biome.REGISTRY.getObject(new ResourceLocation("biomesoplenty:outback"));
        Biome rainforest = Biome.REGISTRY.getObject(new ResourceLocation("biomesoplenty:tropical_rainforest"));
        WeatherProbe.require(outback != null && rainforest != null, "BOP arid and tropical biomes registered");
        ClimateSample dry = WeatherApi.getClimate(world, low, outback).get();
        ClimateSample tropical = WeatherApi.getClimate(world, low, rainforest).get();
        ClimateSample high = WeatherApi.getClimate(world, low.up(150), rainforest).get();
        WeatherProbe.require(!dry.normallyAllowsPrecipitation && dry.openSkyWeather.precipitation == LocalWeather.Precipitation.NONE,
                "BOP Outback remains dry during an ordinary front");
        WeatherProbe.require(tropical.normallyAllowsPrecipitation && tropical.openSkyWeather.precipitation == LocalWeather.Precipitation.RAIN
                && tropical.biomeClimate.seasonalScale < 1 && high.vanillaTemperature < tropical.vanillaTemperature,
                "BOP tropical rainforest keeps its reduced seasonal variation and elevation response");
        System.out.println("BIOME_COMPAT supplied BOP Outback dry; Tropical Rainforest rain, scale=" + tropical.biomeClimate.seasonalScale);
    }

    private void sample(boolean winter) {
        LocalWeather.Precipitation expected = clear ? LocalWeather.Precipitation.NONE
                : winter ? LocalWeather.Precipitation.SNOW : LocalWeather.Precipitation.RAIN;
        BlockPos center = findFresh(winter, expected);
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        Biome selected = world.getBiomeProvider().getBiome(center);
        ClimateSample before = WeatherApi.getClimate(world, center, selected).get();
        WeatherProbe.require(world.getChunkProvider().getLoadedChunk(cx, cz) == null, "generation API leaves chunk unloaded");
        WeatherProbe.require(before.biomeRainfall == selected.getRainfall() && before.vanillaTemperature == selected.getFloatTemperature(center),
                "supplied-biome climate uses generator temperature and rainfall");
        String rainfall = System.getProperty("skyweather.expectedRainfall");
        if (rainfall != null) WeatherProbe.require(before.biomeRainfall == Float.parseFloat(rainfall), "pack rainfall edit is visible");
        snowEvents = 0;
        targetX = cx;
        targetZ = cz;
        sawIcePass = false;
        generating = true;
        try {
            world.getChunkFromChunkCoords(cx, cz);
            world.getChunkFromChunkCoords(cx + 1, cz);
            world.getChunkFromChunkCoords(cx, cz + 1);
            world.getChunkFromChunkCoords(cx + 1, cz + 1);
            if ("RTG".equalsIgnoreCase(world.getWorldType().getName())) {
                // RTG defers decoration until its surrounding chunks exist.
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) world.getChunkFromChunkCoords(cx + dx, cz + dz);
                WeatherProbe.require(sawIcePass, "RTG decorated target chunk");
            }
        } finally {
            generating = false;
        }
        WeatherProbe.require(world.getChunkFromChunkCoords(cx, cz).isTerrainPopulated(), "naturally populated chunk");
        Biome saved = world.getBiome(center);
        int snow = 0;
        int ice = 0;
        int eligible = 0;
        for (int dx = 8; dx < 24; dx++) for (int dz = 8; dz < 24; dz++) {
            BlockPos column = new BlockPos(cx * 16 + dx, 0, cz * 16 + dz);
            if (world.getBiome(column) != saved) continue;
            BlockPos top = world.getPrecipitationHeight(column);
            if (world.getBlockState(top.down()).getBlock() == Blocks.ICE) ice++;
            BlockPos layer = world.getBlockState(top.down()).getBlock() == Blocks.SNOW_LAYER ? top.down() : top;
            if (world.getBlockState(layer).getBlock() == Blocks.SNOW_LAYER) snow++;
            if (Blocks.SNOW_LAYER.canPlaceBlockAt(world, layer)) eligible++;
        }
        BlockPos sky = center.up(127);
        ClimateSample loaded = WeatherApi.getClimate(world, center).get();
        System.out.println("BIOME_COMPAT " + (winter ? "winter" : "summer") + " " + before.biomeId + " at " + center
                + " generatorBiome=" + selected.getBiomeName() + " savedBiome=" + saved.getBiomeName() + " loaded=" + loaded.biomeId
                + " type=" + world.getWorldType().getName() + " provider=" + world.getBiomeProvider().getClass().getName()
                + " rainfall=" + before.biomeRainfall + " temperature=" + before.openSkyWeather.temperature
                + " sky=" + WeatherApi.getWeather(world, sky).get().precipitation + " snow=" + snow + " eligible=" + eligible + " events=" + snowEvents + " ice=" + ice);
        ClimateSample savedSample = WeatherApi.getClimate(world, center, saved).get();
        WeatherProbe.require(Objects.equals(loaded.biomeId, saved.getRegistryName())
                && loaded.seasonalTemperature == savedSample.seasonalTemperature,
                "loaded climate follows the biome saved by the generator");
        if (!world.getWorldType().getName().equalsIgnoreCase("OTG")) {
            WeatherProbe.require(selected == saved, "generator and saved biome match");
        }
        WeatherProbe.require(WeatherApi.getWeather(world, sky).get().precipitation == expected, "exposed runtime precipitation");
        if (waterSample) {
            WeatherProbe.require((ice > 0) == (winter && seasonalCover), "expected physical ice on natural water");
        } else {
            WeatherProbe.require(eligible > 0, "sample has exposed snow-supporting terrain");
            WeatherProbe.require((snow > 0) == (winter && seasonalCover), "expected physical cover on temperate natural terrain");
            WeatherProbe.require((snowEvents > 0) == (winter && seasonalCover), "seasonal generation events");
        }
        if (winter) winterBiome = selected;
    }

    private BlockPos findFresh(boolean winter, LocalWeather.Precipitation expected) {
        for (int i = 0; i < 80000; i++) {
            int cx = positions.nextInt(2048) - 1024;
            int cz = positions.nextInt(2048) - 1024;
            if (world.getChunkProvider().getLoadedChunk(cx, cz) != null) continue;
            BlockPos pos = new BlockPos(cx * 16 + 12, 105, cz * 16 + 12);
            Biome biome = world.getBiomeProvider().getBiome(pos);
            if (biome == null) continue;
            String id = Objects.toString(biome.getRegistryName(), "");
            if (!id.startsWith(prefix) || !biome.getBiomeName().startsWith(biomeName)
                    || (!winter && biome != winterBiome) || !biome.canRain()
                    || biome.getTemperature() < 0.35F || biome.getTemperature() > 0.9F) continue;
            if (WeatherApi.getClimate(world, pos, biome).get().openSkyWeather.precipitation == expected) return pos;
        }
        throw new IllegalStateException("No fresh temperate sample for " + prefix + " in " + world.getBiomeProvider().getClass());
    }
}
