# Modpack support (Minecraft 1.11.2)

All integrations are optional. Install their original jars alongside Sky's
Realistic Weather; none are bundled. The `integrations` config category can
disable each bridge. Restart after changing these flags or rule definitions.
The default `runClient` launches this mod alone. Separate Eclipse launch files
cover CraftTweaker, ToughAsNails, JEI, Hwyla, TheOneProbe and PackSupport
(all five plus the farming mods).

## CraftTweaker 3

Target: CraftTweaker 1.11.2 **3.1.0-legacy** (`crafttweaker`). Example:

```zenscript
import mods.skysrealisticweather.Climate;

// Absolute Celsius temperatures, regardless of a player's display units.
// Selector syntax matches plant_growth.mappings, including block properties.
Climate.setCropTemperatureCelsius("minecraft:wheat", -5, 5, 25, 40);
Climate.exemptCrop("minecraft:nether_wart");
Climate.mapBiome("minecraft:plains", "central_european");
```

Place this in the pack's `scripts` directory as a `.zs` file. Crop definitions
resolve in this order: **script, config, Java API registration, bundled rule**.
Specific property selectors beat general selectors within the same source.
Unknown plants keep native growth; magical crops remain exempt unless mapped.
Sky never destroys crops, speeds them above native growth or replaces native
soil, light, genetics and fertiliser checks.

The script actions support CraftTweaker undo/reload. While a world is running,
changed definitions are held pending its next restart. Weather, existing crop
rules and connected clients keep the active snapshot. `/skyweather validate`
reports pending changes. An unchanged reload does not require a restart.

CraftTweaker 3 runs its initial scripts **after spawn terrain preparation**.
Script biome mappings apply before gameplay and to subsequently generated
chunks. Put mappings needed for initial spawn generation in
`biome_climates` in Sky's config. Existing terrain is never regenerated.

Read-only queries accept a CraftTweaker `IPlayer`:

```zenscript
// Use from an event that supplies a player, rather than at script load time.
// Climate.getSeason(player) -> "spring", "summer", "autumn", "winter", or "unavailable"
// Climate.getLocalClimate(player) -> IData map
// Climate.getPlantGrowth(player, x, y, z) -> IData map
```

Check `available` before using results. Local climate includes
`temperatureCelsius`, `source`, `precipitation`, `climate` and `season` when
available. Plant growth includes `multiplier` and `reason`. These queries
inspect loaded positions only; they do not generate terrain.

## World-specific settings

Controller edits are previews until **Save to world** or
`/skyweather world save`. Only edited fields are stored, so untouched values
inherit future pack changes. A chosen preset is stored by ID and inherits
future edits to that definition; explicitly changed phase fields override it.
World fields take precedence over pack settings. They do not replace crop or
biome definition banks.

**Discard** or `/skyweather world discard` restores the last saved settings.
`/skyweather world reset` removes the saved overrides and restores pack defaults.
An unsupported/malformed saved record is retained unchanged and reported;
explicit reset is required before replacing it. Save files live in the world's
normal `data/skysrealisticweather_settings.dat` storage.

Calendar controls use a saved, whole-day seasonal offset. They preserve the
Minecraft daylight clock; `/time`, sleep and `doDaylightCycle` retain their
normal clock effects. Forced storm previews expire on elapsed server ticks and
are never saved. Changing to a preset with different phase lengths can change
the season represented by the same date.

`calendar.startingSeason` can choose a starting phase for new worlds. The
default `WORLD_CLOCK` preserves existing behaviour. It never resets the season
on an existing world. The default calendar remains 365 Minecraft days.

## Tough As Nails

Target: **Tough As Nails 1.11.2-2.1.0.65** on Minecraft 1.11.2. When present, the
bridge uses Sky's effective local temperature, including registered heated
zones and burning furnaces, as TAN's ambient target. TAN retains its body
temperature, response timer, clothing, wetness, proximity and other modifiers,
health and thirst systems. Sky adds only its own named ambient correction,
at most once per second per player. This does not replace TAN's UI or set the
player's body temperature directly.

While Sky's weather is active, the bridge temporarily disables TAN's seasonal
feature in its synchronized runtime config, including TAN winter crop death
and seasonal snow. Sky supplies those seasons. The previous TAN option is
restored when Sky is passive or closes the world; TAN's config file is not
rewritten. Set `integrations.toughasnails=false` to retain TAN's own behaviour.
Missing capabilities, unloaded sampling positions and unsupported worlds use
native TAN behaviour. An incompatible bridge disables itself and logs why.

## Farming information

JEI **4.5.1.296** adds a **Climate requirements** category for recognised plant
items once a world is available. It shows preferred temperatures, current
local suitability and typical phase-midpoint temperatures at the player's
position. Phase estimates exclude weather episodes and local heating; native
soil/light requirements still apply. Moving, changing season or heating an
area changes the displayed live reading. The item catalogue is indexed once
per joined world, rather than scanned every frame.
The compact recipe panel includes a **?** marker: hover over it for full
details, including the local temperature source. The summary stays readable
while the pointer is over its ordinary text.

Hwyla **1.8.22-B37** and The One Probe **1.4.19** show the same current crop
requirements and growth status when looking at a plant. Rules and climate data
come from the server; each player sees Celsius or Fahrenheit according to their
existing preference. An unavailable temperature never becomes a false
grow/do-not-grow claim. These interfaces need no operator permission.
Hwyla uses server NBT for tile-based plants; ordinary crops use the same
synchronized client climate calculation as inventory tooltips because legacy
Hwyla does not request NBT for blocks without tile entities.

## Diagnostics

Operators and the server console can run:

- `/skyweather validate`: summarise invalid/unresolved rules, missing IDs,
  unavailable mercury, saved-data issues and scripts awaiting restart.
- `/skyweather dump`: write a JSON report below the world's
  `skyweather-reports` directory, with resolved biome climates and crop states,
  rule sources/precedence, integration/hook status, calendar offset and saved
  field overrides.

Validation is read-only: it does not repair config, load chunks or alter
weather. Optional absent-mod IDs can be harmless warnings. Reports omit player
data and real-world location coordinates. Inspect the normal log for complete
startup errors and third-party warnings.

For local packaged checks, build `check preparePackProbe`, then run
`scripts/Run-PackChecks.py --profile combined --phase contracts --output
build/verification/pack-support/my-check`. Run the same command with
`--phase reload` afterwards. Profiles also include `core`, `crafttweaker`,
`tan`, `jei`, `hwyla` and `top`; `full` adds the supported farming mods to the
combined profile. These hidden native server tests use one CPU at
BelowNormal priority and disposable worlds. Server checks do not establish
visual acceptance of client interfaces.
