# Changelog

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
