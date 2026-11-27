# Overworld biome and generator compatibility on Minecraft 1.11.2

Sky's Realistic Weather reads the selected local biome for temperature and
precipitation. Generator integration additionally applies seasonal snow and ice
while terrain is populated. Runtime climate support alone does not establish
support for a generator's surface pass.

The pinned builds are listed in [the port matrix](PORTING_1_11_2.md). Disposable
Forge 13.20.1.2588 fixtures test Biomes O' Plenty 6.2.0.2319, AbyssalCraft
1.9.4.11-FINAL, Vampirism 1.2.9, GeographiCraft 0.8.9, BiomeTweaker 3.1.304,
OTG v9 with Biome Bundle v5.1, and The Lost Cities 1.0.2 with CompatLayer 0.3.1.

Each controlled generation test uses Burlington at strength 2, winter day 45,
then summer day 45, with a forced frontal precipitation event and seed 123948.
The probe queries the generator's biome, generates fresh terrain and counts
cover immediately, before normal weather ticks can alter it. This tests the
native generation path without relying on a later snow tick. Actual candidate
hashes, weather readings and counts are retained in the port's verification
folder; 1.10.2 counts and performance figures are not reused.


| Profile | Snow columns in fresh winter terrain | Snow columns in fresh summer terrain |
| --- | ---: | ---: |
| Biomes O' Plenty | 229 | 0 |
| AbyssalCraft | 212 | 0 |
| Vampirism | 256 | 0 |
| GeographiCraft | 236 | 0 |
| BiomeTweaker | 196 | 0 |
| The Lost Cities | 210 | 0 |
| OTG + Biome Bundle | 173 | 0 |

Counts refer to the target biome within a 16 × 16 population area, not the
whole world. Client visuals and whole-chunk performance are separate checks.
BOP's native decoration can vary across fresh JVMs, so its exact cover count
can also vary. The table records the corrected candidate's fresh recheck.
The repeated with/without-weather timings, terrain fingerprints and explicit
comparison limits are in [multiplayer and performance results](PERFORMANCE_1_11_2.md).

Terrain providers retain native tree generation during their population
callback, including Lost Cities' forced sapling growth for parks. Subsequent
natural growth uses local temperature as usual. Seasonal cover may influence
later neighbouring decoration; it does not change the generator's base biome
selection or pre-population height map in the tested comparisons.

## Native exceptions and ownership

Protected polar and glacier biomes keep their native frost routine. Seasonal
cover outside those exceptions is tracked so warming can remove only deposits
the mod owns. Forge ICE denial and the public seasonal-surface event remain
available to veto placement. Disabling seasonal world generation retains the
generator's native behaviour; it does not regenerate existing chunks.

## Open Terrain Generator

The v9 adapter uses the virtual biome and height selected by OTG's frost pass.
Warm native surfaces may receive one thawable snow layer or freeze an exposed
water source when ordinary ice is configured. Naturally cold surfaces retain
OTG's native materials and frost behaviour. Both virtual and saved registry IDs
are checked against the protection list.

OTG can generate with a virtual biome and store another biome ID in the chunk.
The tested TemperatePlainsMeadowM has no registry ID while the saved biome is
`minecraft:plains`. During generation, consumers should use the supplied-biome
API and allow `ClimateSample.biomeId` to be null. Loaded queries use the biome
actually saved in the chunk. See [the weather API](API.md).

## Unavailable version targets

RTG and Thaumcraft have no 1.11.2 artifacts in their project file listings; their
1.10.2 integration does not establish support here. Oh The Biomes You'll Go has
no verified original artifact in this qualification set. The port's launch
profiles contain only pinned, version-appropriate jars.
