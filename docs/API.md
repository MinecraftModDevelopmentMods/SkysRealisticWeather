# Weather API, version 8

This guide targets Minecraft 1.11.2. Recompile consumers against the matching
API and Forge jars; the matching API number does not imply cross-Minecraft binary compatibility.

Use `zone.moddev.mc.skysrealisticweather.api`. The `-api.jar` in `build/libs`
is the compile-only artifact. Use the main mod jar at runtime.
Declare Forge loading order after `skysrealisticweather`.
For optional integrations, check `Loader.isModLoaded("skysrealisticweather")`
before loading a separate integration class that references the API.

```groovy
dependencies {
    compileOnly files('libs/SkysRealisticWeather-0.1.21.111021-api.jar')
}
```

The published compile-only coordinate is
`zone.moddev.mc.skysrealisticweather:SkysRealisticWeather:0.1.21.111021:api`.
Use the MMD Maven repository configured by your project. The API jar does not
replace the main mod at runtime.

API 8 retains the API 7 method signatures and appends `FIRE`, `LAVA` and `TORCH`
to `TemperatureSample.Source`. Local queries include their server-configured
warmth; outdoor and world-generation queries remain independent. The source ID
is `skysrealisticweather:heat/<type>/<x>/<y>/<z>` for these automatic sources.

API version 7 retained the API 6 signatures and added runtime
[local temperature zones](TEMPERATURE_ZONES.md), effective-temperature queries
and source metadata. Existing outdoor and generation temperature queries retain
their meaning. API 6 introduced the plant growth profiles and queries described
below. The thermal episode queries introduced in API 5 are
`getTemperatureEpisode(World, Biome)` and `getTemperatureEpisode(World, BlockPos)`.
The position overload requires a loaded position; the supplied-biome overload
does not load terrain. The primary weather episode query remains unchanged.
Current temperature includes day/night and air-mass effects; seasonal
temperature excludes both. Client and server must use the matching mod version.

`ClimateSample.mode` identifies the active climate mode and
`ClimateSample.climateId` identifies the resolved per-biome preset and
`phaseLabel` gives its local season or wet/dry phase name. Its season,
temperature and weather multipliers follow that local preset. Both climate
query overloads use the same mappings, including the supplied-biome world
generation query. `getSeason(World)` reports the shared calendar.

Use `WeatherApi.getWeatherEpisode(World, BlockPos)` for a loaded position or
`WeatherApi.getWeatherEpisode(World, Biome)` during generation. These return the
local climate's event. `getWeatherEpisode(World)` reports the Overworld carrier
event used for vanilla weather timers. The server sends its climate library,
biome mappings and schedule seed to clients.

| Entry point | Contract |
| --- | --- |
| `WeatherApi.getSeason(World)` | Immutable `SeasonSnapshot` with season, 1-based season day, length in Minecraft days and fractional progress in [0, 1). |
| `WeatherApi.getWeather(World, BlockPos)` | Immutable `LocalWeather` with precipitation, local temperature in Minecraft units, fog, falling intensity, outdoor wind, thunder and source (`VANILLA`, `SEASONAL` or `LIVE`). Includes shelter and local storm coverage. |
| `WeatherApi.getTemperature(World, BlockPos)` | Current temperature in Minecraft biome units at a loaded position, without sampling shelter or other weather values. |
| `WeatherApi.getLocalTemperature(World, BlockPos)` | Effective loaded-position temperature including zones and controlled climates; empty for absent terrain. |
| `WeatherApi.getTemperatureSample(World, BlockPos)` | Effective/outdoor temperature, source and winning zone ID. |
| `WeatherApi.putTemperatureZone(World, TemperatureZone)` and `removeTemperatureZone(World, ResourceLocation)` | Register/update/remove runtime cuboids on the server world's logical thread; synchronized to watching clients. |
| `WeatherApi.getClimate(World, BlockPos)` | Immutable `ClimateSample` at a loaded position. Includes the biome, altitude-adjusted vanilla temperature, seasonal temperature, biome modifiers and current open-sky weather. |
| `WeatherApi.getTemperature(World, BlockPos, Biome)` and `getClimate(World, BlockPos, Biome)` | The corresponding queries using the caller's biome and height. Call during terrain generation before the target chunk is populated or created. |
| `WeatherApi.getWeatherEpisode(World)` | Scheduled or forced `WeatherEpisode` with type, start tick, exclusive end tick, peak intensity and wind bearing. Empty for clear intervals, vanilla command weather, live weather and inactive worlds. |
| `WeatherApi.getBiomeClimate(Biome)` | The resolved temperature offset, seasonal scale and precipitation rule after mod loading. |
| `WeatherApi.getPopulationPolicy(World, Biome)` | The server's configured `NATIVE` or `SEASONAL` snow and ice policy for supported surface hooks. Empty on the client. |

## Queries during terrain generation

Pass the biome selected by your generator and the position being evaluated.
The supplied-biome overloads use the world's current climate frame and
never look up chunks, blocks, heightmaps or sky access. The caller can
apply its own shelter rules. A useful distinction for generation is the
biome's stable temperature, the seasonal adjustment and current weather.

