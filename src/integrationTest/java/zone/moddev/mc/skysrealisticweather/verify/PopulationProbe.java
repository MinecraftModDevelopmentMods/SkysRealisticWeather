package zone.moddev.mc.skysrealisticweather.verify;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.BlockStone;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.ClimateSample;
import zone.moddev.mc.skysrealisticweather.api.SeasonalSurfaceEvent;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.game.WeatherHooks;

/** Samples the native frost pass before a weather tick can touch the new chunks. */
public final class PopulationProbe {
    private static final int CHUNK = 320;
    private static final BlockPos WATER = new BlockPos(CHUNK * 16 + 8, 120, CHUNK * 16 + 8);
    private static final BlockPos REPLACED_WATER = WATER.south(2);
    private static final BlockPos SNOW = WATER.add(2, 1, 0);
    private static final BlockPos VETO = SNOW.east();
    private final WorldServer world;
    private final String mode;
    private boolean frostPass;
    private boolean completed;
    private boolean vetoed;
    private int ticks;

    PopulationProbe(WorldServer world, String mode) {
        this.world = world;
        this.mode = mode;
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.TERRAIN_GEN_BUS.register(this);
        boolean summer = nativeCover() || mode.equals("taiga") || mode.equals("unprotect") || mode.equals("reload");
        command("skyweather season set " + (summer ? "summer" : "winter") + " 45");
        if (summer) command("skyweather strength 1.6");
        if (mode.equals("off")) command("skyweather strength 1.2");
        command("gamerule doDaylightCycle false");
        command("skyweather weather " + (mode.equals("clear") ? "clear" : "frontal_precipitation") + " 90");
    }

