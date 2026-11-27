# Local temperature zones

API 8 lets another mod register an inclusive 3D cuboid with a fixed temperature.
The zone supplies the effective temperature for supported crop growth, plant
tooltips, thermometers and the compact HUD. F3 and `/skyweather climate` also
report the effective temperature, outdoor temperature and its source/zone ID.
Forestry ambient climate consumers also see zones through the existing adapter;
Forestry's own controlled positions retain precedence.

This version assigns a temperature directly. It does not detect rooms, check
walls, diffuse heat, accumulate warmth or model ventilation. A child mod can
calculate an enclosure and register or remove its corresponding zone. Multiple
cuboids can describe more complex spaces.

## Child-mod example

Use the API jar for compilation, the main mod at runtime, and call from the
server world's logical thread:

```java
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import zone.moddev.mc.skysrealisticweather.api.TemperatureZone;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;

ResourceLocation id = new ResourceLocation("mygreenhouses", "house/12/64/12");
TemperatureZone zone = TemperatureZone.celsius(
    id, new BlockPos(10, 64, 10), new BlockPos(20, 70, 20), 22.0F);
WeatherApi.putTemperatureZone(world, zone);

// Reuse the ID to replace bounds or temperature; an identical update is a no-op.
// Remove it when the heater/controller stops managing this space.
WeatherApi.removeTemperatureZone(world, id);
```

The corners can be supplied in either order. Coordinates and Y boundaries are
inclusive. IDs are scoped to a world; use your own namespace and a stable,
unique ID for each controller. `putTemperatureZone` returns whether it changed
the registration. `removeTemperatureZone` returns false for an absent ID.
Mutations from a client world or a non-server thread are rejected. Schedule
work on the server thread when calling from a worker.

Registrations are **runtime-only**. Your mod owns persistence and should restore
its registrations after loading its controller/world data. Remove registrations
when they should cease applying, including controller unload if that should
disable heating. This mod clears the registry when the world unloads. Neither
registration nor a temperature query loads missing terrain.

For more control, use the full constructor:

```java
new TemperatureZone(id, minimum, maximum, temperatureInMinecraftUnits,
                    priority, TemperatureZone.Mode.OVERRIDE);
```

The Celsius factory uses priority 0 and an exact override. Higher priorities
win; equal priorities use the lexically smaller full ID, independently of
registration order. `Mode.MINIMUM` provides a temperature floor and never
lowers warmer ambient air. Zones do not stack or blend. After Forestry-controlled
positions, only the highest-priority covering zone applies.

Temperatures in the full constructor and query results use Minecraft units,
where `WeatherApi.SNOW_LINE` (0.15) corresponds to 0 °C and each Celsius degree
is 0.045 MC. The Celsius factory avoids needing to perform this conversion.
Display preferences do not change API values.

Bounds must lie within vanilla's Y 0–255 and horizontal ±30,000,000 block
range, with at most 1,024 intersected horizontal chunks per zone. IDs are limited
to 128 characters; temperature must be finite. A chunk can contain up to 4,096
registered zones. Invalid/over-limit changes are rejected before replacing the
existing registration. Use cuboids rather than a separate zone for every block.

## Queries and compatibility

- `WeatherApi.getLocalTemperature(world, pos)` returns the effective temperature
  as an `OptionalDouble` at a loaded position, including in other dimensions.
- `WeatherApi.getTemperatureSample(world, pos)` returns the effective and
  outdoor temperatures, `AMBIENT`, `ZONE`, `FURNACE`, `FORESTRY`, `FIRE`, `LAVA`
  or `TORCH` source, and the winning zone/source ID where applicable.
- Existing `getTemperature`, `getWeather` and `getClimate` continue to describe
  the outdoor atmospheric climate. Their supplied-biome generation queries are
  unchanged and do not consult local zones.

Read and write on the world's logical thread. Forestry-controlled positions
keep their existing precedence when that integration is enabled. Its controlled
position availability on clients remains Forestry's responsibility. Our own
zone definitions are server-authoritative and synchronized to players watching
the affected chunks. Updates are coalesced, with up to 64 dirty chunks processed
per world tick; large changes can take multiple ticks to reach existing clients.
New watchers receive the current chunk snapshot. Unwatch and world-unload paths
clear client copies. No client-to-server zone editing packet is exposed.

Surface freezing, snow/ice generation and thaw, precipitation type, weather
events and climate maps continue to use the outdoor climate. An API-heated space
does not by itself prevent water freezing or remove native Arctic snow. Native
lighting and other mods' own rules still apply.

