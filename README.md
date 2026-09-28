# One More Audio Controller

[![](https://jitpack.io/v/LSS02-Minecraft-Mods/OneMoreAudioController.svg)](https://jitpack.io/#LSS02-Minecraft-Mods/OneMoreAudioController)

NeoForge 1.21.1, **fully client-side** mod.

Adds independent audio sliders to the vanilla **Options → Music & Sounds** screen, next to the
vanilla ones (Master, Music, Jukebox/Noteblocks, Weather, Blocks, Hostile Creatures, Friendly
Creatures, Players, Ambient/Environment, Voice/Speech). Each extra slider controls **only** the
sounds assigned to it, without touching Master, Players, or any other vanilla category.

Two separate concerns, two separate mechanisms:

1. **Which controllers exist, and in what order** - this is configuration: `controllers.json` and
   `orders.json`, editable either by hand (a modpack, a resource pack, an end user) or in-game
   through the **Controller Manager** screen (see part 1) - no code needed either way.
2. **Which sounds a controller actually affects** - this is a code concern, owned by whichever mod's
   sounds they are. It's set exclusively through the `OneMoreAudioControllerApi` Java API, never
   through JSON: only the mod that ships a sound really knows which controller it belongs under.

Everything applies **without restarting the game**: both JSON files are reloaded every time you
open the Controller Manager or Music & Sounds screens (including when opened from the Mods menu
"Config" button or from mods like **Catalogue**).

---

## 1. In-game: the Controller Manager screen

The "Config" button in the Mods menu, and mods like **Catalogue**, open this mod's own
**Controller Manager** screen instead of jumping straight to Music & Sounds. From there you can,
entirely in-game:

- **Add controller** - creates a new JSON-backed entry (id + display name), the in-game equivalent
  of hand-adding an entry to `controllers.json`. Just like a hand-added entry, it stays a visible
  but silent slider until some mod registers sounds for that same id through the API (part 3).
- **Rinomina / Elimina** (Rename / Delete) - shown next to any controller defined in
  `controllers.json`. Vanilla categories and API-registered controllers can't be renamed or deleted
  from here: an API controller's name and existence are owned by the mod that registered it and get
  redefined on every launch, so editing them here would just be undone at the next restart. You can
  still reorder them (see below).
- **Drag and drop** any row (grab anywhere on it, except the Rename/Delete text) to reorder it -
  vanilla, JSON, and API controllers alike. Released changes are written straight to `orders.json`.
- **Apri Music & Sounds** (Open Sound Options) - jumps to the actual sliders to set volumes; **Fatto**
  (Done) returns to the previous screen.

This screen is just a GUI on top of `controllers.json`/`orders.json`, so anything it can't do
(assigning sounds, renaming/deleting an API controller) still requires either a JSON edit or code
through the API, exactly as described below.

---

## 2. JSON configuration

The files live in `config/onemoreaudiocontroller/` and are created automatically on first launch:

- `controllers.json` starts empty (`[]`) - the mod ships with **no** predefined controllers, you
  (or a modpack, or another mod) decide what goes here. The `menu_music` controller used in the
  examples below is just that, an example: it's not shipped, and it isn't functional until some
  mod registers sounds for it through the API (see part 3).
- `orders.json` starts pre-filled with every vanilla category (`music`, `records`, `weather`,
  `blocks`, `hostile`, `neutral`, `players`, `ambient`, `voice`), so it's immediately editable
  without having to look up valid ids first. If you delete one of those lines, the mod adds it
  back to the file the next time it loads - only custom controller ids are ever left out.

### `controllers.json` - which controllers exist

Each entry declares a controller's **identity and label only** - never sounds. An entry here just
means "this id exists, and this is its English name"; it stays a real, visible slider that
controls nothing until a mod registers sounds for that same id through
[the API](#2-api-for-developers). This is intentional: a modpack author generally has no reliable
way to know another mod's internal sound event ids, so this mod never asks JSON to guess at that -
only the mod that ships a sound can correctly say which controller it belongs under.

Fields:

| Field          | Required | Description |
|-----------------|:---:|-------------|
| `id`             | yes | Unique identifier for the controller (lowercase, no spaces). Cannot match a vanilla category name (`master`, `music`, `records`, `weather`, `blocks`, `hostile`, `neutral`, `players`, `ambient`, `voice`). |
| `default_name`   | yes | English label shown in the menu. Used as-is unless a resource pack (or another mod) supplies a translation - see below. |
| `volume`         | no | Initial volume, `0.0`-`1.0`. Default `1.0`. The mod rewrites this automatically every time you move the slider in-game, so you don't need to touch it by hand after the first launch. |

Example - pre-declaring the label/order for two controllers a couple of installed mods are expected
to register sounds for via the API:

```json
[
  { "id": "menu_music", "default_name": "Menu Music" },
  { "id": "tavern_music", "default_name": "Tavern Music" }
]
```

### Translating a controller's name via resource pack

Every controller's label uses the translation key `soundCategory.<id>` if one exists for the
player's current language, falling back to `default_name` (JSON) or the name passed to the API
otherwise. A resource pack can add that translation for any controller - JSON- or API-defined -
without touching `config/` at all, just by shipping a lang file:

```
assets/onemoreaudiocontroller/lang/it_it.json
```
```json
{
  "soundCategory.menu_music": "Musica Menù"
}
```
This is the only thing a resource pack can contribute to a controller: a translated label. It
can't create a working controller or attach sounds to one - see part 3 for that.

### `orders.json` - the order sliders appear in

List of ids (vanilla categories in lowercase + custom controller ids) that decides how they're
paired up two-by-two in the grid. Ships pre-filled with every vanilla category; add your own
controller ids wherever you want them to appear. Continuing the example above:

```json
[
  "music", "menu_music",
  "records", "voice",
  "ambient", "weather",
  "hostile", "neutral",
  "players", "blocks"
]
```

Rules:

- **Master** is always first, alone at the top: it must not (and cannot) be listed here.
- **Sound Device**, **Subtitles**, and **Directional Audio** always stay the last three entries,
  below every controller: these must not be listed here either, the mod never touches them.
- Every id listed here is shown first, in the order you write it.
- Any controller (vanilla or custom) **not** listed here is still shown, appended at the end, so a
  typo in this file never hides a slider.
- An unknown id (typo, or a controller that isn't defined anywhere) is skipped with a warning in
  the log.

### `externalcontroller.json` - read-only, generated by the mod

Whenever a mod registers a controller through the API (see below), this mod saves it here - id,
default name, the sounds it controls, and its current volume. This file exists **only** so you can
see which ids (and sounds) are already taken by other mods' code when hand-editing
`controllers.json`, to avoid reusing the same id. Don't edit it by hand: it's regenerated on every
launch and every volume change, and it is never read as a source of new controllers (only to
restore the saved volume of an API-registered controller).

If `controllers.json` and an API-registered controller ever use the same id, **the API always
wins**: the JSON entry is skipped, with a warning in the log pointing you to
`externalcontroller.json`.

---

## 3. API for developers

Sound assignment is always a code concern. If you're writing a mod (e.g. a guns mod) and want your
own independent slider, register it - id, label, and the sounds it controls - in code with
`OneMoreAudioControllerApi`. This is the only way to give a controller working sounds; JSON alone
never can (see part 2).

### Dependency

The mod is published on [JitPack](https://jitpack.io/#LSS02-Minecraft-Mods/OneMoreAudioController).
The examples below point at `1.1`, the current release - swap it for whatever tag/commit you
actually want (JitPack builds any git tag or `main-SNAPSHOT` on demand).

**Gradle** (`build.gradle`):

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // NeoForge's dev and production environments both use official Mojang mappings, so no
    // deobfuscation step is needed here - unlike the old ForgeGradle fg.deobf(...) wrapper.
    implementation 'com.github.LSS02-Minecraft-Mods:OneMoreAudioController:1.1'
}
```

**Maven** (`pom.xml`):

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.LSS02-Minecraft-Mods</groupId>
    <artifactId>OneMoreAudioController</artifactId>
    <version>1.1</version>
</dependency>
```

Then declare it as an optional dependency in your `neoforge.mods.toml`, so your mod still works without it:

```toml
[[dependencies.yourmodid]]
    modId="onemoreaudiocontroller"
    type="optional"
    versionRange="[1.1,)"
    ordering="NONE"
    side="CLIENT"
```

If your mod also needs to work without `onemoreaudiocontroller` installed, wrap the call in a
`ModList.get().isLoaded("onemoreaudiocontroller")` check before calling the API.

### Usage

```java
import net.ngsh.shydevelopment.onemoreaudiocontroller.api.OneMoreAudioControllerApi;
import net.minecraft.resources.ResourceLocation;

OneMoreAudioControllerApi.registerController(
        "mygunmod_gun_sounds",
        "Gun Sounds", // English fallback label; translatable via soundCategory.mygunmod_gun_sounds
        ResourceLocation.fromNamespaceAndPath("mygunmod", "gun_shot"),
        ResourceLocation.fromNamespaceAndPath("mygunmod", "gun_reload")
);

// Or without a label - it's auto-generated from the id ("gun_sounds" -> "Gun Sounds")
OneMoreAudioControllerApi.registerController(
        "mygunmod_gun_sounds",
        ResourceLocation.fromNamespaceAndPath("mygunmod", "gun_shot"),
        ResourceLocation.fromNamespaceAndPath("mygunmod", "gun_reload")
);
```

Call it once, early - either your mod's constructor or `FMLCommonSetupEvent` work fine, since the
final on-screen order is only computed the first time the player opens the Sound Options screen,
well after every mod has finished loading.

Practical rules:

- **Pick an id that includes your own mod id** (e.g. `"mygunmod_gun_sounds"`, not
  `"gun_sounds"`) to avoid clashing with other mods.
- You can't use an id reserved for vanilla categories (`master`, `music`, `records`, `weather`,
  `blocks`, `hostile`, `neutral`, `players`, `ambient`, `voice`).
- Calling `registerController` again with the same id redefines it (useful if your mod recomputes
  its sound list), keeping whatever volume the user already set.
- The volume is saved automatically to `externalcontroller.json` on every slider change and
  restored on the next launch, no extra code needed on your side.
- If your id isn't listed in `orders.json`, your slider still shows up, appended after every other
  controller.

---

## Real Minecraft sound categories

Every controller - JSON or API - is also grafted onto the actual `net.minecraft.sounds.SoundSource`
enum at runtime, as a brand new constant, not just something this mod's own mixins recognize. This
is what lets **other mods select a controller as a genuine sound category**, the same way they'd
pick `music` or `players` - for example FancyMenu's audio elements, which only ever offer whatever
`SoundSource.values()` reports.

- A controller is promoted to a real `SoundSource` the moment it's registered: at game start for
  ids already in `controllers.json`, or immediately for controllers registered later through the
  API by another mod, or added in-game through the Controller Manager screen.
- Promoting a controller patches the running `Options` instance directly, so its Music & Sounds
  slider works immediately - including persistence to `options.txt` - without a restart.
- Once promoted, a controller's volume is driven entirely by that (now real, vanilla-native) slider
  - the same one `SoundEngineMixin` uses for sounds assigned through the API, so both stay in sync.
- Renaming a controller, or deleting and recreating one under the same id, updates its live slider
  immediately too.

This relies on `sun.misc.Unsafe` to add the enum constant at runtime (no `--add-opens` JVM flag
needed). If it ever fails, it's logged and the controller falls back to being a mod-only channel
instead of a real sound category.

## Compatibility with Catalogue / the Mods menu

The mod registers NeoForge's `IConfigScreenFactory` extension point, so the "Config" button in the Mods menu
and mods like **Catalogue** open the Controller Manager screen described in part 1. Every time it
opens, `controllers.json` and `orders.json` are reloaded from disk, so you can edit the JSON files
by hand, reopen that screen, and see the changes immediately - no need to restart Minecraft.

---

## Build

```bash
./gradlew build
```

Requires Java 21 (Minecraft 1.21.1 requires it at runtime); Gradle will download a matching
toolchain automatically if one isn't already installed. The compiled jar ends up in `build/libs/`.
