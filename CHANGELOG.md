# Changelog

## [1.4.0-release]

### Added

- **Advancements**: Civillis now has its own advancement tree. Pick up an emerald and open
  your advancements tab — it surfaces each major feature as you encounter it, from the
  Civil Detector to bell sonar to the Podium of Undying.
- **Entity Presence Upkeep**: Nearby villagers, iron golems, and similar entities now slow
  civilization decay while they are around. Toggle under *Miscellaneous* in settings.
  Which entity types count can be customized via data packs.
- **Zone HUD font scale**: New slider in Zone Transition HUD settings to resize the
  transition text, ranging from 50% to 500%.
- **Minimap overlay colors configurable** *(1.20.1 and 1.21.1 only)*: The zone overlay
  colors shown on JourneyMap and Xaero's maps can now be changed in `civil.properties`
  using standard `0xAARRGGBB` hex values (`advanced.chunkBand.color*`).

### Changed

- **Mob Flee AI — allow/block lists**: Pillagers, evokers, and raid-type mobs no longer
  flee from civilization, so raids stay as challenging as ever. Which mobs do or don't
  flee is now fully configurable via data packs (`civil_mob_flee_entities`).
- **Config update behavior**: GUI settings now only reset when a release explicitly calls
  for it. Previously, any version bump could trigger a reset — that is no longer the case
  by default. Users with `simple.persistAcrossSchema=true` are fully shielded either way.

### Fixed

- **Sonar aura walls**: Civilization boundary walls from sonar scans now only detect
  boundaries at your current height layer instead of spanning the full world height.
  Overlapping wall faces between adjacent zones are also resolved.
- **Exploration performance**: Significantly improved performance when loading into or
  moving through large areas. The key change is a reworked NBT bulk-load schedule that
  avoids unnecessary work during fast exploration.

### Note

- Feedback, bug reports, ideas — join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.3.3-release]

### Added

- **Adventure structures**: Hostile spawns are now allowed in more major adventure structures,
  including Pillager Outposts, YUNG's Better structures, Cataclysm, When Dungeons Arise,
  Twilight Forest, Incendium, and more.
- **Zone visualization**: Dangerous adventure zones now show in orange across sonar pulses,
  Civil Detector feedback, civil maps, and supported minimap overlays.
- **Podium of Spawning HUD**: Shrine pockets now get their own HUD state. Civil Detector and
  sonar scans use a shriek cue with purple-toned particle effects when they find one.

### Changed

- **Civil Detector feedback**: Right-click scanning now uses clearer item states — orange for
  danger zones, blue for wilderness, and swamp green for civilization borders.
- **Config defaults**: GUI settings now refresh to each release's intended defaults after
  updating. If you really want to keep old GUI values, uncomment
  `simple.persistAcrossSchema=true` in `civil.properties`; future saves still write the current
  release marker cleanly.

### Fixed

- **Fast exploration performance**: Civillis now keeps less background work in flight while
  you move quickly, making exploration smoother while preserving the sense of discovery.
- **Minimap overlay freshness** *(1.20.1 and 1.21.1 only)*: Supported minimap overlays now
  follow layered zone changes more reliably instead of lagging behind certain updates.

### Note

- Thanks to Stick Boy for reworking part of the Civil Detector textures.
- Feedback, bug reports, ideas — join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.3.2-release]

### Added

- **Podium of Spawning**: Crying obsidian, a soul campfire burning on top, and a bone
  to sacrifice. Build it in any civilization area to carve out a **mob-friendly pocket**
  — hostile mobs spawn freely inside as if it were open wilderness, regardless of the
  surrounding civilization score. Mob heads placed inside shape which types are drawn to
  the area. Attract Strength and Attract Range are tunable in the in-game settings GUI.

### Changed

- **Civil map tinting — faster**: Chunk height sampling now uses a sparse grid, cutting
  the per-chunk work while keeping tint accuracy on civil maps and map mod overlays.
- **Lighter chunk loading**: Civillis no longer pre-populates empty scoring slots when a
  chunk loads. That work is deferred until a score is actually needed, which should make
  chunk loading noticeably lighter on busy servers.
