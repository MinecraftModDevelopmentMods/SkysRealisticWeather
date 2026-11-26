# Build and verify

Run Gradle from this checkout with Java 17 and configure a Java 8 toolchain
for Minecraft. The wrapper uses Gradle 9.6.1 and the sibling ForgeGradle
toolchain with stable 29 mappings.

Gradle downloads the RTG 4.1.3.0 and Open Terrain Generator v21 jars from
CurseMaven to compile the optional integrations. The main jar runs on plain Forge
and does not bundle either mod.

```text
gradlew check build writeReleaseChecksum prepareRuntimeProbe
gradlew genEclipseRuns eclipse
```

The main jar, compile-only API jar, sources, Javadocs and main jar checksum are in `build/libs`.
Use the main jar for a normal Minecraft installation. The mapped jar in
`build/libs-dev` is for the development classpath.

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
| `runClientRTG` | Realistic Terrain Generation |
| `runClientOTG` | Open Terrain Generator |
| `runClientBiomeBundle` | Open Terrain Generator and Biome Bundle |
| `runClientLostCities` | The Lost Cities and CompatLayer |
| `runClientRTGBoPGeo` | RTG, Biomes O' Plenty and GeographiCraft |
| `runClientGeographiCraft` | GeographiCraft |
| `runClientBiomeTweaker` | BiomeTweaker |
| `runClientAbyssalCraft` | AbyssalCraft |
| `runClientThaumcraft` | Thaumcraft and Baubles |
| `runClientVampirism` | Vampirism |
| `runClientHarvestCraft` | Pam's HarvestCraft 1.10.2j |
| `runClientAgriCraft` | AgriCraft a21 and InfinityLib 0.11.0 |
| `runClientMysticalAgriculture` | Mystical Agriculture 1.5.10 |
| `runClientForestry` | Forestry 5.2.17.388 |
| `runClientBaseMetals` | Base Metals 2.5.1.110021 and OreSpawn 4.1.6.110021 |
| `runClientFarming` | All four farming mods and InfinityLib |

Choose the matching world type when creating a test world for Biomes O'
Plenty, RTG, OTG or The Lost Cities. Use the Biome Bundle preset in the OTG
world creation screen for `runClientBiomeBundle`.

The runtime probe jars in `build/verification/probe` belong in disposable
test profiles. They exercise native Forge lifecycle, hooks, save/reload,
configuration and client synchronization. The main jar contains only the
mod's production classes and resources.

For accelerated climate checks on Windows, build `prepareRuntimeProbe`, then run:

```text
python scripts/Run-WeatherEndurance.py
python scripts/Analyze-WeatherEndurance.py build/verification/weather-endurance/<run-directory>
```

This uses the existing disposable dedicated Forge fixture under
`build/verification/worldgen-performance/whole-chunk-v1/1-vanilla`. It starts
one Java 8 server at a time, hidden, at below-normal priority, pinned to one
logical CPU with a 1 GiB heap limit. It opens no client and uses no desktop
input. The default sweep samples every 100 Minecraft ticks for 20 years per
preset on three seeds, plus five years at strength 0 and 2. The native-weather
stage runs 2,190 Minecraft days in every preset and Per Biome Climate,
discarding the first 365 days for warm-up and scoring five 365-day blocks. Eight sites include lowlands, Y=200, coast, mountain,
jungle, desert, Cold Taiga and native Arctic terrain. Surface columns use a
seeded geometric weather-selection cadence of 1/4096 per tick, with 09:00
majority cover observations, runtime reconstruction and a process save/reload.
A 30-day synthetic real-weather replay checks the feed-to-timer control path.

Use `--years`, `--seeds`, `--native-days`, `--phase`, `--output`, `--jar` and
`--extra-mod` to choose durations, independent seeds, stages, a fresh evidence
directory, a candidate jar and optional compatibility jars. Existing
stage logs are preserved. Receipts record artifact hashes, exit status, CPU
time and peak working set; CSV files retain seasonal frequencies, event types,
start hours, temperatures, spell lengths, estimated rainfall thresholds and
daily biome/deposit readings. Use the analyzer's `--sweep-only` option for
an independent-seed sweep without the native stages. The default jar follows
`mod_version` in `gradle.properties`.