    @SubscribeEvent
    public void beforeFrost(PopulateChunkEvent.Populate event) {
        if (event.getType() != PopulateChunkEvent.Populate.EventType.ICE || !target(event)) return;
        frostPass = true;
        if (mode.startsWith("bop")) {
            require(event.getGenerator().getClass().getName().startsWith("biomesoplenty."),
                    "actual Biomes O' Plenty generator");
            Biome sample = mode.equals("bop") ? Biomes.PLAINS
                    : Biome.REGISTRY.getObject(new ResourceLocation("biomesoplenty:glacier"));
            require(sample != null, "BOP fixture biome registered");
            byte biome = (byte) Biome.getIdForBiome(sample);
            for (BlockPos position : new BlockPos[]{WATER, SNOW, VETO}) {
                int column = (position.getZ() & 15) << 4 | (position.getX() & 15);
                world.getChunkFromBlockCoords(position).getBiomeArray()[column] = biome;
            }
        }
        world.setBlockState(WATER.down(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(WATER, Blocks.WATER.getDefaultState(), 2);
        if (mode.equals("winter")) {
            world.setBlockState(REPLACED_WATER.down(), Blocks.STONE.getDefaultState(), 2);
            world.setBlockState(REPLACED_WATER, Blocks.WATER.getDefaultState(), 2);
        }
        for (BlockPos position : new BlockPos[]{SNOW, VETO}) {
            world.setBlockState(position.down(), Blocks.GRASS.getDefaultState(), 2);
        }
        if (mode.equals("deny")) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public void afterPopulation(PopulateChunkEvent.Post event) {
        if (target(event)) completed = true;
    }

    @SubscribeEvent
    public void protect(SeasonalSurfaceEvent event) {
        if (event.world == world && event.position.equals(VETO)) {
            event.setCanceled(true);
            vetoed = true;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.world != world || event.phase != TickEvent.Phase.END || ++ticks != 120) return;
        boolean clear = mode.equals("clear");
        require(world.isRaining() != clear, "preview settled before generating terrain");
        if (mode.equals("reload")) {
            loadTarget();
            require(!frostPass && hasSnow(SNOW) && hasIce(), "seasonal deposits survive restart");
            WeatherHooks.freeze(world, WATER);
            WeatherHooks.freeze(world, SNOW.down());
            require(world.getBlockState(WATER).getBlock() == Blocks.WATER && !hasSnow(SNOW),
                    "tracked generation deposits thaw and restore the source");
            require(world.getBlockState(REPLACED_WATER).getBlock() == Blocks.ICE, "second tracked ice survives restart");
            world.setBlockState(REPLACED_WATER, Blocks.STONE.getDefaultState(), 2);
            world.setBlockState(REPLACED_WATER, Blocks.ICE.getDefaultState(), 2);
            WeatherHooks.freeze(world, REPLACED_WATER);
            require(world.getBlockState(REPLACED_WATER).getBlock() == Blocks.ICE,
                    "replacing tracked ice invalidates its thaw record");
        } else {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    require(world.getChunkProvider().getLoadedChunk(CHUNK + dx, CHUNK + dz) == null, "new chunks");
                }
            }
            if (mode.equals("winter")) verifyBeforeGeneration();
            loadTarget();
            require(frostPass && completed, "native snow and ice population ran");
            boolean winterStorm = mode.equals("winter") || mode.equals("bop");
            boolean polar = nativeCover();
            require(hasSnow(SNOW) == (winterStorm || polar), "expected initial snow in " + mode);
            require(hasIce() == (winterStorm || polar || clear), "expected initial ice in " + mode);
            require(hasSnow(VETO) == polar && vetoed == winterStorm, "surface event veto applies to seasonal snow");
            LocalWeather local = WeatherApi.getWeather(world, SNOW.up()).get();
            if (winterStorm || mode.equals("off") || mode.equals("deny")) {
                require(local.precipitation == LocalWeather.Precipitation.SNOW, "active local snow");
            } else if (!clear) {
                require(local.precipitation == LocalWeather.Precipitation.RAIN, "active local rain");
            }
            if (polar) {
                WeatherHooks.freeze(world, WATER);
                WeatherHooks.freeze(world, SNOW.down());
                require(hasIce() && hasSnow(SNOW), "native polar cover is not tracked as seasonal");
            }
        }
        System.out.println("SKY_WEATHER_CHECK POPULATION_" + mode.toUpperCase(java.util.Locale.ROOT) + " PASS");
        world.getMinecraftServer().initiateShutdown();
    }

    private void verifyBeforeGeneration() {
        verifyPropertyHash();
        require(!WeatherApi.getClimate(world, SNOW).isPresent()
                && !WeatherApi.getTemperature(world, SNOW).isPresent(), "loaded-only queries decline future terrain");
        BlockPos.MutableBlockPos prospective = new BlockPos.MutableBlockPos(SNOW);
        ClimateSample sample = WeatherApi.getClimate(world, prospective, Biomes.PLAINS).get();
        prospective.setPos(0, 0, 0);
        require(sample.position.equals(SNOW), "generation sample owns its position");
        require(sample.openSkyWeather.precipitation == LocalWeather.Precipitation.SNOW
                && sample.openSkyWeather.temperature == WeatherApi.getTemperature(world, SNOW, Biomes.PLAINS).getAsDouble()
                && sample.vanillaTemperature < sample.biomeTemperature, "generation sample includes altitude and snow");
        require(world.getChunkProvider().getLoadedChunk(CHUNK, CHUNK) == null,
                "supplied-biome queries do not provide the chunk");
    }

    private static void verifyPropertyHash() {
        PropertyEnum<BlockStone.EnumType> stone = BlockStone.VARIANT;
        PropertyEnum<BlockStone.EnumType> equivalent = PropertyEnum.create(stone.getName(),
                BlockStone.EnumType.class, stone.getAllowedValues());
        Map<String, BlockStone.EnumType> names = new HashMap<>();
        for (BlockStone.EnumType type : stone.getAllowedValues()) names.put(type.getName(), type);
        int base = 31 * stone.getValueClass().hashCode() + stone.getName().hashCode();
        int expected = 31 * (31 * base + stone.getAllowedValues().hashCode()) + names.hashCode();
        require(stone.equals(equivalent) && stone.hashCode() == expected && equivalent.hashCode() == expected,
                "independently constructed enum properties retain vanilla equality and hash");
        require(Blocks.STONE.getDefaultState().getValue(equivalent) == BlockStone.EnumType.STONE,
                "terrain can read its stone variant using an equivalent property");
    }

    private boolean target(PopulateChunkEvent event) {
        return event.getWorld() == world && event.getChunkX() == CHUNK && event.getChunkZ() == CHUNK;
    }

    private boolean nativeCover() {
        return mode.equals("polar") || mode.equals("bop-glacier");
    }

    private void loadTarget() {
        Chunk chunk = world.getChunkFromChunkCoords(CHUNK, CHUNK);
        world.getChunkFromChunkCoords(CHUNK + 1, CHUNK);
        world.getChunkFromChunkCoords(CHUNK, CHUNK + 1);
        world.getChunkFromChunkCoords(CHUNK + 1, CHUNK + 1);
        require(chunk.isTerrainPopulated(), "test chunk is fully populated");
    }

    private boolean hasSnow(BlockPos position) {
        return world.getBlockState(position).getBlock() == Blocks.SNOW_LAYER;
    }

    private boolean hasIce() {
        return world.getBlockState(WATER).getBlock() == Blocks.ICE;
    }

    private void command(String text) {
        MinecraftServer server = world.getMinecraftServer();
        require(server.getCommandManager().executeCommand(server, text) == 1, "command applied: " + text);
    }

    private static void require(boolean condition, String message) {
        WeatherProbe.require(condition, message);
    }
}
