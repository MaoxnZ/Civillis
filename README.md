# Civillis

**Build more, spawn less.** Hostile mobs naturally avoid civilized areas.

Civillis is a **gameplay mod** about how **civilization tames the world**: the blocks you place build up a **civilization score** around you. **Natural** hostile spawning follows that score — busy bases feel safer; empty land does not change on its own. You do not need special torches, ritual blocks, or commands for the core idea to work.

## How it feels in-game

Around each spawn attempt the mod looks at a large area (by default **240×240×48** blocks, configurable) and turns what it sees into a score from **0 to 1**. That maps to roughly three situations: **wilderness** (spawns behave like vanilla), a **frontier** where strong spawns are only partly throttled, and **civilized** ground where natural hostile spawns are fully suppressed. A higher **Civilization strength** setting (in-game or config) makes full protection easier to reach with fewer blocks.

Over time, areas you stop visiting **decay** from the edges inward, while coming back **recovers** protection. Single-player **offline time does not count** as absence.

## What else is in the mod

- **Civilization Detector** — handheld item that shows local level and head-related zones; pairs with optional **sonar** visuals (portable pulse or bell-on-lodestone setup).  
- **Monster heads** — placed skulls can override suppression and attract specific mobs; see the wiki for clustering / conversion-style behavior.  
- **Mob flee AI** — hostiles in civilized areas try to leave; in dense areas they may panic mid-fight.  
- **Zone transition HUD** — short **Civilized / Wilderness / Caution** hints when you cross large spans (toggle in settings).  
- **Podium of Undying** — late-game structure that ties into death / recovery (full behavior on the wiki).  
- **World rules** — some **vanilla structures** and **dimensions** can follow special rules (e.g. guardians in monuments, packs that turn scoring off in certain dimensions).  
- **Settings** — **Civillis Settings** (Cloth Config): **Fabric** via **Mod Menu** → Configure; **NeoForge / Forge** via the mod list → Civillis → **Config**. Advanced overrides in **`civil.properties`**.  

Full walkthrough: **[Getting started](https://maoxnz.github.io/Civillis/play/getting-started/)** on the wiki.

## Versions and loaders

Civillis ships **Fabric**, **Forge**, and **NeoForge** builds across **Minecraft 1.20.1 through 1.21.11**. Which loader goes with which patch version is spelled out in the **[changelog port table](https://github.com/MaoxnZ/Civillis/blob/main/CHANGELOG.md)** (look for the standardized port list in recent release notes).

Download the jar that matches **your** Minecraft version and mod loader from **[Modrinth](https://modrinth.com/mod/civillis)** or **[GitHub Releases](https://github.com/MaoxnZ/Civillis/releases)**.

## Wiki, roadmap, and community

- **[Wiki (documentation)](https://maoxnz.github.io/Civillis/)** — how scoring works, sonar, heads, commands, compatibility, internals, and more.  
- **[Interactive roadmap](https://maoxnz.github.io/Civillis/roadmap/)** — shipped direction and tracker.  
- **[Issue tracker](https://github.com/MaoxnZ/Civillis/issues)**  
- **[Discord](https://discord.gg/dA7QCPx7zd)**  
- **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/civillis)**  

## Datapacks and JSON (data-driven)

Pack authors can change a lot **without** a fork: block weights, recognized head types, **zone policies** (structure-related spawn rules), and **dimension policies** (per-dimension toggles) all load from datapack JSON. Files are picked up at startup and on **`/reload`**. Overview: **[Data-Driven Registries](https://maoxnz.github.io/Civillis/modpack/data-driven/)**; built-in mod support is summarized under **[Built-in compatibility](https://maoxnz.github.io/Civillis/modpack/compatibility/)**.

## Building from source

```bash
git clone https://github.com/MaoxnZ/Civillis.git
cd Civillis
./gradlew build
```

This repository is an **Architectury** project: platform outputs land under `fabric/build/libs/`, `neoforge/build/libs/`, and on branches that include it, **Forge** as well. The **default branch** may track the latest development target; **release jars** on Modrinth/Releases cover the full **1.20.1–1.21.11** matrix above.
