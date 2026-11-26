# Weather events

Rain and snow use the weather at their own location, so storms remain
visible from a neighbouring clear biome. Distant rain and snow curtains extend
up to 192 blocks by default over loaded terrain; the distance is configurable
and Fast graphics uses a smaller range. See [distant weather](DISTANT_WEATHER.md).
Wind-driven snow, hail and dust particles use their spawn location's weather.
Sky colour, ambient sounds and immersion fog follow the camera's local
conditions. Mod-provided custom weather renderers retain their rendering path.




The Overworld samples a climate-specific schedule from the world seed and

Minecraft daylight clock. Every preset has its own four-phase event table.

Weather can span midnight. Up to one new primary event and one temperature

spell can start each Minecraft day. A new event replaces the earlier event in

its own lane. Cold air can persist under rain, allowing ordinary fronts to

produce snow; heatwaves can also coexist with storms. The primary event takes

precedence in the usual event readout. Sleeping skips

elapsed events and clears an active wet episode. `/weather` takes precedence

for its requested duration. The mod's `doSeasonalWeather` game rule suspends

the schedule and live feed when set to `false`, letting vanilla weather run.

Pausing daylight also pauses an episode's progress.



Convective showers, thunderstorms, hail and snow squalls travel in bands with

dry gaps. Fronts and monsoon bursts cover a much wider area. Biome, altitude

and shelter determine what falls at a position. Blizzards and snow squalls

fall where temperatures support snow. Freezing rain appears in a narrow

temperature band, with ordinary rain on the warm side and snow on the cold side.

Selected monsoon, savanna and desert events can reach dry biomes, including

vanilla savannas, deserts and mesas.

Winter storms carry a stronger cold-air anomaly, allowing occasional lowland

snow in mild oceanic winters without making the whole winter permanently cold.

Cold snaps and heatwaves shift the local temperature, affecting seasonal

freeze and thaw. Same-sign event and air-mass anomalies use the strongest

value, avoiding double cooling. A smooth day/night cycle is warmest at 15:00

and coldest at 05:00, with a smaller temperature range in wet climates and

during precipitation. These changes retain altitude and biome adjustments. Hail, dust and windblown snow have outdoor particle effects.



## Built-in event timing



Times are Minecraft ticks within a day for onsets, and game time at 20 ticks

per second for durations. Dawn is tick 0, noon is 6000 and sunset is 12000.

These durations and event frequencies are tuned for gameplay.



| Event ID | Typical onset | Duration | What players experience |

| --- | --- | --- | --- |

| `DRIZZLE` | Any time | 4–15 min | Light, persistent precipitation |

| `FRONTAL_PRECIPITATION` | Any time | 8–30 min | Broad, steady rain or snow |

| `CONVECTIVE_SHOWER` | Around noon to late afternoon | 26–95 sec | Sudden, locally heavy downpour |

| `THUNDERSTORM` | Noon to early evening | 48–150 sec | Moving rain and lightning |

| `SEVERE_THUNDERSTORM` | Afternoon into evening | 1.4–5.5 min | Heavier rain, poorer visibility and stronger wind |

| `HAILSTORM` | Around noon into early evening | 30–90 sec | Hail particles and lightning on the warm side of the snow line |

| `MONSOON_BURST` | Any time | 15–35 min | Sustained, widespread heavy rain |

| `TROPICAL_STORM` | Any time | 20–40 min | Widespread heavy rain, strong wind and lightning |

| `WINTER_STORM` | Any time | 10–31 min | Sustained winter precipitation, with rain below the snow line |

| `SNOW_SQUALL` | Any time | 30–105 sec | Moving, short bursts of snow and sharply reduced visibility |

| `FREEZING_RAIN` | Any time | 3.5–12 min | Liquid precipitation at near-freezing surface temperatures |

| `BLIZZARD` | Any time | 12–35 min | Long stretches of wind, snow and poor visibility |

| `RADIATION_FOG` | Late night into early morning | 2–4 min | Thickest at lower elevations, fading around dawn |

