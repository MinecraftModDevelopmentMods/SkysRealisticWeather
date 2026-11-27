# Minecraft 1.11.2 port

This checkout targets **Minecraft 1.11.2**, **Forge 13.20.1.2588**, Java 8,
and stable **32-1.11** mappings. The local candidate is **0.1.21.111021**,
with **API 8**, **config 8** and **world settings schema 1**. It starts from
the merged 1.10.2 implementation at `a4c6f7930c06cd97c2a503deecc6f92137c167f3`.

The 365-Minecraft-day calendar and Per Biome Climate remain the defaults.
Seasonal surface ownership, native Arctic exceptions, natural crop growth,
mercury thermometers, tapered local heaters and child temperature zones retain
their existing contracts. Config schema compatibility does not make whole
Minecraft saves or third-party mod jars interchangeable between versions.
Use a backup and a version-appropriate test pack for migration.

## Version-specific changes

- Forge dependency and optional mod IDs match their 1.11.2 registrations.
- Items use the 1.11 empty-stack and count contracts. Thermometer placement,
  hand use, creative-tab entries and block callbacks use the new signatures.
- The config GUI supports the Forge 1.11 factory methods; language assets use
  the lowercase resource name expected by this version.
- The OTG v9 bridge uses its new package and loaded-terrain material access.
  It retains OTG's virtual biome, protected native cover and native materials.
- Forestry 5.3's ambient climate-info fallback receives local temperature and
  seasonal humidity. Controlled climate rooms return through Forestry's own path.
- Tough As Nails uses `SeasonsOption` and its configurable temperature-scale
  midpoint; its body temperature, response timer and other modifiers remain native.
- JEI uses the 4.x ingredient registry and category contract.

## Integration builds

All jars are optional and unmodified. These are the pinned **1.11.2** targets;
version-specific qualification is recorded below. A server load check cannot
establish client visual acceptance.

