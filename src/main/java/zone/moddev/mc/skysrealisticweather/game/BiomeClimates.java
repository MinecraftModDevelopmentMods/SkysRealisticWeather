package zone.moddev.mc.skysrealisticweather.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import zone.moddev.mc.skysrealisticweather.api.BiomeClimate;

public final class BiomeClimates {
    private static final BiomeClimate DEFAULT = new BiomeClimate(0, 1, BiomeClimate.PrecipitationRule.BIOME_DEFAULT);
    private static Map<ResourceLocation, BiomeClimate> definitions = new HashMap<>();
    private static volatile Map<Biome, BiomeClimate> resolved = Collections.emptyMap();

    private BiomeClimates() {
    }

    public static synchronized void register(ResourceLocation id, BiomeClimate climate) {
        if (definitions == null) throw new IllegalStateException("Biome climate registration has closed");
        if (definitions.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(climate)) != null) {
            throw new IllegalArgumentException("A biome climate is already registered for " + id);
        }
    }

    public static synchronized void freeze() {
        Map<Biome, BiomeClimate> byBiome = new HashMap<>();
        for (Biome biome : Biome.REGISTRY) {
            BiomeClimate climate = definitions.get(biome.getRegistryName());
            if (climate == null && BiomeDictionary.hasType(biome, BiomeDictionary.Type.JUNGLE)) {
                climate = new BiomeClimate(0, 0.35F, BiomeClimate.PrecipitationRule.BIOME_DEFAULT);
            }
            if (climate != null) byBiome.put(biome, climate);
        }
        resolved = Collections.unmodifiableMap(byBiome);
        definitions = null;
    }

    public static float seasonalScale(Biome biome) {
        return get(biome).seasonalScale;
    }

    public static BiomeClimate get(Biome biome) {
        return resolved.getOrDefault(biome, DEFAULT);
    }

    public static boolean allowsPrecipitation(Biome biome) {
        return allowsPrecipitation(biome, false);
    }

    public static boolean allowsPrecipitation(Biome biome, boolean includesDryBiomes) {
        switch (get(biome).precipitation) {
            case ALLOW: return true;
            case DRY: return false;
            default: break;
        }
        return biome.canRain() || biome.getEnableSnow()
                || includesDryBiomes && (BiomeDictionary.hasType(biome, BiomeDictionary.Type.DRY)
                        || BiomeDictionary.hasType(biome, BiomeDictionary.Type.SAVANNA)
                        || BiomeDictionary.hasType(biome, BiomeDictionary.Type.MESA));
    }
}
