# Changelog

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
