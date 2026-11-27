package zone.moddev.mc.skysrealisticweather.verify;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.game.*;
import zone.moddev.mc.skysrealisticweather.instrument.Thermometer;
import zone.moddev.mc.skysrealisticweather.network.TemperaturePreferenceMessage;

/** Real crafting, non-operator placement/use and process reload, without a desktop window. */
public final class ThermometerProbe {
    private static final Item MERCURY = new Item().setRegistryName("skyweatherprobe", "quicksilver_sample");
    private static final Item GLASS = new Item().setRegistryName("skyweatherprobe", "glass_sample");
    private static final BlockPos FLOOR = new BlockPos(8, 81, 8), WALL = new BlockPos(12, 81, 8);
    private static int checks;
    public static void register() { GameRegistry.register(MERCURY); GameRegistry.register(GLASS); }
    private static void require(boolean result, String name) { checks++; if (!result) throw new IllegalStateException(name); }
    public static void run(WorldServer world) {
        try {
            if (Boolean.getBoolean("thermometer.reload")) reload(world); else contracts(world);
            System.out.println("THERMOMETER_PASS checks=" + checks);
        } catch (Throwable failure) {
            System.out.println("THERMOMETER_FAILED " + failure); failure.printStackTrace();
        } finally { world.getMinecraftServer().initiateShutdown(); }
    }
    private static ItemStack craft(WorldServer world, ItemStack glass, ItemStack metal) {
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return true; }
        }, 3, 3);
        grid.setInventorySlotContents(1, glass.copy()); grid.setInventorySlotContents(4, glass.copy()); grid.setInventorySlotContents(7, metal.copy());
        return CraftingManager.getInstance().findMatchingRecipe(grid, world);
    }
    private static boolean thermometer(ItemStack stack) { return stack != null && stack.getItem() == Item.getItemFromBlock(Thermometer.BLOCK) && stack.getCount() == 1; }
    private static void contracts(WorldServer world) throws Exception {
        Block block = Thermometer.BLOCK; // Use the vanilla owner for inherited-method reobfuscation in this separate probe jar.
        Item item = Item.getItemFromBlock(Thermometer.BLOCK);
        require(item != null && Item.REGISTRY.getObject(Thermometer.BLOCK.getRegistryName()) == item, "survival tool registry");
        List<ItemStack> existing = new ArrayList<>(OreDictionary.getOres(Thermometer.MERCURY));
        if (Boolean.getBoolean("thermometer.provider")) require(Loader.isModLoaded("basemetals") && !existing.isEmpty(), "real Base Metals mercury provider");
        else require(existing.isEmpty(), "no mercury provider in standalone test");
        require(!thermometer(craft(world, new ItemStack(Blocks.GLASS), new ItemStack(Items.IRON_INGOT))), "iron cannot replace mercury");
        require(!thermometer(craft(world, new ItemStack(Blocks.GLASS), new ItemStack(MERCURY))), "untagged material cannot replace mercury");
        for (ItemStack mercury : existing) require(thermometer(craft(world, new ItemStack(Blocks.GLASS), mercury)), "registered mercury crafts thermometer");
        OreDictionary.registerOre(Thermometer.MERCURY, new ItemStack(MERCURY));
        OreDictionary.registerOre(Thermometer.GLASS, new ItemStack(GLASS));
        require(thermometer(craft(world, new ItemStack(Blocks.GLASS), new ItemStack(MERCURY))), "late mercury registration remains live");
        require(thermometer(craft(world, new ItemStack(GLASS), new ItemStack(MERCURY))), "foreign glass and mercury providers accepted");
        require(!thermometer(craft(world, new ItemStack(Blocks.DIRT), new ItemStack(MERCURY))), "glass required");
        world.getChunkFromBlockCoords(FLOOR);
        List<String> messages = new ArrayList<>();
        FakePlayer player = new FakePlayer(world, new com.mojang.authlib.GameProfile(UUID.fromString("3a7127d5-098c-4e48-9c14-2d563d2133f9"), "WeatherVisitor")) {
            @Override public void sendMessage(ITextComponent text) { messages.add(text.getUnformattedText()); }
        };
        player.setPosition(8.5, 81, 6.5); player.capabilities.isCreativeMode = false;
        require(!player.canUseCommand(2, "skyweather"), "ordinary non-operator fixture");
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(item));
        require(Thermometer.heldBy(player), "main hand enables instrument HUD");
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(item, 0));
        require(!Thermometer.heldBy(player), "consumed stack does not enable instrument HUD");
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY); player.setHeldItem(EnumHand.OFF_HAND, new ItemStack(item));
        require(Thermometer.heldBy(player), "offhand enables instrument HUD");
        player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY); player.inventory.setInventorySlotContents(3, new ItemStack(item));
        require(!Thermometer.heldBy(player), "inventory alone does not enable instrument HUD");
        world.setBlockState(FLOOR.down(), Blocks.STONE.getDefaultState(), 2); world.setBlockToAir(FLOOR);
        ItemStack held = new ItemStack(item, 2); player.setHeldItem(EnumHand.MAIN_HAND, held);
        require(item.onItemUse(player, world, FLOOR.down(), EnumHand.MAIN_HAND, EnumFacing.UP, .5F, 1, .5F) == EnumActionResult.SUCCESS,
                "non-operator places standing thermometer");
        require(held.getCount() == 1 && world.getBlockState(FLOOR).getBlock() == Thermometer.BLOCK && !world.getBlockState(FLOOR).getValue(Thermometer.WALL), "standing item consumed and correct state");
        world.setBlockState(WALL.south(), Blocks.STONE.getDefaultState(), 2); world.setBlockToAir(WALL);
        require(item.onItemUse(player, world, WALL.south(), EnumHand.MAIN_HAND, EnumFacing.NORTH, .5F, .5F, 0) == EnumActionResult.SUCCESS,
                "non-operator places wall thermometer");
        require(world.getBlockState(WALL).getValue(Thermometer.WALL) && world.getBlockState(WALL).getValue(BlockHorizontal.FACING) == EnumFacing.NORTH, "wall mount faces outward");
        require(!block.canPlaceBlockOnSide(world, WALL, EnumFacing.DOWN), "no ceiling placement");
        for (boolean wall : new boolean[] {false, true}) for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            IBlockState state = block.getDefaultState().withProperty(Thermometer.WALL, wall).withProperty(BlockHorizontal.FACING, facing);
            require(block.getStateFromMeta(block.getMetaFromState(state)) == state, "mount/facing metadata round trip");
            AxisAlignedBB box = state.getBoundingBox(world, FLOOR);
            require(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0 && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1, "selection box within block");
        }
        require(Thermometer.temperature(world, FLOOR).getAsDouble() == WeatherApi.getTemperature(world, FLOOR).getAsDouble(), "reading matches public weather API");
        for (TemperatureDisplay.Unit unit : TemperatureDisplay.Unit.values()) {
            new TemperaturePreferenceMessage(unit).apply(player); messages.clear();
            IBlockState state = world.getBlockState(FLOOR);
            require(block.onBlockActivated(world, FLOOR, state, player, EnumHand.MAIN_HAND, EnumFacing.NORTH, .5F, .5F, 0), "non-operator reads placed thermometer");
            require(messages.size() == 1 && messages.get(0).contains(unit.symbol), "placed reading follows user units");
            messages.clear();
            ItemStack hand = new ItemStack(item); player.setHeldItem(EnumHand.OFF_HAND, hand);
            require(item.onItemRightClick(world, player, EnumHand.OFF_HAND).getType() == EnumActionResult.SUCCESS && hand.getCount() == 1, "hand use does not consume item");
            require(messages.size() == 1 && messages.get(0).contains(unit.symbol), "hand reading follows user units");
        }
        List<ItemStack> drops = block.getDrops(world, FLOOR, world.getBlockState(FLOOR), 0);
        require(drops.size() == 1 && thermometer(drops.get(0)) && drops.get(0).getMetadata() == 0, "whole thermometer recoverable");
        world.setBlockToAir(WALL.south());
        require(world.isAirBlock(WALL), "wall thermometer drops when support removed");
        world.setBlockState(WALL.south(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(WALL, block.getDefaultState().withProperty(Thermometer.WALL, true), 2);
        require(world.getTileEntity(FLOOR) == null && world.getTileEntity(WALL) == null, "passive block has no ticking tile entity");
        BlockPos far = new BlockPos(800008, 81, 800008);
        require(!world.isBlockLoaded(far, false) && !Thermometer.temperature(world, far).isPresent() && !world.isBlockLoaded(far, false), "measurement never loads terrain");
        require(Math.abs(Thermometer.gauge(.15F) - .4F) < .00001 && Thermometer.gauge(-5) == 0 && Thermometer.gauge(5) == 1, "HUD gauge endpoints and freezing point");
        WorldServer nether = world.getMinecraftServer().getWorld(-1); nether.getChunkFromBlockCoords(FLOOR);
        require(!WeatherApi.getTemperature(nether, FLOOR).isPresent() && Thermometer.temperature(nether, FLOOR).getAsDouble() == nether.getBiome(FLOOR).getFloatTemperature(FLOOR), "native biome fallback outside weather dimensions");
        new File("thermometer").mkdirs();
        Files.write(new File("thermometer/saved.txt").toPath(), "standing and north wall".getBytes(StandardCharsets.UTF_8));
        world.getMinecraftServer().saveAllWorlds(false);
        System.out.println("THERMOMETER_RECIPES existingMercury=" + existing.size() + " BaseMetals=" + Loader.isModLoaded("basemetals"));
    }
    private static void reload(WorldServer world) {
        world.getChunkFromBlockCoords(FLOOR);
        require(world.getBlockState(FLOOR).getBlock() == Thermometer.BLOCK && !world.getBlockState(FLOOR).getValue(Thermometer.WALL), "standing thermometer survives physical reload");
        require(world.getBlockState(WALL).getBlock() == Thermometer.BLOCK && world.getBlockState(WALL).getValue(Thermometer.WALL), "wall thermometer survives physical reload");
        require(Thermometer.temperature(world, FLOOR).isPresent(), "live reading after reload");
    }
}
