package zone.moddev.mc.skysrealisticweather.game;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.event.terraingen.SaplingGrowTreeEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthProfile;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthSample;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthSample.Reason;
import zone.moddev.mc.skysrealisticweather.climate.FarmingSettings;
import zone.moddev.mc.skysrealisticweather.core.PlantHookStatus;

/** Only natural-growth entry points call the gate. Direct fertiliser paths are intentionally left native. */
public final class PlantGrowthHooks {
    public static final PlantGrowthHooks INSTANCE = new PlantGrowthHooks();
    private static final Map<Class<?>, Map<String, Method>> METHODS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> FERTILIZING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Deque<World>> POPULATING = new ThreadLocal<>();
    private static final Map<Class<?>, Method> SAPLING_FERTILIZERS = new ConcurrentHashMap<>();
    private static Field forestryManager;
    private static Method forestryPosition, forestryTemperature;
    private static boolean forestryFailed;

    public static void initialize() {
        if (!Loader.isModLoaded("forestry")) return;
        try {
            forestryManager = Class.forName("forestry.api.core.ForestryAPI").getField("climateManager");
            forestryPosition = Class.forName("forestry.api.climate.IClimateManager").getMethod("getPosition", World.class, BlockPos.class);
            forestryTemperature = Class.forName("forestry.api.climate.IClimatePosition").getMethod("getTemperature");
        } catch (ReflectiveOperationException | LinkageError invalid) {
            forestryFailed = true;
            PlantHookStatus.failed("Forestry controlled climate", new IllegalStateException(invalid));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void crop(BlockEvent.CropGrowEvent.Pre event) {
        if (event.getResult() != Event.Result.DEFAULT || event.getWorld().isRemote) return;
        WeatherState.Frame frame = WeatherState.frame(event.getWorld());
        if (frame == null || !frame.active() || !frame.settings.farming.enabled) return;
        BlockPos source = event.getPos();
        // Forge 1.10 cactus events use the empty destination above the actual plant.
        if (event.getWorld().isBlockLoaded(source, false)
                && event.getWorld().getBlockState(source).getBlock() != event.getState().getBlock()
                && event.getWorld().isBlockLoaded(source.down(), false)
                && event.getWorld().getBlockState(source.down()).getBlock() == event.getState().getBlock()) source = source.down();
        if (!allow(event.getWorld(), source, event.getWorld().rand)) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tree(SaplingGrowTreeEvent event) {
        if (populating(event.getWorld())) return;
        if (!event.getWorld().isBlockLoaded(event.getPos(), false)) return;
        net.minecraft.block.Block block = event.getWorld().getBlockState(event.getPos()).getBlock();
        if (block instanceof net.minecraft.block.BlockSapling && PlantHookStatus.fallback("net.minecraft.block.BlockSapling")
                || PlantHookStatus.fallback(block.getClass().getName())) return;
        if (event.getResult() == Event.Result.DEFAULT && !event.getWorld().isRemote && !FERTILIZING.get()
                && !allow(event.getWorld(), event.getPos(), event.getRand())) event.setResult(Event.Result.DENY);
    }

    /** Scope only the direct IGrowable fertiliser entry point; tree generation still posts native events. */
    public static void fertilizeSapling(Object sapling, World world, BlockPos pos, IBlockState state, Random random, Class<?> owner, String name) {
        boolean previous = FERTILIZING.get(); FERTILIZING.set(true);
        try {
            Method method = SAPLING_FERTILIZERS.get(owner);
            if (method == null) {
                method = owner.getDeclaredMethod(name, World.class, BlockPos.class, IBlockState.class, Random.class);
                method.setAccessible(true); SAPLING_FERTILIZERS.put(owner, method);
            }
            method.invoke(sapling, world, pos, state, random);
        } catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Native sapling fertiliser failed", cause);
        } catch (ReflectiveOperationException invalid) { throw new IllegalStateException("Sapling fertiliser callback differs", invalid); }
        finally { if (previous) FERTILIZING.set(true); else FERTILIZING.remove(); }
    }

    public static int naturalRoll(Random random, int bound, World world, BlockPos pos) {
        int original = random.nextInt(bound);
        return original == 0 && !allow(world, pos, random) ? 1 : original;
    }

    public static boolean allow(World world, BlockPos pos, Random random) {
        if (populating(world)) return true;
        WeatherState.Frame frame = WeatherState.frame(world);
        if (world.isRemote || frame == null || !frame.active() || !frame.settings.farming.enabled || !world.isBlockLoaded(pos, false)) return true;
        PlantGrowthSample sample = sample(world, pos);
        float multiplier = sample == null ? 1 : sample.naturalGrowthMultiplier;
        // Preserve native RNG traces exactly for unmodified, ideal and fully blocked growth.
        return multiplier >= 1 || multiplier > 0 && random.nextFloat() < multiplier;
    }

    /** Preserve native generation callbacks, including parks that explicitly grow saplings. */
    public static void populateChunk(net.minecraft.world.chunk.IChunkGenerator generator,int x,int z,net.minecraft.world.chunk.Chunk chunk) {
        Deque<World> worlds=POPULATING.get();
        if(worlds==null){worlds=new ArrayDeque<>();POPULATING.set(worlds);}
        worlds.push(chunk.getWorld());
        try {generator.populate(x,z);}
        finally {worlds.pop();if(worlds.isEmpty())POPULATING.remove();}
    }

    private static boolean populating(World world) {
        Deque<World> worlds=POPULATING.get();return worlds!=null&&worlds.contains(world);
    }

    public static PlantGrowthSample sample(World world, BlockPos pos) {
        WeatherState.Frame frame = WeatherState.frame(world);
        if (frame == null || !world.isBlockLoaded(pos, false)) return null;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IPlantable) && !(state.getBlock() instanceof IGrowable)) return null;
        String id = state.getBlock().getRegistryName().toString();
        boolean magical = id.startsWith("mysticalagriculture:") || id.startsWith("mysticalagradditions:");
        if (id.startsWith("agricraft:")) {
            Object crop = world.getTileEntity(pos);
            if (crop != null) try {
                if (!(Boolean) call(crop, "hasSeed")) return null;
                Object plant = call(call(crop, "getSeed"), "getPlant");
                id = "agricraft:" + call(plant, "getId");
                ItemStack seed = (ItemStack) call(plant, "getSeed");
                if (seed != null && seed.getItem() != null && seed.getItem().getRegistryName() != null) {
                    String item = seed.getItem().getRegistryName().toString();
                    magical = item.startsWith("mysticalagriculture:") || item.startsWith("mysticalagradditions:");
                }
            } catch (ReflectiveOperationException | LinkageError invalid) {
                PlantHookStatus.failed("AgriCraft crop identity", new IllegalStateException(invalid));
                return new PlantGrowthSample(id, "native", frame.temperature(world.getBiome(pos), pos), 1, false, Reason.ADAPTER_DISABLED);
            }
        }
        return sample(world, pos, state, id, magical);
    }