- **Civilization overlay** *(1.20.1 and 1.21.1 only)*: JourneyMap and Xaero's overlays
  now **start disabled** by default — the scanning is server-side and can be costly at
  larger ranges. Turn each one on in settings when you want it, and use the new
  **Exploration Radius** setting to dial in how aggressively it scans.
- **Modpack block tagging** *(for modpack authors)*: Civilization block weights now include
  tiered block tags such as `#civil:high_civilized` and `#civil:low_civilized`, so packs can
  add custom blocks by assigning them to a tag instead of writing one weight entry per block.

### Fixed

- **Score not updating after block changes**: In rare cases, placing or removing a block
  could leave civilization score wrong or completely unchanged — a nested block update on
  the server thread caused the mod to read the wrong previous block state. Fixed.
- **Stale temporary scores blocking real updates**: A rough early score could get
  continuously refreshed by background activity, preventing the real score from ever
  replacing it. Those estimates now expire on a fixed schedule regardless of touches.

### Note

- **Civilization overlay** changes above apply to **1.20.1** and **1.21.1** builds only.
- Feedback, bug reports, ideas — join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.3.1-release]

### Added

- **JourneyMap & Xaero's Minimap — civilization overlay**: Civil region coloring now
  shows directly on JourneyMap, and on Xaero's Minimap + World Map (both mods required
  for Xaero's). Toggle each one independently in the in-game settings GUI.
- **Zone transition HUD — more control**: You can now set your own text for the
  Civilized, Wilderness, and Caution messages, add a cooldown so the hint doesn't repeat
  too often, and move the HUD to a different spot on screen. All under the new Zone
  Transition HUD section in the settings GUI.
- **`/civil ring` admin command**: Triggers a sonar pulse at your position — useful for
  quickly checking how the aura wall looks from a given spot without having to ring a bell.
  Requires OP.
- **Spawn gate — entity blacklist / whitelist** *(for modpack & datapack authors)*:
  You can now explicitly force extra mob types through the spawn gate or exclude them via
  `data/<namespace>/civil_spawn_gate_entities/`.

### Fixed

- **Dimension policy not fully blocking mob-head mechanics**: In certain setups, a
  dimension marked as "no civilization" could still trigger mob-head spawn behavior.
  Fixed — dimension policy now takes full effect before any head logic runs.
- **Supplementaries map tint conflict** *(1.20.1 and 1.21.1)*: Civil map region coloring
  was being overridden by Supplementaries' client-side map rendering. Civil tints now
  display correctly alongside Supplementaries.

### Changed

- **Admin commands now require OP**: `/civil rebuild` and `/civil ring` are no longer
  usable by regular players; they require game-master permission (OP level 2+, or cheats
  enabled in single player).

### Note

- **JourneyMap / Xaero's support** is currently available on **1.20.1 and 1.21.1** only.
- Feedback, bug reports, ideas — join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.3.0-release]

### Added

- **Civil maps**: crafted from a filled map and a Civil Detector. An empty map or a locked map will
  not work. A civil map is easy to spot from its lore (*The sheet holds civility.*) and from the
  regional tinting on the map.
- **Civil map tints**: applied at the same time as the vanilla map reveals terrain. Washed-white
  tint for civilized areas and purple tint for mob-head zones, with clear border lines for both.
  The regions update when the world changes, for example when you place or remove blocks that feed
  into civilization scoring.
- **Cartography table**: civil maps work like vanilla maps: paper to zoom out, glass panes to lock,
  an empty map to duplicate. Once a normal filled map has been turned into a civil map, that lore
  stays on the item; everything you pull out of the table is still a civil map.

### Note

- **Dimension policies**: if civilization is off for a dimension, maps there get no civil tints.
- **Config**: `mapTint.*` in `civil.properties` adjusts tint strength (defaults unchanged).
- **Supplementaries**: client-side map recoloring in Supplementaries overrides civil map tints.
  We are aware of this and plan to address it in a future release (compat layer / draw order).
- Feedback, bug reports, ideas. Join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.2.3-release]

### Fixed

- **Deadlock**: Rare deadlock could freeze the server mid-tick. That bug dates back to the
  first public release; zone recognition (extra chunk work on top) made it show up far
  more often in normal play. Fixed.
