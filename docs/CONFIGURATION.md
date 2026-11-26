# Configuration

In **Mods → Sky's Realistic Weather → Config**, select the climate mode first,
then **Configure selected mode**. Restart Minecraft or the dedicated server to
apply saved changes.

Client display settings apply immediately. **Display / units** includes
`distantWeatherDistance`: 192 blocks by default, adjustable from 0 (off) to 256.
Fast graphics caps it at 96 blocks. Terrain view distance and loaded chunks
also bound the visible range. See [distant weather](DISTANT_WEATHER.md).

The server's `heating` category controls [temperature zones and furnace heating](TEMPERATURE_ZONES.md).
Defaults are enabled, a two-block furnace radius and a 20 °C minimum. These
settings require a restart; the furnace temperature editor follows your display
unit preference, while the file stores Celsius.

| Mode | Settings |
| --- | --- |
| **Per Biome Climate**, the default | Biome-to-preset mappings, shared calendar length, seasonal strength and effects. Each mapped climate supplies its own temperature curve and weather schedule. |
| **Universal Climate** | A single selected climate preset supplies the Overworld's seasons and events. Local biome and elevation adjustments still apply. |
| **Real World Climate** | A real-world location supplies live weather. Select a fallback climate for the initial request or unavailable data. |

In Universal Climate or the real-world fallback picker, browse the scrollable
list and select an entry to preview its phase names and year length. Click
**Use Preset** to select it.
On multiplayer servers, everyone receives the server's gameplay settings.
Operators can try changes without saving them through the
[Weather Controller](ADMIN.md).

**Manage climate presets** opens **New**, **Duplicate**, **Edit** and **Events**. New presets start
with Burlington's settings. Duplicates start with the selected preset's
settings. Use **Overview** to change the name, description or strength, and
the four phase tabs to edit the display names and weather values. **Done**
saves the draft. **Cancel** or Escape closes it without saving. Use **Events**
to edit the selected preset's event rows. [Weather event settings](WEATHER.md)

The active choice follows the saved preset. Renaming or editing that entry
also updates the active choice. **Edit Settings** is for one-off adjustments.
Changing its climate parameters, phase names or event rows saves an independent **Custom**
profile. Changing general gameplay options keeps the preset selected.

| Section | Setting | Effect |
| --- | --- | --- |
| General | `strength` | 0–2, default 1. Scales changes in temperature, wetness, thunder, fog and foliage. |
| General | `biomeStrength` | 0–2, default 1. Seasonal strength in Per Biome Climate, stored independently of the universal preset. |
| General | `climateMode` | `PER_BIOME`, `UNIVERSAL` or `REAL_WORLD`. Defaults to `PER_BIOME`. |
| Calendar | `springDays`, `summerDays`, `autumnDays`, `winterDays` | Shared year lengths for Per Biome Climate. Defaults to 92, 92, 91 and 90 Minecraft days. |
| General | `surface` | Adds seasonal snow, freezing and thaw. On by default. |
| General | `seasonalWorldgen` | Uses local weather for surface snow and ice during new chunk population. On by default, and requires `surface`. |
| General | `nativeSnowBiomes` | Registry IDs that keep native snow/ice generation and their native temperature reference in Per Biome Climate. |
| General | `foliage`, `fog` | Biome-coloured grass and leaves and outdoor fog. Both on by default. |
| General | `scheduledWeather` | Uses the climate's event schedule by default. Turn it off to use the original seasonal rain and thunder timers. |
| General | `passive` | Calendar and API remain available. Weather, visuals and surface behaviour follow vanilla or your other weather mod. Off by default. |
| Spring through winter | `days` | 1–365 Minecraft days per season. Standard presets use 92 spring, 92 summer, 91 autumn and 90 winter days. |
| Spring through winter | `temperature` | Seasonal temperature offset, in Minecraft biome temperature units, from –2 to 2. Lower values favour snow. |
| Spring through winter | `wetness`, `thunder` | 0.25–4. Higher values increase wet and thunder event frequency. In timer mode they also lengthen rain and thunder. |
| Spring through winter | `fog` | 0–1. Controls the frequency and density of the preset's scheduled fog events, or dawn and storm mist in timer mode. |

## Editing the config file

The `biome_climates` section maps biome registry IDs to climate preset IDs.
Startup adds missing entries for registered vanilla and mod biomes. The starter
mappings cover vanilla, Biomes O' Plenty and known Overworld biomes from
Thaumcraft, AbyssalCraft and Vampirism. Other registered biomes use Forge tags,
temperature, rainfall and descriptive biome names to choose a default.
GeographiCraft and BiomeTweaker edits are read before these defaults are
generated. Existing mapping entries take precedence.

```text
biome_climates {
    S:"minecraft:jungle"=tropical_rainforest
    S:"minecraft:desert"=hot_desert
    S:"biomesoplenty:temperate_rainforest"=cool_maritime
    S:"biomesoplenty:alps"=alpine_highlands
    S:"example:enchanted_forest"=mistwood
}
```

