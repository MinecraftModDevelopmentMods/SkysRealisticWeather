package zone.moddev.mc.skysrealisticweather.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DistantPrecipitationTest {
    @Test
    void distanceAndFadeRespectQualityTerrainAndOffSwitch() {
        assertEquals(0, DistantPrecipitationField.range(0, 32, true));
        assertEquals(256, DistantPrecipitationField.range(999, 32, true));
        assertEquals(96, DistantPrecipitationField.range(256, 32, false));
        assertEquals(48, DistantPrecipitationField.range(192, 4, true));
        assertEquals(0, DistantPrecipitationField.range(192, 1, true));
        assertEquals(0, DistantPrecipitationField.fade(7, 192, true));
        assertEquals(1, DistantPrecipitationField.fade(50, 192, true));
        assertEquals(.5, DistantPrecipitationField.fade(180, 192, true));
        assertEquals(0, DistantPrecipitationField.fade(193, 192, true));
    }
}
