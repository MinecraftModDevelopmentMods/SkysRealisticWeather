package zone.moddev.mc.skysrealisticweather.instrument;

import java.util.OptionalDouble;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.ShapedOreRecipe;
import zone.moddev.mc.skysrealisticweather.BuildInfo;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay;

/** A passive, recoverable instrument. No ticking tile entity or saved temperature is needed. */
public final class Thermometer extends BlockHorizontal {
    public static final PropertyBool WALL = PropertyBool.create("wall");
    public static final Thermometer BLOCK = new Thermometer();
    public static final String MERCURY = "ingotMercury", GLASS = "blockGlass";
    private Thermometer() {
        super(Material.CIRCUITS);
        setRegistryName(BuildInfo.ID, "thermometer"); setUnlocalizedName(BuildInfo.ID + ".thermometer");
        setHardness(.3F); setResistance(1); setSoundType(SoundType.GLASS); setCreativeTab(CreativeTabs.TOOLS);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(WALL, false));
    }
    public static void register() {
        GameRegistry.register(BLOCK);
        GameRegistry.register(new ThermometerItem().setRegistryName(BLOCK.getRegistryName()));
        // String ore entries remain live, including providers that register after this recipe.
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(BLOCK), "G", "G", "M", 'G', GLASS, 'M', MERCURY));
    }
    public static boolean heldBy(EntityLivingBase entity) {
        return entity != null && (isThermometer(entity.getHeldItemMainhand()) || isThermometer(entity.getHeldItemOffhand()));
    }
    private static boolean isThermometer(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() == Item.getItemFromBlock(BLOCK); }
    public static OptionalDouble temperature(World world, BlockPos pos) {
        if (world == null || !world.isBlockLoaded(pos, false)) return OptionalDouble.empty();
        OptionalDouble weather = WeatherApi.getLocalTemperature(world, pos);
        double value = weather.isPresent() ? weather.getAsDouble() : world.getBiome(pos).getFloatTemperature(pos);
        return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
    }
    /** The numeric reading is never clamped; only the small -40..60 Celsius gauge saturates. */
    public static float gauge(float mc) {
        return (float) Math.max(0, Math.min(1, (TemperatureDisplay.Unit.CELSIUS.temperature(mc) + 40) / 100));
    }
    private static void read(World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote) return;
        OptionalDouble value = temperature(world, pos);
        if (value.isPresent()) player.sendMessage(new TextComponentString("Temperature: " + TemperatureDisplay.forSender(player).format((float) value.getAsDouble())));
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                               EnumFacing side, float x, float y, float z) {
        read(world, pos, player); return true;
    }
    private static boolean support(World world, BlockPos pos, EnumFacing towardSupport) {
        BlockPos support = pos.offset(towardSupport);
        return world.isBlockLoaded(support, false) && world.getBlockState(support).isSideSolid(world, support, towardSupport.getOpposite());
    }
    @Override public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return side != EnumFacing.DOWN && support(world, pos, side.getOpposite());
    }
    @Override public boolean canPlaceBlockAt(World world, BlockPos pos) {
        if (!super.canPlaceBlockAt(world, pos)) return false;
        for (EnumFacing side : EnumFacing.values()) if (side != EnumFacing.DOWN && canPlaceBlockOnSide(world, pos, side)) return true;
        return false;
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z,
                                                     int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(WALL, side.getAxis().isHorizontal())
                .withProperty(FACING, side.getAxis().isHorizontal() ? side : placer.getHorizontalFacing().getOpposite());
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighbor, BlockPos fromPos) {
        EnumFacing direction = state.getValue(WALL) ? state.getValue(FACING).getOpposite() : EnumFacing.DOWN;
        if (!support(world, pos, direction)) { dropBlockAsItem(world, pos, state, 0); world.setBlockToAir(pos); }
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, WALL); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex() | (state.getValue(WALL) ? 4 : 0); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)).withProperty(WALL, (meta & 4) != 0); }
    @Override public int damageDropped(IBlockState state) { return 0; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return NULL_AABB; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing facing = state.getValue(FACING);
        if (!state.getValue(WALL)) return facing.getAxis() == EnumFacing.Axis.Z
                ? new AxisAlignedBB(.25, 0, .3125, .75, .9375, .6875) : new AxisAlignedBB(.3125, 0, .25, .6875, .9375, .75);
        switch (facing) {
            case SOUTH: return new AxisAlignedBB(.3125, .0625, 0, .6875, .9375, .1875);
            case EAST: return new AxisAlignedBB(0, .0625, .3125, .1875, .9375, .6875);
            case WEST: return new AxisAlignedBB(.8125, .0625, .3125, 1, .9375, .6875);
            default: return new AxisAlignedBB(.3125, .0625, .8125, .6875, .9375, 1);
        }
    }
    private static final class ThermometerItem extends ItemBlock {
        ThermometerItem() { super(BLOCK); }
        @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
            read(world, zone.moddev.mc.skysrealisticweather.game.ClimateReadout.eyes(player), player);
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }
    }
}
