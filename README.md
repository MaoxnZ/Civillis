# Civillis

A Fabric mod that suppresses hostile mob spawning based on nearby player-built structures. The more you build, the safer the area becomes — no torches, no special blocks, no commands.

## Overview

Civillis scans a configurable area (default 240×240×48 blocks) around each mob spawn attempt, scores the surrounding blocks for signs of human presence, and decides whether to allow or suppress the spawn. Furnaces, beds, campfires, workstations, beacons, and hundreds of other blocks all contribute to the score. Protection decays over time when areas are left unvisited, but recovers when players return.

## Features

- **Spawn Suppression** — Hostile mobs are pushed out of built-up areas. A thriving settlement creates a ~90-block safe perimeter; even a small cabin helps.
- **Civilization Decay** — Unvisited areas gradually lose protection from the edges inward. The core holds. Singleplayer offline time is excluded.
- **Monster Head Totems** — Place mob skulls to punch through protection and attract specific mob types. Three or more skulls in an area trigger mob conversion. Skulls also redirect distant spawns toward themselves across the dimension.
- **Civilization Detector** — Craftable item (compass + emeralds) that fires a sonar pulse to visualize your civilization boundaries with color-coded walls and sound cues.
- **Mod Compatibility** — Built-in scoring for Farmer's Delight, Supplementaries, and Create. All block weights and head types are JSON data files, fully overridable via datapacks.
- **Server-Ready** — Async H2 persistence, player-aware cache prefetching, dedicated I/O thread pool. Zero main-thread blocking.

## Architecture

Real-time scoring across millions of blocks with decay, persistence, and mod extensibility — at near-zero tick cost. ~O(1) average query time via a custom shard-based civilization engine.

- **Voxel Chunk Partitioning** — 16³ spatial shards as the fundamental scoring unit
- **Palette Pre-filtering** — accelerated block recognition via Minecraft's internal chunk palette
- **Two-Layer Shard Cache** — per-chunk scores + pre-aggregated results for O(1) queries
- **Delta Propagation** — real-time world changes drive gradient-style score updates; cache stays perpetually fresh
- **Tiered TTL + Async H2** — dedicated I/O with TTL-driven hot/cold storage
- **Player-Aware Prefetching** — proactively warms shards near online players

All scoring is data-driven and fully extensible via JSON datapacks. See the [Wiki](https://maoxnz.github.io/Civillis/) for detailed architecture documentation.

## Requirements

- Minecraft **1.20.1 ~ 1.21.11**
- Fabric Loader **≥ 0.15.0**
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Building

The main branch targets Minecraft **1.21.11**. Other version ports are not publicly available.

```bash
git clone https://github.com/MaoxnZ/Civillis.git
cd Civillis
./gradlew build
```

Output jar is in `build/libs/`.

## Links

- [Modrinth](https://modrinth.com/mod/civillis)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/civillis)
- [Official Wiki](https://maoxnz.github.io/Civillis/)
- [Issue Tracker](https://github.com/MaoxnZ/Civillis/issues)
