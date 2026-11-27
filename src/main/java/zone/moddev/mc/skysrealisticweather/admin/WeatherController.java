package zone.moddev.mc.skysrealisticweather.admin;

import net.minecraft.util.NonNullList;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import zone.moddev.mc.skysrealisticweather.BuildInfo;
import zone.moddev.mc.skysrealisticweather.WeatherMod;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;

public final class WeatherController extends Block {
    public static final WeatherController BLOCK = new WeatherController();
    private static final CreativeTabs ADMIN = new CreativeTabs("skysweatheradmin") {
        @Override
        public ItemStack getTabIconItem() {
            return new ItemStack(BLOCK);
        }
    };

    private WeatherController() {
        super(Material.IRON);
        setRegistryName(BuildInfo.ID, "weather_controller");
        setUnlocalizedName(BuildInfo.ID + ".weather_controller");
        setHardness(3F);
        setResistance(12F);
        setSoundType(SoundType.METAL);
    }

    public static void register() {
        BLOCK.setCreativeTab(ADMIN);
        GameRegistry.register(BLOCK);
        GameRegistry.register(new ControllerItem().setRegistryName(BLOCK.getRegistryName()));
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, NonNullList<ItemStack> items) {
        if (WeatherMod.proxy.showAdminItem()) super.getSubBlocks(item, tab, items);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand == EnumHand.MAIN_HAND && !world.isRemote && player instanceof EntityPlayerMP) {
            WeatherCommand.open((EntityPlayerMP) player);
        }
        return true;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    private static final class ControllerItem extends ItemBlock {
        ControllerItem() {
            super(BLOCK);
        }

        @Override
        public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                         EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
            if (!WeatherCommand.permitted(player) || !WeatherState.supports(world)) {
                if (!world.isRemote) player.sendMessage(new TextComponentString(
                        "Only operators can place the Weather Controller in the Overworld."));
                return EnumActionResult.FAIL;
            }
            return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
        }
    }
}
