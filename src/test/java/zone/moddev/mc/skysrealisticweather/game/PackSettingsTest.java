package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysrealisticweather.climate.*;
import static org.junit.jupiter.api.Assertions.*;

class PackSettingsTest {
    private WeatherSettings defaults() {
        Preset p=PresetCatalog.defaults().get("burlington");
        return new WeatherSettings(p.profile,false,true,true,true,p.weather,true);
    }
    @Test void savedFieldsInheritUntouchedPackChanges() {
        WeatherSettings original=defaults();NBTTagCompound edits=new NBTTagCompound();
        WeatherSettings changed=original.withOptions(false,true,true,false,true);
        WorldWeatherData.record(edits,original,changed);
        assertEquals(1,edits.getKeySet().size());assertTrue(edits.hasKey("fog"));
        WeatherSettings updatedPack=original.withOptions(false,false,true,true,false);
        WeatherSettings merged=WorldWeatherData.merge(updatedPack,edits);
        assertFalse(merged.fog);assertFalse(merged.surface);assertFalse(merged.scheduled);
    }
    @Test void unknownSavedSchemaIsPreservedUntilExplicitReset() {
        NBTTagCompound future=new NBTTagCompound();future.setInteger("schema",42);future.setString("future","keep");
        WorldWeatherData data=new WorldWeatherData();data.readFromNBT(future);
        assertEquals(future,data.writeToNBT(new NBTTagCompound()));
        assertThrows(IllegalStateException.class,()->data.save(new NBTTagCompound()));
        data.reset();assertEquals(1,data.writeToNBT(new NBTTagCompound()).getInteger("schema"));
    }
    @Test void malformedOffsetCannotSilentlyResetCalendar() {
        NBTTagCompound fields=new NBTTagCompound();fields.setString("offset","later");
        assertThrows(IllegalArgumentException.class,()->WorldWeatherData.merge(defaults(),fields));
    }
    @Test void scriptCapacityRejectsOverflowWithoutChangingTheActiveBank() {
        java.util.List<String> inserted=new java.util.ArrayList<>();
        try {
            for(int i=0;i<8192;i++){String id="capacity:crop_"+i;zone.moddev.mc.skysrealisticweather.compat.ScriptRules.crop(id,zone.moddev.mc.skysrealisticweather.api.PlantGrowthProfile.EXEMPT);inserted.add(id);}
            assertThrows(IllegalArgumentException.class,()->zone.moddev.mc.skysrealisticweather.compat.ScriptRules.crop("capacity:overflow",zone.moddev.mc.skysrealisticweather.api.PlantGrowthProfile.EXEMPT));
            assertTrue(zone.moddev.mc.skysrealisticweather.compat.ScriptRules.crops().isEmpty());
        } finally {for(String id:inserted)zone.moddev.mc.skysrealisticweather.compat.ScriptRules.crop(id,null);}
    }
    @Test void readoutsValidatePacketsAndConvertUnits() {
        NBTTagCompound data=new NBTTagCompound();data.setBoolean("crop",true);data.setBoolean("available",true);
        data.setString("plant","minecraft:wheat");data.setFloat("temperature",.15F);data.setFloat("multiplier",1);
        data.setInteger("reason",zone.moddev.mc.skysrealisticweather.api.PlantGrowthSample.Reason.SUITABLE.ordinal());
        assertTrue(CropPresentation.lines(data,TemperatureDisplay.Unit.CELSIUS).toString().contains("0.0 °C"));
        assertTrue(CropPresentation.lines(data,TemperatureDisplay.Unit.FAHRENHEIT).toString().contains("32.0 °F"));
        data.setFloat("temperature",Float.NaN);assertTrue(CropPresentation.lines(data,TemperatureDisplay.Unit.CELSIUS).get(0).contains("unavailable"));
        data.setFloat("temperature",1);data.setInteger("reason",500);assertTrue(CropPresentation.lines(data,TemperatureDisplay.Unit.CELSIUS).get(0).contains("unavailable"));
    }
    @Test void offsetCopiesAndValidationPreserveDaylight() {
        WeatherSettings s=defaults().withCalendarOffset(-92L*24000);
        assertEquals(s.calendarOffset,s.withOptions(true,false,false,false,false).calendarOffset);
        assertEquals(s.calendarOffset,s.withFarming(s.farming.withEnabled(false)).calendarOffset);
        assertEquals(6000,Math.floorMod(ClimateClock.shift(6000,s.calendarOffset),24000));
        assertThrows(IllegalArgumentException.class,()->s.withCalendarOffset(1));
        assertThrows(IllegalArgumentException.class,()->s.withCalendarOffset(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE,ClimateClock.shift(Long.MAX_VALUE,24000));
    }
    @Test void shiftedSchedulesKeepEpisodeTimesOnDaylightClock() {
        WeatherSettings s=defaults();WeatherSchedule schedule=new WeatherSchedule(4813,s.profile,s.weather,180L*24000);
        int episodes=0;
        for(long time=365L*24000;time<400L*24000;time+=1200) {
            zone.moddev.mc.skysrealisticweather.api.WeatherEpisode episode=schedule.at(time);
            if(episode!=null){episodes++;assertTrue(episode.contains(time));assertTrue(episode.intensity(episode.time(time,0))>0);}
        }
        assertTrue(episodes>0);
    }
}
