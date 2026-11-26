package zone.moddev.mc.skysrealisticweather.climate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

final class GenerationSettings {
    private static final String[] NATIVE_BIOMES = {
            "minecraft:ice_flats", "minecraft:ice_mountains", "minecraft:mutated_ice_flats",
            "minecraft:frozen_ocean", "minecraft:frozen_river", "biomesoplenty:glacier"
    };
    static final GenerationSettings DEFAULT = new GenerationSettings(true, NATIVE_BIOMES);

    final boolean enabled;
    private final Set<ResourceLocation> nativeBiomes;

    GenerationSettings(boolean enabled, String[] ids) {
        this.enabled = enabled;
        Set<ResourceLocation> biomes = new HashSet<>();
        for (String value : ids) {
            String id = value.trim();
            if (id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) biomes.add(new ResourceLocation(id));
            else LogManager.getLogger("Sky's Realistic Weather").warn("Ignoring invalid native snow biome ID '{}'.", value);
        }
        nativeBiomes = Collections.unmodifiableSet(biomes);
    }

    static String[] defaultBiomeIds() {
        return NATIVE_BIOMES.clone();
    }

    boolean keepsNativeSurface(ResourceLocation biome) {
        return nativeBiomes.contains(biome);
    }

    String[] biomeIds() {
        return nativeBiomes.stream().map(ResourceLocation::toString).sorted().toArray(String[]::new);
    }
}