```java
WeatherApi.getClimate(world, candidateSurface, selectedBiome).ifPresent(climate -> {
    float vanillaAtHeight = climate.vanillaTemperature;
    float withSeason = climate.seasonalTemperature;
    float current = climate.openSkyWeather.temperature;
    LocalWeather.Precipitation falling = climate.openSkyWeather.precipitation;
    // Use the level of variation appropriate to the feature being generated.
});
```

Use `vanillaTemperature` for a temperature input independent of in-game time.
`seasonalTemperature` applies the biome adjustment and season, excluding live
weather and temporary episode adjustments. The temperature in
`openSkyWeather` includes current conditions. `biomeRainfall` is the vanilla
biome parameter. Wetness and thunder are relative seasonal multipliers.
Temperature uses Minecraft biome units, with `WeatherApi.SNOW_LINE` at 0.15.

In Per Biome Climate, the seasonal temperature references Plains at 0.8 and
preserves the supplied biome's elevation departure. Baseline replacement is
scaled with seasonal strength and the registered seasonal scale. Configured
native snow biomes keep their original reference. Temperature offsets still
apply, and the runtime and supplied-biome generation queries share this path.
The server also synchronizes its native snow exceptions to clients. Universal
and live-weather temperature references retain their previous behavior.

## Runtime queries and events

Scheduled episodes use the daylight clock. Forced admin previews use total
world time. Use `episode.time(world.getWorldTime(), world.getTotalWorldTime())`
to choose the clock and pass the result to `episode.intensity(time)`.
`covers(x, z, time)` tests moving storm coverage. Use `getWeather` for
final precipitation and shelter at a position. Intensity, fog and wind use a
0–1 scale. The wind bearing describes where the wind is blowing toward,
clockwise from north. The original precipitation values remain available
alongside `HAIL` and `FREEZING_RAIN`.

Query on the world's logical thread. An unsupported or uninitialized world,
including a client awaiting server synchronization, returns an empty result.
Location queries that look up the biome or shelter require a loaded chunk.
Passive mode provides the calendar and vanilla weather. Use `getWeather` for
the precipitation, fog and wind actually reaching a loaded position.

Preset phase labels affect the configuration screens. API consumers use the
same four `Season` values in order, including for wet and dry climates.

Register a biome climate during initialization or post-initialization on both
physical sides. Use the biome's registered ID. Each ID accepts one override.

```java
WeatherApi.registerBiomeClimate(
    new ResourceLocation("example", "misty_highlands"),
    new BiomeClimate(-0.2F, 0.75F, BiomeClimate.PrecipitationRule.BIOME_DEFAULT)
);
```

The three values are temperature offset, multiplier for the seasonal
temperature offset and tint, and precipitation policy. Policies are
`BIOME_DEFAULT`, `ALLOW` and `DRY`. The configured biome rules are used when
seasonal hooks are active.

Subscribe on `MinecraftForge.EVENT_BUS` for these server events:

- `SeasonChangedEvent` fires after the server observes a season boundary. It
  includes the previous season and the current snapshot. A time command that
  jumps across more than one season reports the destination.
- `WeatherEpisodeChangedEvent` reports the previous and current scheduled or
  forced episode after the server updates its weather at the end of the tick.
  The current value is empty when the last episode ends. Use a weather query
  to read ordinary vanilla or live conditions.
- `SeasonalSurfaceEvent` fires before seasonal snow placement, water freezing
  or thaw, including seasonal deposits placed during chunk population.
  Cancel it to protect the position. Handlers should return promptly and
  avoid loading chunks or changing the target block.

Only the `api` package is a supported integration surface.

## Plant growth

Register selectors during initialization or post-initialization, on both physical
sides. Registration freezes at load completion and accepts one profile per
selector. Server configuration takes priority. A selector may include block state
properties. Unregistered, unmapped plants remain native.

```java
WeatherApi.registerPlantGrowthProfile("example:tomato",
    new PlantGrowthProfile(0.45F, 0.95F, 1.70F, 2.40F));
WeatherApi.registerPlantGrowthProfile("example:magic_crop", PlantGrowthProfile.EXEMPT);
WeatherApi.getPlantGrowth(world, pos).ifPresent(sample -> {
    float multiplier = sample.naturalGrowthMultiplier;
    // Apply only to a natural growth attempt after native eligibility succeeds.
});
```

`PlantGrowthSample` exposes `plantId`, `profileId`, `temperature`,
`naturalGrowthMultiplier`, `controlledClimate` and `reason`. Query it on the
world's logical thread at a loaded plant; it never loads chunks. Unsupported,
uninitialized or unloaded positions return empty, as do blocks without the
plant/growable contract or positions with no valid local temperature. An empty
sample leaves native growth in effect. Disabled rules return a sample with multiplier 1 and a
bypass reason. The sample describes natural growth suitability, independently
of light, hydration, maturity or the native random growth roll. Do not apply it
again to a growth path already covered by this mod.

Plant registration is separate from biome registration. During world generation
use the supplied-biome climate queries and `PlantGrowthProfile.multiplier` if a
custom generator needs a suitability decision before a plant exists. The runtime
plant query requires an existing plant, including its state.
See [farming](FARMING.md) for the native hook and fertiliser contracts.

Player-facing displays may use Celsius or Fahrenheit. All API temperature values
and `PlantGrowthProfile` knots remain in Minecraft units, independently of the
viewer's display preference. Do not apply display conversions to growth decisions.
