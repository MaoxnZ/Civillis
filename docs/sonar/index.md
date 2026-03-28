# Civilization Sonar

**Sonar** is Civillis’s shared name for **scans that sample civilization data and visualize it** — expanding particle rings, optional **aura walls** at civilization and head-zone boundaries, and matching audio. The server computes boundaries; the client renders the effect.

## Overview

Two variants:

| Variant | How you trigger it | Role |
|---------|-------------------|------|
| **Handheld sonar** | **Portable** tab — Civilization Detector, right-click | Portable scouting; configurable pulse toggle and sonar radius |
| **Static sonar** | **Static** tab — ring a bell placed **on top of** a lodestone | Stationary “detection station”; longer range, stronger presentation, per-player cooldown |

Both use the same underlying idea: a **BFS-style scan** out to a **sonar radius** (in voxel-chunk units) that is **independent** from the much larger spawn **detection radius** used for civilization scoring. Colors along the wave indicate protected ground, wilderness, and monster-head zones (see the **Portable** tab for the particle legend).

### See also

- [How It Works](../how-it-works.md) — what the score means
- [Monster Heads](../monster-heads.md) — purple/orange head zones in sonar

## Portable and static detail

=== "Portable (handheld)"

    ## Civilization Detector

    The **Civilization Detector** is the handheld form of civilization sonar. It scans and visualizes your civilization level. Recent releases refreshed the item art toward a clearer **crystal-ball** look — the mechanics below are unchanged.

    ### Crafting

    Surround a **compass** with **8 emeralds** in a crafting table:

    ```
    E E E
    E C E
    E E E
    ```

    Where E = Emerald, C = Compass.

    ### Basic use

    Right-click to scan. The detector reads the civilization score at your position and gives immediate feedback:

    | Color | Meaning | What it tells you |
    |-------|---------|-------------------|
    | 🟢 Green | HIGH | Fully protected — hostile spawns are blocked here |
    | 🟡 Yellow | MID | Partially protected — some spawns may get through |
    | 🔴 Red | LOW | Unprotected wilderness — spawns proceed normally |
    | 🟣 Purple | MONSTER | Inside a monster head zone — skulls are active here |

    Each reading plays a distinct sound cue, so you can tell the result without looking at the item.

    The detector has a brief enchantment glint while the reading is active (~2 seconds).

    ### Sonar pulse

    When the aura effect is enabled (on by default), right-clicking also fires a **sonar pulse** (see **Overview** above for how this relates to **static sonar**):

    #### Charge-up

    A vertical column of particles appears at your position for ~0.4 seconds, accompanied by a charge-up tone. The particle color reflects your current zone:

    - **White sparks** (End Rod) — you're in a protected area
    - **Blue flames** (Soul Fire) — you're in wilderness
    - **Orange flames** (Fire) — you're in a head zone

    #### Expanding shockwave

    After the charge-up, a ring of particles expands outward from your position over ~1.5 seconds, reaching up to 120 blocks. Each particle is colored by the zone it passes through, painting the landscape with civilization data.

    A boom sound (breeze shoot) plays as the wave launches.

    #### Boundary walls

    ~1.2 seconds after the scan, glowing translucent walls rise at the boundaries of your civilization:

    - **Gold/amber walls** — civilization boundaries (where protection ends and wilderness begins)
    - **Amethyst/purple walls** — monster head zone boundaries

    Walls appear with a fade-in, hold steady for ~2.5 seconds, then gently fade out over ~2 seconds. Firing the detector again while walls are visible extends their duration — and if you've moved toward a boundary, newly discovered faces appear while existing ones hold steady.

    Wall height spans ±48 blocks from the scan center.

=== "Static (bell on lodestone)"

    ## Bell on lodestone

    **Static sonar** is the stationary variant: a **bell** must be placed **on top of a lodestone** (bell block directly above lodestone). When a player **rings the bell** with a proper hit (the same geometry that would make a vanilla bell ring), Civillis fires a sonar pulse centered on the bell — no detector item required.

    ### Why use it

    - **Base perimeter** — Bells contribute to civilization score ([Blocks & Scoring](../blocks-and-scoring.md)); placed on lodestones they double as heavy **detection stations**.
    - **Vanilla-first** — Uses only vanilla blocks; good for survival builds and “alarm” aesthetics.

    ### Behavior vs handheld sonar

    | Aspect | Handheld (Portable tab) | Static (this tab) |
    |--------|-------------------------|-------------------|
    | Trigger | Right-click detector | Ring bell (proper hit) on bell-over-lodestone |
    | Sonar radius (default) | Smaller BFS radius (`sonar.detectorRadius`, voxel chunks) | Larger (`sonar.staticRadius`) |
    | Audio / particles | Lighter | Louder boom, denser ring, **golden accent** particles |
    | Cooldown | Short detector cooldown | Longer **per-player** bell sonar cooldown (`sonar.staticCooldownTicks`) |
    | Toggle | **Detector Sonar** can disable only the detector’s pulse while aura stays on | Bell requires global **aura** (`aura.enabled`); no per-bell GUI toggle |

    Improper hits (e.g. directions that would not ring the bell in vanilla) do **not** trigger sonar.

## In-game configuration

Sonar has a dedicated **collapsible subcategory** on the config screen (not mixed into decay or heads).

Open **Civillis Settings** → **Civilization** → expand **Sonar Visualization** (requires [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) on Fabric; NeoForge/Forge use the loader’s Civillis config entry).

| Setting (in-game label) | Type | Range | Default | What it controls |
|-------------------------|------|-------|---------|------------------|
| **Detector Sonar** | Toggle | on / off | on | Shockwave, boundary walls, and extra sounds for the **Civilization Detector**. **Off** still leaves instant color + sound readout. Does **not** turn off **bell** sonar. |
| **Detector Sonar Range** | Slider | 3–7 voxel chunks | 5 | Handheld BFS radius (UI shows **blocks** = VC × 16). |
| **Bell Sonar Range** | Slider | 8–12 voxel chunks | 10 | Static bell-on-lodestone BFS radius (UI shows blocks). |

**Not on this screen:** global **`aura.enabled`** (master switch for all sonar visuals, including bell), per-player **`sonar.staticCooldownTicks`**, and detector timing keys **`ui.detectorCooldownTicks`** / **`ui.detectorAnimationTicks`** — those live in `civil.properties`. See the aura/sonar group in [Configuration](../configuration.md).

!!! warning "Advanced: civil.properties"
    Raw keys override the GUI for matching parameters. If pulses or bells misbehave, compare `aura.enabled`, `sonar.detectorEnabled`, `sonar.detectorRadius`, `sonar.staticRadius`, and `sonar.staticCooldownTicks` against the commented template, or delete `civil.properties` to regenerate defaults.
