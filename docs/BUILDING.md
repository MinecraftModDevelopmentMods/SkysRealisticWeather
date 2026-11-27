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

Resources use pack format 3 and lowercase language filenames for Minecraft
1.11.2. `gradlew prepareEclipseResources` refreshes `bin/main` after resource
changes. Restart the client after changing `pack.mcmeta`; Forge selects its
legacy or current resource wrapper when the client starts.

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

## Multiplayer and generation performance

```text
gradlew prepareRuntimeProbe prepareControlProbe preparePerformanceProbe
python scripts/Run-MultiplayerChecks.py --case calendar --output build/verification/multiplayer-performance-1112/<fresh-calendar>
python scripts/Run-MultiplayerChecks.py --case biome --output build/verification/multiplayer-performance-1112/<fresh-biome>
python scripts/Run-MultiplayerChecks.py --case admin --output build/verification/multiplayer-performance-1112/<fresh-admin>
python scripts/Run-MultiplayerChecks.py --case thermal --output build/verification/multiplayer-performance-1112/<fresh-thermal>
python scripts/Run-MultiplayerChecks.py --case control --output build/verification/multiplayer-performance-1112/<fresh-without-weather>
python scripts/Run-MultiplayerChecks.py --case control --control-weather --output build/verification/multiplayer-performance-1112/<fresh-with-weather>
python scripts/Run-GenerationPerformance.py --rounds 3 --bop-calibration build/verification/<bop-calibration>.json --output build/verification/multiplayer-performance-1112/<fresh-generation>
```

The Windows multiplayer runner opens two real packaged clients on a separate
desktop and never switches input to it. Clients are muted, capped at 10 FPS and
share one logical CPU; the dedicated server uses another. All processes run at
BelowNormal priority and use fresh disposable worlds. The client runtime must
contain the pinned Forge installation, Minecraft assets and Windows natives.
`WEATHER_CLIENT_RUNTIME` and `WEATHER_SERVER_RUNTIME` can override the installed
runtime locations in `scripts/verification_clients.py`.
`WEATHER_CLIENT_ASSETS` can select a separate complete Minecraft assets folder.

Create the BOP calibration receipt with the controls below before running the
four-provider generation comparison.

The calendar fixture has a deterministic test-only winter storm; normal presets
are unchanged. Biome and thermal cases keep Per Biome Climate. The thermal case
uses ordinary network traffic to test personal display units, instruments, crop
suitability, heat changes, watched chunks and reconnects. The two control variants
use the same test jar, weather, world and two-player workload. After 120 warm-up
ticks with both players present, they record 600 complete server-tick intervals.
Repeat in opposite order to compare CPU time and tick duration.

Generation uses vanilla terrain, Biomes O' Plenty, Biome Bundle/OTG and Lost
Cities/CompatLayer, one server at a time on one logical CPU. Every process warms
576 chunk requests before timing separate 784-request winter and summer regions.
Three matched repeats counterbalance mod presence and seasonal order. Both
variants use the same seed and rain; the weather variant uses fresh Per Biome
Climate defaults. The world's auxiliary RNG is seeded before each region.
Population coverage and named-state fingerprints are checked outside timing,
normalizing seasonal snow layers and source-water ice. Lightweight biome and
height-map snapshots are captured before population, identically in both variants.
Measurements exclude chunk saving and are not client FPS or a full modpack benchmark. Preserve
every failed receipt and use fresh output directories for repeats.

Biomes O' Plenty's weighted decorators can vary between fresh JVMs even without
weather. Establish that limitation with two identical controls before allowing
decoration variation in its comparisons; base biome and height-map equality
remains mandatory. Every other provider still requires full normalized block
equality. Keep the calibration receipt and original controls with the results.