Values can reference any saved preset, including edited and fantasy entries.
Remove an entry to regenerate its automatic default on the next startup.
Unknown preset IDs are logged and use automatic classification. OTG virtual
biomes without registry IDs use their own supplied metadata during generation.
All modes retain the configured native snow and glacier exceptions.

The shared year defaults to 365 Minecraft days. Per-biome climates retain their
own phase proportions within that year, including tropical wet and dry phases.
Changing the shared calendar length scales those local phase lengths. The
selected universal preset stays saved while you use Per Biome Climate.

Stop the game or server and open `config/skysrealisticweather.cfg`. The
`presets` section contains the complete starter library. Copy an existing
preset block, give the copy a unique section ID such as `harbour_weather`, and
edit its fields. Keep the four nested sections named `spring`, `summer`,
`autumn` and `winter`. Change each section's `label` to set its display name.

- Inside a preset, `name` appears in the picker, `description` appears in the
  preview, and `strength` applies when the preset is selected.
- Set `group` to `tropical`, `dry`, `temperate`, `continental`, `polar`,
  `highlands`, `fantasy`, `calendar` or `user`. Omitting it puts the entry
  under **User Presets**.
- Set `general.activePreset` to the section ID, such as `burlington`,
  `mediterranean` or `harbour_weather`. IDs use lowercase letters, digits,
  underscores and hyphens, beginning with a letter or digit. Keep the same ID
  to rename a preset. The game logs invalid entries by ID and leaves them in
  the file for correction.
- Set `general.activePreset=custom` to use `general.strength`, the four
  top-level season sections and `weather_events.weatherEvents`. With a preset selected, those values
  hold a copy of the last valid active profile. If the selected preset is
  removed or becomes invalid, the game keeps that copy as Custom.

Unchanged presets from 0.1.0 or 0.1.1 link to their new library entries on
upgrade. Existing custom settings are kept. Stock 24-day seasons from
0.1.0 adopt the 365-day calendar. The saved world day may correspond to a
different season after a calendar change. **Short Seasons** gives Burlington
four 24-day seasons.

## Starter climates

