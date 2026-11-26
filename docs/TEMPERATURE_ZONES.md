# Local temperature zones

API 7 lets another mod register an inclusive 3D cuboid with a fixed temperature.
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
  outdoor temperatures, `AMBIENT`, `ZONE`, `FURNACE` or `FORESTRY` source, and
  the winning zone ID where applicable.
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

## Vanilla furnaces

A loaded, ticking, burning **vanilla furnace** automatically supplies a
**5 × 5 × 5** zone centred on its block, with a **20 °C minimum**. It never
cools a warmer biome. Its priority is −100, so ordinary child-mod zones override
it. Multiple furnaces do not add their temperatures together. Walls are not
checked, and the zone can extend through them. Furnaces are the only automatic
vanilla heat source in this version; torches, fire and lava do not register zones.

Registration is refreshed through the furnace's existing tick at a staggered
one-second interval. Burnout/broken/unloaded source blocks stop contributing;
short internal leases clean up abandoned registrations. Saved furnace fuel is
restored by Minecraft and heating re-registers after its native tick resumes.
The furnace integration adds no general block-change or world-generation hook.
If another transformer prevents its attachment, it logs a warning and leaves
the zone API and weather system available.

Configure the server's `heating` category, then restart Minecraft/the server:

| Setting | Default | Effect |
| --- | --- | --- |
| `enabled` | `true` | Enables API zones and automatic furnace zones. Forestry's existing controlled climates remain independent. |
| `furnaceRadius` | `2` | Radius on all axes, 0–8. Zero disables automatic furnace heating. |
| `furnaceMinimumCelsius` | `20` | Temperature floor, −50 to 100 °C in the file. The config editor uses the player's Celsius/Fahrenheit preference. |

These settings appear under **Configure selected mode → Edit Settings → Local
temperature zones and furnaces**. Dedicated-server settings take precedence over
the connecting client's heating configuration.