- **Civilization score in fast exploration**: Sprinting through brand-new chunks could leave
  local score wrong for a bit — a few timing holes. Closed those.
- **Civil Detector (Forge & NeoForge)**: Used to land at the bottom of Tools & Utilities via
  the default add hook, unlike Fabric. Now it sits right after the compass, creative
  search included.

### Added

- **Block weights for more mods**: More `civil_blocks` datapack entries (storage, decoration,
  tech, …). Civilization scoring should feel more consistent when you run lots of popular
  mods together. Modpacks can add or override under `data/<namespace>/civil_blocks/`.
- **Dimension policies (datapack)**: `civil_dimension_policies` — per-dimension overrides.
  Civilization (zones + scoring) and mob-head spawn handling can be turned off
  independently per dimension. Ships with defaults for Minecells; replace or extend via
  your own datapack if needed.

### Changed

- **Zone transition HUD**: Civilized / Wilderness text fires less often; fewer stray
  Civilized flashes where nothing really changed. Toggle under Miscellaneous in the
  in-game settings GUI.
- **In-game settings GUI**: New icon. Tooltips on a few rows — line breaks and wording only.

### Note

- Thanks to **Mitemi** for block scoring data on popular mods.
- Feedback, bug reports, ideas — join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.2.2-release]

### Added

- **Zone transition HUD**: Short on-screen hints with smooth fade animations.
  **Civilized** and **Wilderness** show when you **enter or leave** a large span of
  civilized chunks; **Caution** marks structure-tinted areas (for example **ocean
  monuments**). You can turn this off under **Miscellaneous** in the in-game settings
  GUI.
- **Hostile spawns bypass Civillis in specific structures**: For example, **ocean
  monuments** now behave as you’d expect — **Guardians** are no longer suppressed by
  civilization. Modpack authors can tweak or extend this with a **datapack**
  (`data/<namespace>/civil_zone_policies/`).
- **Round-robin prefetch engine**: Reworked how decay is driven so it **tracks patrol
  behavior more faithfully**, and **wilderness no longer burns server time on decay**
  in the background.

### Changed

- **Civil Detector look**: Big texture refresh — more of a **magic crystal-ball** vibe.
  Tell us whether you like the new art.

### Fixed

- **NeoForge — sonar aura wall**: No longer **jitters back and forth** as you move.
- **Fabric — sonar aura wall**: **Scrolling texture** works again on builds where it
  stalled; the wall no longer **sinks too deep underwater** on some versions.
- **Detector sonar**: **Head-zone sounds** were wrong on some versions — fixed.

### Note

- Thanks to **Stick Boy** for the new Civil Detector artwork.
- Feedback, bug reports, ideas — join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.2.1-release]

### Fixed

- **Podium "drains civilization"**: Fixed the major issue where moving far away from an
  activated Podium of Undying could unexpectedly drain nearby civilization scores over
  time (creating civilization hollows), and fixed a broader set of instability paths
  tied to unloaded chunks and high-mobility scenarios that could cause civilization
  values to read too low.
- **Shared-gold Podium deactivation**: Fixed an issue where two Podiums of Undying
  sharing connected gold blocks could deactivate only one podium when the shared block
  was broken.
- **Beds/Campfires not contributing to civilization**: On some Forge/Fabric ports, beds, campfires,
  and similar blocks could fail to contribute civilization score correctly. For
  modpack users, this actually fixes `#tag`-based block weight loading.

### Added

- **`/civil rebuild` admin command**: Added a one-shot rebuild command to clear
  stale or historically polluted civilization data and rebuild clean state.

### Note

- If this is **not your first time** using Civillis in the same world, it is strongly
  recommended to run `/civil rebuild` once after updating to `1.2.1-release`.
- The only known side effect is that already-activated **Podiums of Undying** will reset.
  Most players who rely on Undying are serious endgame survivors, so we hope this impact
  is acceptable for a cleaner and more stable data baseline.
- Feedback, bug reports, ideas — join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.2.0-release]

### Changed

