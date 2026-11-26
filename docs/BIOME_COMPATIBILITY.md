# Overworld biome compatibility on Minecraft 1.10.2

Sky's Realistic Weather reads the local biome for temperature and precipitation.
The world generator determines whether a newly populated chunk also receives
seasonal surface cover. This matters for modpacks that use the same biome
library with a different world type.

The following checks used Sky's Realistic Weather 0.1.7.110021 on dedicated
Forge 12.18.3.2511 with Java 8. The selection covers popular Overworld biome
projects, biome configuration tools and replacement generators with over
one million project-wide downloads on CurseForge. The table reports a winter
and a summer sample in the named biome for each setup.

| Mod and tested build | World type and biome | Exposed weather from the API | Snow on fresh winter terrain |
| --- | --- | --- | --- |
| [Biomes O' Plenty 5.0.0.2290](https://www.curseforge.com/minecraft/mc-mods/biomes-o-plenty/files/2452877) | Biomes O' Plenty, Meadow | Winter snow, summer rain | 190 columns |
| [AbyssalCraft 1.9.4.11](https://www.curseforge.com/minecraft/mc-mods/abyssalcraft/files/2596856) | Default, Darklands Plains | Winter snow, summer rain | 212 columns |
| [Thaumcraft 6.0.BETA8](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2546413) | Default, Magical Forest | Winter snow, summer rain | 251 columns |
| [Vampirism 1.2.5](https://www.curseforge.com/minecraft/mc-mods/vampirism-become-a-vampire/files/2555238) | Default, Vampire Forest | Winter snow, summer rain | 220 columns |
| [GeographiCraft 0.8.9](https://www.curseforge.com/minecraft/mc-mods/climate-control-geographicraft/files/2486027) | Default with its biome layout, Plains | Winter snow, summer rain | 236 columns |
| [BiomeTweaker 2.3.239](https://www.curseforge.com/minecraft/mc-mods/biometweaker/files/2402226) | Default, Plains with a scripted humidity of 0.3125 | Winter snow, summer rain. Both climate queries reported the edited humidity. | 196 columns |
| [Realistic Terrain Generation 4.1.3.0](https://www.curseforge.com/minecraft/mc-mods/realistic-terrain-generation/files/2550453) | RTG, Plains | Winter snow, summer rain | 189 columns |
| RTG 4.1.3.0 + Biomes O' Plenty 5.0.0.2290 + GeographiCraft 0.8.9 | RTG, Biomes O' Plenty Meadow | Winter snow, summer rain | 176 columns |
| [Biome Bundle 5.1](https://www.curseforge.com/minecraft/mc-mods/biome-bundle/files/2430752) with [Open Terrain Generator v21](https://www.curseforge.com/minecraft/mc-mods/open-terrain-generator/files/2439892) | OTG, TemperatePlainsMeadowM | Winter snow, summer rain | 173 columns |
| [The Lost Cities 1.0.2](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities/files/2497880) | Lost Cities, Plains | Winter snow, summer rain | 210 columns |

Each count is from a 16 × 16 population area and includes only the target
biome. All summer samples contained zero snow columns. RTG waits for neighboring
chunks before decorating. Its measurements include the target chunk's Forge
ICE pass. RTG's snow-layers setting and surface eligibility rules still apply.
RTG and The Lost Cities honor Forge ICE event denial.

Biomes O' Plenty Glacier also kept its native snow and ice in a warm-season
population fixture. During a winter front, a supplied Outback biome stayed
dry, and the lowland Tropical Rainforest received rain with a seasonal
temperature scale of 0.35. Its native temperature fell with elevation.

OTG uses the virtual biome and surface height it selected during its frost
pass. When that biome would ordinarily be warm, seasonal weather can add a
single thawable snow layer or freeze an exposed water source if the preset
uses ordinary ice. OTG retains its native frost routine and materials on
naturally cold surfaces that remain cold. Protected biomes always use native
frost. Both the virtual and the saved registry ID, when present, are checked
against the configured protection list.

OTG can choose a virtual biome for terrain generation and save a different
biome ID in the chunk. In the tested meadow, the supplied-biome climate query
read TemperatePlainsMeadowM, which had no Forge registry name. After generation
the loaded query read `minecraft:plains`, the biome OTG had saved. Integrations
that need the virtual biome's temperature should use the supplied-biome API
during generation and allow for a null `ClimateSample.biomeId`. [Weather API](API.md)

The published listings for Oh The Biomes You'll Go did not provide a 1.10.2
artifact for this check. The author's historical notes mention developing
with 1.10.2. [CurseForge files](https://www.curseforge.com/minecraft/mc-mods/oh-the-biomes-youll-go/files/all)
and [author's MCreator listing](https://mcreator.net/modification/21622/oh-biomes-youll-go-5-new-update?page=13)

## Test conditions

Each setup used a disposable world with seed 123948. The test forced a
frontal precipitation event at Burlington seasonal strength 2 on winter
day 45, then summer day 45. It queried the generator's chosen biome before
loading a chunk, generated the area synchronously, counted snow before a
weather tick could touch it, and sampled exposed weather on the server.
The Glacier test supplied controlled water and snow-supporting columns during
the Biomes O' Plenty frost pass. Client rendering, other dimensions and the
frequency of naturally scheduled storms were outside these checks.
