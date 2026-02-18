# Data-Driven Registries

Civillis uses two data-driven registries that can be fully customized via datapacks:

- **Block Weights** (`civil_blocks`) — which blocks count as civilization and how much they contribute
- **Head Types** (`civil_heads`) — which skull types are recognized and what mobs they map to

Both follow the same pattern: JSON files placed under `data/<namespace>/<registry>/` are loaded at server startup and on `/reload`. No mod code changes needed — just drop JSON into your datapack.

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

The head type registry controls which monster skulls are recognized by Civillis and maps each one to a mob entity. Recognized skulls activate three gameplay mechanisms: local spawn gate bypass, dimension-wide spawn redirection, and mob conversion. See [Monster Heads](../monster-heads.md) for how these mechanics work in detail.

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

## Tips for Modpack Authors

- **Namespace your files** — use your modpack's namespace (e.g. `data/mypack/civil_blocks/overrides.json`) to keep things organized and avoid conflicts with other datapacks
- **Don't use `replace: true` unless you need a clean slate** — it clears everything loaded before your file, including other datapacks
- **Test with `/reload`** — both registries reload live without a server restart. Check the server log for `[civil-registry]` messages confirming your entries loaded
- **Unknown blocks are safe** — if a block ID doesn't exist (mod not installed), the entry is silently skipped at load time with a log warning. No errors, no performance cost
