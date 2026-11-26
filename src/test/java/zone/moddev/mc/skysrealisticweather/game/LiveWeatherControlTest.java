package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldInfo;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.live.Observation;

import static org.junit.jupiter.api.Assertions.*;

final class LiveWeatherControlTest {
    @Test
    void preservesManualWeatherDurationAndRestoresTheFeed() {
        WorldInfo info = new WorldInfo(new NBTTagCompound());
        LiveWeatherControl control = new LiveWeatherControl(info);
        Observation wet = new Observation(10000, 10, Observation.Condition.THUNDER);
        assertSame(wet, control.beforeTick(12, wet));
        assertTrue(info.isRaining());
        assertTrue(info.isThundering());
        info.setRainTime(1);
        info.setThunderTime(1);
        control.afterTick();

        info.setCleanWeatherTime(60);
        info.setRainTime(0);
        info.setThunderTime(0);
        info.setRaining(false);
        info.setThundering(false);
        assertNull(control.beforeTick(13, wet));
        assertFalse(info.isRaining());
        control.afterTick();
        assertNull(control.beforeTick(72, wet));
        control.afterTick();
        assertSame(wet, control.beforeTick(73, wet));
    }

    @Test
    void remembersManualWeatherWhileWaitingForTheFeed() {
        WorldInfo info = new WorldInfo(new NBTTagCompound());
        LiveWeatherControl control = new LiveWeatherControl(info);
        control.afterTick();
        info.setCleanWeatherTime(50);
        assertNull(control.beforeTick(10, null));
        control.afterTick();
        Observation rain = new Observation(10000, 10, Observation.Condition.RAIN);
        assertNull(control.beforeTick(30, rain));
        control.afterTick();
        assertSame(rain, control.beforeTick(60, rain));
    }

    @Test
    void fallingBackReleasesVanillaTimers() {
        WorldInfo info = new WorldInfo(new NBTTagCompound());
        LiveWeatherControl control = new LiveWeatherControl(info);
        Observation snow = new Observation(10000, -4, Observation.Condition.SNOW);
        assertSame(snow, control.beforeTick(0, snow));
        info.setRainTime(1);
        info.setThunderTime(1);
        control.afterTick();
        assertNull(control.beforeTick(1, null));
        assertEquals(0, info.getRainTime());
        assertEquals(0, info.getThunderTime());
    }

    @Test
    void scheduleYieldsToLiveWeatherAndSleepThenResumesAtTheNextEvent() {
        WorldInfo info = new WorldInfo(new NBTTagCompound());
        LiveWeatherControl control = new LiveWeatherControl(info);
        WeatherEpisode front = new WeatherEpisode(WeatherKind.FRONTAL_PRECIPITATION, 0, 24000, 0.9F);
        WeatherEpisode next = new WeatherEpisode(WeatherKind.THUNDERSTORM, 29000, 2800, 0.8F);
        Observation clear = new Observation(10000, 10, Observation.Condition.CLEAR);
        assertSame(clear, control.beforeTick(0, clear, true, front));
        assertFalse(info.isRaining());
        assertNull(control.episode());
        assertNull(control.beforeTick(1, null, true, front));
        assertSame(front, control.episode());
        assertTrue(info.isRaining());
        info.setRaining(false);
        info.setRainTime(0);
        info.setThunderTime(0);
        control.afterTick();
        control.beforeTick(2, null, true, front);
        assertTrue(control.scheduled());
        assertFalse(info.isRaining());
        assertNull(control.episode());
        control.afterTick();
        control.beforeTick(3, null, true, next);
        assertSame(next, control.episode());
        assertTrue(info.isRaining());
        assertTrue(info.isThundering());
    }

    @Test
    void manualTimersTakePrecedenceOverScheduledRain() {
        WorldInfo info = new WorldInfo(new NBTTagCompound());
        LiveWeatherControl control = new LiveWeatherControl(info);
        WeatherEpisode shower = new WeatherEpisode(WeatherKind.CONVECTIVE_SHOWER, 7000, 1600, 0.9F);
        control.beforeTick(0, null, true, shower);
        control.afterTick();
        info.setCleanWeatherTime(20);
        info.setRaining(false);
        control.beforeTick(1, null, true, shower);
        assertFalse(control.scheduled());
        assertFalse(info.isRaining());
        control.afterTick();
        control.beforeTick(20, null, true, shower);
        assertNull(control.episode());
        control.beforeTick(21, null, true, shower);
        assertSame(shower, control.episode());
    }
}
