package zone.moddev.mc.skysrealisticweather.climate;

import java.util.List;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.client.PlantTooltips;
import static org.junit.jupiter.api.Assertions.*;

class PlantTooltipTest {
    @Test
    void describesPreferredLimitsAndEachGrowthOutcomeWithoutPromisingNativeEligibility() {
        PlantGrowthProfile profile = new PlantGrowthProfile(.05F, .4F, 1.1F, 1.7F);
        PlantGrowthSample.Reason[] reasons = PlantGrowthSample.Reason.values();
        for (PlantGrowthSample.Reason reason : reasons) {
            float multiplier = reason == PlantGrowthSample.Reason.COLD || reason == PlantGrowthSample.Reason.HEAT ? 0 : 1;
            String text = String.join("\n", PlantTooltips.lines(profile, new PlantGrowthSample("test:plant", "cool", .8F, multiplier, false, reason)));
            assertTrue(text.contains("5.6 °C to 21.1 °C")); assertTrue(text.contains("-2.2 °C / 34.4 °C"));
            assertTrue(text.contains("soil, light and native conditions"));
            switch (reason) {
                case COLD: assertTrue(text.contains("Too cold; natural growth paused")); break;
                case HEAT: assertTrue(text.contains("Too hot; natural growth paused")); break;
                case SUITABLE: assertTrue(text.contains("Suitable; normal natural growth")); break;
                case EXEMPT: assertTrue(text.contains("exempt")); break;
                default: assertTrue(text.contains("native growth"));
            }
        }
        String slowed = String.join(" ", PlantTooltips.lines(profile,
                new PlantGrowthSample("test:plant", "cool", .2F, .42857F, true, PlantGrowthSample.Reason.COLD)));
        assertTrue(slowed.contains("controlled climate")); assertTrue(slowed.contains("about 43%"));
        assertTrue(String.join(" ", PlantTooltips.lines(profile,
                new PlantGrowthSample("test:plant", "cool", .051F, .003F, false, PlantGrowthSample.Reason.COLD))).contains("<1%"));
        List<String> exempt = PlantTooltips.lines(PlantGrowthProfile.EXEMPT,
                new PlantGrowthSample("test:magic", "exempt", -3, 1, false, PlantGrowthSample.Reason.EXEMPT));
        assertFalse(String.join(" ", exempt).contains("Growth limits"));
        assertTrue(String.join(" ", PlantTooltips.lines(profile, null)).contains("unavailable"));
    }
}
