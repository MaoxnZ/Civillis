# Changelog

## [1.2.1-beta]

### Changed

- **Dimension-aware monster heads**: Skull types now respect the
  dimension they belong to. Wither skeleton skulls and piglin heads
  are Nether-only by default — bypass, attraction, and conversion
  all stay silent outside `the_nether`. Every other vanilla head
  remains active in all dimensions. Modpack authors can customize
  per-head dimension rules through the head type datapack
- **Wither skeleton conversion restored**: With dimension boundaries
  now in place, wither skeleton skulls participate in conversion
  again. Cluster three or more in a Nether fortress and let them
  do what they were always meant to

### Fixed

- **Conversion ping-pong**: When multiple skull types were clustered
  together, a converted mob could occasionally re-enter the spawn
  gate and flip to another type before settling. Conversion now
  bypasses the gate entirely — one transformation, done

### Added

- **Documentation wiki**: A comprehensive wiki covering mechanics,
  configuration, modpack authoring guides, and technical architecture
  is now live on GitHub Pages. This has been a labor of love and I'm
  genuinely excited to share it with you — feedback is very welcome

## [1.2.0-beta]

### Changed

- **Head mechanics reworked**: Monster skulls now follow clearer,
  more intuitive rules. One or two skulls simply allow extra spawns
  nearby — no conversion, just a breach in your civilization's
  defenses. Place three or more skulls in the same chunk and mobs
  start converting into the skull types you've placed, with conversion
  strength scaling smoothly up to ten heads. By default, wither
  skeleton skulls do not participate in conversion — they shouldn't
  be wandering out of the Nether just because you decorated your base
- **Block scoring rebalanced**: Civilization scores now follow a
  consistent design framework based on crafting complexity and
  symbolic weight. Simple stone-age crafts — crafting tables,
  furnaces, chests — contribute modestly, as they should. Iron-age
  workstations carry more weight. Nether and End materials — brewing
  stands, enchanting tables, lodestones — are serious civilization
  anchors. Boss-tier structures like beacons and conduits remain the
  strongest pillars. Glowing or magical blocks get a bonus on top

### Added

- **New scored blocks**: Bells, respawn anchors, lodestones, end rods,
  and decorated pots now contribute to your civilization score
- **Built-in mod compatibility**: Civillis can now recognize blocks
  from other mods as part of your civilization. This version ships
  with built-in support for:
  - **Farmer's Delight** — stoves, cooking pots, cutting boards,
    baskets, and all cabinet types
  - **Supplementaries** — safes, globes, clock blocks, sconces, jars
  - **Create** — steam engines, blaze burners, mechanical mixers and
    presses, basins, depots

  More mods will be added in future updates
- **Datapack-driven registries**: Both civilization block scoring and
  monster head types are now loaded from JSON data files, fully
  overridable via datapacks. Every default value can be tweaked,
  replaced, or extended without touching the mod's code

#### For modpack authors and bridge-mod developers

Block scores and head types are now fully data-driven:

- **Blocks**: `data/<namespace>/civil_blocks/*.json` — add entries to
  register new blocks or override existing weights. Use tags
  (`#minecraft:beds`) or individual block IDs
- **Heads**: `data/<namespace>/civil_heads/*.json` — map custom skull
  type strings to entity types. Toggle any head with `"enabled": false`
  to make it purely decorative, or set `"convert": false` to keep it
  active but exclude it from the conversion pool

Both support `"replace": true` to wipe all previously loaded entries
and start from a clean slate (use with caution — this clears everything,
including defaults from other mods).

Mods that add new skull types via custom `SkullBlock` subclasses are
automatically compatible. Player-head-based mob heads (as used by mods
like All The Heads or Just Mob Heads) cannot be distinguished by skull
type and are not currently supported.

### Note

Learning to listen before building. Still a long way to go.

## [1.1.1-beta]

### Fixed

- **Sonar charge-up particle mismatch**: The charge-up column
  previously showed civilization particles (white or soul-blue) even
  when standing inside an active totem zone. It now displays orange
  fire consistently

## [1.1.0-beta]

### Added

- **Detector Sonar**: The Civilization Detector now fires a sonar
  pulse. Right-click to charge up — with a rising tone — then an
  expanding shockwave booms outward, sweeping the terrain. Particle
  colors tell you where you stand: white sparks mean protected
  ground, soul-blue flames mean exposed territory, and orange fire
  marks active totem zones
- **Aura Walls**: Glowing barriers rise at your civilization
  boundaries as the shockwave passes — gold for civilization edges,
  amethyst for totem zones. They breathe, scroll, then gently fade.
  Fire the detector again and existing walls hold steady while new
  faces appear
- **Aura toggle**: The sonar effect can be switched on or off in the
  settings GUI (enabled by default). The detector's original color
  and sound feedback is unaffected

See your borders, my lord.

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
