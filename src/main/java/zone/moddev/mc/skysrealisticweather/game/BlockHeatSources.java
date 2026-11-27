package zone.moddev.mc.skysrealisticweather.game;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import zone.moddev.mc.skysrealisticweather.api.TemperatureSample;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;

/** Lazy, loaded-section heat lookup. Values never retain the weak section keys or their worlds. */
public final class BlockHeatSources {
    private static final Field PALETTE = paletteField();
    private static final short[] EMPTY = new short[0];
    private final Map<BlockStateContainer,Snapshot> sections = new WeakHashMap<>();
    private static Field paletteField() {
        try { return ReflectionHelper.findField(BlockStateContainer.class,"palette","field_186022_c"); }
        catch (RuntimeException unavailable) { return null; } // Only an optimization; scanning remains correct.
    }
    public enum Kind {
        FIRE(TemperatureSample.Source.FIRE), LAVA(TemperatureSample.Source.LAVA), TORCH(TemperatureSample.Source.TORCH);
        final TemperatureSample.Source source;
        Kind(TemperatureSample.Source source) { this.source=source; }
    }
    private static final Kind[] KINDS=Kind.values();
    private static int kind(Block block) {
        return block==Blocks.FIRE?0:block==Blocks.LAVA||block==Blocks.FLOWING_LAVA?1:block==Blocks.TORCH?2:-1;
    }
    static boolean typeChanged(IBlockState previous,IBlockState current) {
        return previous.getBlock()!=current.getBlock()&&kind(previous.getBlock())!=kind(current.getBlock());
    }
    /** Server-authoritative radii and MC-unit temperature differences, carried with watched chunks. */
    public static final class Settings {
        private final int[] radius;
        private final float[] bonus;
        final int maximum;
        public Settings(int fireRadius,float fireBonus,int lavaRadius,float lavaBonus,int torchRadius,float torchBonus) {
            radius=new int[]{fireRadius,lavaRadius,torchRadius};bonus=new float[]{fireBonus,lavaBonus,torchBonus};
            int max=0;
            for(int i=0;i<3;i++) {
                if(radius[i]<0||radius[i]>32||!Float.isFinite(bonus[i])||bonus[i]<0||bonus[i]>4.501F)
                    throw new IllegalArgumentException("Invalid block heat settings");
                if(bonus[i]>0)max=Math.max(max,radius[i]);
            }
            maximum=max;
        }
        public static Settings configured() { return new Settings(
                WeatherConfiguration.fireHeatRadius(),WeatherConfiguration.fireHeatBonus(),
                WeatherConfiguration.lavaHeatRadius(),WeatherConfiguration.lavaHeatBonus(),
                WeatherConfiguration.torchHeatRadius(),WeatherConfiguration.torchHeatBonus()); }
        public void write(ByteBuf out) { for(int i=0;i<3;i++){out.writeByte(radius[i]);out.writeFloat(bonus[i]);} }
        public static Settings read(ByteBuf in) { return new Settings(in.readUnsignedByte(),in.readFloat(),in.readUnsignedByte(),in.readFloat(),in.readUnsignedByte(),in.readFloat()); }
    }
    public static final class Heat {
        public final float bonus;
        public final TemperatureSample.Source source;
        public final BlockPos position;
        Heat(float bonus,Kind kind,BlockPos position) { this.bonus=bonus;this.source=kind.source;this.position=position; }
    }
    private static final class Snapshot {
        final long tick;
        final short[] sources;
        Snapshot(long tick,short[] sources) { this.tick=tick;this.sources=sources; }
    }
    /** Called by the existing committed block-change hook, on either logical side. */
    void changed(Chunk chunk,BlockPos pos) {
        int y=pos.getY();if(y<0||y>255)return;
        ExtendedBlockStorage section=chunk.getBlockStorageArray()[y>>4];
        if(section!=null)sections.remove(section.getData());
    }
    private Snapshot snapshot(BlockStateContainer data,long tick) {
        Snapshot cached=sections.get(data);
        if(cached!=null&&tick>=cached.tick&&tick-cached.tick<20)return cached;
        short[] sources=EMPTY;
        if(mayContainHeat(data)) {
            short[] found=new short[4096];int size=0;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                int kind=kind(data.get(x,y,z).getBlock());
                if(kind>=0)found[size++]=(short)((kind<<12)|(y<<8)|(z<<4)|x);
            }
            if(size!=0)sources=Arrays.copyOf(found,size);
        }
        Snapshot result=new Snapshot(tick,sources);sections.put(data,result);return result;
    }
    private static boolean mayContainHeat(BlockStateContainer data) {
        if(PALETTE==null)return true;
        try {
            IBlockStatePalette palette=(IBlockStatePalette)PALETTE.get(data);
            // Registry palettes can have sparse IDs. Only the two compact, contiguous vanilla palettes are filtered.
            if(!(palette instanceof BlockStatePaletteLinear)&&!(palette instanceof BlockStatePaletteHashMap))return true;
            for(int i=0;i<256;i++) {
                IBlockState state=palette.getBlockState(i);
                if(state==null)return false;
                if(kind(state.getBlock())>=0)return true;
            }
            return true;
        } catch(IllegalAccessException unavailable) { return true; }
    }
    private static int distanceTo(int p,int min) { return p<min?min-p:p>min+15?p-min-15:0; }
    public Heat sample(World world,BlockPos pos,Settings settings) {
        if(settings.maximum==0)return null;
        int radius=settings.maximum,px=pos.getX(),py=pos.getY(),pz=pos.getZ();
        int[] nearest=new int[3];BlockPos[] positions=new BlockPos[3];
        for(int i=0;i<3;i++)nearest[i]=settings.bonus[i]>0?settings.radius[i]*settings.radius[i]:0;
        long tick=world.getTotalWorldTime();
        for(int x=(px-radius)>>4;x<=(px+radius)>>4;x++)for(int z=(pz-radius)>>4;z<=(pz+radius)>>4;z++) {
            Chunk chunk=world.getChunkProvider().getLoadedChunk(x,z);if(chunk==null)continue;
            int dx=distanceTo(px,x<<4),dz=distanceTo(pz,z<<4);
            for(int y=Math.max(0,py-radius)>>4;y<=Math.min(255,py+radius)>>4;y++) {
                int dy=distanceTo(py,y<<4),distance=dx*dx+dy*dy+dz*dz;
                if(distance>=nearest[0]&&distance>=nearest[1]&&distance>=nearest[2])continue;
                ExtendedBlockStorage section=chunk.getBlockStorageArray()[y];if(section==null||section.isEmpty())continue;
                for(short packed:snapshot(section.getData(),tick).sources) {
                    int kind=packed>>12;if(distance>=nearest[kind])continue;
                    int sx=(x<<4)+(packed&15),sy=(y<<4)+((packed>>8)&15),sz=(z<<4)+((packed>>4)&15);
                    int ox=px-sx,oy=py-sy,oz=pz-sz,d=ox*ox+oy*oy+oz*oz;
                    if(d<nearest[kind]){nearest[kind]=d;positions[kind]=new BlockPos(sx,sy,sz);}
                }
            }
        }
        Heat best=null;
        for(int i=0;i<3;i++)if(positions[i]!=null) {
            float bonus=settings.bonus[i]*(float)(1-Math.sqrt(nearest[i])/settings.radius[i]);
            if(best==null||bonus>best.bonus)best=new Heat(bonus,KINDS[i],positions[i]);
        }
        return best;
    }
}