```text
python scripts/Run-GenerationPerformance.py --terrain bop --variant vanilla --rounds 1 --output build/verification/<bop-baseline-a>
python scripts/Run-GenerationPerformance.py --terrain bop --variant vanilla --rounds 1 --output build/verification/<bop-baseline-b>
python scripts/Calibrate-GenerationBaseline.py build/verification/<bop-baseline-a>/bop-1-vanilla/result.json build/verification/<bop-baseline-b>/bop-1-vanilla/result.json --output build/verification/<bop-calibration>.json
python scripts/Run-GenerationPerformance.py --bop-calibration build/verification/<bop-calibration>.json --output build/verification/<fresh-comparison>
python scripts/Run-GenerationPerformance.py --terrain vanilla --climate-mode UNIVERSAL --fixed-biome 1 --output build/verification/<fresh-snow-stress>
```

The last case uses fixed Plains terrain and Burlington's winter to ensure dense
snow/ice placement is measured in addition to the default climate regions.
Physical cover can affect later neighbouring decoration, so this stress case
may fail full post-population equality even though base terrain matches. To
isolate the cause, repeat one pair with `--veto-deposits --rounds 1`. It must
restore complete normalized block equality while actually vetoing deposits.
Then record the diagnostic and pass it only to the same cold-stress workload:

```text
python scripts/Calibrate-GenerationBaseline.py --cover-veto build/verification/<veto-campaign> --output build/verification/<cover-diagnostic>.json
python scripts/Run-GenerationPerformance.py --terrain vanilla --climate-mode UNIVERSAL --fixed-biome 1 --cover-diagnostic build/verification/<cover-diagnostic>.json --output build/verification/<verified-snow-stress>
python scripts/Analyze-GenerationPerformance.py build/verification/<completed-campaign> --output build/verification/<summary>.json
```

The resulting cold comparison still requires matching base biomes and height
maps and exact summer blocks. Its winter timings include the real population
effects of snow and ice; they are not an equal-decoration microbenchmark.

## JEI client checks

```text
gradlew check build writeReleaseChecksum prepareRuntimeProbe
python scripts/Run-JeiClientChecks.py --profile vanilla --output build/verification/jei-client-1112/<fresh-vanilla>
python scripts/Run-JeiClientChecks.py --profile combined --output build/verification/jei-client-1112/<fresh-combined>
python scripts/Run-JeiClientChecks.py --profile disabled --output build/verification/jei-client-1112/<fresh-disabled>
```

Each profile uses one packaged JEI 4.5.1.296 client and a fresh dedicated server.
The client is muted, capped at 10 FPS, BelowNormal and on a non-input Windows
desktop. Its ordinary guest player opens native JEI recipe screens, checks
server crop overrides and changing local temperatures, then disconnects/rejoins.
Screenshots cover 800 x 480 and 1280 x 720 at GUI scale 2. The combined profile
adds the pinned HarvestCraft, Mystical Agriculture and Forestry jars. The
disabled profile checks that ordinary JEI remains usable without the climate
category.

The runner reads complete launcher assets from `%APPDATA%/.minecraft/assets`
by default. Pass `--assets <complete-assets-folder>` or set
`WEATHER_CLIENT_ASSETS` to use another location. It verifies the original 1.11
asset index and every indexed object before launch, without modifying them.
An incomplete runtime can leave Minecraft's current language unavailable and
crash JEI before a world opens. Client checks therefore require actual assets,
including language and pack metadata, even when sound is muted.
See [JEI client results](JEI_CLIENT_1_11_2.md) for the tested layouts and fix.

## Acceptance scope

Unit tests verify native renderer and Overworld generation bytecode as well as
climate, network and API contracts. Packaged servers cover native hooks, crops,
commands, temperature zones, crafting and save/reload. The distant-render probe
can draw through an offscreen LWJGL Pbuffer without a game window. These checks
do not replace manual client gameplay, visual acceptance or testing a
particular modpack. Old 1.10.2 timing and gameplay receipts do not qualify this
version. See [current port evidence and limitations](PORTING_1_11_2.md).
