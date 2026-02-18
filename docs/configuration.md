# Configuration

Civillis works out of the box with no configuration required. Every parameter has a carefully tuned default. This page covers the two ways to customize behavior: the **in-game GUI** and the **raw properties file**.

---

## In-Game GUI

Install [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config), then open the Civillis settings screen from the mod list.

Settings are organized into one top-level category (**Civilization**) with two expandable subcategories.

### Main

| Setting | Type | Range | Default | Description |
|---------|------|-------|---------|-------------|
| Spawn Suppression Strength | Slider | 1–10 | 5 | How strongly buildings suppress hostile mob spawning. Higher values lower both spawn thresholds, making it easier to reach full protection |
| Detection Range | Slider | 112–496 blocks (step 32) | 240 blocks | Size of the area scanned around each spawn attempt for nearby civilization. Also determines the sonar scan radius |
| Detector Sonar | Toggle | on / off | on | Whether the Civilization Detector's sonar effects (boundary walls, shockwave particles, sounds) are enabled. Disabling keeps only the basic color/sound feedback |

### Decay & Recovery Details

Controls how protection fades when you leave and recovers when you return. See [Civilization Decay](civilization-decay.md) for mechanics.

| Setting | Type | Range | Default | Description |
|---------|------|-------|---------|-------------|
| Freshness Duration | Slider | 1–48 hours | 6 hours | Grace period before protection starts fading after your last visit |
| Decay Speed | Slider | 1–10 | 5 | How fast protection fades after the grace period expires. The slider label shows the approximate half-life |
| Decay Floor | Slider | 0–50% | 25% | Minimum remaining protection even if fully abandoned. At 0% a settlement can decay to nothing; at 50% half the score always remains |
| Recovery Speed | Slider | 1–10 | 5 | How fast protection recovers when you return. The slider label shows the approximate recovery time |
| Patrol Influence Range | Slider | 2–8 VC (32–128 blocks) | 4 VC (64 blocks) | How far your presence refreshes nearby shards. Larger values let you restore a wider area by walking through it |

### Mob Head Attraction

Controls how monster skulls attract and redirect spawns. See [Monster Heads](monster-heads.md) for mechanics.

| Setting | Type | Range | Default | Description |
|---------|------|-------|---------|-------------|
| Attraction Strength | Slider | 1–10 | 5 | How strongly skulls suppress distant spawns, concentrating mobs near the heads |
| Attraction Range | Slider | 48–160 blocks (step 16) | 128 blocks | Maximum horizontal radius within which skulls exert their redirection effect |

---

## civil.properties

For fine-grained control, you can edit `config/civil.properties` directly. The file is auto-generated on first launch with all values commented out — the mod uses the GUI-derived internal values unless a raw override is present.

!!! warning "Advanced Users Only"
    Raw parameters bypass the GUI's safety mapping. If things break, delete `civil.properties` and restart — the mod will regenerate it with defaults. Changing a GUI slider will overwrite any raw override for the corresponding parameter.

### File Location

- **Production:** `.minecraft/config/civil.properties`
- **Dev environment:** `run/config/civil.properties`

### Parameter Reference

Below is the full list of raw parameters. Values shown are defaults derived from GUI defaults. All are normally commented out; uncomment to override.

#### Spawn Thresholds

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `spawn.thresholdLow` | double | ~0.14 | Civilization score below which spawns are fully allowed |
| `spawn.thresholdMid` | double | ~0.41 | Score above which spawns are fully blocked. Between low and mid, suppression is probabilistic |

#### Scoring Curve

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `scoring.sigmoidMid` | double | 0.8 | Sigmoid midpoint for the score normalization curve |
| `scoring.sigmoidSteepness` | double | 3.0 | Sigmoid steepness — higher values create a sharper transition |
| `scoring.distanceAlphaSq` | double | 0.5 | Distance weighting factor (squared) for blocks farther from the spawn point |
| `scoring.normalizationFactor` | double | 5.0 | Per-voxel-chunk score cap — a chunk's total block weight is divided by this value and clamped to 1.0. When the sum of block weights in a single 16³ chunk reaches 5.0, the chunk score is maxed out |

#### Detection Ranges (Voxel Chunks)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `range.detectionRadiusX` | int | 7 | Detection radius along X in voxel chunks (7 VC = 112 blocks per side → 240 block box) |
| `range.detectionRadiusZ` | int | 7 | Detection radius along Z |
| `range.detectionRadiusY` | int | 1 | Detection radius along Y (vertical) |
| `range.coreRadiusX` | int | 1 | Core (inner zone) radius X |
| `range.coreRadiusZ` | int | 1 | Core radius Z |
| `range.coreRadiusY` | int | 0 | Core radius Y |
| `range.headRangeX` | int | 1 | Mob head bypass zone radius X |
| `range.headRangeZ` | int | 1 | Head bypass zone radius Z |
| `range.headRangeY` | int | 0 | Head bypass zone radius Y |

#### Decay

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `decay.gracePeriodHours` | double | 6.0 | Hours before decay begins after your last visit |
| `decay.lambda` | double | ~0.008 | Exponential decay rate; half-life ≈ ln(2) / λ hours |
| `decay.minFloor` | double | 0.25 | Minimum score fraction retained after full decay (0.0–1.0) |

#### Recovery

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `recovery.cooldownMs` | long | ~60000 | Minimum milliseconds between recovery steps |
| `recovery.fraction` | double | ~0.20 | Fraction of the gap recovered per step |
| `recovery.minMs` | long | ~2266666 | Minimum recovery jump per step (ms). Each recovery step advances presenceTime by at least this amount, preventing the final stretch from dragging out when the remaining gap is small |

#### Head Attraction

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `headAttract.enabled` | boolean | true | Master switch for the head attraction system |
| `headAttract.nearBlocks` | double | 32.0 | Distance (blocks) within which skulls never suppress spawns |
| `headAttract.maxRadius` | double | 128.0 | Maximum horizontal attraction radius in blocks |
| `headAttract.lambda` | double | 0.15 | Attraction decay rate — higher values concentrate mobs more tightly around skulls |

#### Cache & Performance

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `cache.l1TtlMs` | long | 300000 | L1 voxel chunk score TTL in milliseconds (5 min) |
| `cache.resultTtlMs` | long | 3600000 | Result shard TTL in milliseconds (60 min) |
| `cache.clockPersistTicks` | int | 6000 | How often the server clock is persisted to H2 (in ticks) |

#### Detector & UI

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `ui.detectorAnimationTicks` | int | 40 | Sonar pulse animation length in ticks (2 seconds) |
| `ui.detectorCooldownTicks` | int | 10 | Cooldown between detector uses in ticks |

#### Diagnostics

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `tpsLog.enabled` | boolean | true | Whether TPS/MSPT is periodically logged to `latest.log`. Only effective when the mod's internal DEBUG flag is also enabled — in production builds this has no effect regardless of the setting |
| `tpsLog.intervalTicks` | int | 20 | Interval between TPS log entries in server ticks (~1 second). Same DEBUG requirement as above |
