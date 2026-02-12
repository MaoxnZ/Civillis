# Changelog

## [1.0.1-beta]

### Fixed

- **Spawn eggs, spawners, and commands now work properly in civilized areas**:
  Civilization scoring previously intercepted all monster spawns
  regardless of origin. Now only natural spawns are subject to
  civilization checks — spawn eggs, mob spawners, /summon, raid
  events, and zombie reinforcements all bypass it as intended

## [1.0.0-beta]

### Changed

- **Fusion Architecture**: A 0-1 revamp of the entire engine.
  It does everything it did before, but 100x faster

### Added

- **Patrol influence range**: New GUI slider for how far your
  patrol sustains and restores settlements
  (requires ModMenu + Cloth Config)

### Note

At last, I see the light.

After two weeks of dedicated work, I am finally at peace with
how this mod performs. Global civilization impact — something
I long believed to be out of reach — is now real.

Ready for beta.
Will keep doing better. Will not disappoint.

## [1.2.0-alpha]

### Added

- **Mob heads are now true totems**: Monster skulls no longer just
  allow spawns nearby — they actively pull hostile mobs toward them
  from across the dimension. Place a skeleton skull and watch
  monsters converge on it, even from deep underground caves. No more
  lighting up every last tunnel or building your mob farm in the sky
  — let the totems do the work
- **Configurable attraction**: Adjust how strongly and how far totems
  attract mobs through the in-game settings GUI (Mod Menu + Cloth
  Config). Dial it up for a powerful funnel, or tone it down for a
  subtler nudge

### Note

The totem attraction is a significant suppression of distant spawns,
not a 100% redirect. For absolute peak mob farm efficiency, traditional
techniques (like building above the spawn cap height) still give that
last few percent. But for most players, a handful of skulls is all you
need. Mix and match different skull types to further control the ratio
of mobs that show up.

Civillis won't break your farms, it only makes them better.

## [1.1.1-alpha]

### Added

- **Full MC 1.21 series compatibility**: The mod now ships separate
  builds for MC 1.21.1, 1.21.2–1.21.3, 1.21.4, and 1.21.5–1.21.11,
  covering the entire 1.21 release line

### Fixed

- **Inflated score on first teleport**: Teleporting to a previously
  unvisited location no longer briefly reports a high civilization
  score. Unloaded regions now default to zero instead of the maximum
  estimate, which better reflects reality given the persistent storage
  and prefetcher systems already in place

## [1.1.0-alpha]

### Added

- **In-game settings GUI**: Install Mod Menu + Cloth Config to configure
  Civillis directly from the pause menu. Six intuitive sliders let you
  adjust spawn suppression strength, detection range, decay speed,
  recovery speed, decay floor, and freshness duration
- **Gradual civilization decay and recovery**: Civilization protection
  now fades smoothly over time when you leave an area. Revisiting a
  decayed settlement gradually restores its protection — the longer you
  stay, the more it recovers

### Changed

- **Internal data system rebuilt**: Save data is now stored more
  efficiently and reliably, reducing file overhead and improving
  compatibility with large multiplayer servers
- **Smarter patrol detection**: The game now detects your presence
  around settlements more precisely, with built-in "debounce" —
  flying past an abandoned city at high speed does not count as a
  proper patrol

### Fixed

- **Offline decay**: Logging out of a single-player world no longer
  counts as absence — your civilization protection picks up right
  where you left off
- **Monster head score spike**: Monster heads placed far from the
  detection center no longer inflate the civilization score at a
  distance. Head influence is now tracked separately
- **Detector colors ignoring settings**: The Civilization Detector now
  respects your configured suppression strength when choosing the
  result color (red / yellow / green)

## [1.0.0-alpha]

Initial public release.

### Features

- **Civilization-based spawn control**: Mob spawning is dynamically
  suppressed near player-built structures based on a real-time
  civilization score computed over a 240×240×48 block detection area
  (15×15×3 voxel chunks)
- **Monster head mechanic**: Placing monster skulls overrides spawn
  blocking — matching skull types attract specific mob types, enabling
  players to selectively invite danger
- **Civilization Detector**: A craftable handheld item that scans and
  displays the local civilization level with color-coded visual feedback
  and custom sound effects
- **Civilization decay**: Unvisited areas gradually lose their civilization
  score over 24 hours, weakening but not eliminating protection for
  well-established settlements. For reference, a maximally developed city
  maintains a ~90-block spawn-free perimeter at full strength; after
  decay, this shrinks to ~40 blocks — still well within the detection
  range. Smaller builds see proportionally less protection to begin with
- **Scalable architecture**: Designed for large multiplayer servers with
  async database persistence and player-aware cache prefetching
- **Zero configuration**: Works out of the box with sensible defaults