Use `--sample-step`, `--sweep-climates`, `--extreme-years`, `--native-climates`
and `--warmup-days` for resolution checks and focused runtime cases. The analyzer
supports `--native-only` for runtime/reload-only evidence. `prepareRuntimeProbe`
records compiled source and jar hashes; the runner rejects stale probes or
source changes and changing candidate jars between stages. It also snapshots
packaged climate tables and acceptance objectives.

The native stage advances the production weather handlers and Minecraft's
weather timers directly. It does not simulate entity AI, random block ticks,
client rendering or a full modpack's world-generation workload. The analyzer
checks broad climate relationships; its pass is not a claim of calibrated
meteorological accuracy. Review frequency and temperature findings alongside
the checks. The initial report is in
`build/verification/weather-endurance/RESULT.md`.
The subsequent realism fixes and independent-seed checks are recorded in
`build/verification/weather-realism/RESULT.md`.

The revised climate balance, corrected observations, independent seeds and
old-candidate comparison are recorded in
`build/verification/climate-balance/RESULT.md`.

## Packaged farming checks

```text
python scripts/Run-PlantChecks.py --profile combined --phase contracts --output build/verification/plant-growth/<new-run>
python scripts/Run-PlantChecks.py --profile combined --phase reload --output build/verification/plant-growth/<same-run>
python scripts/Run-PlantChecks.py --profile combined --phase endurance --output build/verification/plant-growth/<new-run>
python scripts/Run-PlantChecks.py --profile combined --phase performance --output build/verification/plant-growth/<new-run>
```

Other profiles are `vanilla`, `harvestcraft`, `agricraft`, `mystical` and
`forestry`. The runner expects original released jars under
`build/verification/plant-growth-review`, and uses the same disposable Forge
fixture and resource limits as the weather checks. It rejects unbuilt source,
changed candidates and existing stage logs, and records original jar hashes.
The endurance phase defaults to five 365-day Minecraft years on three seeds
in every preset. It selects actual native plant updates at a seeded geometric
cadence of 3/4096 per game tick, with controlled light and hydrated soil. Rates
are checked against independently measured native growth and the configured
suitability curve. This isolates temperature from irrigation and light effects.
The performance phase measures native block update cost with the policy enabled
and its event listener removed for the native baseline, then translates it to the expected native random-tick cadence
for 1,000 and 10,000 crops. It does not measure whole-server tick time.

## Packaged thermometer checks

```text
python scripts/Run-ThermometerChecks.py --profile vanilla --output build/verification/thermometer/<new-run>
python scripts/Run-ThermometerChecks.py --profile vanilla --phase reload --output build/verification/thermometer/<same-run>
python scripts/Run-ThermometerChecks.py --profile basemetals --output build/verification/thermometer/<another-new-run>
python scripts/Run-ThermometerChecks.py --profile basemetals --phase reload --output build/verification/thermometer/<same-basemetals-run>
```

Build `prepareRuntimeProbe` first. The runner uses the disposable Forge fixture
and hidden, below-normal-priority, single-CPU server limits described above.
The Base Metals profile expects the original pinned `base-metals-240967-9280029.jar`
and `mmd-orespawn-245586-9518063.jar` under
`build/verification/thermometer/providers/`. It records their hashes and checks
that the originals and test copies are unchanged. Recipes, ordinary-player
placement and readings, both hands, support loss, metadata, unit preferences,
native-temperature fallback and physical save/reload are exercised. Test-only
ore-dictionary fixtures demonstrate late registration without adding mercury
to the distributed mod. These server checks do not render the client HUD.

## Precipitation boundaries

`PrecipitationRendererTest` transforms the real mapped vanilla renderer and
verifies its bytecode stack, the precipitation-pass gate, both rain/snow opacity
hooks and the existing local fog/particle hooks. A disposable dedicated server
with the packaged probe and `-Dskyweather.probe=precipitation` tests the shared
column policy against native biome data. It checks snow/rain beside clear
climates, packet reconstruction, moving cell gaps, intensity scaling, forced
blizzard temperature limits, live/passive fallback and unloaded terrain.
Headless tests verify policy and bytecode; they do not establish rendered visual
acceptance. See `build/verification/precipitation-boundaries/RESULT.md` for the
local candidate and receipts.

