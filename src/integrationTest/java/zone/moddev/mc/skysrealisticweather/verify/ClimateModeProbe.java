package zone.moddev.mc.skysrealisticweather.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.*;
import zone.moddev.mc.skysrealisticweather.game.*;
import zone.moddev.mc.skysrealisticweather.network.WeatherMessage;

final class ClimateModeProbe {
    static void run(WorldServer world) {
        WeatherSettings settings = WeatherConfiguration.startupSettings();
        WeatherProbe.require(settings.mode == ClimateMode.PER_BIOME, "default mode is per biome");
        WeatherProbe.require(settings.biomes.preset(Biomes.JUNGLE).id.equals("tropical_rainforest"), "jungle climate");
        WeatherProbe.require(settings.biomes.preset(Biomes.DESERT).id.equals("hot_desert"), "desert climate");
        WeatherProbe.require(settings.biomes.preset(Biomes.COLD_TAIGA).id.equals("subarctic"), "cold taiga climate");
        WeatherProbe.require(settings.biomes.preset(Biomes.EXTREME_HILLS).id.equals("alpine_highlands"), "mountain climate");
        BlockPos fresh = new BlockPos(900008, 70, -900008);
        WeatherProbe.require(!world.isBlockLoaded(fresh, false), "query starts outside generated terrain");
        world.setWorldTime(320L * 24000 + 6000);
        WeatherState.Frame winter = new WeatherState.Frame(world, settings, null);
        WeatherState.server(winter);
        ClimateSample tropical = WeatherApi.getClimate(world, fresh, Biomes.JUNGLE).get();
        ClimateSample desert = WeatherApi.getClimate(world, fresh, Biomes.DESERT).get();
        ClimateSample polar = WeatherApi.getClimate(world, fresh, Biomes.ICE_PLAINS).get();
        WeatherProbe.require(tropical.mode == ClimateMode.PER_BIOME && tropical.climateId.equals("tropical_rainforest"), "API climate identity");
        WeatherProbe.require(tropical.seasonalTemperature > 0.15 && desert.seasonalTemperature > 0.15
                && polar.seasonalTemperature < 0.15, "distinct winter temperatures");
        WeatherProbe.require(!world.isBlockLoaded(fresh, false), "supplied biome queries do not generate terrain");

        int tropicalRain = 0, desertRain = 0, different = 0, savannaWet = 0, savannaDry = 0;
        for (int day = 0; day < 3650; day++) {
            long time = day * 24000L + 9000;
            world.setWorldTime(time);
            WeatherEpisode carrier = new WeatherEpisode(WeatherKind.THUNDERSTORM, time - 100, 1000, 0.8F, true);
            WeatherState.Frame frame = new WeatherState.Frame(world, settings, null, carrier, true, winter.regions, world.getSeed());
            WeatherEpisode wet = frame.currentEpisode(Biomes.JUNGLE);
            WeatherEpisode dry = frame.currentEpisode(Biomes.DESERT);
            if (wet != null && wet.kind.wet) tropicalRain++;
            if (dry != null && dry.kind.wet) desertRain++;
            if ((wet == null) != (dry == null) || wet != null && dry != null && wet.kind != dry.kind) different++;
            WeatherEpisode savanna = frame.currentEpisode(Biomes.SAVANNA);
            if (savanna != null && savanna.kind.wet) {
                if (day % 365 < 120) savannaWet++; else savannaDry++;
            }
        }
        WeatherProbe.require(tropicalRain > desertRain * 4 && different > 1000, "independent local schedules");
        WeatherProbe.require(savannaWet / 120F > savannaDry / 245F * 2, "savanna wet and dry season rates");
        System.out.println("BIOME_MODE_SCHEDULES tropical=" + tropicalRain + " desert=" + desertRain
                + " savannaWet=" + savannaWet + " savannaDry=" + savannaDry);

        world.setWorldTime(55L * 24000 + 9000);
        WeatherEpisode carrier = new WeatherEpisode(WeatherKind.THUNDERSTORM, world.getWorldTime() - 100, 1000, 0.8F, true);
        WeatherState.Frame server = new WeatherState.Frame(world, settings, null, carrier, true);
        WeatherMessage packet = new WeatherMessage(server);
        ByteBuf bytes = Unpooled.buffer();
        try {
            packet.toBytes(bytes);
            WeatherMessage decoded = new WeatherMessage();
            decoded.fromBytes(bytes);
            WeatherState.Frame client = new WeatherState.Frame(world, decoded.settings, null, decoded.episode, true, null, decoded.seed);
            for (Biome biome : Biome.REGISTRY) {
                WeatherProbe.require(server.climateId(biome).equals(client.climateId(biome)), "mapping synchronized " + biome.getRegistryName());
                WeatherProbe.require(server.temperature(biome, fresh) == client.temperature(biome, fresh), "temperature synchronized");
                WeatherEpisode a = server.currentEpisode(biome), b = client.currentEpisode(biome);
                WeatherProbe.require(a == null ? b == null : b != null && a.kind == b.kind && a.startTick == b.startTick, "local episode synchronized");
                WeatherProbe.require(WeatherConfiguration.file().getCategory(WeatherConfiguration.BIOMES).containsKey(biome.getRegistryName().toString()), "registered biome written to config");
            }
            System.out.println("BIOME_MODE_PACKET bytes=" + bytes.writerIndex());
        } finally {
            bytes.release();
        }
        WeatherState.Frame universal = new WeatherState.Frame(world, settings.withMode(ClimateMode.UNIVERSAL, settings.biomes), null);
        WeatherProbe.require(universal.climate(Biomes.JUNGLE) == universal.climate(Biomes.DESERT), "universal climate shared");
        java.util.Map<String, String> overrides = new java.util.LinkedHashMap<>(settings.biomes.mappings);
        overrides.put(Biomes.DESERT.getRegistryName().toString(), "winter_wonderland");
        WeatherSettings fantasy = settings.withMode(ClimateMode.PER_BIOME, new BiomeClimateMap(settings.biomes.presets, overrides));
        WeatherProbe.require(fantasy.biomes.preset(Biomes.DESERT).id.equals("winter_wonderland"), "fantasy biome mapping");

        for (Biome biome : Biome.REGISTRY) {
            if (biome.getRegistryName().getResourceDomain().equals("biomesoplenty")) {
                if (biome.getRegistryName().getResourcePath().equals("temperate_rainforest")) {
                    WeatherProbe.require(settings.biomes.preset(biome).id.equals("cool_maritime"), "temperate rainforest mapping");
                }
                System.out.println("BIOME_MODE_BOP " + biome.getRegistryName() + "=" + settings.biomes.preset(biome).id);
            }
        }
        WeatherState.server(new WeatherState.Frame(world, settings, null));
        System.out.println("SKY_WEATHER_CHECK BIOME_MODES PASS");
        world.getMinecraftServer().initiateShutdown();
    }

    private ClimateModeProbe() {
    }
}
