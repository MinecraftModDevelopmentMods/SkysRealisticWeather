package zone.moddev.mc.skysrealisticweather.climate;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.PlantGrowthProfile;
import zone.moddev.mc.skysrealisticweather.network.WeatherMessage;
import static org.junit.jupiter.api.Assertions.*;

class PlantGrowthTest {
    @Test
    void curveHasColdHeatAndIdealRangesWithoutExtraGrowth() {
        PlantGrowthProfile p = new PlantGrowthProfile(0, 1, 2, 4);
        assertEquals(0, p.multiplier(-1)); assertEquals(0, p.multiplier(0));
        assertEquals(0.5F, p.multiplier(0.5F)); assertEquals(1, p.multiplier(1));
        assertEquals(1, p.multiplier(2)); assertEquals(0.5F, p.multiplier(3));
        assertEquals(0, p.multiplier(4)); assertEquals(0, p.multiplier(10));
        assertEquals(1, PlantGrowthProfile.EXEMPT.multiplier(-20));
        PlantGrowthProfile wide = new PlantGrowthProfile(-Float.MAX_VALUE, -1, 1, Float.MAX_VALUE);
        assertTrue(Float.isFinite(wide.multiplier(-Float.MAX_VALUE / 2)));
        assertThrows(IllegalArgumentException.class, () -> new PlantGrowthProfile(1, 1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new PlantGrowthProfile(0, 1, 2, Float.NaN));
    }

    @Test
    void editableProfilesAndInvalidRowsSurviveConfigRead() {
        Configuration config = new Configuration();
        FarmingSettings.read(config);
        String[] profiles = {"my_crop,0.2,0.7,1.3,2.0", "bad,NaN,1,2,3"};
        String[] mappings = {"example:crop=my_crop", "invalid selector"};
        config.getCategory(FarmingSettings.CATEGORY).get("profiles").set(profiles);
        config.getCategory(FarmingSettings.CATEGORY).get("mappings").set(mappings);
        FarmingSettings rules = FarmingSettings.read(config);
        assertArrayEquals(profiles, rules.profileRows());
        assertArrayEquals(mappings, rules.mappingRows());
        assertArrayEquals(profiles, config.getCategory(FarmingSettings.CATEGORY).get("profiles").getStringList());
        assertFalse(rules.withEnabled(false).enabled);
        assertArrayEquals(profiles, rules.withEnabled(false).profileRows());
        assertThrows(IllegalArgumentException.class, () -> new FarmingSettings.Selector("minecraft:sapling[type=spruce"));
        assertThrows(IllegalArgumentException.class, () -> new FarmingSettings.Selector("minecraft:sapling[type=spruce,type=oak]"));
    }

    @Test
    void authoritativeRulesAndRegistrationsRoundTripAndSurviveWeatherEdits() {
        FarmingSettings farms = new FarmingSettings(true, false, true, false, true,
                new String[] {"custom,0,1,2,3"}, new String[] {"example:crop=custom"},
                Collections.singletonMap("example:api[type=red]", new PlantGrowthProfile(-1, 0, 1, 2)));
        WeatherSettings settings = new WeatherSettings(PresetCatalog.defaults().get("oxford").profile, false, true, true, true)
                .withFarming(farms).withOptions(false, false, false, false, false)
                .withClimate(PresetCatalog.defaults().get("burlington").profile, null);
        assertSame(farms, settings.farming);
        WeatherMessage outgoing = new WeatherMessage(); outgoing.settings = settings;
        ByteBuf buffer = Unpooled.buffer();
        try {
            outgoing.toBytes(buffer);
            WeatherMessage incoming = new WeatherMessage(); incoming.fromBytes(buffer);
            FarmingSettings decoded = incoming.settings.farming;
            assertTrue(decoded.enabled); assertFalse(decoded.harvestcraft); assertFalse(decoded.mystical);
            assertArrayEquals(farms.profileRows(), decoded.profileRows());
            assertArrayEquals(farms.mappingRows(), decoded.mappingRows());
            assertEquals(-1, decoded.registeredProfiles().get("example:api[type=red]").minimum);
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test
    void unknownOptionalHookShapeRetainsBytesAndWeather() {
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(52, org.objectweb.asm.Opcodes.ACC_PUBLIC, "example/ChangedCrop", null, "java/lang/Object", null);
        writer.visitEnd(); byte[] original = writer.toByteArray();
        boolean available = zone.moddev.mc.skysrealisticweather.core.HookStatus.available();
        String target = "com.pam.harvestcraft.blocks.growables.BlockPamCrop";
        assertSame(original, new zone.moddev.mc.skysrealisticweather.core.PlantTransformer().transform(target, target, original));
        assertEquals(available, zone.moddev.mc.skysrealisticweather.core.HookStatus.available());
        assertTrue(zone.moddev.mc.skysrealisticweather.core.PlantHookStatus.states().get(target).startsWith("native fallback:"));
    }

}
