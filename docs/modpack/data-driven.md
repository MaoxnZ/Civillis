# Data-Driven Registries

Civillis loads several JSON registries from datapacks:

- **Block Weights** (`civil_blocks`) — which blocks count as civilization and how much they contribute
- **Head Types** (`civil_heads`) — which skull types are recognized and what mobs they map to
- **Spawn Gate Entities** (`civil_spawn_gate_entities`) — entity types that should enter or bypass the Civillis spawn gate
- **Zone Policies** (`civil_zone_policies`) — structure-tagged rules (e.g. allow hostile spawns inside monuments)
- **Dimension Policies** (`civil_dimension_policies`) — per-dimension toggles for civilization scoring and head mechanics

Files live under `data/<namespace>/<registry>/` and load at server startup and on `/reload`. No mod code changes needed — drop JSON into your datapack.

---

## Block Weights

The block weight registry determines which blocks are recognized as civilization and how much each one contributes to the per-voxel-chunk score. A higher weight means stronger spawn suppression. Weights follow a progression from Stone Age basics (0.2–0.3) to Mythic landmarks (2.0–5.0) — see [Blocks & Scoring](../blocks-and-scoring.md) for the full weight framework and vanilla block table.

### File Location

```
data/<namespace>/civil_blocks/<any_name>.json
```

Files are discovered by scanning all namespaces for JSON under `civil_blocks/`. You can use any namespace (your modpack ID, `civil`, or anything else).

### JSON Format

```json
{
  "replace": false,
  "entries": [
    { "block": "minecraft:beacon", "weight": 5.0 },
    { "block": "#minecraft:beds", "weight": 0.7 },
    { "block": "mymod:fancy_lamp", "weight": 0.6 }
  ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `replace` | No | If `true`, clears all previously loaded block weights before applying this file. Default `false` (merge) |
| `entries` | Yes | Array of block weight definitions |
| `entries[].block` | Yes | Block ID (`"minecraft:beacon"`) or block tag with `#` prefix (`"#minecraft:beds"`) |
| `entries[].weight` | Yes | Civilization weight (typically 0.2–5.0). See [Blocks & Scoring](../blocks-and-scoring.md) for the weight framework |

### Tag Support

Prefix a block ID with `#` to target an entire block tag. For example, `"#minecraft:beds"` assigns the weight to all bed variants (oak, spruce, birch, etc.) in one entry. Unknown tags are silently skipped.

### Built-in Tier Tags

Civillis ships one extra helper file, `data/civil/civil_blocks/default_tags.json`, so modpacks can add blocks to shared tags instead of writing one entry per block:

| Tag | Weight |
|-----|--------|
| `#civil:very_very_high_civilized` | 5.0 |
| `#civil:very_high_civilized` | 3.0 |
| `#civil:high_civilized` | 2.0 |
| `#civil:medium_high_civilized` | 1.5 |
| `#civil:medium_civilized` | 1.0 |
| `#civil:medium_low_civilized` | 0.7 |
| `#civil:low_civilized` | 0.5 |
| `#civil:very_low_civilized` | 0.3 |
| `#civil:very_very_low_civilized` | 0.2 |

Add a tag file under `data/<namespace>/tags/blocks/` (or extend the `civil` tag from your datapack) and the weight applies through the normal block-weight loader.

### Loading Order & Overrides

- Files are processed in resource identifier order (namespace alphabetically, then path)
- Later entries for the same block override earlier ones
- A file with `"replace": true` clears all accumulated weights before applying its own entries — useful for modpacks that want a completely custom weight table

### Examples

**Add blocks from a custom mod:**

```json
{
  "entries": [
    { "block": "mymod:arcane_table", "weight": 1.5 },
    { "block": "mymod:crystal_lamp", "weight": 0.6 },
    { "block": "#mymod:workstations", "weight": 0.5 }
  ]
}
```

**Override a vanilla block's weight:**

```json
{
  "entries": [
    { "block": "minecraft:beacon", "weight": 10.0 }
  ]
}
```

**Replace the entire weight table:**

```json
{
  "replace": true,
  "entries": [
    { "block": "minecraft:beacon", "weight": 5.0 },
    { "block": "minecraft:enchanting_table", "weight": 3.0 }
  ]
}
```

