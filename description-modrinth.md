*The land remembers your presence.*

*Where players build, monsters yield.*

---

## What is Civillis?

Have you ever wished your base just *felt* safe — without flooding every surface with torches or carpeting the ground with slabs?

Civillis makes that happen. It recognizes the signs of civilization you naturally create — a campfire burning, a bed placed, a workbench humming — and silently pushes hostile mobs away. The more your settlement grows, the stronger the protection. No commands, no rituals, no new mechanics to learn — just play, and the land responds.

Leave for too long, and nature slowly reclaims its ground. But a well-established city never truly falls.

## How It Works

The mod evaluates a 240×240×48 block area (configurable) around each potential spawn point and computes a **civilization score** based on nearby structures. Blocks that reflect human presence — furnaces, beds, campfires, beacons, workstations, and others like them — are what the mod looks for.

- **Settle and grow → safer land.** A thriving city can push monsters back ~90 blocks from its borders.
- **Leave for a while → gradual decay.** Over time without visits, protection weakens — but a large settlement still keeps a ~40-block safe perimeter.
- **A small cabin → modest protection.** A lone outpost won't create a fortress, but it helps.

*That's millions of blocks evaluated per spawn attempt — and it happens every time, everywhere in the world. See the [Wiki](https://maoxnz.github.io/Civillis/) to learn how the shard-based civilization engine keeps this at constant time.*

## Features

### 🏘️ Natural Spawn Suppression
The blocks you already use to build your world are what keep you safe. Campfires, beds, furnaces, crafting tables — the things that make a place feel *lived in* — are exactly what the mod looks for. Build the home you've always wanted, and safety follows.

### 💀 Monster Head Mechanic
Want danger back? Place a monster skull to override the protection. A skeleton skull increases the chance of skeletons spawning nearby, a zombie head draws more zombies, and so on. Stack three or more skulls in an area and mobs start converting into the types you've placed. Mix and match to fine-tune the threats you face.

### 🧭 Civilization Detector
Craft a detector from a compass surrounded by emeralds, and scan your local civilization level. Trigger a sonar pulse that sweeps the land around you. Color-coded boundaries and custom sound cues tell you exactly how safe (or exposed) your surroundings are.

### ⏳ Civilization Decay
The world doesn't stay tamed forever. Unvisited areas gradually lose their protection over time, creating a living, breathing sense of territory that rewards active presence. Offline time in singleplayer doesn't count — your civilization picks up right where you left off.

### 🔧 Mod & Modpack Friendly
Ships with built-in scoring for blocks from Farmer's Delight, Supplementaries, and Create — with more mods added over time. All block weights and head types are loaded from JSON data files, fully overridable via datapacks. Mods that add custom skull types are automatically compatible.

### 🖥️ Server-Ready
Built from the ground up for multiplayer. Async database persistence, player-aware cache prefetching, and a dedicated I/O thread pool keep performance rock-solid even on large servers with many players exploring vast worlds.

---

*You strike the anvil — its ring echoes across the village.*  
***They know this is civilization. So they leave.***

*You murmur over the enchanting table — runes flicker in the dark.*  
***They know this is civilization. So they leave.***

*You sit by the campfire with old friends — laughter rises into the night.*  
***They know this is civilization. So they leave.***

Natural. Dignified. Elegant. This should have been part of the world all along.

**Now it is.**

---

## Requirements

- Minecraft **1.20.1 ~ 1.21.11**
- Fabric Loader **≥ 0.15.0**
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Links

- [Source Code](https://github.com/MaoxnZ/Civillis)
- [Issue Tracker](https://github.com/MaoxnZ/Civillis/issues)
- [Official Wiki](https://maoxnz.github.io/Civillis/)
