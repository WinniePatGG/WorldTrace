# Changelog

All notable changes to WorldTrace are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2026-10-18

First public release for Minecraft 1.21.1 on Fabric.

### Added

**Automatic discovery**
- Biome recording driven by the biome registry id at the player's own position. Modded biomes work
  identically to vanilla ones.
- Exploration milestones: cumulative distance walked (1 km → 250 km), reaching the floor and ceiling
  of each dimension, and biome / confirmed-structure coverage counts.
- Watch pins: player-supplied expectations of where a structure or landmark is, resolved when the
  player actually arrives and, for structures, verified against the structure's characteristic
  building blocks in already-loaded chunks.
- Verified discoveries are labelled *confirmed*; unverified ones are labelled *unconfirmed* and
  marked in the list. WorldTrace never presents a guess as a fact.

**Journal interface**
- Dark field-journal interface with a header, search box, wrapping category chips, dimension cycler,
  favourites toggle and four sort orders.
- Search across names, source names, notes, tags, category, dimension, biome/structure ids and
  coordinates. Multiple tokens narrow the result; an exact `x y z` triplet matches directly.
- Detail pane with coordinates, dimension, discovery and last-seen times, visit count, biome and
  structure metadata, wrapped notes, tag chips and screenshot reference.
- Actions: favourite, set/clear destination, copy coordinates, edit, delete, attach screenshot.
- Distinct empty states for an empty journal and for a search with no matches.
- Full keyboard navigation, and a responsive layout that swaps panes instead of clipping on small
  windows.

**Bookmarks**
- `K` bookmarks the current position and opens the editor.
- Custom name, notes, tags and favourite flag, with length caps and input normalisation.
- Separate pin management screen showing each pin's distance, status and verification state.

**Navigation**
- Active destination per world, persisted with the journal.
- HUD readout above the hotbar with live distance and relative heading.
- Cross-dimension destinations state that plainly instead of inventing a distance.
- Coordinate copying to the clipboard. No teleport command exists in this mod.

**Notifications**
- Subtle action-bar announcement on a new discovery, which becomes a HUD strip that fades on its own.
- Opening the journal shortly after a notification jumps straight to that entry.
- No sound by default.

**Storage**
- One JSON journal per world, stored under `worldtrace/worlds/`, plus a `.bak` copy.
- Atomic writes through a temporary file, so a crash cannot truncate a journal.
- Per-record validation: a malformed row is skipped and reported; the rest of the journal survives.
- Corrupt files are quarantined as `.corrupt-<timestamp>` and recovered from the backup.
- Versioned schema with forward migration; files written by a newer version open read-only so they
  can never be downgraded.
- Background, coalesced writes: a burst of discoveries produces one disk write, and the render
  thread never waits on I/O.

**Configuration**
- `worldtrace/config.json` with validated options for discovery categories, notification mode,
  revisit behaviour, the navigation HUD, technical ids, accent colour, automatic screenshots, the
  entry cap and a world id override.

**Privacy**
- No telemetry, analytics, accounts, updaters or network calls. All data stays inside the game
  directory.

### Known limitations
- Vanilla never sends structure data to clients, so structures are only recorded through watch pins
  that the player creates.
- Singleplayer worlds are identified by level name plus a hash, because the save path is not exposed
  to client-side mods. `worldIdOverride` covers edge cases.
- Display names are derived from registry ids rather than translation keys, so they appear in
  English even when the game is in another language.
- Notes are edited in a single-line field.