---

## Head Types

The head type registry controls which monster skulls are recognized by Civillis and maps each one to a mob entity. Eligible skulls are used by active Podiums of Spawning for attraction and conversion; dimension rules still apply. See [Podium of Spawning](../play/podium-of-spawning.md) for gameplay details.

### Default Registry

| Skull Type | Entity | Conversion | Active Dimensions |
|------------|--------|------------|-------------------|
| `SKELETON` | `minecraft:skeleton` | Yes | All |
| `ZOMBIE` | `minecraft:zombie` | Yes | All |
| `CREEPER` | `minecraft:creeper` | Yes | All |
| `PIGLIN` | `minecraft:piglin` | Yes | Nether only |
| `WITHER_SKELETON` | `minecraft:wither_skeleton` | Yes | Nether only |

### File Location

```
data/<namespace>/civil_heads/<any_name>.json
```

### JSON Format

```json
{
  "replace": false,
  "entries": [
    {
      "skull_type": "ZOMBIE",
      "entity_type": "minecraft:zombie"
    },
    {
      "skull_type": "WITHER_SKELETON",
      "entity_type": "minecraft:wither_skeleton",
      "dimensions": ["minecraft:the_nether"]
    },
    {
      "skull_type": "BLAZE",
      "entity_type": "minecraft:blaze",
      "convert": false,
      "dimensions": ["minecraft:the_nether"]
    }
  ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `replace` | No | If `true`, clears all previously loaded head types. Default `false` |
| `entries` | Yes | Array of head type definitions |
| `entries[].skull_type` | Yes | Must match `SkullBlock.SkullType.toString()` for vanilla, or a custom string for modded skulls |
| `entries[].entity_type` | No | The mob entity this skull attracts/converts to. If omitted, the skull contributes to zone/range but has no mob mapping |
| `entries[].enabled` | No | Master switch. `false` makes this skull type invisible to all gameplay systems. Default `true` |
| `entries[].convert` | No | Whether this skull participates in the conversion pool. `false` means it still counts for bypass and redirection but never produces conversions. Default `true` |
| `entries[].dimensions` | No | Array of dimension IDs where this skull type is active (e.g. `["minecraft:the_nether"]`). If omitted, active in all dimensions. In non-matching dimensions the skull is purely decorative |

### Override & Disable

Later entries for the same `skull_type` fully replace earlier ones. To disable a specific skull type without removing it:

```json
{
  "entries": [
    { "skull_type": "ZOMBIE", "enabled": false }
  ]
}
```

### Modded Skull Types

Mods that add new skull types via custom `AbstractSkullBlock` subclasses (with custom `SkullType` enums) are automatically compatible. Register them by matching the `skull_type` string:

```json
{
  "entries": [
    { "skull_type": "BLAZE", "entity_type": "minecraft:blaze" }
  ]
}
```

!!! warning "Player-texture heads not supported"
    Mods that use `PLAYER` heads with custom textures (such as All The Heads, Just Mob Heads) are **not** supported — the system sees them all as skull type `PLAYER` and cannot distinguish which mob they represent. Only mods using distinct `SkullType` values are compatible.

### Examples

**Restrict a skull to specific dimensions:**

```json
{
  "entries": [
    {
      "skull_type": "SKELETON",
      "entity_type": "minecraft:skeleton",
      "dimensions": ["minecraft:overworld"]
    }
  ]
}
```

**Allow bypass and redirection but no conversion:**

```json
{
  "entries": [
    {
      "skull_type": "BLAZE",
      "entity_type": "minecraft:blaze",
      "convert": false,
      "dimensions": ["minecraft:the_nether"]
    }
  ]
}
```

**Replace the entire head type table:**

```json
{
  "replace": true,
  "entries": [
    { "skull_type": "SKELETON", "entity_type": "minecraft:skeleton" },
    { "skull_type": "ZOMBIE", "entity_type": "minecraft:zombie" }
  ]
}
```

Only skeleton and zombie skulls remain active. All other vanilla skulls become decorative.

---

## Spawn Gate Entities

Registry path: `civil_spawn_gate_entities`. Controls entity-type exceptions around the natural-spawn gate.

!!! important "Blacklist does not mean deny"
    In this registry, `blacklist` means **extra entity types that should enter the Civillis natural-spawn gate**, not “always block this mob.” The normal civilization / podium / zone-policy decision still decides the result.

### File location

```text
data/<namespace>/civil_spawn_gate_entities/<any_name>.json
```

### JSON format

```json
{
  "replace": false,
  "entries": [
    {
      "blacklist": ["mymod:hostile_construct"],
      "whitelist": ["minecraft:slime"]
    }
  ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `replace` | No | If `true`, clears accumulated spawn-gate entity lists before applying this file. Default `false`. |
| `entries` | Yes | Array of list entries. |
| `entries[].blacklist` | No | Entity IDs that should be treated as eligible for Civillis spawn gating in addition to normal hostile categories. |
| `entries[].whitelist` | No | Entity IDs that should be allowed before zone and civilization scoring. |

Files load in resource-id order. Unknown entity IDs are skipped with a warning. If the same entity appears in both lists after merging, **whitelist wins** at decision time.

### Examples

**Make a modded hostile construct obey civilization:**

```json
{
  "entries": [
    { "blacklist": ["mymod:clockwork_raider"] }
  ]
}
```

**Let a specific mob bypass Civillis scoring:**

```json
{
  "entries": [
    { "whitelist": ["minecraft:slime"] }
  ]
}
```

---

## Zone Policies

Registry path: `civil_zone_policies`. Controls how **natural** hostile spawns behave **inside matched vanilla structures**, independent of nearby civilization score. Also feeds **structure-tint** style hints for the [zone HUD](../play/zone-transition-hud.md).

### File location

```text
data/<namespace>/civil_zone_policies/<any_name>.json
```

### Shape (conceptual)

Each file contains `replace` (optional) and `entries`. Each entry has:

- **`id`** — stable string label for the rule set
- **`structures`** — list of structure IDs (e.g. `minecraft:monument`)
- **`rules`** — at minimum `allow_hostile_spawn` (boolean). When `true`, hostiles can spawn as usual inside those structures. Optional `allow_mobs` can refine which mobs are affected when `allow_hostile_spawn` is false

Defaults ship with the mod (monuments, fortresses, bastions, mansions, ancient cities, end cities, trial chambers, strongholds, etc.). Use additional files in your namespace to add or override behavior; `"replace": true` wipes policies loaded earlier in lexicographic order from **previous** files (use with care).

See also: [Structure spawn rules](../play/structure-spawn-rules.md), [Built-in Compatibility](compatibility.md) (default zone JSON).

---

## Dimension Policies

Registry path: `civil_dimension_policies`. Per-dimension switches so Civillis can stay out of dimensions where civilization scoring or head logic does not make sense (dungeon dimensions, instanced rooms, etc.).

### File location

```text
data/<namespace>/civil_dimension_policies/<any_name>.json
```

### JSON format

```json
{
  "replace": false,
  "entries": [
    {
      "dimension": "mymod:custom_dimension",
      "civilization": false,
      "head_mechanics": true
    }
  ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `dimension` | Yes | Dimension ID (e.g. `minecraft:the_nether`) |
| `civilization` | No | Default `true`. If `false`, civilization zones and scoring short-circuit (score reads as uncivilized for spawn logic) |
| `head_mechanics` | No | Default `true`. If `false`, head proximity / suppression stages are skipped for that dimension |

Later entries for the **same dimension** override earlier ones. The mod ships defaults for several known modded dimensions (Minecells, DimDungeons); replace or extend with your own datapack.

See also: [Dimension rules](../play/dimension-rules.md), [Built-in Compatibility](compatibility.md) (default dimension table).

---

## Tips for Modpack Authors

- **Namespace your files** — use your modpack's namespace (e.g. `data/mypack/civil_blocks/overrides.json`) to keep things organized and avoid conflicts with other datapacks
- **Don't use `replace: true` unless you need a clean slate** — it clears everything loaded before your file, including other datapacks
- **Test with `/reload`** — both registries reload live without a server restart. Check the server log for `[civil-registry]` messages confirming your entries loaded
- **Unknown blocks are safe** — if a block ID doesn't exist (mod not installed), the entry is silently skipped at load time with a log warning. No errors, no performance cost
