package zone.moddev.mc.skysrealisticweather.game;

import io.netty.buffer.*;
import java.util.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.TemperatureZone;
import zone.moddev.mc.skysrealisticweather.network.TemperatureZoneMessage;
import static org.junit.jupiter.api.Assertions.*;

final class TemperatureZoneTest {
    @Test void furnaceEditorUsesPersonalDisplayUnits() {
        net.minecraftforge.common.config.Property property=new net.minecraftforge.common.config.Property("furnaceBonusCelsius","20",net.minecraftforge.common.config.Property.Type.DOUBLE);
        property.setMinValue(0);property.setMaxValue(100);
        zone.moddev.mc.skysrealisticweather.client.TemperatureConfigElement editor=new zone.moddev.mc.skysrealisticweather.client.TemperatureConfigElement(property,TemperatureDisplay.Unit.FAHRENHEIT,false,true);
        assertEquals("36.0",editor.get());editor.set("45.0");assertEquals(25,property.getDouble(),.00001);
        assertTrue(editor.getName().contains("bonus (Δ"));assertEquals(0.0,Double.parseDouble(editor.getMinValue().toString()));
        for(String source:new String[]{"fire","lava","torch"}) {
            net.minecraftforge.common.config.Property heat=new net.minecraftforge.common.config.Property(source+"BonusCelsius","4",net.minecraftforge.common.config.Property.Type.DOUBLE);
            zone.moddev.mc.skysrealisticweather.client.TemperatureConfigElement field=new zone.moddev.mc.skysrealisticweather.client.TemperatureConfigElement(heat,TemperatureDisplay.Unit.FAHRENHEIT,false,true);
            assertEquals("7.2",field.get());field.set("18");assertEquals(10,heat.getDouble(),.00001);
            assertTrue(field.getName().startsWith(Character.toUpperCase(source.charAt(0))+source.substring(1)));
        }
    }
    @Test void furnaceBonusFallsOffSphericallyAndSurvivesChunkSnapshots() {
        BlockPos source=new BlockPos(15,81,8);
        TemperatureZone heat=new TemperatureZone(new ResourceLocation("skysrealisticweather:furnace/test"),source.add(-10,-10,-10),source.add(10,10,10),.9F,-100,TemperatureZone.Mode.MINIMUM);
        TemperatureZoneIndex.Entry entry=new TemperatureZoneIndex.Entry(heat,source);
        assertEquals(20,TemperatureDisplay.Unit.CELSIUS.adjustment(entry.furnaceBonus(source)),.0001);
        assertEquals(18,TemperatureDisplay.Unit.CELSIUS.adjustment(entry.furnaceBonus(source.east())),.0001);
        assertEquals(16,TemperatureDisplay.Unit.CELSIUS.adjustment(entry.furnaceBonus(source.east(2))),.0001);
        assertEquals(entry.furnaceBonus(source.east(2)),entry.furnaceBonus(source.up(2)),.00001);
        assertEquals(0,entry.furnaceBonus(source.add(8,6,0)));assertEquals(0,entry.furnaceBonus(source.add(10,10,10)));
        assertTrue(entry.furnaceBonus(source.add(1,1,1))<entry.furnaceBonus(source.east()));
        TemperatureZoneMessage message=new TemperatureZoneMessage(0,1,0,true,Collections.singletonList(entry));
        message.blockHeat=new BlockHeatSources.Settings(3,.4F,31,4F,0,0);
        ByteBuf bytes=Unpooled.buffer();try {
            message.toBytes(bytes);TemperatureZoneMessage decoded=new TemperatureZoneMessage();decoded.fromBytes(bytes);
            assertEquals(entry.furnaceBonus(source.east(2)),decoded.entries.get(0).furnaceBonus(source.east(2)));
            ByteBuf settings=Unpooled.buffer();try {
                decoded.blockHeat.write(settings);
                assertEquals(3,settings.readUnsignedByte());assertEquals(.4F,settings.readFloat());
                assertEquals(31,settings.readUnsignedByte());assertEquals(4F,settings.readFloat());
                assertEquals(0,settings.readUnsignedByte());assertEquals(0,settings.readFloat());
            } finally {settings.release();}
        } finally {bytes.release();}
    }
    @Test void untrustedBlockHeatSettingsAreValidated() {
        assertThrows(IllegalArgumentException.class,()->new BlockHeatSources.Settings(33,1,1,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new BlockHeatSources.Settings(1,Float.NaN,1,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new BlockHeatSources.Settings(1,1,1,-1,1,1));
        assertThrows(IllegalArgumentException.class,()->new BlockHeatSources.Settings(1,1,1,1,1,5));
    }
    private TemperatureZone zone(String id,int priority,int y) {
        return new TemperatureZone(new ResourceLocation("test",id),new BlockPos(-1,y,0),new BlockPos(17,y+2,2),1,priority,TemperatureZone.Mode.OVERRIDE);
    }
    @Test void inclusiveBoundsAndValidation() {
        TemperatureZone zone=TemperatureZone.celsius(new ResourceLocation("test:room"),new BlockPos(2,3,4),new BlockPos(-1,1,0),20);
        assertTrue(zone.contains(new BlockPos(-1,1,0)));assertTrue(zone.contains(new BlockPos(2,3,4)));
        assertFalse(zone.contains(new BlockPos(3,3,4)));assertEquals(1.05F,zone.temperature,.00001);
        assertThrows(IllegalArgumentException.class,()->zone("bad",0,255));
        assertThrows(IllegalArgumentException.class,()->new TemperatureZone(new ResourceLocation("test:large"),new BlockPos(0,0,0),new BlockPos(20000,255,20000),1,0,TemperatureZone.Mode.OVERRIDE));
        assertThrows(IllegalArgumentException.class,()->new TemperatureZone(new ResourceLocation("test:nan"),BlockPos.ORIGIN,BlockPos.ORIGIN,Float.NaN,0,TemperatureZone.Mode.OVERRIDE));
    }
    @Test void priorityReplacementAndNegativeChunkSectionIndex() {
        TemperatureZoneIndex index=new TemperatureZoneIndex();TemperatureZone a=zone("a",0,15),b=zone("b",0,15),high=zone("high",9,15);
        assertTrue(index.put(new TemperatureZoneIndex.Entry(b,null)));index.put(new TemperatureZoneIndex.Entry(a,null));
        assertEquals(a.id,index.at(new BlockPos(-1,16,1)).get(0).zone.id);
        assertTrue(index.at(new BlockPos(-1,40,1)).isEmpty());
        index.put(new TemperatureZoneIndex.Entry(high,null));assertEquals(high.id,index.at(new BlockPos(16,16,1)).get(0).zone.id);
        assertFalse(index.put(new TemperatureZoneIndex.Entry(high,null)));
        index.put(new TemperatureZoneIndex.Entry(zone("high",9,40),null));assertEquals(a.id,index.at(new BlockPos(-1,16,1)).get(0).zone.id);
        assertEquals(high.id,index.at(new BlockPos(-1,41,1)).get(0).zone.id);assertTrue(index.remove(high.id));assertFalse(index.remove(high.id));
    }
    @Test void chunkSnapshotCodecClearsAndRejectsMalformedCounts() {
        TemperatureZoneIndex server=new TemperatureZoneIndex();TemperatureZone zone=zone("room",3,15);
        server.put(new TemperatureZoneIndex.Entry(zone,new BlockPos(0,15,1)));
        TemperatureZoneMessage message=new TemperatureZoneMessage(0,-1,0,true,server.snapshot(-1,0));
        ByteBuf bytes=Unpooled.buffer();try {
            message.toBytes(bytes);TemperatureZoneMessage decoded=new TemperatureZoneMessage();decoded.fromBytes(bytes);
            assertEquals(zone,decoded.entries.get(0).zone);assertEquals(new BlockPos(0,15,1),decoded.entries.get(0).furnace);
            TemperatureZoneIndex client=new TemperatureZoneIndex();client.receive(decoded.x,decoded.z,decoded.entries);
            assertEquals(zone,client.at(new BlockPos(-1,16,1)).get(0).zone);
            assertTrue(client.at(new BlockPos(16,16,1)).isEmpty());
            client.receive(-1,0,Collections.emptyList());assertTrue(client.at(new BlockPos(-1,16,1)).isEmpty());
            bytes.clear();bytes.writeInt(0).writeInt(0).writeInt(0).writeBoolean(true).writeInt(4097);
            assertThrows(IllegalArgumentException.class,()->new TemperatureZoneMessage().fromBytes(bytes));
        } finally {bytes.release();}
    }
}
