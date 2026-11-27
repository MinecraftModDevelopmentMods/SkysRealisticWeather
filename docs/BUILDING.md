# Build and verify

Run Gradle from this checkout with Java 17 and configure a Java 8 toolchain
for Minecraft. The wrapper uses Gradle 9.6.1 and the sibling ForgeGradle
toolchain with stable 32-1.11 mappings.

Gradle resolves pinned 1.11.2 integrations for compilation and separate optional
Eclipse profiles. Original third-party jars are never bundled. The supported
builds and port qualification are listed in [port status](PORTING_1_11_2.md).

```text
gradlew check build writeReleaseChecksum prepareRuntimeProbe
gradlew genEclipseRuns eclipse
```

The main jar, compile-only API jar, sources, Javadocs and main jar checksum are in `build/libs`.
Use the main jar for a normal Minecraft installation. The mapped jar in
`build/libs-dev` is for the development classpath.

Use the outer `SkysRealisticWeather111` folder as the Eclipse workspace.
Import the nested checkout in Eclipse with Buildship and use the generated
`runClient` or `runServer` launch profile with Java 8 and a disposable game
directory. Use **Gradle → Refresh Gradle Project** in Eclipse after dependency
changes so Buildship updates the launch classpath. The Gradle equivalents are
`gradlew runClient` and `gradlew runServer`. Set `-PweatherRunDirectory=<path>` to
select another development game folder.

The development client opens at 1280 × 720 by default. To use another size,
set `overrideWidth` and `overrideHeight` in `options.txt` in the development
game folder.

`runClient` and `runServer` use Sky's Realistic Weather and Forge. For a
compatibility session, choose one of the generated Eclipse launches below.
Each uses its own folder under `run-compat/` for mods, configs and saves.
`genEclipseRuns` resolves the pinned CurseMaven jars and maps them for the
development runtime. Its first run needs access to CurseMaven.

| Eclipse launch | Additional mods |
| --- | --- |
| `runClientBoP` | Biomes O' Plenty |
| `runClientOTG` | Open Terrain Generator |
| `runClientBiomeBundle` | Open Terrain Generator and Biome Bundle |
| `runClientLostCities` | The Lost Cities and CompatLayer |
| `runClientGeographiCraft` | GeographiCraft |
| `runClientBiomeTweaker` | BiomeTweaker |
| `runClientAbyssalCraft` | AbyssalCraft |
| `runClientVampirism` | Vampirism |
| `runClientHarvestCraft` | Pam's HarvestCraft 1.11.2h |
| `runClientMysticalAgriculture` | Mystical Agriculture 1.5.9 |
| `runClientForestry` | Forestry 5.3.4.257 |
| `runClientBaseMetals` | Base Metals 2.5.2.111021 and OreSpawn 4.1.7.111021 |
| `runClientFarming` | HarvestCraft, Mystical Agriculture and Forestry |

Choose the matching world type when creating a test world for Biomes O'
Plenty, OTG or The Lost Cities. Use the Biome Bundle preset in the OTG
world creation screen for `runClientBiomeBundle`.

The runtime probe jars in `build/verification/probe` belong in disposable
test profiles. They exercise native Forge lifecycle, hooks, save/reload,
configuration and client synchronization. The main jar contains only the
mod's production classes and resources.

## Disposable runtime fixture

Prepare the probes and original integration jars, then point the fixture helper
at an already installed Forge 13.20.1.2588 server engine (with its libraries):

```text
gradlew preparePackProbe prepareCrashProbe preparePublishedIntegrationMods
python scripts/Prepare-RuntimeFixture.py --engine-dir <installed-Forge-server>
```

The helper copies only the verified engine jars and creates fresh test settings.
It copies no saves, modpacks or player worlds. Runtime checks use Java 8, a hidden
process, below-normal priority, one logical CPU and a 1 GiB heap. Evidence stays
under ignored `build/verification`; failed receipts are preserved.

```text
python scripts/Run-PlantChecks.py --profile combined --output build/verification/plants/<fresh>
python scripts/Run-PlantChecks.py --profile combined --phase reload --output build/verification/plants/<same>
python scripts/Run-PackChecks.py --profile full --output build/verification/packs/<fresh>
python scripts/Run-PackChecks.py --profile full --phase reload --output build/verification/packs/<same>
python scripts/Run-ThermometerChecks.py --profile basemetals --output build/verification/thermometer/<fresh>
python scripts/Run-ThermometerChecks.py --check temperature-zones --output build/verification/heat/<fresh>
python scripts/Run-ThermometerChecks.py --check temperature-zones --phase reload --output build/verification/heat/<same>
python scripts/Run-CrashQualification.py --case edge --output build/verification/crash/<fresh>
python scripts/Run-CrashQualification.py --case stress --output build/verification/crash/<fresh>
```

Plant profiles are `vanilla`, `harvestcraft`, `mystical`, `forestry` and `combined`.
Pack profiles also include individual optional bridges and `--disabled` coverage.
Receipts bind the candidate, probes, source and unchanged original provider jars.
The 1.10.2 Sylvester pack is not a 1.11.2 test fixture; this port's crash runner
accepts synthetic edge and stress worlds only.

## Accelerated years and native generation

```text
python scripts/Run-WeatherEndurance.py --years 2 --seeds 81173 29119 --native-days 730 --warmup-days 365 --extreme-years 1 --output build/verification/weather/<fresh>
python scripts/Analyze-WeatherEndurance.py build/verification/weather/<same>
python scripts/Run-PlantChecks.py --profile combined --phase endurance --years 2 --output build/verification/plants/<fresh>
python scripts/Run-RuntimeChecks.py --mode population --world-type CUSTOMIZED --fixed-biome 1 --universal --option skyweather.population=winter --expect "SKY_WEATHER_CHECK POPULATION_WINTER PASS" --output build/verification/generation/<fresh>
```

`--universal` selects Burlington for a controlled generator comparison; omitting
it keeps the normal Per Biome Climate default. `--fixed-biome` writes the native
customized-world fixed-biome setting. `--mod` adds an original pinned integration
jar, for example `--mod BoP`. Use `--world-type BIOMESOP` for that generator;
Biome Bundle needs `--world-type OTG --level-name "Biome Bundle"`, plus its two
mods. `--option` passes additional named probe settings. Use a new output for a
new candidate or scenario; `--stage reload` reuses a matching passing world.

The short example is a pilot; the retained balance thresholds were designed for
longer samples. Use `--years 20 --native-days 2190 --warmup-days 365
--extreme-years 5` for full qualification, and preserve unsuccessful pilot results.

Climate checks advance production weather handlers and native weather timers.
They isolate seasonal behaviour rather than simulating full-server physics.
Farming endurance uses seeded natural crop callbacks with valid light and soil.
Neither establishes real meteorological accuracy or full-server performance.

## Acceptance scope

Unit tests verify native renderer and Overworld generation bytecode as well as
climate, network and API contracts. Packaged servers cover native hooks, crops,
commands, temperature zones, crafting and save/reload. The distant-render probe
can draw through an offscreen LWJGL Pbuffer without a game window. These checks
do not replace manual client visuals, live multiplayer testing or testing a
particular modpack. Old 1.10.2 timing and gameplay receipts do not qualify this
version. See [current port evidence and limitations](PORTING_1_11_2.md).
