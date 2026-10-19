# Configuration

Civillis runs with **sane defaults**; you only need this page if you want to tune behavior. There are two layers:

1. **In-game screen** — **Civillis Settings**, one category named **Civilization** (Cloth Config). Safe mappings, live labels on sliders, and collapsible subcategories.
2. **`config/civil.properties`** — raw key/value overrides for advanced use. A commented template is generated on first launch.

---

## Singleplayer vs multiplayer

Civilization logic is **server-authoritative**.

| Context | What changes when you edit settings |
|---------|-------------------------------------|
| **Singleplayer / LAN host** | The same JVM runs the server; **Civillis Settings** and `civil.properties` on that install affect gameplay. |
| **Dedicated server** | Only **`civil.properties` on the server** (and server-side reload paths) define gameplay. Clients changing their local GUI do not override the server. |

---

## In-game screen: **Civilization**

**Open the screen**

- **Fabric:** [Mod Menu](https://modrinth.com/mod/modmenu) -> **Civillis** -> **Configure** (requires [Cloth Config](https://modrinth.com/mod/cloth-config)).
- **NeoForge / Forge:** Mods list -> **Civillis** -> **Config** (loader-specific entry opens the same Cloth screen).

There is one top-level category. Two sliders are always visible at the top. Everything else lives in collapsible subcategories.

If a value was overridden in `civil.properties`, a warning line may appear above the affected slider; changing that slider clears the override.

### Top-level sliders

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Civilization Strength** | Slider | 1-10 | 5 | Moves both spawn thresholds together — higher = easier to reach full protection. |
| **Max. Civilization Radius** | Slider | 112-496 blocks (step 32) | 240 blocks | Horizontal footprint of the civilization scan around each spawn attempt. |

### Subcategory: **Podium of Spawning**

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Attract Strength** | Slider | 1-10 | 5 | How strongly active spawning podiums suppress distant eligible spawns. |
| **Attract Range** | Slider | 3-10 (x16 blocks) | 8 (128 blocks) | Maximum radius for distant podium attraction. |

Mechanics: [Podium of Spawning](play/podium-of-spawning.md).

### Subcategory: **Sonar Visualization**

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Detector Sonar** | Toggle | on / off | on | When on (and global aura is on), the **Civilization Detector** plays shockwave / walls / extra sounds. Off keeps instant color + sound readout only. Does **not** disable bell sonar. |
| **Detector Sonar Range** | Slider | 3-7 voxel chunks | 5 | Handheld sonar BFS radius (label shows blocks = VC x 16). |
| **Bell Sonar Range** | Slider | 8-12 voxel chunks | 10 | Static bell-on-lodestone BFS radius (label shows blocks). |

**Not on this screen:** per-player **bell sonar cooldown** (`sonar.staticCooldownTicks` in `civil.properties`, default **40** ticks). Detector item cooldown uses `ui.detectorCooldownTicks`.

See [Civilization Sonar](sonar/index.md) for gameplay context.

### Subcategory: **Decay & Recovery Details**

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Decay Enabled** | Toggle | on / off | on | Master switch for time-based decay. |
| **Decay Speed** | Slider | 1-10 | 5 | Faster decay after the grace period; slider shows approximate half-life. |
| **Recovery Speed** | Slider | 1-10 | 5 | Faster recovery when you return; slider shows rough total minutes to full catch-up. |
| **Decay Floor** | Slider | 0-50% | 25% | Minimum fraction of score retained after long absence. |
| **Freshness Duration** | Slider | 1-48 hours | 6 hours | Grace period before decay starts. |
| **Patrol Influence Range** | Slider | 2-8 VC (32-128 blocks) | 4 VC (64 blocks) | How far your presence refreshes nearby shards. |

Mechanics: [Civilization Decay](civilization-decay.md).

### Subcategory: **Zone Transition HUD**

| Setting | Type | Default | What it controls |
|---------|------|---------|------------------|
| **Enable Zone Transition HUD** | Toggle | on | Shows civilized / wilderness / caution labels when zone semantics change. |
| **Zone HUD: Civilized label** | Text | empty | Optional custom Civilized message. |
| **Zone HUD: Wilderness label** | Text | empty | Optional custom Wilderness message. |
| **Zone HUD: Caution label** | Text | empty | Optional custom Caution message. |
| **Minimum interval** | Slider | 10 s | Cooldown between HUD messages. |
| **Horizontal offset** | Slider | 0% | Moves the HUD anchor left / right. |
| **Vertical offset** | Slider | 30% | Moves the HUD anchor up / down. |

Gameplay: [Zone transition HUD](play/zone-transition-hud.md).

### Subcategory: **Podium of Undying**

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Enable Podium of Undying** | Toggle | on / off | on | Feature master switch. |
| **Max Resurrection Distance** | Slider | 32-256 blocks | 128 blocks | Search radius for a valid podium when a rescue triggers. |
| **Rescue Cooldown** | Slider | 1-300 seconds | 10 s | Global cooldown between rescues. |

The screen does **not** expose **`undyingAnchor.civRatio`** (minimum civilization fraction at the podium) — that remains a `civil.properties` key only (default **0.8**).

Gameplay: [Podium of Undying](play/podium-of-undying.md).

### Subcategory: **Mob Flee Behavior**

| Setting | Type | Default | What it controls |
|---------|------|---------|------------------|
| **Mob Flee AI** | Toggle | on | Hostile mobs retreat from high civilization pressure (linked to **Civilization Strength**). |

Flee behavior: [Mob Flee AI](mob-flee-ai.md).

### Miscellaneous / client integrations

Some builds expose additional client-side controls for external map overlays (JourneyMap / Xaero) and exploration radius. These controls are version- and port-specific; see [Map mod overlays](play/map-overlays.md). Civil map palette strength is configured through raw `mapTint.*` values rather than a main GUI section.

---

## civil.properties

The file is created under `.minecraft/config/civil.properties` (or `run/config` in dev). Keys are commented by default; uncomment to force a raw value. Saving from the in-game screen overwrites the matching keys for anything you touched.

!!! warning "Advanced users only"
    Raw values bypass Cloth's safety mapping. If behavior explodes, delete `civil.properties` and restart to regenerate defaults. See also [Architecture](technical/architecture.md) for how storage and ticks interact with cache keys below.

### Quick reference (grouped)

| Group | Example keys | Notes |
|-------|--------------|-------|
| Spawn thresholds | `spawn.thresholdLow`, `spawn.thresholdMid` | Usually driven by **Civilization Strength** slider. |
| Scoring curve | `scoring.sigmoidMid`, `scoring.sigmoidSteepness`, `scoring.distanceAlphaSq`, `scoring.normalizationFactor` | Per-chunk normalization cap lives here. |
| Detection ranges (VC) | `range.detectionRadiusX/Y/Z`, `range.coreRadius*`, `range.headRange*` | Low-level box sizes; **Max. Civilization Radius** maps the horizontal detection steps. |
| Decay / recovery | `decay.*`, `recovery.*` | Mirror **Decay & Recovery** sliders. |
| Podium of Spawning | `headAttract.*`, `farmShrine*` / generated equivalents | Mirror **Attract Strength** and **Attract Range**; controls distant podium attraction. |
| Mob flee | `mobFlee.*` | Fine-tune intervals, panic duration, sample distance. |
| Podium of Undying | `undyingAnchor.enabled`, `undyingAnchor.maxSearchRadius`, `undyingAnchor.globalCooldownSeconds`, `undyingAnchor.civRatio` | GUI covers the first three; **civRatio** is properties-only. |
| Aura / sonar | `aura.enabled`, `sonar.detectorEnabled`, `sonar.detectorRadius`, `sonar.staticRadius`, `sonar.staticCooldownTicks` | Bell still requires `aura.enabled`. |
| Zone HUD | `ui.zoneTransitionHud*`, `ui.zoneTransitionLabel.*` | Toggle, labels, cooldown, and screen anchor. |
| Civil maps | `mapTint.highFillAlpha`, `mapTint.highEdgeAlpha`, `mapTint.monsterFillAlpha`, `mapTint.monsterEdgeAlpha` | Palette blend strengths for civil maps and compatible overlays. |
| UI / detector | `ui.detectorAnimationTicks`, `ui.detectorCooldownTicks` | Pulse length and item cooldown. |
| Cache / perf | `cache.l1TtlMs`, `cache.resultTtlMs`, ... | See [Performance](technical/performance.md) before tuning. |
| Diagnostics | `tpsLog.*` | Effective only when debug logging is enabled in the build. |

For a line-by-line table of types and defaults as shipped in the template, keep reading the generated file on disk or ask on Discord — the wiki avoids duplicating every raw key that may drift between ports.