| Integration | Minecraft version | Pinned build | Coverage |
| --- | --- | --- | --- |
| [Pam's HarvestCraft](https://www.curseforge.com/minecraft/mc-mods/pams-harvestcraft/files/2410937) | 1.11.2 | 1.11.2h | Native growth, fruit/logs/saplings, produce tooltips and fertiliser |
| [Mystical Agriculture](https://www.curseforge.com/minecraft/mc-mods/mystical-agriculture/files/2487658) | 1.11.2 | 1.5.9 | Default exemptions, opt-in growth rules and item identities |
| [Forestry](https://www.curseforge.com/minecraft/mc-mods/forestry/files/2542509) | 1.11.2 | 5.3.4.257 | Ambient climate info, humidity and controlled-room precedence |
| [CraftTweaker](https://www.curseforge.com/minecraft/mc-mods/crafttweaker/files/2838724) | 1.11.2 | 3.1.0-legacy, file 2838724 | Startup/reload rules, scripts, synchronized snapshots |
| [Tough As Nails](https://modrinth.com/mod/tough-as-nails/version/rVR7XpTd) | 1.11.2 | 2.1.0.65 | Ambient correction, heated zones and restored seasonal ownership |
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei/files/2461378) | 1.11.2 | 4.5.1.296 | Native client category, live units/suitability, optional plants, disabled bridge and reconnect checks; dedicated-server loading |
| [Hwyla](https://www.curseforge.com/minecraft/mc-mods/hwyla/files/2496989) | 1.11.2 | 1.8.22-B37 | Registered provider, server data and ordinary-crop climate presentation |
| [The One Probe](https://www.curseforge.com/minecraft/mc-mods/the-one-probe/files/2496984) | 1.11.2 | 1.4.19 with CompatLayer 0.3.1 | Provider registration and crop climate presentation |
| [Biomes O' Plenty](https://www.curseforge.com/minecraft/mc-mods/biomes-o-plenty/files/2536549) | 1.11.2 | 6.2.0.2319 | Climate mapping and native seasonal population |
| [AbyssalCraft](https://www.curseforge.com/minecraft/mc-mods/abyssalcraft/files/2596860) | 1.11.2 | 1.9.4.11-FINAL | Overworld climate mapping and seasonal population |
| [Vampirism](https://www.curseforge.com/minecraft/mc-mods/vampirism-become-a-vampire/files/2555244) | 1.11.2 | 1.2.9 | Overworld climate mapping and seasonal population |
| [GeographiCraft](https://www.curseforge.com/minecraft/mc-mods/climate-control-geographicraft/files/2486027) | 1.11.2 | 0.8.9 | Generated biome layout and seasonal population |
| [BiomeTweaker](https://www.curseforge.com/minecraft/mc-mods/biometweaker/files/2478061) | 1.11.2 | 3.1.304 | Native biome configuration and seasonal population |
| [Open Terrain Generator](https://www.curseforge.com/minecraft/mc-mods/open-terrain-generator/files/2530369) | 1.11.2 | v9, file 2530369 | Optional frost bridge, tested with Biome Bundle |
| [Biome Bundle](https://www.curseforge.com/minecraft/mc-mods/biome-bundle/files/2431190) | 1.11.2 | v5.1, file 2431190 | Virtual-biome winter/summer population |
| [The Lost Cities](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities/files/2497880) | 1.11.2 | 1.0.2 with CompatLayer 0.3.1 | Native seasonal population |
| [Base Metals](https://www.curseforge.com/minecraft/mc-mods/base-metals/files/9541705) | 1.11.2 | 2.5.2.111021 with OreSpawn 4.1.7.111021 | Actual ore-dictionary mercury recipe, placement and reload |

RTG, Thaumcraft and AgriCraft have no 1.11.2 artifacts in their project file
listings. Their 1.10.2 support does not carry over; this port supplies no launch
profiles or support claim for them. Legacy AgriCraft config fields remain
readable. Oh The Biomes You'll Go has no verified original 1.11.2 artifact in
this port's qualification set.

## Verification and acceptance

The corrected local build passes **57 unit tests**, including native renderer
and generation bytecode verification. The combined farming suite passes
**571,778 contracts** and process reload. The initial port's two-year farming
simulations cover all 28 presets on two independent seeds. Core, individual and
combined optional bridge profiles pass, as do disabled bridges, mercury
crafting, heaters, temperature zones, invalid
climate fallback, a plant/item stress matrix and native save/reload.

Seven biome/generator profiles pass fresh winter/summer generation checks on the
corrected candidate. Separate fixtures recheck Arctic and glacier native cover
and Cold Taiga seasonal behaviour. Initial port fixtures also verify
Forge ICE denial, clear weather, seasonal ownership and thaw after reload.
Three Java API examples compile against the matching 1.11.2 API. Distant weather
passes native sampling and six offscreen rendering configurations.

The initial port's longer weather run uses 20-year sweeps on two independent
seeds, five-year strength extremes and six native years per climate (one warm-up,
five scored).
It completes without crashes and passes **335 of 336 balance checks**. British
Isles (`oxford`) averages **35.025 snow dates/year** against a design band of
18–35: one extra snow date across 40 simulated years. This small upper-band
finding is retained, and no climate values or acceptance thresholds were changed
for the port. The shorter pilot's three low-band findings are also preserved;
they do not recur in the longer sample. Statistical qualification is therefore
not reported as an unqualified pass. The climate engine and tuning are unchanged
by the subsequent population-scope correction; this long sample was not rerun.

Two real packaged clients now pass season/storm/sleep, server biome mapping,
admin permissions and UI, personal display units, thermometers, crop tooltips,
temperature-zone synchronization, heaters, chunk watching and reconnect checks.
Three paired whole-chunk repeats compare with and without weather for vanilla,
Biomes O' Plenty, OTG/Biome Bundle and Lost Cities; a further six JVMs exercise
dense winter snow and ice. See [multiplayer and performance results](PERFORMANCE_1_11_2.md)
for timings, the Lost Cities park-tree fix, BOP's native decoration variability
and the isolated downstream effects of winter cover.

[JEI client checks and screenshot review](JEI_CLIENT_1_11_2.md) are also complete
for the pinned build. They corrected a compact recipe-panel overflow and moved
full details to a dedicated hover marker. Only JEI integration class files
changed after multiplayer/performance qualification; the client report records
the updated jar hash separately from the earlier server qualification.

Local checks and their candidate hashes are retained under
`build/verification/port-1.11.2` for initial qualification and
`build/verification/multiplayer-performance-1112` for the corrected candidate.
See `RESULT.md` and artifact-bound receipts in each, including preserved
unsuccessful attempts. [Build instructions](BUILDING.md)
explain how to repeat the checks with fresh disposable worlds.

The port is local and unpublished. General manual client gameplay and visual
acceptance remain separate work. Targeted JEI client checks, automated
two-client multiplayer and the new whole-chunk baseline are complete for the
documented scenarios. Published 1.10.2 timings and the 1.10.2 Sylvester pack
are not used to claim 1.11.2 coverage. No player world is launched by these tests.
