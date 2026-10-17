# WorldTrace — The Memory of Your World

WorldTrace remembers your adventures, not just your inventory.

It quietly records the biomes you walk through and the milestones you reach, keeps your own
bookmarks with notes and tags, and gives you a fast, searchable field journal to find those places
again months later. Everything stays on your machine, in one JSON file per world.

- **Minecraft** 1.21.1
- **Mod loader** Fabric (Fabric API required)
- **Side** Client only
- **License** MIT

---

## Why not just a waypoint mod?

Waypoints tell you *where to go*. WorldTrace tells you *where you have been*.

- It records the journey automatically, so your history exists even if you never marked anything.
- It annotates: a bookmark can carry notes, tags, a screenshot and a favourite flag.
- It is honest about what it knows. A biome you actually stood in is a fact; a structure you were
  told about is labelled as unconfirmed until real evidence is found. See
  [Limitations](#limitations-honest-about-vanilla).
- It never teleports you, never talks to a server, and never uploads anything.

---

## Features

### Automatic discovery

| What | How it is detected | Confidence |
| --- | --- | --- |
| **Biomes** | The biome registry id read from the chunk the player is standing in | Certain |
| **Distance milestones** | Cumulative blocks actually walked across the save | Certain |
| **Depth milestones** | Reaching the floor or ceiling of the player's current dimension | Certain |
| **Coverage milestones** | How many biomes / confirmed structures the journal holds | Certain |
| **Structures** | A *watch pin* you placed, plus verification against the structure's building blocks | Verified or unconfirmed |
| **Landmarks** | A watch pin with no structure expectation | Certain |

### The journal

- Dark, restrained interface that feels native to Minecraft rather than bolted on.
- Free-text search across names, notes, tags, biome/structure ids and coordinates. Typing
  `120 64 -305` jumps straight to that block.
- Category chips (Biome / Structure / Landmark / Bookmark / Milestone), a dimension cycler, and a
  favourites-only toggle. Filters are independent: clearing the search never clears a filter.
- Sort by newest, oldest, name, or favourites first.
- Detail pane with coordinates, dimension, discovery time, visit count, biome/structure metadata,
  notes, tags and screenshot reference.
- Full keyboard navigation: arrows, Page Up/Down, Home/End, `F` to favourite, `Delete` to remove.
- Works from a tiny 320×240 window at GUI scale 4 up to 4K at scale 1. On narrow windows the list and
  detail pane swap instead of shrinking, so nothing ever becomes unreachable.
- Distinct empty states for "no discoveries yet" and "nothing matches your search".

### Bookmarks

- `K` drops a bookmark where you are standing and opens the editor immediately.
- Custom name, category, notes, tags and favourite flag. Everything validated and length-capped.
- Edit or delete at any time. Positions and dimensions are immutable, because they are facts.
- Bookmarks are visually distinct from automatic discoveries.

### Navigation

- Select any entry as your active destination; it shows on the HUD above the hotbar.
- Live distance and relative heading (`ahead`, `behind-left`, …) while you walk.
- One click copies `x, y, z (Dimension)` to the clipboard.
- Cross-dimension destinations say so explicitly instead of inventing a distance.
- **There is no teleport command anywhere in this mod.**

### Screenshots (optional)

WorldTrace never grabs your framebuffer. It copies screenshots **you** take with `F2` into the
journal folder, either automatically when a discovery happens (`autoScreenshotOnDiscovery`) or via
the `Screenshot` button in the detail pane.

---

## Installation

1. Install the [Fabric Loader](https://fabricmc.net/use/installer) for Minecraft **1.21.1**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 into your `mods` folder.
3. Drop `WorldTrace-1.0.0.jar` into your `mods` folder.
4. Launch Minecraft using the Fabric profile.

WorldTrace is client-side, so on a dedicated server only the players who want the journal need it.

---

## Controls

| Key | Action | Default |
| --- | --- | --- |
| `J` | Open the journal (press again to close) | `J` |
| `K` | Bookmark the current position and edit it | `K` |
| `L` | Set / clear the navigation destination | `L` |

All three are rebindable in *Options → Controls → Key Binds → WorldTrace*.

---

## Configuration

`worldtrace/config.json`, created on first launch inside your game directory.

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `biomeDiscoveries` | boolean | `true` | Record biomes the player enters |
| `structurePinDiscoveries` | boolean | `true` | Turn reached watch pins into discoveries |
| `milestoneDiscoveries` | boolean | `true` | Record distance, depth and coverage milestones |
| `notificationMode` | `OFF` \| `ACTION_BAR` | `ACTION_BAR` | How new discoveries are announced |
| `revisitMode` | `COUNT_ONLY` \| `ANNOUNCE` \| `RECORD_SEPARATE` | `COUNT_ONLY` | What happens on a re-visit |
| `navigationHud` | boolean | `true` | Show the destination HUD line |
| `showTechnicalIds` | boolean | `false` | Show registry ids next to pretty names |
| `accentColor` | `#RRGGBB` | `#7FC4E0` | Journal accent colour |
| `autoScreenshotOnDiscovery` | boolean | `false` | Attach a just-taken F2 screenshot |
| `maxEntries` | integer | `20000` | Cap on automatic discoveries (100–200000) |
| `worldIdOverride` | string | `""` | Force a specific journal id for edge cases |

`revisitMode` in detail:

- **`COUNT_ONLY`** — bumps the visit counter and "last seen" time silently. The default, because
  walking back through a plains biome should not spam your journal.
- **`ANNOUNCE`** — same, but you also get a line on the action bar.
- **`RECORD_SEPARATE`** — every re-visit becomes its own entry, preserving the full travel
  chronology. Useful for speedrun-style logs, at the cost of a much larger journal.

Every value is validated on load. A typo produces a log warning and the default, never a crash.

---

## Where your data lives

```
<game directory>/
└── worldtrace/
    ├── config.json                     preferences
    ├── worlds/
    │   ├── <world-id>.json             one journal per save or server
    │   ├── <world-id>.json.bak         previous good copy, used for recovery
    │   └── <world-id>.json.corrupt-*   quarantined unreadable files
    └── screenshots/
        └── <world-id>/                 screenshots you attached
```

`<world-id>` is derived from the level name (singleplayer) or the server address (multiplayer) plus
a short hash, so two saves sharing a display name still get separate journals. Use
`worldIdOverride` if you ever need to force a specific id.

**Backing up** your journal means copying the `worldtrace` folder. That is the whole story.

### Data integrity

- Writes go to a temporary file and are then atomically moved into place, so a crash mid-write can
  never leave a half-written journal.
- Every record is validated on load. One malformed row is reported and skipped; the rest of the
  journal survives.
- An unreadable file is moved aside as `.corrupt-<timestamp>` and the `.bak` copy is used to
  recover. The original is never deleted.
- Every file carries a `schemaVersion`. A file from a **newer** WorldTrace is opened read-only so it
  can never be downgraded and destroyed. Older files are migrated forward automatically.
- Saving happens on a background thread and is coalesced: a burst of discoveries produces one write,
  never one per tick.

---

## Privacy

WorldTrace has **no** telemetry, analytics, crash reporting, account system, updater or network calls
of any kind. It performs no HTTP requests during play.

- Nothing is uploaded.
- Screenshots are copied from your own `screenshots` folder into your own game directory. They are
  never transmitted.
- World data is never sent to other players, and a server is never asked for extra information.
- The only files written are the ones listed above, all inside your game directory.

On multiplayer servers the mod deliberately stays within what a normal client can observe: your own
position, the biome you are standing in, and the chunks your client already has. It does not
request privileged information and works fine on servers that do not run any WorldTrace component.

---

## Limitations (honest about vanilla)

**Vanilla Minecraft never sends structure data to clients.** `ClientChunkManager` holds no structure
starts, and no packet carries them. This means a client-side mod genuinely cannot say "there is a
desert temple here" on its own. Many mods fake it anyway; WorldTrace does not.

Instead, WorldTrace uses **watch pins**:

1. You place a pin where you *expect* something — from a seed map, a friend's coordinates, or an
   expedition plan.
2. When you actually get within the pin's radius, that fact alone is recorded.
3. If the pin expected a structure, WorldTrace then scans the **already-loaded** chunks around the
   pin for that structure's characteristic building blocks (prismarine for an ocean monument, sculk
   for an ancient city, and so on).
4. The journal entry is marked **confirmed** if the blocks were found, or **unconfirmed** if you
   were in the right place but nothing matched. Unconfirmed entries are always visibly marked.

Because the marker blocks are a heuristic, a player-built sandstone stack near a temple pin may
leave the entry unconfirmed. That is the honest outcome, and the label says so.

Other known limitations:

- Singleplayer worlds are identified by level name rather than save directory, because vanilla does
  not expose the save path to a client-side mod. `worldIdOverride` is the escape hatch.
- Biome and structure display names are derived from their registry ids rather than translation keys,
  because modded content has no guaranteed translation. Names are therefore shown in English.
- There is no server-side component. The client is self-sufficient by design; a future
  WorldTrace server module could supply authoritative structure data, and the `discoveryKey` format
  already accommodates server-provided discoveries.
- Notes are edited in a single-line field (paste to paste multi-line text).

---

## Building from source

Requirements: **JDK 21** (to compile) and **JDK 25 or newer** (to run Gradle — required by
Fabric Loom 1.19).

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
.\gradlew.bat build
```

```bash
# Linux / macOS
./gradlew build
```

The distributable jar lands in `build/libs/WorldTrace-1.0.0.jar`. Sources land next to it.

Other useful tasks:

```bash
./gradlew test          # run the unit test suite (101 tests, no game required)
./gradlew runClient     # launch a development client against the run/ directory
./gradlew runServer     # launch a dev dedicated server (needs run/eula.txt)
./gradlew build --offline
```

To exercise the multiplayer path end to end — including the client-only world separation that
multiplayer servers require — start the dev server and launch the client straight into it:

```bash
./gradlew runServer
./gradlew runClient -PwtQuickPlay=localhost:25565
```

### Compatibility matrix

| Component | Version | Notes |
| --- | --- | --- |
| Minecraft | 1.21.1 | Yarn `1.21.1+build.3` mappings |
| Fabric Loader | ≥ 0.16.14 | |
| Fabric API | 0.116.17+1.21.1 | |
| Fabric Loom | 1.19.1 | Requires Gradle ≥ 9.8 on a JDK 25 runtime |
| Java | 21 | Minecraft 1.21.1's runtime |
| Gradle | 9.8.0 | Wrapper included |

---

## Project layout

```
src/main/java/dev/worldtrace/
├── WorldTrace.java          mod entry point and Minecraft lifecycle wiring
├── WorldTraceKeys.java      key bindings
├── config/                  config model, validation, persistence
├── discovery/               dedup keys, discovery manager, biome/milestone/pin trackers
├── hud/                     heads-up display
├── journal/                 screens, layout, filtering, sorting
├── model/                   TraceEntry, TracePin, EntryCategory
├── navigation/              pure navigation maths plus the live overlay
├── notify/                  discovery announcements
├── storage/                 repository, JSON codec, migrations, atomic writes, screenshots
└── util/                    validation, formatting, distance and timestamp helpers

src/test/java/dev/worldtrace/ 101 unit tests, none of which launch Minecraft
```

Design notes are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Contributing

1. Fork and create a branch.
2. Keep it testable: anything without a Minecraft dependency can and should have a unit test.
3. Run `./gradlew build` before opening a pull request. Both `build` and `test` must pass.
4. Keep classes focused; prefer a clear name over a clever abstraction.
5. Never add a dependency without discussing it first. WorldTrace is dependency-light on purpose.

Bug reports and feature ideas are welcome through the tracker on the project page.

---

## Changelog

See [`CHANGELOG.md`](CHANGELOG.md). The Modrinth project description is in
[`MODRINTH_DESCRIPTION.md`](MODRINTH_DESCRIPTION.md).

## License

MIT — see [`LICENSE`](LICENSE).