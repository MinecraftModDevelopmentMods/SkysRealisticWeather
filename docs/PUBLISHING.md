# Dependency metadata and CurseForge relations

The 1.11.2 jar declares its 17 supported integrations as optional dependencies
in `mcmod.info`. Forge is the only required dependency. `useMetadata` and
`useDependencyInformation` enable those declarations; installed integration mods
load before Sky's Realistic Weather, while missing integrations do not prevent
startup. The annotation keeps the minimum Forge requirement as a fallback.
Supported builds are listed in [port status](PORTING_1_11_2.md).

Forge mod IDs and CurseForge projects are different identifiers. For example,
Hwyla uses the Forge ID `waila` but the CurseForge slug `hwyla`. These identities
must be mapped explicitly when preparing publication.

CurseForge documents relationships under **Related Projects** on a file upload,
or as project-level **Default Relations**. Jar metadata alone is not a documented
replacement for those settings. See [CurseForge's file and relationship guide](https://support.curseforge.com/support/solutions/articles/9000197242-file-project-types-and-additional-fields).

The matching upload relation object is tracked at
[publishing/curseforge-relations.json](../publishing/curseforge-relations.json).
Every entry uses `optionalDependency`. In an upload API metadata document, use
the entire object as the value of `relations`, for example:

```python
metadata["relations"] = json.loads(Path("publishing/curseforge-relations.json").read_text())
```

The repository currently has no automatic CurseForge uploader, so this file is
prepared input, not an active publication step. A manual website upload must
select the matching projects as **Optional Dependency** under Related Projects.
Uploading the jar or placing this JSON beside it does not submit those relations.

Keep relations specific to the Minecraft version being uploaded. This list
excludes RTG, Thaumcraft, AgriCraft and BYG because this port has no qualified
1.11.2 targets for them. CompatLayer and OreSpawn are dependencies of other mods,
rather than direct integrations to advertise here. Base Metals remains optional;
any mod providing ore-dictionary mercury can supply the thermometer ingredient.

Avoid project-wide defaults for integrations that are supported only on some
Minecraft versions. Before another port is published, update its own metadata,
relation list and integration-version matrix together.

## Verification

The build and 57 unit tests pass. Fresh native server starts verify the bound
metadata with integrations absent, with farming/bridge mods, with biome mods,
and with OTG/Biome Bundle, Lost Cities and Base Metals installed. Only
`mcmod.info` and `WeatherMod.class` differ from the JEI-qualified jar; gameplay,
client, API and hook class files are byte-identical. Earlier gameplay and
performance reports retain their original tested artifact hashes.

Exact artifacts and receipts are retained under ignored
`build/verification/dependency-metadata-1112`. Its original flat-world attempt
is retained as failed: OTG rejected that world configuration. The successful
generator fixture uses the proper OTG world type and Biome Bundle world name.