| `ADVECTION_FOG` | Any time | 4–18 min | Fog that can persist into daytime |

| `DUST_STORM` | Late morning into evening | 45 sec–6 min | Reduced visibility and windblown dust in drier biomes |

| `HEATWAVE` | Morning | 15–40 min | Warmer air, independently of precipitation |

| `COLD_SNAP` | Any time | 15–40 min | Colder air, independently of precipitation |

| `BLOWING_SNOW` | Any time | 3–15 min | Lower visibility and snow raised from snow-covered ground |



## Climate guide



| Preset | Characteristic weather |

| --- | --- |

| Tropical Rainforest | Frequent afternoon showers and thunder year-round, with occasional prolonged bursts |

| Tropical Monsoon | Short afternoon downpours and thunder in the wet phases, plus fewer, longer monsoon bursts and rare tropical storms |

| Tropical Savanna | Concentrated wet-season showers and thunderstorms, with heat and dust in the dry phase |

| Hot Desert | Mostly dry, with summer heatwaves, dust and isolated rain or thunder |

| Cold Desert | Dust and summer heat, with mostly dry winters punctuated by snow, squalls and cold snaps |

| Semi-Arid Steppe | Warm-season thunderstorms, occasional hail and dust, then colder spells and occasional winter snow |

| Mediterranean | Autumn and cool-season fronts, spring showers, and dry, hot summers |

| Oceanic – British Isles | Frontal rain, drizzle and autumn/winter fog, plus occasional summer thunder |

| Cool Maritime | Showers and fronts year-round, greater exposure to advection fog and occasional winter squalls |

| Humid Subtropical | Summer afternoon storms, rarer tropical storms in summer and autumn, and cooler-season fronts and fog |

| Subtropical Highland | Two rainier phases, with afternoon storms and morning fog |

| Humid Continental – Burlington | Summer thunderstorms and occasional hail, with winter snowstorms, squalls, blizzards and freezing rain |

| Central European – Warsaw | Summer convection and heat, autumn fog, winter mixed precipitation and occasional snowstorms |

| Snowy Continental – Sapporo | Frequent winter snow and squalls, autumn fronts and occasional late warm-season tropical storms |

| Subarctic | Long snow season with squalls and cold snaps, followed by a short period of showers and thunderstorms |

| Tundra | Winter snowfall and blowing snow, with advection fog and showers during the brief thaw |

| Ice Cap | Windblown snow and cold spells with sparse fresh snowfall |

| Alpine Highlands | Warm-season convection and hail, with a high-elevation snow line and frequent cold-season snowstorms |

| Winter Wonderland | Frequent snowfall, whiteouts and blowing snow in every phase |

| Storm Coast | Persistent fronts and fog, plus strong summer thunderstorms and autumn tropical storms |

| Golden Autumn | Frequent dawn fog through the long autumn |

| Eternal Spring | Gentle drizzle and showers, morning mist and occasional thunder |

| Endless Summer | A long hot season with afternoon storms mixed with heatwaves |

| Mistwood | Frequent dawn fog and persistent advection fog with little thunder |

| Thunderlands | Frequent thunder, with stronger storms and hail in warm phases |

| Two Winters | Two snow and blizzard seasons separated by two thaws with showers |

| Sunscorched Wastes | Prolonged heat and regular dust, with rare late hot-season thunderstorms |

| Short Seasons | Burlington's event table on a 96-Minecraft-day calendar |



## Climate balance and measurement



The stock library aims for recognisable, expressive weather while leaving

clear intervals. British Isles represents a northern British lowland climate,

with occasional snow-bearing cold spells and more snow on high ground. Its

legacy `oxford` ID is retained for existing mappings; it is no longer an Oxford

station simulation. Burlington, snowy continental and Two Winters have longer

winter cover; tropical climates retain sudden afternoon storms and wet/dry

contrasts. Dry climates emphasise rare rain and dust. Fantasy climates extend

these patterns, with longer thunder in Thunderlands and persistent Storm Coast

