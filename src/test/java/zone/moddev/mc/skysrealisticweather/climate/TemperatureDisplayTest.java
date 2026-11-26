package zone.moddev.mc.skysrealisticweather.climate;

import io.netty.buffer.*;
import net.minecraftforge.common.config.Property;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.client.TemperatureConfigElement;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit;
import zone.moddev.mc.skysrealisticweather.network.TemperaturePreferenceMessage;
import static org.junit.jupiter.api.Assertions.*;

class TemperatureDisplayTest {
    @Test
    void absoluteAndAdjustmentConversionsRespectFreezingAndNeverChangeRawConfigOnAnUneditedSave() {
        assertEquals("0.0 °C", Unit.CELSIUS.format(.15F)); assertEquals("32.0 °F", Unit.FAHRENHEIT.format(.15F));
        assertEquals("14.4 °C", Unit.CELSIUS.format(.8F)); assertEquals("58.0 °F", Unit.FAHRENHEIT.format(.8F));
        assertEquals(18, Unit.FAHRENHEIT.adjustment(.45), 0.00001);
        for (Unit unit : Unit.values()) {
            for (float mc : new float[] {-2, 0, .15F, .8F, 2}) {
                assertEquals(mc, unit.fromTemperature(unit.temperature(mc)), 0.000001);
                assertEquals(mc, unit.fromAdjustment(unit.adjustment(mc)), 0.000001);
            }
            Property offset = new Property("temperature", "0.12345679", Property.Type.DOUBLE).setMinValue(-2).setMaxValue(2);
            TemperatureConfigElement element = new TemperatureConfigElement(offset, unit, false);
            element.set(element.get()); assertEquals("0.12345679", offset.getString());
            element.set(Float.toString((float) unit.adjustment(.45)));
            assertEquals(.45, offset.getDouble(), 0.000001);
            Property profiles = new Property("profiles", new String[] {"cool,0.05,0.4,1.1,1.7", "invalid row"}, Property.Type.STRING);
            TemperatureConfigElement rows = new TemperatureConfigElement(profiles, unit, true);
            Object[] displayed = rows.getList(); assertEquals("invalid row", displayed[1]);
            rows.set(displayed); assertEquals("cool,0.05,0.4,1.1,1.7", profiles.getStringList()[0]);
            displayed[0] = "custom," + (float) unit.temperature(0) + "," + (float) unit.temperature(.5) + "," + (float) unit.temperature(1) + "," + (float) unit.temperature(2);
            rows.set(displayed);
            String[] data = profiles.getStringList()[0].split(",");
            assertEquals(.5, Float.parseFloat(data[2]), .000001);
        }
    }

    @Test
    void preferencePacketIsBoundedToTheTwoDisplayChoices() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new TemperaturePreferenceMessage(Unit.FAHRENHEIT).toBytes(buffer);
            TemperaturePreferenceMessage read = new TemperaturePreferenceMessage(); read.fromBytes(buffer);
            assertEquals(Unit.FAHRENHEIT, read.unit); assertEquals(0, buffer.readableBytes());
            buffer.clear().writeByte(255);
            assertThrows(IllegalArgumentException.class, () -> read.fromBytes(buffer));
        } finally { buffer.release(); }
    }
}
