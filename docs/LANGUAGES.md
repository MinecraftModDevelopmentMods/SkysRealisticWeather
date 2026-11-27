# Languages

Choose a language in Minecraft's **Options → Language** screen. The item names,
weather-admin creative tab, climate HUD keybinding, thermometer HUD labels and
thermometer instructions follow that client setting, including in multiplayer.
The mod name stays Sky's Realistic Weather.

The translations match Iron Age Furniture's locale set. Austrian German also
uses Minecraft's correct `de_AT` locale; `de_AU` is retained as a legacy alias.
Likewise, `en_EN` is an English alias. These aliases do not add entries to the
vanilla language menu.

| Locale | Translation |
| --- | --- |
| `de_AT` | German (Austria) |
| `de_AU` | German, legacy alias |
| `de_DE` | German (Germany) |
| `en_CA` | English (Canada) |
| `en_EN` | English, legacy alias |
| `en_GB` | English (United Kingdom) |
| `en_PT` | Pirate English |
| `en_US` | English (United States), fallback |
| `es_ES` | Spanish (Spain) |
| `es_MX` | Spanish (Mexico) |
| `fr_CA` | French (Canada) |
| `fr_FR` | French (France) |
| `ja_JP` | Japanese |
| `ko_KR` | Korean |
| `pt_BR` | Portuguese (Brazil) |
| `pt_PT` | Portuguese (Portugal) |
| `ru_RU` | Russian |
| `zh_CN` | Simplified Chinese |

The config and admin editors, crop suitability text, climate preset names and
command messages still contain English strings in code. The language files
cover the nine existing translation keys; they do not translate those screens
or player-defined climate and crop names.

## Editing translations

Files are UTF-8 without a byte-order mark, under
`src/main/resources/assets/skysrealisticweather/lang`. Keep every English key
in every locale, with exactly one non-empty value per key. Preserve any
formatting placeholders when adding future keys.

Minecraft 1.10.2 uses mixed-case filenames such as `fr_FR.lang` and pack format 2.
Minecraft 1.11.2 uses lowercase filenames such as `fr_fr.lang` and pack format 3.
When backporting translations, change the filename casing as appropriate.

Run `gradlew build writeReleaseChecksum prepareEclipseResources syncDevelopmentOutput`
after editing. This packages the translations and refreshes Eclipse's resource
folder. Restart the client to load the rebuilt resources.
