package zone.moddev.mc.skysrealisticweather.game;

import java.util.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import zone.moddev.mc.skysrealisticweather.api.TemperatureZone;

/** Chunk/section buckets keep lookups independent of the number of zones elsewhere in the world. */
public final class TemperatureZoneIndex {
    public static final class Entry {
        public final TemperatureZone zone;
        public final BlockPos furnace;
        public Entry(TemperatureZone zone, BlockPos furnace) { this.zone=zone;this.furnace=furnace==null?null:furnace.toImmutable(); }
    }
    private static final Comparator<Entry> ORDER = Comparator.<Entry>comparingInt(e -> e.zone.priority).reversed().thenComparing(e -> e.zone.id.toString());
    private final Map<ResourceLocation,Entry> entries = new HashMap<>();
    private final Map<Long,List<Entry>[]> chunks = new HashMap<>();
    private final Map<Long,Integer> counts = new HashMap<>();
    public final Set<Long> dirty = new LinkedHashSet<>();

    public boolean put(Entry entry) {
        Entry previous=entries.get(entry.zone.id);
        if(previous!=null && previous.zone.equals(entry.zone) && Objects.equals(previous.furnace,entry.furnace))return false;
        TemperatureZone z=entry.zone;
        for(int x=z.minimum.getX()>>4;x<=z.maximum.getX()>>4;x++)for(int k=z.minimum.getZ()>>4;k<=z.maximum.getZ()>>4;k++) {
            boolean replacing=previous!=null&&x>=(previous.zone.minimum.getX()>>4)&&x<=(previous.zone.maximum.getX()>>4)
                    &&k>=(previous.zone.minimum.getZ()>>4)&&k<=(previous.zone.maximum.getZ()>>4);
            if(counts.getOrDefault(ChunkPos.asLong(x,k),0)>=4096&&!replacing)throw new IllegalArgumentException("At most 4096 temperature zones may intersect one chunk");
        }
        remove(entry.zone.id);entries.put(entry.zone.id,entry);index(entry,true);return true;
    }
    public boolean remove(ResourceLocation id) {
        Entry previous=entries.remove(id);if(previous==null)return false;index(previous,false);return true;
    }
    @SuppressWarnings("unchecked")
    private void index(Entry entry,boolean add) {
        TemperatureZone z=entry.zone;
        for(int x=z.minimum.getX()>>4;x<=z.maximum.getX()>>4;x++)for(int k=z.minimum.getZ()>>4;k<=z.maximum.getZ()>>4;k++) {
            long key=ChunkPos.asLong(x,k);List<Entry>[] sections=chunks.get(key);
            if(sections==null){if(!add)continue;sections=new List[16];chunks.put(key,sections);}
            for(int y=z.minimum.getY()>>4;y<=z.maximum.getY()>>4;y++) {
                List<Entry> list=sections[y];
                if(add){if(list==null)sections[y]=list=new ArrayList<>();list.add(entry);list.sort(ORDER);}
                else if(list!=null){list.remove(entry);if(list.isEmpty())sections[y]=null;}
            }
            if(Arrays.stream(sections).allMatch(Objects::isNull))chunks.remove(key);
            int count=counts.getOrDefault(key,0)+(add?1:-1);if(count<=0)counts.remove(key);else counts.put(key,count);
            dirty.add(key);
        }
    }
    public List<Entry> at(BlockPos position) {
        if(position.getY()<0||position.getY()>255)return Collections.emptyList();
        List<Entry>[] sections=chunks.get(ChunkPos.asLong(position.getX()>>4,position.getZ()>>4));
        List<Entry> list=sections==null?null:sections[position.getY()>>4];return list==null?Collections.emptyList():list;
    }
    public List<Entry> snapshot(int x,int z) {
        List<Entry>[] sections=chunks.get(ChunkPos.asLong(x,z));
        if(sections==null)return Collections.emptyList();
        Set<Entry> found=new LinkedHashSet<>();for(List<Entry> list:sections)if(list!=null)found.addAll(list);
        return new ArrayList<>(found);
    }
    @SuppressWarnings("unchecked")
    public void receive(int x,int z,List<Entry> entries) {
        long key=ChunkPos.asLong(x,z);
        if(entries.isEmpty()){chunks.remove(key);counts.remove(key);return;}
        List<Entry>[] sections=new List[16];
        for(Entry e:entries)for(int y=e.zone.minimum.getY()>>4;y<=e.zone.maximum.getY()>>4;y++) {
            if(sections[y]==null)sections[y]=new ArrayList<>();sections[y].add(e);
        }
        for(List<Entry> list:sections)if(list!=null)list.sort(ORDER);chunks.put(key,sections);counts.put(key,entries.size());
    }
}