    /** Evaluate an inventory plant at a loaded location without placing it or reading a crop tile there. */
    public static PlantGrowthSample sample(World world, BlockPos pos, IBlockState state, String id, boolean magical) {
        WeatherState.Frame frame = WeatherState.frame(world);
        if (frame == null || !world.isBlockLoaded(pos, false)) return null;
        FarmingSettings settings = frame.settings.farming;
        zone.moddev.mc.skysrealisticweather.api.TemperatureSample local=TemperatureZones.sample(world,pos).orElse(null);
        if(local==null)return null;
        float temperature=local.temperature;
        Float room=local.source==zone.moddev.mc.skysrealisticweather.api.TemperatureSample.Source.AMBIENT?null:temperature;
        Reason bypass = !frame.active() || !settings.enabled ? Reason.DISABLED
                : id.startsWith("harvestcraft:") && !settings.harvestcraft
                || id.startsWith("agricraft:") && !settings.agricraft || magical && !settings.mystical ? Reason.ADAPTER_DISABLED : null;
        FarmingSettings.Resolved resolved = settings.resolve(id, state);
        PlantGrowthProfile profile = resolved == null ? null : resolved.profile;
        if (bypass != null) return new PlantGrowthSample(id, resolved == null ? "native" : resolved.id, temperature, 1, room != null, bypass);
        if (profile == null) return new PlantGrowthSample(id, "native", temperature, 1, room != null, magical ? Reason.EXEMPT : Reason.UNMAPPED);
        float multiplier = profile.multiplier(temperature);
        Reason reason = profile.exempt ? Reason.EXEMPT : multiplier == 1 ? Reason.SUITABLE : temperature < profile.idealMinimum ? Reason.COLD : Reason.HEAT;
        return new PlantGrowthSample(id, resolved.id, temperature, multiplier, room != null, reason);
    }