- **Storage engine replaced — H2 removed**: Civillis no longer uses the H2 database.
  All persistence (civilization scores, mob heads, undying anchors) now runs on a
  custom NBT-based storage layer with a dedicated I/O queue. Functionality is
  unchanged, but the mod jar is now 20× smaller.

### Added

- **Podium of Undying**: Build a small altar in a high-civilization area and
  activate it with a totem. When you would die, you are teleported there and
  revived with totem effects — regeneration, absorption, fire resistance —
  without consuming a totem. One activation per altar. Hint: gold blocks, waxed
  oxidized cut copper stairs, and an emerald at the heart. Can you guess the
  layout?
- **Config screen update**: Added a dedicated Podium of Undying section
  (including options such as enable/disable, civilization requirement
  threshold, and cooldown-related controls) and a Miscellaneous category for
  options that do not fit cleanly into other groups.

### Fixed

- **World-switch cache pollution**: Cached civilization data from the previous
  world no longer pollutes the current one when switching worlds or reloading.
  Each world now keeps its own data strictly isolated.
- **Payload completion on world switch**: Fixed potential crashes when changing
  worlds while sonar, particle, or other payloads were still in progress.

### Note

- Thanks to **Stick Boy** for designing the Podium of Undying altar structure.
- Feedback, bug reports, ideas — join our Discord: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.1.1-release]

### Fixed

- **Major compatibility issue resolved**: H2 database dependency is now fully
  relocated and isolated, eliminating conflicts with mods like Biomancy that
  bundle their own H2 version. Civillis should now run stable in large modpacks.

### Added

- **Mob Head System toggle**: New option to globally disable mob head gameplay
  effects (spawn attraction, detector zone display, flee AI awareness) while
  keeping the data intact. Useful for players who like decorating walls with
  skulls without affecting spawn behavior.
- **Civilization Decay toggle**: New option in the Decay settings to disable
  time-based decay entirely. When off, your settlements stay forever fresh —
  no need to patrol them to maintain protection.

### Changed

- **Config labels clarified**: "Spawn Suppression Strength" is now "Civilization
  Strength" and "Detection Range" is now "Max. Civilization Radius" (with block
  units shown). The Mob Flee AI tooltip now references the renamed setting.

### Note

- Thanks to **Blugori** and **Stick Boy** for reporting the Biomancy
  incompatibility — your feedback made this fix possible.
- If you notice anything off, join our Discord and I'll always be there to
  help: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.1.0-release]

### Added

- **Bell on Lodestone sonar**: When a bell is placed on a lodestone, it can act as a
  powerful sonar with cooler particle effects. Right-click the bell to trigger a
  civilization sweep of the surrounding area — no Civil Detector required. A
  vanilla-friendly way to scout before you build.

### Changed

- **Sonar visualization config reorganized**: Detector sonar options moved into a
  dedicated Sonar Visualization section. The portable detector's sonar can be turned
  off independently; bell-on-lodestone sonar always runs when you ring it. Both
  sonars now have configurable detection ranges, with bell sonar using a larger
  default suited to stationary "detection stations."
- **Bell sonar tuning**: Bell sonar has a longer cooldown and a longer-lasting aura
  wall effect than the detector, reflecting its heavier, more deliberate use.

### Note

- **Pro tip**: A bell on lodestone has intrinsic civilization value. Place a few at
  your base perimeter and they'll extend your safe zone while doubling as detection
  stations.
- **Vanilla first**: Going forward, we'll aim to add vanilla counterparts whenever we
  introduce new items. Thanks, Stick Boy, for suggesting the bell-on-lodestone idea —
  it's exactly the kind of thing we love to support.

## [1.0.1-release]

### Fixed

- **Civil Detector crafting restored (`1.20.1-1.21.1`)**: Fixed the cross-version
  recipe compatibility issue that made `civil_detector` uncraftable on part of the
  supported ports.
- **Mob heads no longer pull from the entire dimension**: Heads now affect spawns
  only within the configured attraction range, instead of influencing faraway areas
  across the world. This also improves runtime performance in normal gameplay.
- **Forge mapping issues fixed for cleaner builds**: Resolved mapping/remap edge
  cases so development and production builds are more consistent and stable.

### Added

