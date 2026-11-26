# Climate and farming

Natural plant growth follows the local temperature, including biome climate,
altitude, seasons, day/night changes and cold or hot spells. Suitable conditions
retain the plant's normal growth rate. Less suitable temperatures slow growth;
conditions beyond its limits pause it. Plants are never destroyed by this feature.
Soil, water, light, growth stages, genetic stats and native growth requirements
still apply. Bonemeal and direct fertiliser keep their native behaviour and
eligibility. Machines that invoke the ordinary natural-growth path follow the
climate rules.

Registered [temperature zones](TEMPERATURE_ZONES.md) and burning vanilla furnace
zones can override that temperature. The same effective temperature reaches
crop tooltips, thermometers and the compact HUD. F3 and `/skyweather climate`
identify the local source alongside the outdoor climate. Forestry-controlled
positions retain precedence.

## Supported paths

| Plants or system | Integration |
| --- | --- |
| Vanilla crops, stems, cocoa, reeds, cactus and plants using Forge's crop event | Natural growth suitability through the Forge event; respect another mod's explicit ALLOW or DENY. |
| Vanilla and compatible saplings | Forge sapling event, with state-specific tree profiles. |
| Pam's HarvestCraft field crops and fruit | Natural growth rolls, including fruit logs. Saplings use the Forge sapling event. |
| AgriCraft | The seeded, immature natural crop-stick callback. Seed identity selects the profile. Mature plants, cross-crop processing, genetics and direct growth/fertiliser calls retain native behaviour. |
| Mystical Agriculture | Resource crops are exempt by default. An explicit mapping can opt them into a temperature profile. |
| Forestry | Ambient temperature and a seasonal humidity proxy reach Forestry's climate manager, including consumers such as bees. Its controlled climate positions retain their own temperature and humidity. Ordinary mapped crops also recognise a Forestry controlled temperature at their position. |

The pinned Minecraft 1.10.2 targets are HarvestCraft **1.10.2j**, AgriCraft
**2.0.0-0.11.0-a21** with InfinityLib **0.11.0**, Mystical Agriculture
**1.5.10**, and Forestry **5.2.17.388**. Other releases need their own
compatibility check.

The optional adapters use narrow runtime hooks inside this mod. Downloaded
third-party jars and sources are not edited or bundled. A hook whose expected
signature differs falls back independently to native behaviour and reports its
status in the log and `/skyweather status`.

Forestry humidity uses `clamp(0.5 * seasonal wetness, 0, 1)`, blended with native
biome rainfall by seasonal strength, capped at 1. It is a gameplay proxy, not
relative humidity or a new soil moisture model. The real-world feed supplies no
humidity reading to this integration, so the configured seasonal wetness remains
the fallback. Forestry's own species tolerances and controlled rooms remain
responsible for their normal eligibility decisions.

## Configuration

Open **Mods → Sky's Realistic Weather → Config → Configure selected mode →
Plant Growth** (via **Edit Settings** in Universal Climate or **General settings**
in Real World Climate), or edit the
`plant_growth` category in `config/skysrealisticweather.cfg` with the server
stopped. The master `enabled` flag and individual `harvestcraft`, `agricraft`,
`mystical` and `forestry` switches are independent of the weather system.
Passive mode also leaves growth native. The server sends its rules to clients.

Profiles are editable rows with five comma-separated fields:
`id,minimum,ideal minimum,ideal maximum,maximum`. Temperatures are Minecraft
biome units; snow's familiar threshold is 0.15. The multiplier rises linearly from
0 to 1 between minimum and ideal minimum, stays at 1 in the ideal range, then
falls to 0 at maximum. These values are gameplay choices inspired by plant
preferences, not agronomic temperature limits.

| Profile | Minimum | Ideal range | Maximum |
| --- | ---: | ---: | ---: |
| cool | 0.05 | 0.40–1.10 | 1.70 |
| temperate | 0.15 | 0.60–1.30 | 2.00 |
| warm | 0.45 | 0.95–1.70 | 2.40 |
| tropical | 0.70 | 1.05–1.85 | 2.60 |
| desert | 0.25 | 1.00–2.20 | 3.20 |
| exempt | Always native | Always native | Always native |

The `mappings` list contains overrides, for example:

```text
minecraft:wheat=cool
minecraft:sapling[type=spruce]=cool
harvestcraft:pamtomatocrop=warm
agricraft:tomato_plant=warm
mysticalagriculture:inferium_crop=exempt
```

AgriCraft selectors use `agricraft:<plant ID>` from its plant definitions, rather
than the shared crop-stick block ID. A block selector can provide a fallback.
State selectors are more specific than a plain block ID within the same source.
Config overrides API registrations, which override the bundled mappings. The
last equally specific matching row wins. Unknown plants keep native growth.
Invalid rows remain in the config for correction; unresolved profiles keep
native growth. Magical seed namespaces remain exempt unless a rule explicitly
selects another profile. The bundled plant list is in
`assets/skysrealisticweather/plant_growth.json` inside the mod jar.

`/skyweather crop` inspects the plant under an operator's crosshair;
`/skyweather crop x y z` inspects a loaded position. It reports the resolved
plant/profile, temperature, controlled climate source, natural-growth percentage
and reason. This percentage multiplies an eligible native growth attempt; it is
not a promise of growth every tick. The Weather Controller's **Effects** page and
`/skyweather option plants false` toggle the feature for the session. Use normal
configuration for lasting defaults.

## Seed and crop tooltips

Hover a seed, sapling or harvested crop in your inventory to see its preferred
temperature range, lower/upper growth limits and suitability at your current
position. Values use **Celsius** by default; choose **Fahrenheit** in
**Mods → Sky's Realistic Weather → Config → Display / units**. This preference
also applies to the HUD, inspection commands and temperature editors. Green means normal natural growth; yellow means slowed growth; red means
it is paused by cold or heat. Growth stops at or beyond either limit. The
percentage applies to an eligible native growth attempt: soil, light, irrigation,
maturity and other native conditions still matter. Fertiliser keeps its native rules.

Tooltips use the current synchronized server profiles and local temperature,
including altitude, biome, current weather and Forestry controlled positions.
They update as you move or conditions change, without placing a plant. Disabled,
exempt and unmapped plants are labelled accordingly. If the local climate has
not synchronized, or a biome supplies an invalid temperature, the tooltip reports
that it is unavailable. Missing temperature data leaves native growth in effect;
climate rules resume when a valid reading returns.

Vanilla and supported farming seeds, sapling variants, HarvestCraft produce,
Mystical Agriculture crops/essences and AgriCraft's NBT seed identities are
recognised. Tagged AgriCraft seeds label their crop-stick growth rules; ordinary
planting outside crop sticks uses the underlying plant's own rules. Crafted foods do not acquire crop tooltips. When several plants
produce the same item and have different climate rules, the tooltip directs you
to their seeds rather than picking one. Native plantable items from other mods
can use the existing profile mappings; unrelated custom item encodings may need
an adapter.

## Scope

No new watering, room-detection, crop death or yield system is introduced. Vanilla
farmland already responds to rain and nearby water, including whether that water
has frozen. A roof alone does not create a heated growing space; a child mod can
manage one through the temperature-zone API. Other farming
mods must use a supported natural path or the public API to participate; an
unrelated custom growth implementation remains native.