The real-world presets follow the major Köppen–Geiger climate groups, plus
an Alpine Highlands preset for elevation-driven weather. The library covers
tropical rainforest, monsoon and savanna, hot and cold deserts and semi-arid
steppe, Mediterranean and maritime climates, humid subtropical and subtropical
highland climates, humid and snowy continental climates, Central Europe,
subarctic, tundra and ice cap. [Köppen–Geiger classification](https://pmc.ncbi.nlm.nih.gov/articles/PMC10593765/#Tab1)

Fantasy choices include Winter Wonderland, Storm Coast, Golden Autumn,
Eternal Spring, Endless Summer, Mistwood, Thunderlands, Two Winters and
Sunscorched Wastes. All are editable in the same library.

## Calendar and world effects

The standard 365-day year follows the lengths of Northern Hemisphere
meteorological seasons in a common year. Day zero is the first day of the
first phase. Tropical presets use rain-phase names in the picker and editor.
For example, Tropical Monsoon runs through Early Wet, Peak Wet, Late Wet and
Dry. Sleeping advances Minecraft time and the calendar. `/time` can move the
calendar in either direction.
`doDaylightCycle=false` pauses the calendar. With custom season lengths,
the same world day may fall in a different season. The year wraps after the
four configured lengths.

In Per Biome Climate, a mapped preset uses the Plains temperature reference
(0.8) while retaining the biome's local elevation departure and registered
temperature modifiers. This avoids adding a subarctic curve to an already-cold
forest baseline twice. The replacement blends in with seasonal strength; at
strength zero, vanilla temperatures remain. Entries in `nativeSnowBiomes`
retain their own baseline, keeping the Arctic and glacier exceptions.
Universal Climate and Real World Climate retain biome temperature departures.
Selected events can bring rain to dry biomes.
A rain-capable lowland
can get snow in cold weather and a high peak can cool below its surrounding
valley. New snow and ice are standard Minecraft blocks. The mod tracks deposits
it placed, so later warm weather thaws those deposits and restores frozen water
sources. Vanilla light-driven melting also applies. Only loaded, exposed
surfaces are eligible. If you disable seasonal surface changes or remove the
mod, existing snow and ice behave as standard Minecraft blocks.

### Snow and ice on new chunks

The standard Overworld, Biomes O' Plenty, RTG, The Lost Cities and Open Terrain
Generator v21 use local weather during their snow and ice pass. A cold storm can
leave a fresh snow layer on newly explored grassland. Cold, clear weather
can freeze exposed water, and naturally cold surfaces can retain a dusting
between storms. Warm weather skips the seasonal surface deposits. The mod
tracks these layers and frozen sources so they can thaw later, including
after a save and reload. Forge and biome eligibility checks still apply.

By default, Ice Plains, Ice Mountains, Ice Spikes, Frozen Ocean, Frozen River
and the Biomes O' Plenty Glacier keep their own native snow and ice. Cold
Taiga follows the seasonal rules. Edit `general.nativeSnowBiomes` to add or
remove exceptions by registry ID. The list controls the generator's surface
pass and preserves that biome's native temperature reference in Per Biome
Climate. Falling precipitation still uses local weather. The server sends
these exceptions to clients along with the climate library.

Version 0.1.11 uses config version 6, adding the editable `plant_growth`
category without replacing existing climate edits. See [farming](FARMING.md)
for profiles, mappings and adapter switches. Untouched stock event tables in versions
2, 3 and 4 adopt the revised climate balance. Untouched version 4 stock
profiles also adopt the revised temperature curves while retaining phase
labels. Edited event tables and profiles remain authoritative; custom presets,
biome mappings and native snow exceptions are retained. The British Isles
preset keeps the `oxford` ID but now represents northern British lowlands.
To adopt the complete new defaults for a deliberately edited preset, reset or
replace that entry after saving a copy of your configuration.

Set `general.seasonalWorldgen=false` to keep native chunk decoration while
using seasonal snow and thaw during normal play. Superflat and generators
without a seasonal integration use their own population rules.

For results with other Overworld generators and biome mods, see the
[Minecraft 1.10.2 biome compatibility checks](BIOME_COMPATIBILITY.md).

The presets use Minecraft temperature and weather multipliers. Regional
profiles take their seasonal patterns from published climate normals and
weather summaries, scaled to Minecraft. Thunder and fog are tuned for gameplay.
[NOAA Burlington normals](https://www.ncei.noaa.gov/access/services/data/v1?dataset=normals-monthly-1991-2020&endDate=9996-12-31&format=pdf&startDate=0001-01-01&stations=USW00014742)
[Met Office Oxford normals](https://www.metoffice.gov.uk/research/climate/maps-and-data/location-specific-long-term-averages/gcpn7mp10)
[Madrid normals, AEMET](https://www.aemet.es/en/serviciosclimaticos/datosclimatologicos/valoresclimatologicos?k=val&l=3195)
[Faroe Islands climate](https://www.faroeislands.fo/nature-environment/weather)
[New Orleans normals](https://www.weather.gov/lix/newnormals)
[Polish climate normals](https://klimat.imgw.pl/en)
[Sapporo normals](https://www.data.jma.go.jp/stats/en/normal/normal.html)
[Singapore climate](https://www.weather.gov.sg/climate-climate-of-singapore)
[South Florida wet season](https://www.weather.gov/media/mfl/news/RainySeasonOutlook2024.pdf)

## Real-world weather

Select **Real World Climate → Configure selected mode → Location and feed**,
then choose `BURLINGTON`, `OXFORD` or
`CUSTOM`. For `CUSTOM`, enter decimal latitude (–90 through 90) and longitude
(–180 through 180). The game server requests current model data for the location
about every 15 minutes while an Overworld is open. Requests contain the selected
coordinates and run asynchronously. The season continues on Minecraft time.

The reference lowland mirrors the feed's clear conditions, fog, rain, snow or
thunder. Biomes and elevation still affect local effects. `/weather` can take
temporary control. Until the first valid reading, and once the last reading is
more than 45 minutes old, the seasonal simulation supplies the weather.

Selecting Real World Climate enables requests to Open-Meteo. The older
`live_weather.enabled` setting migrates existing live-weather installations to
this mode. Other existing installations adopt Per Biome Climate and retain
their selected universal preset and custom definitions.

Weather data provided by [Open-Meteo.com](https://open-meteo.com/), transformed
into Minecraft effects. The free endpoint is for non-commercial use under
[Open-Meteo's terms](https://open-meteo.com/en/terms). Current conditions are
weather-model data. [API documentation](https://open-meteo.com/en/docs)

## Temperature display units

Open **Mods → Sky's Realistic Weather → Config → Display / units** and select
`CELSIUS` (default) or `FAHRENHEIT` for `temperatureUnit`. The same preference is
available under **Display and Climate HUD** in the mode settings. Display changes
apply immediately. Each multiplayer client has its own preference; the server
uses it for that player's `/skyweather climate` and `/skyweather crop` output.
The server's own config choice controls console output. The player's preference
is retained across respawns and refreshed from the client after connecting.

Tooltips, the compact/F3 HUD, the Weather Controller and temperature editors use
the selected units. Seasonal offsets are temperature **changes**: +10 °C is
+18 °F, without the +32 used for an absolute temperature. The live-feed scale is
used consistently, with 0.15 MC representing 0 °C / 32 °F and 0.045 MC per °C.
This is the mod's gameplay temperature scale.

Config files and the public API retain Minecraft units for compatibility. GUI
temperature values and plant-profile rows convert on display/save; simply changing
display units or saving an unedited editor does not change the climate values.
The raw `/skyweather phase` command continues to accept Minecraft temperature
offsets for existing scripts; the Weather Controller converts its displayed
Celsius/Fahrenheit adjustment before sending that command.