- **Index-driven mob head acceleration**: Introduced a spatial index for head-based
  spawn calculations, greatly reducing overhead in dense head setups and removing
  the last known performance hotspot in Civillis.

### Note

- **Join our Discord**: We'd love your feedback, bug reports, and ideas in the
  Civillis community: [Civillis Official](https://discord.gg/dA7QCPx7zd)

## [1.0.0-release]

### Fixed

- **Smarter mob flee behavior**: Hostile mobs no longer freeze at civilization borders.
  They now continue leaving civilized areas instead of stopping exactly on the edge.
- **Totem-zone entry is now correct**: When a monster head totem is nearby, mobs
  correctly move into the totem zone instead of halting at the outer rim.
- **Mob Flee AI settings UI cleanup**: Removed the empty expandable
  `Mob Flee AI` section in `InGameSettingsGui`. For now, this feature
  is exposed as a single toggle only.

### Added

- **Combat panic visual feedback**: Fear-driven fleeing during combat now has matching
  particle effects, making panic behavior visible and readable in real time.

### Changed

- **Major Architectury refactor (full 1.20-1.21 line)**: This release includes a large-scale
  architecture refactor to support long-term multi-loader maintenance across Fabric, Forge,
  and NeoForge.
- **Version support is now explicitly standardized by port range**:
  - `1.20.1` (Fabric + Forge)
  - `1.20.2-1.20.4` (Fabric + Forge)
  - `1.20.5-1.20.6` (Fabric + NeoForge)
  - `1.21.1` (Fabric + NeoForge)
  - `1.21.2-1.21.3` (Fabric + NeoForge)
  - `1.21.4` (Fabric + NeoForge)
  - `1.21.5` (Fabric + NeoForge)
  - `1.21.6-1.21.8` (Fabric + NeoForge)
  - `1.21.9` (Fabric + NeoForge)
  - `1.21.10` (Fabric + NeoForge)
  - `1.21.11` (Fabric + NeoForge)

### Note

- **Out of beta**:
  Every larger leap has always been our unchanged resolve.
  I've played this game for over ten years, and this mod idea stayed at my fingertips for too long,
  repeatedly delayed by everyday life.
  This time, I won't let it go.

## [1.3.0-beta]

### Added

- **Mob Flee AI**: Hostile mobs in civilized areas now actively
  try to leave. Idle mobs will wander toward less civilized ground,
  and in heavily built-up areas they may even panic and flee
  mid-combat. The stronger your civilization, the more likely they
  are to run ??tied to your Spawn Suppression strength setting
- **Head zone awareness**: Mobs near a monster head totem are
  drawn toward its zone rather than wandering blindly. Mobs already
  inside a head zone won't try to leave ??they belong there
- **Mob Flee AI toggle**: On/off switch in the config GUI, right
  next to Detector Sonar. Turn it off if you prefer mobs to stay
  and fight

## [1.2.1-beta]

### Changed

- **Dimension-aware monster heads**: Skull types now respect the
  dimension they belong to. Wither skeleton skulls and piglin heads
  are Nether-only by default ??bypass, attraction, and conversion
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
  bypasses the gate entirely ??one transformation, done

### Added

- **Documentation wiki**: A comprehensive wiki covering mechanics,
  configuration, modpack authoring guides, and technical architecture
  is now live on GitHub Pages. This has been a labor of love and I'm
  genuinely excited to share it with you ??feedback is very welcome

## [1.2.0-beta]

### Changed

- **Head mechanics reworked**: Monster skulls now follow clearer,
  more intuitive rules. One or two skulls simply allow extra spawns
  nearby ??no conversion, just a breach in your civilization's
  defenses. Place three or more skulls in the same chunk and mobs
  start converting into the skull types you've placed, with conversion
  strength scaling smoothly up to ten heads. By default, wither
  skeleton skulls do not participate in conversion ??they shouldn't
  be wandering out of the Nether just because you decorated your base
- **Block scoring rebalanced**: Civilization scores now follow a
  consistent design framework based on crafting complexity and
  symbolic weight. Simple stone-age crafts ??crafting tables,
  furnaces, chests ??contribute modestly, as they should. Iron-age
  workstations carry more weight. Nether and End materials ??brewing
  stands, enchanting tables, lodestones ??are serious civilization
  anchors. Boss-tier structures like beacons and conduits remain the
  strongest pillars. Glowing or magical blocks get a bonus on top

### Added

- **New scored blocks**: Bells, respawn anchors, lodestones, end rods,
  and decorated pots now contribute to your civilization score
- **Built-in mod compatibility**: Civillis can now recognize blocks
  from other mods as part of your civilization. This version ships
  with built-in support for:
  - **Farmer's Delight** ??stoves, cooking pots, cutting boards,
    baskets, and all cabinet types
  - **Supplementaries** ??safes, globes, clock blocks, sconces, jars
  - **Create** ??steam engines, blaze burners, mechanical mixers and
    presses, basins, depots

  More mods will be added in future updates
- **Datapack-driven registries**: Both civilization block scoring and
  monster head types are now loaded from JSON data files, fully
  overridable via datapacks. Every default value can be tweaked,
  replaced, or extended without touching the mod's code

#### For modpack authors and bridge-mod developers

Block scores and head types are now fully data-driven:

- **Blocks**: `data/<namespace>/civil_blocks/*.json` ??add entries to
  register new blocks or override existing weights. Use tags
  (`#minecraft:beds`) or individual block IDs
- **Heads**: `data/<namespace>/civil_heads/*.json` ??map custom skull
  type strings to entity types. Toggle any head with `"enabled": false`
  to make it purely decorative, or set `"convert": false` to keep it
  active but exclude it from the conversion pool

Both support `"replace": true` to wipe all previously loaded entries
and start from a clean slate (use with caution ??this clears everything,
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
  pulse. Right-click to charge up ??with a rising tone ??then an
  expanding shockwave booms outward, sweeping the terrain. Particle
  colors tell you where you stand: white sparks mean protected
  ground, soul-blue flames mean exposed territory, and orange fire
  marks active totem zones
- **Aura Walls**: Glowing barriers rise at your civilization
  boundaries as the shockwave passes ??gold for civilization edges,
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
  civilization checks ??spawn eggs, mob spawners, /summon, raid
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
how this mod performs. Global civilization impact ??something
I long believed to be out of reach ??is now real.

Ready for beta.
Will keep doing better. Will not disappoint.

## [1.2.0-alpha]

### Added

- **Mob heads are now true totems**: Monster skulls no longer just
  allow spawns nearby ??they actively pull hostile mobs toward them
  from across the dimension. Place a skeleton skull and watch
  monsters converge on it, even from deep underground caves. No more
  lighting up every last tunnel or building your mob farm in the sky
  ??let the totems do the work
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
  builds for MC 1.21.1, 1.21.2??.21.3, 1.21.4, and 1.21.5??.21.11,
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
  decayed settlement gradually restores its protection ??the longer you
  stay, the more it recovers

### Changed

- **Internal data system rebuilt**: Save data is now stored more
  efficiently and reliably, reducing file overhead and improving
  compatibility with large multiplayer servers
- **Smarter patrol detection**: The game now detects your presence
  around settlements more precisely, with built-in "debounce" ??
  flying past an abandoned city at high speed does not count as a
  proper patrol

### Fixed

- **Offline decay**: Logging out of a single-player world no longer
  counts as absence ??your civilization protection picks up right
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
  civilization score computed over a 240?240?48 block detection area
  (15?15?3 voxel chunks)
- **Monster head mechanic**: Placing monster skulls overrides spawn
  blocking ??matching skull types attract specific mob types, enabling
  players to selectively invite danger
- **Civilization Detector**: A craftable handheld item that scans and
  displays the local civilization level with color-coded visual feedback
  and custom sound effects
- **Civilization decay**: Unvisited areas gradually lose their civilization
  score over 24 hours, weakening but not eliminating protection for
  well-established settlements. For reference, a maximally developed city
  maintains a ~90-block spawn-free perimeter at full strength; after
  decay, this shrinks to ~40 blocks ??still well within the detection
  range. Smaller builds see proportionally less protection to begin with
- **Scalable architecture**: Designed for large multiplayer servers with
  async database persistence and player-aware cache prefetching
- **Zero configuration**: Works out of the box with sensible defaults
