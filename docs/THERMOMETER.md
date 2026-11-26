# Thermometer

The thermometer is a survival tool for reading local temperature without
operator permissions. It is also available in the vanilla **Tools** creative
tab. Its registry name is `skysrealisticweather:thermometer`.

## Crafting

Put these ingredients in one vertical column of a crafting table:

| Top | Middle | Bottom |
| --- | --- | --- |
| Glass block | Glass block | Mercury ingot |

This consumes two glass blocks and one mercury ingot and makes one thermometer.
The recipe accepts `blockGlass` and `ingotMercury` ore-dictionary entries,
including entries added after the recipe is registered. Base Metals supplies
mercury; other providers using the same ore-dictionary name also work. Iron and
other metals cannot substitute. Without a mercury provider the mod still
loads, but the thermometer cannot be crafted.

## Reading the temperature

Hold a thermometer in your main hand or offhand to show a compact thermometer
graphic and numeric reading above the right side of the hotbar. The reading
follows your position, altitude and current local weather. Keeping one elsewhere
in your inventory does not enable the HUD. Right-click in the air for a chat
reading.

Right-click a solid block's top to stand it on the floor, or a horizontal face
to mount it on a wall. Look at the placed thermometer within six blocks for a
HUD reading at that instrument, or right-click it for a chat reading. Holding
another thermometer takes priority and measures your own position. Sneak-place
uses the usual Minecraft block-placement behaviour. Ceiling mounting is not
supported. Breaking the thermometer recovers it; removing its support drops it.

Choose **Mods → Sky's Realistic Weather → Config → Display / units** to switch
between Celsius and Fahrenheit. That personal preference also applies to chat
readings on a dedicated server. The graphic's scale spans −40 to 60 °C
(−40 to 140 °F); values outside it still appear numerically.

Thermometers use the public effective-local-temperature API, including
[temperature zones](TEMPERATURE_ZONES.md), burning furnace zones and available
Forestry-controlled positions. Otherwise they show the outdoor climate, or
native biome temperature in other dimensions, converted to your preferred units.
Glass rooms, torches and fire do not independently create heated spaces.
The placed model is decorative; the live scale and number appear in the HUD.
It stores no temperature, has no ticking tile entity and never loads terrain
to take a reading. Hiding the normal HUD also hides the thermometer overlay.
