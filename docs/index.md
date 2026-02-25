# Civillis

**Build more, spawn less.** Hostile mobs naturally avoid civilized areas.

Civillis is a Fabric mod that suppresses hostile mob spawning near player-built structures by computing a real-time civilization score based on block composition. No special items, no commands — just play, and the land responds.

---

## Quick Start

1. Install [Fabric Loader](https://fabricmc.net/) ≥ 0.15.0 and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Drop the Civillis jar into your `mods/` folder
3. Launch the game — civilization protection is active immediately

Optional: install [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) for the in-game settings GUI.

---

## Core Concepts

- [How It Works](how-it-works.md) — civilization scoring, detection range, spawn suppression
- [Blocks & Scoring](blocks-and-scoring.md) — which blocks count and how much they weigh
- [Civilization Decay](civilization-decay.md) — how protection fades and recovers
- [Monster Heads](monster-heads.md) — spawn gate bypass, distant redirection, mob conversion
- [Mob Flee AI](mob-flee-ai.md) — hostile mob retreat behavior in civilized zones
- [Civilization Detector](detector.md) — sonar pulse, boundary walls, visual feedback

## Configuration

- [Configuration Guide](configuration.md) — GUI settings, civil.properties, advanced parameters

## For Modpack Authors

- [Data-Driven Registries](modpack/data-driven.md) — adding blocks and skull types via JSON datapacks
- [Built-in Compatibility](modpack/compatibility.md) — Farmer's Delight, Supplementaries, Create

## Technical

- [Architecture Overview](technical/architecture.md) — the shard-based civilization engine
- [Performance](technical/performance.md) — O(1) queries, delta propagation, benchmarks
