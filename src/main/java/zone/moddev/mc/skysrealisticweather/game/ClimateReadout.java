package zone.moddev.mc.skysrealisticweather.game;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import zone.moddev.mc.skysrealisticweather.api.ClimateSample;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather.Precipitation;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;

/** Shared formatting for the local readouts. */
public final class ClimateReadout {
    private ClimateReadout() {
    }

    public static BlockPos eyes(Entity entity) {
        return new BlockPos(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
    }

    public static String compact(ClimateSample climate, LocalWeather local) {
        return String.format(Locale.ROOT, "%s %d/%d | %s | %s", climate.phaseLabel,
                climate.season.dayOfSeason, climate.season.lengthInDays, TemperatureDisplay.format(local.temperature), precipitation(climate, local));
    }
    public static String temperature(zone.moddev.mc.skysrealisticweather.api.TemperatureSample sample, TemperatureDisplay.Unit unit) {
        return "Local temp " + unit.format(sample.temperature) + " | outdoors " + unit.format(sample.ambientTemperature)
                + " | " + label(sample.source) + (sample.zoneId==null?"":" " + sample.zoneId);
    }

    public static List<String> details(ClimateSample climate, LocalWeather local, WeatherEpisode episode) {
        return details(climate, local, episode, zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration.temperatureUnit());
    }

    public static List<String> details(ClimateSample climate, LocalWeather local, WeatherEpisode episode, TemperatureDisplay.Unit unit) {
        return Arrays.asList(
                climate.mode.label + " | " + climate.climateId,
                climate.phaseLabel + " " + climate.season.dayOfSeason + "/" + climate.season.lengthInDays
                        + " | " + climate.biomeId,
                "Temp " + unit.format(local.temperature) + " | vanilla " + unit.format(climate.vanillaTemperature),
                String.format(Locale.ROOT, "Seasonal temp %s | rainfall %.2f", unit.format(climate.seasonalTemperature), climate.biomeRainfall),
                String.format(Locale.ROOT, "%s %d%% | fog %d%% | wind %d%%", precipitation(climate, local),
                        Math.round(local.intensity * 100), Math.round(local.fog * 100), Math.round(local.wind * 100)),
                String.format(Locale.ROOT, "Wetness %.2fx | thunder %.2fx", climate.wetnessMultiplier, climate.thunderMultiplier),
                label(local.source) + " | " + (episode == null ? "No local episode" : label(episode.kind)));
    }

    private static String precipitation(ClimateSample climate, LocalWeather local) {
        if (local.precipitation != Precipitation.NONE) {
            return label(local.precipitation) + (local.thunder ? " + thunder" : "");
        }
        return climate.openSkyWeather.precipitation != Precipitation.NONE ? "Sheltered" : "Dry";
    }

    public static String label(Enum<?> value) {
        String label = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