fronts. Preset-specific timing overrides the general table above.



`scripts/climate-objectives.json` declares gameplay acceptance envelopes for

all 28 entries before measurement. For example, British Isles targets 18–35

lowland dates with some falling snow per 365 Minecraft days, 35–85 at Y=200,

and 15–50 mornings with majority snow cover. These are realism-inspired design

ranges, not claimed station normals. Short Seasons counts are normalised to

365 Minecraft days for comparisons; its actual year remains 96 days.



The verifier counts midnight-to-midnight local precipitation, separates snow

falling from 09:00 snow cover, records local encounter duration after moving

storm gaps, and separates named fog from general rain/snow haze. A day with a

brief snow shower is not a whole day of snowfall. Storm durations at a point

can be shorter than their scheduled lifetimes. Temperature is in Minecraft

biome units, not measured Celsius. The retained liquid-equivalent “mm” proxy

is diagnostic only: no water accumulation or calibrated rain gauge is simulated.

The earlier station-fit tolerances in `scripts/weather-reference.json` and the

0.1.9 report are historical and no longer establish acceptance.



See [test assumptions and evidence](TESTING_ASSUMPTIONS.md) for source anchors,

observable definitions, scope and limitations.



## Editing event rows



Open **Manage Presets → Events** for the selected preset or edit its

`weatherEvents` list in the configuration file. The one-off copy in

**Edit Settings → Weather Events** belongs to the Custom profile. Duplicate a

preset to copy its complete event table. Saved settings apply after restart.



Each row begins with an event ID and four base daily weights from 0 to 100,

in the order of the preset's four phases. Missing event IDs stay disabled.

The schedule adjusts the weights with the climate's wetness, thunder and fog

values and blends the weights near phase boundaries. When the adjusted total

is below 100, the remainder is a chance of no new weather. Above 100 the

daily choice in that lane is split proportionally between event types. Below

full seasonal strength, the annual mean event weight is weighted by phase

length; unequal tropical wet/dry phases do not each count as a quarter-year.



```text

CONVECTIVE_SHOWER,17,30,20,1.5

CONVECTIVE_SHOWER,17,30,20,1.5,6200,11200,600,1400,0.7,0.95,include_dry_biomes

```



The second row shows the optional onset range (0–23999, inclusive), duration

range (200–48000 ticks) and peak intensity range (0.01–1). Use only one row

per event. Append `include_dry_biomes` to either form to allow the event's

precipitation in Forge-tagged dry, savanna and mesa biomes. Explicit biome

overrides take precedence. An empty list gives no scheduled events. Malformed rows are logged

and the climate uses its default table until the rows are corrected.



The simulation uses Minecraft's existing weather, snow, ice and lightning

mechanics. Wind is visual and available through the API. Freezing rain

uses existing surface freezing, and hail has no impact damage. The presets

model weather conditions with simplified fronts and cells. Local winds driven

by terrain, lake-effect bands, cyclone tracks, floodwater and tornadoes require

their own simulations.



Sources for the weather patterns include tropical afternoon convection and

monsoon surges, frontal versus convective rain, dawn and advection fog, snow

squalls, Atlantic tropical storms and polar blowing snow.

[Meteorological Service Singapore](https://www.weather.gov.sg/climate-climate-of-singapore)

[Australian Bureau of Meteorology](https://www.bom.gov.au/resources/learn-and-explore/climate-knowledge-centre/climate-factors/monsoon)

[Met Office rain and showers](https://weather.metoffice.gov.uk/learn-about/weather/types-of-weather/rain/rain-and-showers)

[Met Office fog](https://weather.metoffice.gov.uk/learn-about/weather/types-of-weather/fog)

[US National Weather Service](https://www.weather.gov/safety/winter-snow-squall)

[US National Hurricane Center](https://www.nhc.noaa.gov/climo/index.php)

[Australian Antarctic Program](https://www.antarctica.gov.au/about-antarctica/weather-and-climate/weather/)