## Distant precipitation

Add `-Dskyweather.distant=true` to the packaged precipitation probe for a
loaded flat-world landscape with adjacent jungle and Arctic biomes. It checks
curtains beyond 128 blocks, mixed rain/snow, distance/quality limits, disabled
and clear paths and absent terrain. It writes `distant-weather/columns.csv`
and sampling timing percentiles after warm-up.

`verify.DistantRenderProbe` is a standalone mapped Java 8 probe. Its arguments
are the exported columns CSV, a Minecraft jar containing vanilla textures,
and a fresh output directory. Add the mapped main/probe jars and normal
Minecraft runtime libraries to the classpath and set `java.library.path` to
the LWJGL natives folder. It creates an offscreen Pbuffer, exercises the
production drawing method, writes previews and measures two resolutions and
three distance bounds. It opens no game window. Timings include view culling,
vertex submission and synchronized drawing, with an empty-pass baseline.
They do not measure Minecraft's terrain, entities or complete frame time.
The retained local runners and artifact-bound receipts are in
`build/verification/distant-weather/`.

## Local temperature zones

The packaged probe's `-Dskyweather.probe=temperature-zones` mode checks native
zone registration, replacement/removal, dimensions, crops, thermometers,
thread/bounds rules, furnace operation, burnout, breakage and query timings
with 2,000 unrelated zones. Use `-Dzones.reload=true` in a second process on
the same disposable world to verify runtime-only child registration and native
furnace recovery. Unit tests cover inclusive bounds, chunk/section indexing,
deterministic priorities, chunk snapshot encoding/clearing and C/F editing.
The retained runner, compiled API-only consumer, source hashes and results are
under `build/verification/temperature-zones/`. These checks do not replace a
live multiplayer UI/transport qualification.

## Crash and saved-world qualification

Build `prepareCrashProbe` to produce the separate, optional-dependency
`weather-qualification-probe.jar`. It can run the same native scenario with
or without the main weather jar. Keep both probes out of a playable profile.

```text
python scripts/Run-CrashQualification.py --case edge --output build/verification/crash-qualification/<fresh-edge>
python scripts/Run-CrashQualification.py --case stress --variant control --output build/verification/crash-qualification/<fresh-control>
python scripts/Run-CrashQualification.py --case stress --output build/verification/crash-qualification/<fresh-weather>
python scripts/Run-CrashQualification.py --case sylvester --variant control --ticks 6000 --output build/verification/crash-qualification/<fresh-world-control>
python scripts/Run-CrashQualification.py --case sylvester --ticks 6000 --output build/verification/crash-qualification/<fresh-world-weather>
python scripts/Run-CrashQualification.py --case sylvester --phase reload --ticks 6000 --output build/verification/crash-qualification/<same-world-weather>
```

The edge case injects a non-finite biome temperature and verifies that crop
queries become unavailable, native growth continues, and valid readings recover.
The stress case ticks valid vanilla, HarvestCraft and Mystical Agriculture plant
states across four season dates and resolves item metadata/NBT variants with the
combined farming stack. Existing packaged farming contracts exercise AgriCraft
tiles, Forestry, fertiliser and hook compatibility separately.

The Sylvester case requires a previously verified, independent snapshot under
`build/verification/crash-qualification/input-sylvester`, plus exported saved
crop points and target chunks. It never launches the original profile. Preserve
the source manifest and verify both source and snapshot after qualification.
This runner is specific to the retained local fixture, not a world-copy tool.
It excludes duplicate older jars and client-only OptiFine; pins Forge 2511 in
both control and weather runs; redirects copied backup settings into the copy;
and disables Forge's automatic deletion of erroring entities in copied configs.

Saved-world tests execute actual server ticks, native crop callbacks, chunk
rotation, tile serialization and physical save/reload. Each tick also advances
the weather calendar by one day; calendar years do not represent years of full
server physics. Tests run hidden, below normal priority on one CPU, with a 3 GiB
heap for Sylvester and 1 GiB for synthetic cases. Receipts bind the jars and
record failures without replacing them. These checks cannot establish live
client, OptiFine, renderer, multiplayer or every modpack interaction coverage.
See `build/verification/crash-qualification/RESULT.md` for the qualified candidate.
