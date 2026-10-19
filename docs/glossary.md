# Glossary

Short definitions for terms used across multiple wiki pages. Narrow or feature-only jargon stays on those pages.

| Term | Meaning |
|------|---------|
| **Civilization score** | A value in [0, 1] summarizing how “built up” an area is for spawn logic, derived from weighted blocks in voxel chunks and distance aggregation. See [How It Works](how-it-works.md). |
| **Voxel chunk (VC)** | A 16 x 16 x 16 volume used as the atomic unit for block weights and many radii. Not the same as a horizontal “chunk” column alone. |
| **Natural spawn** | Hostile mobs placed by Minecraft's normal spawn cycle. Non-natural sources (spawn eggs, spawners, `/summon`, etc.) bypass Civillis spawn gating. |
| **greenLine / mid threshold** | The upper spawn threshold (`spawn.thresholdMid` / **Civilization Strength** mapping). At or above this score, natural hostile spawns are fully blocked before exceptions. Used in discussion of [Mob Flee AI](mob-flee-ai.md). |
| **Core vs outer zone** | For decay, the inner **core** keeps full score contribution; the **outer** ring is modulated by time since last visit. See [Civilization Decay](civilization-decay.md). |
| **Civilization sonar** | Shared visualization pipeline for civilization scans: shockwave + optional aura walls. Portable detector and static bell-on-lodestone — [Civilization Sonar](sonar/index.md). |
| **Civil map** | A filled map upgraded with a Civil Detector. It keeps vanilla map behavior while baking civilization / monster-pocket tints into map pixels. See [Civil Maps](play/civil-maps.md). |
| **Podium of Spawning** | Crying obsidian + soul campfire, activated with a bone. Creates a mob-friendly pocket inside civilization and uses mob heads to shape spawns. See [Podium of Spawning](play/podium-of-spawning.md). |
| **Podium of Undying** | Late-game rescue altar that can teleport and revive a player when death would occur, if civilization and cooldown conditions pass. See [Podium of Undying](play/podium-of-undying.md). |
| **Structure-tinted / zone policy** | Vanilla or datapack-tagged structures where spawn rules differ (e.g. guardians in monuments). See [Structure spawn rules](play/structure-spawn-rules.md). |
| **Dimension policy** | Per-dimension toggles that can disable civilization scoring and/or head mechanics (dungeon dimensions, etc.). See [Dimension rules](play/dimension-rules.md). |
| **Spawn gate entity list** | Datapack registry that can force extra entity types through Civillis' natural-spawn gate or whitelist types before civilization scoring. See [Data-Driven Registries](modpack/data-driven.md#spawn-gate-entities). |
