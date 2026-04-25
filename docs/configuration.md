# Configuration

Civillis runs with **sane defaults**; you only need this page if you want to tune behavior. There are two layers:

1. **In-game screen** — **Civillis Settings**, one category named **Civilization** (Cloth Config). Safe mappings, live labels on sliders (half-life, block radii, etc.).
2. **`config/civil.properties`** — raw key/value overrides for advanced use. Commented template is generated on first launch.

---

## Singleplayer vs multiplayer

Civilization logic is **server-authoritative**.

| Context | What changes when you edit settings |
|---------|-------------------------------------|
| **Singleplayer / LAN host** | The same JVM runs the server; **Civillis Settings** and `civil.properties` on that install affect gameplay. |
| **Dedicated server** | Only **`civil.properties` on the server** (and any server-side config reload path) define gameplay. Clients changing their local GUI **do not** override the server. |

---

## In-game screen: **Civilization**

**Open the screen**

- **Fabric:** [Mod Menu](https://modrinth.com/mod/modmenu) → **Civillis** → **Configure** (requires [Cloth Config](https://modrinth.com/mod/cloth-config)).
- **NeoForge / Forge:** Mods list → **Civillis** → **Config** (loader-specific entry opens the same Cloth screen).

**Layout (matches code order)**  
There is **one** top-level category. **Two sliders** are always visible at the top. Everything else lives in **collapsible subcategories** (collapsed by default) — expand **Sonar Visualization**, **Decay & Recovery Details**, etc., to see more.

If a value was overridden in `civil.properties`, a **warning line** may appear above the affected slider; changing that slider clears the override.

### Top-level sliders

| Setting (in-game label) | Type | Range | Default | What it controls |
|-------------------------|------|-------|---------|------------------|
| **Civilization Strength** | Slider | 1–10 | 5 | Moves **both** spawn thresholds together — higher = easier to reach full protection. Slider text includes an estimated “moat” hint beyond a village-like border. |
| **Max. Civilization Radius** | Slider | 112–496 blocks (step 32) | 240 blocks | Horizontal footprint of the civilization scan around each **spawn attempt** (Y extent stays tied to voxel-chunk height as documented in [How It Works](how-it-works.md)). |

### Subcategory: **Sonar Visualization**

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Detector Sonar** | Toggle | on / off | on | When on (and global aura is on), the **Civilization Detector** plays shockwave / walls / extra sounds. Off keeps instant color + sound readout only. Does **not** disable **bell** sonar. |
| **Detector Sonar Range** | Slider | 3–7 voxel chunks | 5 | Handheld sonar BFS radius (label shows **blocks** = VC × 16). |
| **Bell Sonar Range** | Slider | 8–12 voxel chunks | 10 | Static bell-on-lodestone BFS radius (label shows blocks). |

**Not on this screen:** per-player **bell sonar cooldown** (`sonar.staticCooldownTicks` in `civil.properties`, default **40** ticks). Detector item cooldown uses `ui.detectorCooldownTicks`.

See [Civilization Sonar](sonar/index.md) for gameplay context.

### Subcategory: **Decay & Recovery Details**

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Decay Enabled** | Toggle | on / off | on | Master switch for time-based decay. |
| **Decay Speed** | Slider | 1–10 | 5 | Faster decay after the grace period; slider shows approximate **half-life**. |
| **Recovery Speed** | Slider | 1–10 | 5 | Faster recovery when you return; slider shows rough **total minutes** to full catch-up. |
| **Decay Floor** | Slider | 0–50% | 25% | Minimum fraction of score retained after long absence. |
| **Freshness Duration** | Slider | 1–48 hours | 6 hours | Grace period before decay starts. |
| **Patrol Influence Range** | Slider | 2–8 VC (32–128 blocks) | 4 VC (64 blocks) | How far your presence refreshes nearby shards. |

Mechanics: [Civilization Decay](civilization-decay.md).

### Subcategory: **Mob Head Attraction**

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Mob Head System** | Toggle | on / off | on | Master switch for head gameplay (bypass, redirection, conversion, detector tinting, flee awareness). Off = decorative skulls. |
| **Attraction Strength** | Slider | 1–10 | 5 | Stronger pull / suppression shaping (λ shown on slider). |
| **Attraction Range** | Slider | 3–10 (×16 blocks) | 8 (**128** blocks) | Max horizontal radius for redirection math. |

Mechanics: [Monster Heads](monster-heads.md).

### Subcategory: **Podium of Undying**

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Enable Podium of Undying** | Toggle | on / off | on | Feature master switch. |
| **Max Resurrection Distance** | Slider | 32–256 blocks | 128 blocks | Search radius for a valid podium when a rescue triggers. |
| **Rescue Cooldown** | Slider | 1–300 seconds | 10 s | Global cooldown between rescues. |

The screen does **not** expose **`undyingAnchor.civRatio`** (minimum civilization fraction at the podium) — that remains a `civil.properties` key only (default **0.8**).

Gameplay: [Podium of Undying](play/podium-of-undying.md).

### Subcategory: **Miscellaneous**

| Setting | Type | Default | What it controls |
|---------|------|---------|------------------|
| **Mob Flee AI** | Toggle | on | Hostile mobs retreat from high civilization pressure (linked to **Civilization Strength**). |
| **Zone Transition HUD** | Toggle | on | Short on-screen labels when crossing civilized / wilderness spans and **Caution** near structure-tinted zones. |

Flee behavior: [Mob Flee AI](mob-flee-ai.md). HUD: [Zone transition HUD](play/zone-transition-hud.md).

---

## civil.properties

The file is created under `.minecraft/config/civil.properties` (or `run/config` in dev). Keys are **commented by default**; uncomment to force a raw value. **Saving from the in-game screen** overwrites the matching keys for anything you touched.

!!! warning "Advanced users only"
    Raw values bypass Cloth’s safety mapping. If behavior explodes, **delete** `civil.properties` and restart to regenerate defaults. See also [Architecture](technical/architecture.md) for how storage and ticks interact with cache keys below.

### Quick reference (grouped)

| Group | Example keys | Notes |
|-------|----------------|-------|
| Spawn thresholds | `spawn.thresholdLow`, `spawn.thresholdMid` | Usually driven by **Civilization Strength** slider. |
| Scoring curve | `scoring.sigmoidMid`, `scoring.sigmoidSteepness`, `scoring.distanceAlphaSq`, `scoring.normalizationFactor` | Per-chunk normalization cap lives here. |
| Detection ranges (VC) | `range.detectionRadiusX/Y/Z`, `range.coreRadius*`, `range.headRange*` | Low-level box sizes; **Max. Civilization Radius** maps the horizontal detection steps. |
| Decay / recovery | `decay.*`, `recovery.*` | Mirror **Decay & Recovery** sliders. |
| Heads | `headAttract.*` | Mirror **Mob Head Attraction** (plus internal λ). |
| Mob flee | `mobFlee.*` | Fine-tune intervals, panic duration, sample distance. |
| Podium | `undyingAnchor.enabled`, `undyingAnchor.maxSearchRadius`, `undyingAnchor.globalCooldownSeconds`, `undyingAnchor.civRatio` | GUI covers the first three; **civRatio** is properties-only (rescue score threshold). |
| Aura / sonar | `aura.enabled`, `sonar.detectorEnabled`, `sonar.detectorRadius`, `sonar.staticRadius`, `sonar.staticCooldownTicks` | Bell still requires `aura.enabled`. |
| UI / detector | `ui.detectorAnimationTicks`, `ui.detectorCooldownTicks` | Pulse length and item cooldown. |
| Cache / perf | `cache.l1TtlMs`, `cache.resultTtlMs`, … | See [Performance](technical/performance.md) before tuning. |
| Diagnostics | `tpsLog.*` | Effective only when **debug** logging is enabled in the build. |

For a **line-by-line** table of types and defaults as shipped in the template, keep reading the generated file on disk or ask on Discord — the wiki avoids duplicating fifty rows that drift every port.
