*The land remembers your presence.*

*Where players build, monsters yield.*

---

## What is Civillis?

How do you keep monsters out of your base?

Torches on every surface. A special block that blankets the area. Server claims. Peaceful mode. Or maybe you just don't mind — let them spawn, deal with it, move on.

The world's never been short of solutions. But there's a truth that's been hiding in plain sight:

*You strike the anvil — its ring echoes across the village.*  
***They know this is civilization. So they leave.***

*You murmur over the enchanting table — runes flicker in the dark.*  
***They know this is civilization. So they leave.***

*You sit by the campfire with old friends — laughter rises into the night.*  
***They know this is civilization. So they leave.***

Natural. Dignified. Elegant. This should have been part of the world all along.

**Now it is.**

Civillis makes that happen. It recognizes the signs of civilization you naturally create — a campfire burning, a bed placed, a workbench humming, and hundreds more — and silently pushes hostile mobs away. The more your settlement grows, the stronger the protection. No commands, no rituals, no new mechanics to learn — just play, and the land responds.

Leave for too long, and nature slowly reclaims its ground. But a well-established city never truly falls.

---

## How It Works

Civillis evaluates a 240×240×48 block area (configurable) around each potential spawn point, computes a civilization score, and suppresses hostile spawns accordingly. Blocks that reflect human presence — furnaces, beds, campfires, beacons, workstations, and others like them — are what the mod looks for. And to keep things real, protection decays from the edges inward when left unvisited for long — but the core always holds.

- **Settle and grow → safer land.** A thriving city pushes monsters back ~90 blocks from its borders.
- **Leave for a while → gradual decay.** Protection weakens over time — but a large settlement still holds a ~40-block safe perimeter.
- **A small cabin → modest protection.** A lone outpost won't create a fortress, but it helps.

We haven't forgotten your mob farms, either. Monster skulls punch through civilization protection and redirect distant hostiles toward themselves. Cluster enough skulls and they start converting mobs to match. Automation has never felt this intuitive.

### Under the Hood

Real-time scoring across millions of blocks — with decay, persistence, and mod extensibility — at near-zero tick cost. This should be computationally impossible, but we made it ~O(1) on average with a custom shard-based civilization engine.

- **Voxel Chunk Partitioning** — 16³ spatial shards as the fundamental scoring unit
- **Palette Pre-filtering** — accelerated block recognition via Minecraft's internal palette
- **Two-Layer Shard Cache** — per-chunk scores + pre-aggregated results for O(1) queries
- **Delta Propagation** — real-time world changes drive gradient-style score updates; O(1) cache stays perpetually fresh
- **Tiered TTL + Async H2** — dedicated I/O with TTL-driven hot/cold storage; zero main-thread blocking
- **Player-Aware Prefetching** — proactively warms shards near online players

All scoring is data-driven and fully extensible via JSON datapacks.

*Constant time. We made it.*

---

## Features

### 🏘️ Natural Spawn Suppression
The blocks you already use to build your world are what keep you safe. Campfires, beds, furnaces, crafting tables, enchanting tables, beacons — the things that make a place feel *lived in* — are exactly what the mod looks for. Build the home you've always wanted, and safety follows.

### 💀 Monster Head Mechanic
Want danger back? Place a monster skull to punch through the protection. A skeleton skull draws skeletons nearby, a zombie head draws zombies — but skulls also redirect distant spawns toward themselves across the dimension. Stack three or more and mobs start converting into the types you've placed. Mix and match to fine-tune the threats you face.

### 🧭 Civilization Detector
Craft a detector from a compass surrounded by emeralds and scan your local civilization level. Fire a sonar pulse that sweeps the terrain — glowing boundary walls rise at your civilization's edges, gold for safe ground, amethyst for totem zones. See your borders, my lord.

### ⏳ Civilization Decay
The world doesn't stay tamed forever. Abandon a settlement and protection fades from the edges inward — but the heart holds firm, and it gradually recovers as you stay. A living, breathing territory that rewards active presence. Offline time in singleplayer doesn't count — your civilization picks up right where you left off.

### 🔧 Mod & Modpack Friendly
Ships with built-in scoring for blocks from Farmer's Delight, Supplementaries, and Create — with more mods added over time. All block weights and head types are loaded from JSON data files, fully overridable via datapacks. Mods that add custom skull types are automatically compatible.

### 🖥️ Server-Ready
Built from the ground up for multiplayer. Async database persistence, player-aware cache prefetching, and a dedicated I/O thread pool keep performance rock-solid even on large servers with many players exploring vast worlds.

---

## Requirements

- Minecraft **1.20.1 ~ 1.21.11**
- Fabric Loader **≥ 0.15.0**
- [Fabric API](https://modrinth.com/mod/fabric-api)
