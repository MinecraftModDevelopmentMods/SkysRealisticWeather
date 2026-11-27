package zone.moddev.mc.skysrealisticweather.game;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import org.apache.logging.log4j.LogManager;

/** Inventory identities only. Cached entries retain items/states, never worlds, stacks or climate readings. */
public final class PlantItems {
    private static Map<Item, Map<Integer, List<Plant>>> produce;
    private static Method agriRegistry, agriSeed;
    private static final Set<Class<?>> warned = new HashSet<>();
    private PlantItems() { }

    public static final class Plant {
        public final IBlockState state;
        public final String id;
        public final boolean magical;
        Plant(IBlockState state, String id, boolean magical) { this.state = state; this.id = id; this.magical = magical; }
    }

    public static List<Plant> resolve(ItemStack stack, World world, BlockPos pos) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        Item item = stack.getItem();
        try {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("agricraft") &&
                    (item.getClass().getName().equals("com.infinityraider.agricraft.items.ItemAgriSeed")
                    || stack.hasTagCompound() && (stack.getTagCompound().hasKey("agri_growth") || stack.getTagCompound().hasKey("agri_seed")))) {
                if (agriRegistry == null) {
                    agriRegistry = Class.forName("com.infinityraider.agricraft.api.v1.AgriApi").getMethod("getSeedRegistry");
                    agriSeed = Class.forName("com.infinityraider.agricraft.api.v1.adapter.IAgriAdapterizer").getMethod("valueOf", Object.class);
                }
                Optional<?> seed = (Optional<?>) agriSeed.invoke(agriRegistry.invoke(null), stack.copy());
                if (!seed.isPresent()) return Collections.emptyList();
                Object plant = PlantGrowthHooks.call(seed.get(), "getPlant");
                ItemStack underlying = (ItemStack) PlantGrowthHooks.call(plant, "getSeed");
                Block crop = Block.REGISTRY.getObject(new ResourceLocation("agricraft:crop"));
                if (crop == null || crop == Blocks.AIR) return Collections.emptyList();
                return Collections.singletonList(new Plant(crop.getDefaultState(), "agricraft:" + PlantGrowthHooks.call(plant, "getId"),
                        underlying != null && magical(underlying.getItem())));
            }
            if (item instanceof IPlantable) {
                IBlockState state = ((IPlantable) item).getPlant(world, pos);
                if (plant(state)) return Collections.singletonList(of(state));
            }
            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).getBlock();
                IBlockState state = block.getStateFromMeta(item.getMetadata(stack.getMetadata()));
                if (plant(state)) return Collections.singletonList(of(state));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError invalid) {
            warn(item.getClass(), invalid);
            return Collections.emptyList();
        }
        if (produce == null) buildProduce();
        Map<Integer, List<Plant>> variants = produce.get(item);
        return variants == null ? Collections.emptyList() : variants.getOrDefault(stack.getMetadata(), Collections.emptyList());
    }

    private static boolean plant(IBlockState state) {
        return state != null && (state.getBlock() instanceof IPlantable || state.getBlock() instanceof IGrowable);
    }
    private static Plant of(IBlockState state) {
        String id = state.getBlock().getRegistryName().toString();
        return new Plant(state, id, id.startsWith("mysticalagriculture:") || id.startsWith("mysticalagradditions:"));
    }
    private static boolean magical(Item item) {
        return item != null && item.getRegistryName() != null && (item.getRegistryName().getResourceDomain().equals("mysticalagriculture")
                || item.getRegistryName().getResourceDomain().equals("mysticalagradditions"));
    }

    private static void buildProduce() {
        Map<Item, Map<Integer, List<Plant>>> index = new IdentityHashMap<>();
        for (Block block : Block.REGISTRY) {
            if (block.getRegistryName() == null) continue;
            String domain = block.getRegistryName().getResourceDomain();
            if (!domain.equals("minecraft") && !domain.equals("harvestcraft") && !domain.equals("mysticalagriculture")) continue;
            IBlockState state = block.getDefaultState();
            if (!plant(state)) continue;
            IProperty age = block.getBlockState().getProperty("age");
            if (age == null || age.getValueClass() != Integer.class) continue;
            try {
                IBlockState mature = state.withProperty(age, (Comparable) Collections.max(age.getAllowedValues()));
                Item drop = block.getItemDropped(mature, new Random(0), 0);
                if (drop != null) add(index, drop, block.damageDropped(mature), of(state));
            } catch (RuntimeException | LinkageError invalid) { warn(block.getClass(), invalid); }
        }
        // Cocoa supplies its dye metadata through getDrops rather than getItemDropped.
        add(index, Items.DYE, 3, of(Blocks.COCOA.getDefaultState()));
        add(index, Items.MELON, 0, of(Blocks.MELON_STEM.getDefaultState()));
        add(index, Item.getItemFromBlock(Blocks.MELON_BLOCK), 0, of(Blocks.MELON_STEM.getDefaultState()));
        add(index, Item.getItemFromBlock(Blocks.PUMPKIN), 0, of(Blocks.PUMPKIN_STEM.getDefaultState()));
        produce = index;
    }
    private static void add(Map<Item, Map<Integer, List<Plant>>> index, Item item, int meta, Plant plant) {
        if (item != null && item != Items.AIR) index.computeIfAbsent(item, ignored -> new HashMap<>()).computeIfAbsent(meta, ignored -> new ArrayList<>()).add(plant);
    }
    private static void warn(Class<?> type, Throwable problem) {
        if (warned.add(type)) LogManager.getLogger("Sky's Realistic Weather").warn("Cannot resolve plant tooltip for {}", type.getName(), problem);
    }
}
