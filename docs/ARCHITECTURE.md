# WorldTrace architecture

A short tour of the codebase for contributors. The short version: **everything that can be tested
without Minecraft has no Minecraft dependency**, which is why the test suite covers deduplication,
search, sorting, storage, migration and navigation maths without ever launching the game.

## Layers

```
        Minecraft  (WorldTrace, trackers, screens, HUD)
                          │
        dev.worldtrace.discovery.DiscoveryManager      ← the service layer
                          │
        TraceStore  ⇄  WorldDataRepository  ⇄  StoragePaths / TraceCodec
```

### 1. Model (`dev.worldtrace.model`)

`TraceEntry` is an immutable record holding one journal entry: identity, display name, the
identifier-derived source name, category, dimension, block position, timestamps, visit count, biome
and structure ids, notes, tags, favourite flag, screenshot reference, a confirmation flag and the
deduplication key.

Two decisions are worth calling out:

- **`sourceName` is separate from `name`.** Renaming "Cherry Grove" to "Home Grove" must not destroy
  the fact that it is `minecraft:cherry_grove`, or search, deduplication and the technical id column
  would all break.
- **`discoveryKey` is persisted.** It is the entry's deduplication identity and it survives renaming,
  editing and reloading. See `DiscoveryKeys`.

`TracePin` is a player-supplied expectation of where something interesting is.

### 2. Storage (`dev.worldtrace.storage`)

- **`TraceStore`** — the mutable, single-world repository. Every mutation marks the journal dirty.
  `snapshot()` is the only method that hands data off-thread, and it returns an immutable copy.
- **`WorldDataRepository`** — owns all I/O. Loading is synchronous (a few thousand entries parse in
  single-digit milliseconds and the journal must be ready when the screen opens). Writing is always
  on a dedicated daemon thread, with a dirty flag that coalesces bursts of saves into at most one
  pending write plus one follow-up.
- **`JsonFileStore`** — writes to `*.tmp` and atomically moves it into place, keeping the previous
  good file as `.bak`. Unparseable files are quarantined as `.corrupt-<timestamp>` rather than
  deleted.
- **`TraceCodec` / `SchemaMigrator`** — the versioned format. `TraceCodec` is strict on write and
  forgiving on read: every record is validated individually so one bad row cannot cost you the rest
  of your history. `SchemaMigrator` upgrades old documents forward and may only ever add or repair
  data, never delete it.

### 3. Discovery (`dev.worldtrace.discovery`)

`DiscoveryManager` is the service layer. It owns the store, the deduplication index and every
mutation (record, revisit, bookmark, pin, favourite, destination). It publishes `DiscoveryEvent`s to
listeners so trackers and the GUI never talk to each other.

The trackers are the Minecraft-aware part:

| Tracker | Frequency | Work |
| --- | --- | --- |
| `BiomeTracker` | every tick, only when the block position changed | read the biome id, record first sightings, accumulate distance |
| `MilestoneTracker` | per movement step, plus once per second | distance, depth and coverage thresholds |
| `PinTracker` | every 10 ticks, plus once per second | pin proximity, and the deferred block verification |

`BlockScanJob` is what keeps structure verification from causing a frame spike: it walks a
pre-computed integer box and processes a fixed budget of positions per tick, reading only chunks the
client already has.

### 4. Journal (`dev.worldtrace.journal`)

`JournalFilter` and `JournalSorter` are pure functions over `List<TraceEntry>` and a `JournalQuery`.

`JournalScreen` is the presentation. Its most important structural rule: **hit regions are derived
from the same `JournalLayout` that draws them**, and are rebuilt only when the layout or the result
set changes — never per frame. Drawing and hit testing therefore cannot disagree, and the render
loop allocates nothing.

`JournalFormScreen` is the shared chrome for the modal dialogs (edit entry, place watch pin),
supporting either a text input row or a `« »` stepper row.

### 5. Navigation (`dev.worldtrace.navigation`)

`NavigationCore` holds the geometry: distance, heading in Minecraft's yaw convention, and explicit
handling of cross-dimension and unknown-position cases. It never returns a fabricated distance.

`NavigationOverlay` publishes the player's position once per tick so drawing code can compute
navigation state without touching Minecraft objects. `NavigationManager` wraps the destination
selection logic. None of these can teleport.

## Invariants worth protecting

1. **Never merge distinct discoveries.** The deduplication key is type + dimension + natural
   identity — never a position radius. Two temple pins twenty blocks apart stay two entries.
2. **Never lose history.** A bad record is skipped, not fatal. A newer schema opens read-only rather
   than overwriting.
3. **Never block the render thread on disk.** Snapshots are taken on the game thread; serialisation
   and writing are not.
4. **Never claim more than was observed.** Unconfirmed structures are labelled as such everywhere
   they appear.
5. **Never make an external call.** There is no HTTP client in this codebase, on purpose.