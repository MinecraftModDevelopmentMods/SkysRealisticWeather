// CraftTweaker 3.0.26 with Sky's Realistic Weather 0.1.18+ (Minecraft 1.10.2).
// Copy into your pack's scripts directory, then edit or remove these examples.
// Changed weather definitions require a world restart after /mt reload.
import mods.skysrealisticweather.Climate;

// Minimum, ideal minimum, ideal maximum, maximum: absolute Celsius.
Climate.setCropTemperatureCelsius("minecraft:wheat", -5, 5, 25, 40);
Climate.exemptCrop("minecraft:nether_wart");

// Use an ID from /skyweather presets, including your custom presets.
// Put initial spawn-generation mappings in biome_climates in the mod config;
// CraftTweaker 3 loads scripts after spawn terrain preparation.
Climate.mapBiome("minecraft:plains", "central_european");
