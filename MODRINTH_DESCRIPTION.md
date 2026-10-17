# Modrinth project description

Paste-ready copy for the [Modrinth](https://modrinth.com) project page.

---

## Short summary (tagline)

**WorldTrace — the memory of your world.** It records the biomes and milestones you find, keeps your
own bookmarks with notes and tags, and lets you search and navigate back to any of it later.

---

## Description

You have walked past that cherry grove at night, under that ancient city, with the boat you lost in a
sandbar somewhere. Two weeks later you cannot find any of it again.

WorldTrace keeps a private, searchable journal of the world you are actually exploring — and then
gets out of the way.

### What it does

- **Records as you explore.** Every new biome, plus distance, depth and coverage milestones, are
  logged automatically with the exact block you were standing on.
- **Lets you bookmark anything.** Drop a bookmark wherever you are, give it a name, notes and tags,
  mark it as a favourite, and attach a screenshot.
- **Finds it again fast.** Full-text search across names, notes, tags and coordinates. Filter by
  category or dimension. Sort by newest, oldest, name, or favourites.
- **Helps you get there.** Set an entry as your destination and see live distance and heading as you
  walk. One click copies the coordinates. WorldTrace never teleports you.
- **Stays honest.** A biome you stood in is a fact. A structure you were told about is labelled
  *unconfirmed* until WorldTrace finds its actual blocks in the world. Nothing is invented.

### Design

- A dark, restrained journal that looks native to Minecraft — not a web dashboard in a frame.
- Keyboard navigation throughout, and a layout that works from a 320×240 window to 4K.
- Writes are atomic and backgrounded. A crash cannot lose your history, and a corrupt line cannot
  take the rest of the journal with it.
- One file per world or server, so saves never mix.

### Privacy

No telemetry. No analytics. No account. No network requests of any kind, ever. Your world data and
your screenshots never leave your computer, and are never shared with the server you play on.

### Requirements

Fabric Loader **1.21.1**, Fabric API, Minecraft **1.21.1**. Client-side only.

### Honest limitations

- **Structures are found through watch pins, not automatically.** Vanilla Minecraft never sends
  structure data to clients, so no client-side mod can detect a temple on its own without cheating.
  You tell WorldTrace where you expect something; it records the discovery once you have actually
  been there, and verifies it against the structure's real blocks when it can.
- Display names come from registry ids, so they appear in English.
- Two saves with the same display name are separated by a hash of that name; an edge-case override is
  available in the config.

---

## Gallery captions

1. The journal — list, filters and search.
2. The detail pane, with coordinates, notes, tags and actions.
3. The action-bar notification when something new is discovered.

---

## Categories / tags

`exploration`, `journal`, `map`, `waypoints`, `bookmarks`, `client-side`, `utility`, `qol`,
`survival`, `fabric`

## Links

- Source: the repository root
- License: MIT
- Changelog: `CHANGELOG.md`