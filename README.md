# Sky's Realistic Weather

Sky's Realistic Weather brings spring, summer, autumn and winter to the
Overworld. Highlands can see snow during the same storm that brings
rain to a valley. Winter can freeze exposed water and leave a dusting of snow.
As the weather warms, snow and ice placed by the mod gradually thaw.
Newly explored terrain can arrive with seasonal snow and ice. Polar and
glacier biomes keep their native cover.

Per Biome Climate is the default. Tropical biomes use tropical rainfall,
deserts have sparse storms, temperate forests have four seasons, and polar
biomes stay cold. Universal Climate applies one selected preset throughout
the Overworld. Real World Climate follows a configured location.
Each climate has its own weather
schedule, including moving afternoon showers, prolonged rain, snow squalls,
fog, dust storms and more. Rain and snow remain visible across biome boundaries,
with distant curtains extending up to 192 blocks by default over loaded terrain.
The renderer uses cached samples and coarser detail with distance; Fast graphics
uses a smaller range. See [distant weather](docs/DISTANT_WEATHER.md).
Browse 18 real-world climates from
Tropical Rainforest to Ice Cap, or choose from nine fantasy climates such as
Storm Coast and Two Winters. The standard year lasts 365 Minecraft days.
**Short Seasons** gives Burlington a 96-day year.
Day/night temperature changes and independent cold spells make weather
transitions more noticeable. The British Isles preset represents northern
British lowlands, with snowier uplands. See the [climate balance and test
assumptions](docs/TESTING_ASSUMPTIONS.md). The local 1.11.2 candidate also has
[two-client multiplayer and whole-chunk performance results](docs/PERFORMANCE_1_11_2.md).

Natural plant growth also follows local temperature. Cool-weather crops, warm
crops and fruit use editable profiles; fertiliser keeps its native behaviour.
Terrain providers can still generate their normal forests and parks in winter.
Optional integrations cover HarvestCraft, Mystical Agriculture and
Forestry. Hover seeds, saplings and crops to see preferred temperatures and
whether the current local temperature suits them. Celsius is the default;
choose Fahrenheit through **Config → Display / units**. See
[climate and farming](docs/FARMING.md).
If a biome supplies an invalid temperature, crops keep their native growth
behaviour until a valid local reading becomes available.

Craft a **Thermometer** with two glass blocks above a mercury ingot in a
vertical column. Mercury must come from Base Metals or another mod registering
`ingotMercury` in the ore dictionary. Hold it in either hand for a small live
temperature gauge, or place it on a floor or wall. Looking at a placed
thermometer or right-clicking it gives a local reading. It uses your Celsius
or Fahrenheit preference and needs no operator permissions. See
[thermometers](docs/THERMOMETER.md).

Burning vanilla furnaces, fire, lava and ordinary torches add warmth to the local
temperature, tapering with distance, for crops and temperature readouts. Each
source has a configurable bonus and radius; overlapping heaters use the strongest
bonus. Other mods can register their own
3D temperature zones through the
API, for example to manage a heated greenhouse. See
[local temperature zones](docs/TEMPERATURE_ZONES.md).

Modpacks can define crop temperatures and biome climates through CraftTweaker
3. Tough As Nails can use the local weather temperature while retaining its
body temperature and survival systems. JEI, Hwyla and The One Probe provide
crop climate information. Operator validation and JSON reports help diagnose
pack rules. See [modpack support](docs/PACK_SUPPORT.md).

This is the local **1.11.2 port**, version **0.1.21.111021**. See the
[port status and integration matrix](docs/PORTING_1_11_2.md) for tested builds
and remaining acceptance checks.

## Install

Use Minecraft **1.11.2**, Forge **13.20.1.2588** and **Java 8**. Put the main jar
in the `mods` folder on both the client and the server. The mod works in
single-player, LAN and dedicated-server worlds. It has no required content mods.

Choose **Mods → Sky's Realistic Weather → Config** to select a mode, then
configure its biome mappings, universal preset or real-world location.
**Manage climate presets** lets you add, duplicate and edit entries. For a dedicated
server, edit `config/skysrealisticweather.cfg` while the server is stopped.
See [configuration](docs/CONFIGURATION.md).

Operators can place a Weather Controller from **Sky's Weather Admin** or
open it with `/skyweather gui`. It previews weather and presets live, edits
climate settings live, and jumps between seasons. Save explicit edits to the
world, or discard them to restore saved settings. Calendar controls preserve
the daylight clock.
Use `/skyweather climate` or the F3 climate readout to inspect local conditions.

An optional server-side Open-Meteo feed can mirror modelled weather at a
selected real-world location. Offline seasonal weather is the default.
Weather data provided by [Open-Meteo.com](https://open-meteo.com/).

- [Mod API](docs/API.md)
- [Weather events by climate](docs/WEATHER.md)
- [Weather controller and server commands](docs/ADMIN.md)
- [Build and verification](docs/BUILDING.md)
- [Languages and translation coverage](docs/LANGUAGES.md)
- [Licence](LICENSE) and [credits](NOTICE)
