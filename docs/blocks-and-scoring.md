# Blocks & Scoring

Scoring works in two stages. First, each recognized block contributes its weight to the **voxel chunk** (16×16×16 area) it belongs to, producing a per-chunk civilization score. Then, all chunk scores within the detection range are aggregated into a final score for the spawn point being evaluated. Unrecognized blocks contribute nothing.

## Weight Framework

Block weights follow a progression based on crafting complexity and symbolic significance:

| Era | Weight Range | Examples |
|-----|-------------|----------|
| Stone Age | 0.2–0.3 | Crafting table, furnace, chest, farmland, barrel |
| Iron Age | 0.4–0.5 | Lanterns, workstations, campfires, lecterns |
| Advanced | 0.6–1.0 | Beds, anvils, shulker boxes, brewing stands, sea lanterns |
| Mythic | 2.0–5.0 | Respawn anchors, enchanting tables, beacons, conduits |

Blocks that emit light or have magical properties receive additional weight within their era.

## Vanilla Block Weights

| Block | Weight | | Block | Weight |
|-------|--------|-|-------|--------|
| Beacon | 5.0 | | Lantern | 0.4 |
| Conduit | 5.0 | | Soul Lantern | 0.4 |
| Respawn Anchor | 2.0 | | Trapped Chest | 0.4 |
| Enchanting Table | 1.5 | | Crafting Table | 0.3 |
| Lodestone | 1.5 | | Furnace | 0.3 |
| Brewing Stand | 1.0 | | Chest | 0.3 |
| Sea Lantern | 1.0 | | Barrel | 0.3 |
| Bell | 0.8 | | Farmland | 0.3 |
| Ender Chest | 0.8 | | Composter | 0.3 |
| Beds (all types) | 0.7 | | Beehive | 0.3 |
| Redstone Lamp | 0.7 | | Decorated Pot | 0.3 |
| Anvils (all types) | 0.6 | | Bee Nest | 0.2 |
| Shulker Boxes (all) | 0.6 | | | |
| Campfires (all types) | 0.5 | | | |
| Stonecutter | 0.5 | | | |
| Cartography Table | 0.5 | | | |
| Smithing Table | 0.5 | | | |
| Fletching Table | 0.5 | | | |
| Loom | 0.5 | | | |
| Grindstone | 0.5 | | | |
| Blast Furnace | 0.5 | | | |
| Smoker | 0.5 | | | |
| Lectern | 0.5 | | | |
| End Rod | 0.5 | | | |

Entries marked "all types" use block tags — all variants (e.g., oak bed, spruce bed) share the same weight.

## Mod compatibility (built-in weights)

Civillis ships **`civil_blocks`** JSON for **Create**, **Farmer's Delight**, **Supplementaries**, **Quark**, **Applied Energistics 2**, **Storage Drawers**, **Iron Chests**, **Sophisticated Storage**, **Functional Storage**, plus vanilla **`defaults.json`**. If a mod is not installed, its rows are skipped — no errors, no runtime cost.

Full ID → weight tables, zone/dimension/head defaults, and file paths: **[Built-in Compatibility](modpack/compatibility.md)** (expandable sections per source). Regenerate block tables after JSON changes with `python tools/gen_wiki_compatibility.py`.

## Data-Driven

All block weights are loaded from JSON data files and can be fully overridden via datapacks. See [Data-Driven Registries](modpack/data-driven.md) for details on adding custom blocks.

## In-Game Configuration

Scoring-related parameters are adjustable via the in-game GUI (requires [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config)):

| Setting | Range | Default | What it controls |
|---------|-------|---------|-----------------|
| Civilization Strength | 1–10 | 5 | Adjusts both spawn thresholds together — higher values make it easier to reach full protection |
| Max. Civilization Radius | 112–496 blocks | 240 blocks | Size of the area evaluated around each spawn point |

!!! warning "Advanced: civil.properties"
    Raw parameters can also be edited in `config/civil.properties`, but this is intended for advanced users only. If things break, delete `civil.properties` and restart — the mod will regenerate it with defaults.