## Vanilla heat sources

A loaded, ticking, burning **vanilla furnace** adds warmth to the local outdoor
temperature. The bonus is **+20 °C at the furnace**, tapering linearly to zero
at the configured spherical radius. Cold and warm surroundings both receive
the same bonus at the same distance. For example, a 10-block radius gives
+20, +18, +16 °C at distances 0, 1 and 2 blocks, reaching zero at 10 blocks.
With 5 °C outdoor air those readings are 25, 23 and 21 °C.

The default radius is 10 blocks. Vertical and diagonal distances use the same
spherical falloff; the corners of the enclosing cube are outside it. Player
readouts sample eye height, so standing beside a floor-level furnace receives
about +17.2 °C rather than the source block's full +20 °C.
Its priority is −100, so ordinary child-mod zones override
it. All vanilla heat sources use the strongest applicable bonus, independently of
their registry IDs; their temperatures do not add together. Walls are not
checked, and warmth can extend through them.

Registration is refreshed through the furnace's existing tick at a staggered
one-second interval. Burnout/broken/unloaded source blocks stop contributing;
short internal leases clean up abandoned registrations. Saved furnace fuel is
restored by Minecraft and heating re-registers after its native tick resumes.
The furnace integration uses its existing tick hook.
If another transformer prevents its attachment, it logs a warning and leaves
the zone API and weather system available.

Configure the server's `heating` category, then restart Minecraft/the server:

| Setting | Default | Effect |
| --- | --- | --- |
| `enabled` | `true` | Enables API zones and all automatic vanilla heating. Forestry's existing controlled climates remain independent. |
| `furnaceRadius` | `10` | Spherical falloff radius, 0–32 blocks. Zero disables automatic furnace heating. The default bonus drops 2 °C per block of radial distance. |
| `furnaceBonusCelsius` | `20` | Temperature increase at the source, 0–100 Celsius degrees. The editor uses Δ°C or Δ°F; +20 °C is +36 °F, without adding Fahrenheit's absolute-temperature offset. |
| `fireRadius` / `fireBonusCelsius` | `8` / `35` | Vanilla fire: strong, fairly short-range warmth. |
| `lavaRadius` / `lavaBonusCelsius` | `12` / `60` | Still and flowing vanilla lava: strongest, widest warmth. |
| `torchRadius` / `torchBonusCelsius` | `4` / `4` | Ordinary vanilla torches, including wall torches: gentle local warmth. Redstone torches are excluded. |

All bonuses are gameplay air-temperature increases, not flame or molten-material
temperatures. For example a torch adds +3 °C one block away and +2 °C two blocks
away, reaching zero at four. Fire adds +17.5 °C at four blocks; lava adds +30 °C
at six. Per-source radii accept 0–32 blocks and bonuses 0–100 Celsius degrees.
Zero radius or zero bonus disables that type. No heater bonuses stack, even
between different types. Explicit API zones keep their priority semantics
(automatic sources have priority −100); Forestry-controlled positions still win.
The `furnace/` and `heat/` ID prefixes in this mod's namespace are reserved.

Fire/lava/torch discovery uses a lazy cache of nearby loaded block sections,
including sources already present in saved terrain. It does not create a zone
or ticking object for every lava block. The existing committed block-change
hook invalidates a cached section when a source appears, disappears or changes
type. It checks old/new source types before looking up a world or cache, so ordinary
terrain changes and fire/lava metadata updates avoid that lookup. Extinguishing
fire, breaking torches and solidifying lava remove their
warmth immediately. A one-second refresh also handles mods that bypass that
hook or client full-section replacements. Caches have weak section keys and are
discarded with the world. Queries never load missing chunks. This adds no
world-generation pass or new core hook. Server radii/bonuses are synchronized
with watched chunk snapshots; clients use those values with their loaded block
states, independent of local heating preferences.

On upgrade to config version 8, a radius of 2 from older configurations is
treated as the legacy default and changed to 10. Other radii and disabled
heating are preserved. After upgrading, explicitly setting 2 is respected on
subsequent restarts. When upgrading, the old
`furnaceMinimumCelsius` value seeds the new bonus once (negative values become
zero). The old key is retained but hidden and ignored once the new key exists.
An explicitly configured `furnaceBonusCelsius` always wins.

These settings appear under **Configure selected mode → Edit Settings → Local
temperature zones and heat sources**. Dedicated-server settings take precedence over
the connecting client's heating configuration.