    /** Replaces only the natural block-tick method reference, not fertiliser or direct crop API calls. */
    public static void agriNaturalTick(Object crop) {
        boolean allow = true;
        try {
            if ((Boolean) call(crop, "hasSeed") && !(Boolean) call(crop, "isMature") && !(Boolean) call(crop, "isCrossCrop")) {
                World world = (World) call(crop, "getCropWorld");
                allow = allow(world, (BlockPos) call(crop, "getCropPos"), world.rand);
            }
        } catch (ReflectiveOperationException | LinkageError invalid) {
            PlantHookStatus.failed("AgriCraft crop identity", new IllegalStateException(invalid));
        }
        if (allow) try { call(crop, "onGrowthTick"); }
        catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("AgriCraft native tick failed", cause);
        } catch (ReflectiveOperationException invalid) { throw new IllegalStateException("AgriCraft callback no longer matches", invalid); }
    }

    static Object call(Object target, String name) throws ReflectiveOperationException {
        Map<String, Method> methods = METHODS.computeIfAbsent(target.getClass(), ignored -> new ConcurrentHashMap<>());
        Method method = methods.get(name);
        if (method == null) {
            method = target.getClass().getMethod(name);
            method.setAccessible(true);
            methods.put(name, method);
        }
        return method.invoke(target);
    }

    public static Float controlledTemperature(World world, BlockPos pos) {
        if (forestryManager == null || forestryFailed) return null;
        try {
            Object manager = forestryManager.get(null);
            Object position = manager == null ? null : forestryPosition.invoke(manager, world, pos);
            if (position == null) return null;
            float temperature = ((Number) forestryTemperature.invoke(position)).floatValue();
            return Float.isFinite(temperature) ? temperature : null;
        } catch (ReflectiveOperationException invalid) {
            forestryFailed = true;
            PlantHookStatus.failed("Forestry controlled climate", invalid);
            return null;
        }
    }

    private static WeatherState.Frame forestryFrame(World world, BlockPos pos) {
        WeatherState.Frame frame = WeatherState.frame(world);
        return frame != null && frame.active() && frame.settings.farming.enabled && frame.settings.farming.forestry
                && !forestryFailed && world.isBlockLoaded(pos, false) ? frame : null;
    }
    public static float forestryTemperature(float original, World world, BlockPos pos) {
        WeatherState.Frame frame = forestryFrame(world, pos);
        return frame == null ? original : TemperatureZones.sample(world,pos).map(value -> value.temperature).orElse(original);
    }
    public static float forestryHumidity(float original, World world, BlockPos pos) {
        WeatherState.Frame frame = forestryFrame(world, pos);
        if (frame == null) return original;
        float proxy = Math.max(0, Math.min(1, frame.climate(world.getBiome(pos)).wetness * 0.5F));
        return original + (proxy - original) * Math.min(1, frame.settings.profile.strength);
    }
}
