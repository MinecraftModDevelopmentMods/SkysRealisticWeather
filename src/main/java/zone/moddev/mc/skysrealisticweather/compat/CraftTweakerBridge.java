package zone.moddev.mc.skysrealisticweather.compat;

import java.util.*;
import minetweaker.*;
import minetweaker.api.data.*;
import minetweaker.api.minecraft.MineTweakerMC;
import minetweaker.api.player.IPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import stanhebben.zenscript.annotations.*;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay;

/** Loaded only when the legacy MineTweaker3 mod is installed. */
public final class CraftTweakerBridge {
    public static void register() {
        MineTweakerAPI.registerClass(Climate.class);
        MineTweakerImplementationAPI.onPostReload(event -> ScriptRules.reloaded());
    }

    @ZenClass("mods.skysrealisticweather.Climate")
    public static final class Climate {
        @ZenMethod public static void setCropTemperatureCelsius(String selector, double minimum, double idealMinimum, double idealMaximum, double maximum) {
            try {
                TemperatureDisplay.Unit unit = TemperatureDisplay.Unit.CELSIUS;
                crop(selector, new PlantGrowthProfile(unit.fromTemperature(minimum), unit.fromTemperature(idealMinimum),
                        unit.fromTemperature(idealMaximum), unit.fromTemperature(maximum)));
            } catch (IllegalArgumentException invalid) { MineTweakerAPI.logError("Sky's crop rule " + selector + ": " + invalid.getMessage()); }
        }
        @ZenMethod public static void exemptCrop(String selector) {
            try { crop(selector, PlantGrowthProfile.EXEMPT); }
            catch (IllegalArgumentException invalid) { MineTweakerAPI.logError("Sky's crop rule " + selector + ": " + invalid.getMessage()); }
        }
        private static void crop(String selector, PlantGrowthProfile profile) {
            new zone.moddev.mc.skysrealisticweather.climate.FarmingSettings.Selector(selector);
            MineTweakerAPI.apply(new IUndoableAction() {
                private PlantGrowthProfile previous;
                public void apply() { previous = ScriptRules.crop(selector, profile); }
                public void undo() { ScriptRules.crop(selector, previous); }
                public boolean canUndo() { return true; }
                public String describe() { return "Sky's crop temperature: " + selector; }
                public String describeUndo() { return "Restore Sky's crop rule: " + selector; }
                public Object getOverrideKey() { return "skyweather:crop:" + selector; }
            });
        }
        @ZenMethod public static void mapBiome(String biome, String preset) {
            try {
                if (biome==null||preset==null||!biome.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                        || zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration.preset(preset) == null)
                    throw new IllegalArgumentException("Invalid biome ID or unknown preset");
                MineTweakerAPI.apply(new IUndoableAction() {
                    private String previous;
                    public void apply() { previous = ScriptRules.biome(biome, preset); }
                    public void undo() { ScriptRules.biome(biome, previous); }
                    public boolean canUndo() { return true; }
                    public String describe() { return "Sky's biome climate: " + biome + " = " + preset; }
                    public String describeUndo() { return "Restore Sky's biome climate: " + biome; }
                    public Object getOverrideKey() { return "skyweather:biome:" + biome; }
                });
            } catch (IllegalArgumentException invalid) { MineTweakerAPI.logError("Sky's biome rule: " + invalid.getMessage()); }
        }
        @ZenMethod public static String getSeason(IPlayer player) {
            EntityPlayer entity = player==null?null:MineTweakerMC.getPlayer(player);
            return entity == null ? "unavailable" : WeatherApi.getSeason(entity.world)
                    .map(value -> value.season.name().toLowerCase(Locale.ROOT)).orElse("unavailable");
        }
        @ZenMethod public static IData getLocalClimate(IPlayer player) {
            EntityPlayer entity = player==null?null:MineTweakerMC.getPlayer(player);
            Map<String, IData> values = new LinkedHashMap<>();
            values.put("available", new DataBool(false));
            if (entity != null && zone.moddev.mc.skysrealisticweather.game.WeatherState.frame(entity.world)!=null) {
                BlockPos pos = entity.getPosition();
                WeatherApi.getTemperatureSample(entity.world, pos).ifPresent(sample -> {
                    values.put("available", new DataBool(true));
                    values.put("temperatureCelsius", new DataDouble(TemperatureDisplay.Unit.CELSIUS.temperature(sample.temperature)));
                    values.put("source", new DataString(sample.source.name().toLowerCase(Locale.ROOT)));
                });
                WeatherApi.getWeather(entity.world, pos).ifPresent(sample -> values.put("precipitation", new DataString(sample.precipitation.name().toLowerCase(Locale.ROOT))));
                WeatherApi.getClimate(entity.world, pos).ifPresent(sample -> values.put("climate", new DataString(sample.climateId)));
                values.put("season", new DataString(getSeason(player)));
            }
            return new DataMap(values, true);
        }
        @ZenMethod public static IData getPlantGrowth(IPlayer player, int x, int y, int z) {
            EntityPlayer entity = player==null?null:MineTweakerMC.getPlayer(player);
            Map<String, IData> values = new LinkedHashMap<>();
            values.put("available", new DataBool(false));
            if (entity != null) WeatherApi.getPlantGrowth(entity.world, new BlockPos(x, y, z)).ifPresent(sample -> {
                values.put("available", new DataBool(true));
                values.put("multiplier", new DataDouble(sample.naturalGrowthMultiplier));
                values.put("reason", new DataString(sample.reason.name().toLowerCase(Locale.ROOT)));
            });
            return new DataMap(values, true);
        }
    }
}
