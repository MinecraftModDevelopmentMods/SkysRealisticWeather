package zone.moddev.mc.skysrealisticweather.game;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.api.SeasonalSurfaceEvent;
import zone.moddev.mc.skysrealisticweather.api.SeasonalSurfaceEvent.Action;

public final class SurfaceTracker {
    public static final SurfaceTracker INSTANCE = new SurfaceTracker();
    private static final String KEY = "skysrealisticweather:surface_v1";
    private final Map<Chunk, Int2ObjectOpenHashMap<Deposit>> deposits = new WeakHashMap<>();

    private SurfaceTracker() {
    }

    public boolean place(WorldServer world, BlockPos position, IBlockState proposed) {
        return place(world, position, proposed, 3);
    }

    boolean place(WorldServer world, BlockPos position, IBlockState proposed, int flags) {
        IBlockState original = world.getBlockState(position);
        Deposit deposit;
        Action action;
        if (proposed.getBlock() == Blocks.SNOW_LAYER) {
            if (!original.getBlock().isAir(original, world, position)) return false;
            deposit = Deposit.SNOW;
            action = Action.ACCUMULATE_SNOW;
        } else {
            if (original.getBlock() != Blocks.WATER && original.getBlock() != Blocks.FLOWING_WATER) return false;
            if (original.getValue(BlockLiquid.LEVEL) != 0) return false;
            deposit = original.getBlock() == Blocks.WATER ? Deposit.WATER : Deposit.FLOWING_WATER;
            action = Action.FREEZE_WATER;
        }
        if (!change(world, position, original, proposed, action, flags)) return false;
        if (world.getBlockState(position) == proposed) {
            Chunk chunk = world.getChunkFromBlockCoords(position);
            synchronized (this) {
                deposits.computeIfAbsent(chunk, unused -> new Int2ObjectOpenHashMap<>()).put(localIndex(position), deposit);
            }
            chunk.markDirty();
        }
        return true;
    }

    public void thaw(WorldServer world, BlockPos position, WeatherState.Frame frame) {
        Chunk chunk = world.getChunkProvider().getLoadedChunk(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return;
        Deposit deposit;
        synchronized (this) {
            Int2ObjectOpenHashMap<Deposit> entries = deposits.get(chunk);
            deposit = entries == null ? null : entries.get(localIndex(position));
        }
        if (deposit == null) return;
        IBlockState original = world.getBlockState(position);
        if (original != deposit.placed()) {
            changed(chunk, position);
            return;
        }
        // A little hysteresis keeps a near-freezing surface from oscillating on successive ticks.
        BlockPos test = deposit == Deposit.SNOW ? position : position.up();
        if (!world.canSeeSky(test) || !world.getWorldBorder().contains(position)
                || frame.temperature(world.getBiome(test), test) < WeatherState.SNOW_LINE + 0.04F) return;
        change(world, position, original, deposit.restore(), deposit == Deposit.SNOW ? Action.THAW_SNOW : Action.THAW_ICE, 3);
    }

    private static boolean change(WorldServer world, BlockPos position, IBlockState original,
                                  IBlockState proposed, Action action, int flags) {
        if (MinecraftForge.EVENT_BUS.post(new SeasonalSurfaceEvent(world, position, original, proposed, action))) {
            return false;
        }
        return world.getBlockState(position) == original && world.setBlockState(position, proposed, flags);
    }

    public synchronized void changed(Chunk chunk, BlockPos position) {
        Int2ObjectOpenHashMap<Deposit> entries = deposits.get(chunk);
        if (entries != null && entries.remove(localIndex(position)) != null) {
            if (entries.isEmpty()) deposits.remove(chunk);
            chunk.markDirty();
        }
    }

    @SubscribeEvent
    public synchronized void load(ChunkDataEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || !WeatherState.supports(world) || !event.getData().hasKey(KEY, 9)) return;
        Chunk chunk = event.getChunk();
        NBTTagList list = event.getData().getTagList(KEY, 10);
        Int2ObjectOpenHashMap<Deposit> entries = new Int2ObjectOpenHashMap<>();
        for (int index = 0; index < Math.min(list.tagCount(), 65536); index++) {
            NBTTagCompound row = list.getCompoundTagAt(index);
            if (!row.hasKey("position", 4) || !row.hasKey("deposit", 1)) continue;
            BlockPos position = BlockPos.fromLong(row.getLong("position"));
            int type = row.getByte("deposit");
            if (position.getX() >> 4 != chunk.x || position.getZ() >> 4 != chunk.z
                    || position.getY() < 0 || position.getY() >= 256 || type < 0 || type >= Deposit.ALL.length) continue;
            Deposit deposit = Deposit.ALL[type];
            if (chunk.getBlockState(position) == deposit.placed()) entries.put(localIndex(position), deposit);
        }
        if (!entries.isEmpty()) deposits.put(chunk, entries);
    }

    @SubscribeEvent
    public synchronized void save(ChunkDataEvent.Save event) {
        Chunk chunk = event.getChunk();
        Int2ObjectOpenHashMap<Deposit> entries = deposits.get(chunk);
        if (entries == null) return;
        NBTTagList list = new NBTTagList();
        for (Int2ObjectMap.Entry<Deposit> entry : entries.int2ObjectEntrySet()) {
            int index = entry.getIntKey();
            BlockPos position = new BlockPos((chunk.x << 4) + (index & 15), index >> 8,
                    (chunk.z << 4) + (index >> 4 & 15));
            NBTTagCompound row = new NBTTagCompound();
            row.setLong("position", position.toLong());
            row.setByte("deposit", (byte) entry.getValue().ordinal());
            list.appendTag(row);
        }
        event.getData().setTag(KEY, list);
    }

    private static int localIndex(BlockPos position) {
        return position.getY() << 8 | (position.getZ() & 15) << 4 | (position.getX() & 15);
    }

    private enum Deposit {
        SNOW, WATER, FLOWING_WATER;

        private static final Deposit[] ALL = values();

        IBlockState placed() {
            return this == SNOW ? Blocks.SNOW_LAYER.getDefaultState() : Blocks.ICE.getDefaultState();
        }

        IBlockState restore() {
            switch (this) {
                case SNOW: return Blocks.AIR.getDefaultState();
                case WATER: return Blocks.WATER.getDefaultState();
                case FLOWING_WATER: return Blocks.FLOWING_WATER.getDefaultState();
                default: throw new IllegalStateException();
            }
        }
    }
}
