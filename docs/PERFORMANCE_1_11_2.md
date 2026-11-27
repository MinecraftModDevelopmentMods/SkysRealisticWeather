# Minecraft 1.11.2 multiplayer and generation checks

The local **0.1.21.111021** candidate passed the two-client scenarios and the
repeated generation comparisons on 27 November 2026. There was no sustained
server-thread CPU penalty in the default-climate sample beyond Biomes O'
Plenty's small winter increase. BOP's winter elapsed time increased more than
its CPU time, so this is not a claim of zero overhead.

These results apply to Forge **13.20.1.2588** and the pinned builds below. The
candidate remains local and unpublished; manual gameplay and JEI client
acceptance remain separate. See [port status](PORTING_1_11_2.md) and
[reproduction instructions](BUILDING.md).

## Whole-chunk generation

Each entry is the median of three matched with/without-weather comparisons.
Percentages are calculated within each pair before taking the median. Negative
values mean less measured time in this sample, not a promised speed improvement.

| Terrain provider | Winter CPU change | Summer CPU change | Winter elapsed change | Summer elapsed change |
| --- | ---: | ---: | ---: | ---: |
| Vanilla terrain, Forge only | -2.22% | -0.75% | +0.01% | -4.68% |
| Biomes O' Plenty 6.2.0.2319 | +0.26% | -2.11% | +7.44% | -2.33% |
| OTG v9 + Biome Bundle v5.1 | -0.67% | 0.00% | +1.74% | -4.41% |
| The Lost Cities 1.0.2 + CompatLayer 0.3.1 | -3.61% | -3.78% | -1.71% | -3.26% |

The individual paired CPU changes ranged from -7.04% to +4.72% across the
default-climate cases. BOP winter pairs ranged from -0.28% to +1.41% CPU.
Its independently calculated median elapsed cost was **8.61 to 9.21 ms per
chunk request**; the median per-run 95th percentile was **26.92 to 28.71 ms**.
These small samples on a computer still available for other work cannot
establish a statistically significant speedup or a universal upper bound.

The comparison measures the complete native chunk request, including terrain,
population and weather integration. Each fresh JVM warms 576 requests, then
times 784 winter and 784 summer requests in separate regions. Mod presence and
seasonal order are counterbalanced across three rounds. This gives 24 JVMs and
37,632 measured requests for the default **Per Biome Climate** comparison.
Both variants use seed 123948, rain, identical provider jars and a seeded
auxiliary world RNG. The weather variant uses a fresh default climate library.
The production weather frame is advanced and its date checked before timing.
Population coverage is asserted, including OTG's own population callback.

Server-thread CPU time is less sensitive to other desktop activity than elapsed
time; both are retained. The host was Windows 11 on a Ryzen 9 9900X. Each server
ran alone at BelowNormal priority on one logical CPU, with Java 8, Serial GC
and a 1.5 GiB maximum heap. Fingerprints and cover counts run outside timing;
matching biome/height-map snapshots add the same small cost to both variants.
Chunk saving is excluded. These are not FPS, disk throughput, arbitrary-seed or
full-modpack measurements. RTG has no verified 1.11.2 target in this set.

The chosen vanilla and Lost Cities regions remained warm enough to produce no
seasonal cover in the timed default-climate sample. BOP's winter median was
84,231 snowy columns and 5,276 ice columns; OTG/Biome Bundle produced 61 and
2,292 respectively. The additional vanilla case below tests dense cold cover;
it does not establish every provider's worst-case winter cost.

## Snow-heavy stress case

A further six JVMs repeat the vanilla comparison with fixed Plains terrain and
Universal Burlington climate at normal strength. Each winter run produces
**94,572 snowy columns and 16,931 ice columns**; summer produces neither. This
adds 9,408 measured requests, for **47,040 in the two final campaigns**.

Winter's median paired CPU change is **-7.89%**, and elapsed change **-0.48%**;
summer's changes are **-11.29%** and **-9.61%**. This end-to-end workload includes
the downstream effects of actual snow and ice on neighbouring decoration.
It must not be interpreted as an equal-decoration microbenchmark or evidence
that adding weather always makes generation faster.

## Terrain correctness and the bug found

The tests found that Lost Cities grows park trees through native sapling
callbacks during population. Climate growth rules could stop those trees,
leave saplings behind and change subsequent random-number consumption. The
earlier failing comparison showed roughly 15–19% extra CPU cost.

The corrected build wraps the terrain provider's population callback in an
exception-safe, nested world scope. Natural-growth gates allow native generator
growth inside that scope and resume climate checks afterwards. A regression
test failed before the fix and passes now, including nested calls and exception
cleanup. No third-party jar was changed.

Base biomes and pre-population height maps match in every final pair. Full
named-block-state comparisons, normalizing seasonal snow layers and source-water
ice, match for vanilla, OTG/Biome Bundle and Lost Cities in the default campaign.
Two explicit limits are recorded:

- BOP's weighted decorations vary between fresh JVMs even without weather.
  Two identical weather-free controls reproduced that variation with identical
  base terrain. Its timing comparison therefore requires matching base biomes
  and height maps, but does not claim identical trees and plants.
- Dense winter cover can affect later neighbouring population. The cold stress
  case initially failed full normalized block equality. A diagnostic using the
  public surface event vetoed 250,080 placements across warm-up and timed phases
  and restored complete equality. The final stress comparison requires the same
  base terrain and exact summer blocks, while explicitly recording winter cover
  feedback. The original failure remains retained.

Arctic, BOP glacier and Cold Taiga fixtures pass on the corrected candidate.
All seven biome/generator compatibility profiles also pass fresh winter/summer
checks. Native protected-cover rules were not changed.

## Two-client multiplayer

Two actual packaged Forge clients connected to a dedicated server and passed:

- A 365-day calendar, season/storm changes, manual weather precedence and sleep.
- Server biome-to-climate mapping and each client's local weather response.
- Creative operator versus guest permissions, controller placement and live GUI
  changes, including discarding edits.
- Personal Celsius/Fahrenheit displays, late-join temperature zones, crop
  suitability tooltips, held and placed thermometers, torch/furnace falloff,
  extinguishing/removing heaters, distant watched chunks, unloading and reconnects.

A matched two-player control pair measured 600 complete server ticks after
120 warm-up ticks. Mean elapsed tick time was **0.755 ms without weather** and
**0.786 ms with it**, an increase of **0.031 ms**. The 95th percentile was
**1.874 to 2.329 ms**, against Minecraft's 50 ms tick budget. This is one short
pair in a quiet flat world, not a loaded-server capacity test. Windows CPU
timer granularity makes its small CPU difference unsuitable for a speed claim.

Clients used a separate, non-input Windows desktop that was never switched to,
muted audio, a 10 FPS cap and one shared logical CPU. No computer-control session
or foreground game window was needed. All worlds were fresh disposable fixtures;
no player world was copied or launched. All test clients and servers were stopped.

## Candidate and retained evidence

The final build passes **57 unit tests**, **571,778 combined farming checks**
and process reload. Only `WeatherTransformer` and `PlantGrowthHooks` changed
in the release jar after the initial port qualification. Climate tuning,
renderer, public API, recipes and heating implementation are unchanged.

Main jar SHA-256:

```text
3b937e2d6da0d8802d165e3f564223a765e735c9984f3153d97a071d32db3779
```

Local raw logs, CSVs, source snapshots, calibration diagnostics, failed attempts
and artifact-bound receipts stay under the ignored
`build/verification/multiplayer-performance-1112` directory. Its `RESULT.md`
indexes the evidence. Test output, disposable worlds and probe jars are not
release contents or tracked repository files.
