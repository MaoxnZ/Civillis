# Podium of Spawning

A Podium of Spawning is the controlled opposite of civilization protection: it creates a **mob-friendly pocket** inside a civilized area, useful for farms that should keep working even when the surrounding base is safe.

This page also covers **mob heads**, because heads are not a separate farm structure anymore: they are the way a podium decides what it should attract and convert.

## Structure and activation

Build the podium with:

- **Crying obsidian** as the base block
- A **lit soul campfire** directly on top
- A **bone** to activate it

Right-click the lit soul campfire with a bone. If the structure is valid, the campfire is extinguished and the podium becomes active.

## Local spawn pocket

An active podium creates a local bypass box around its soul-campfire anchor. Inside that box, natural hostile spawns bypass the ordinary civilization score check and can happen as if the area were wilderness.

Current default bypass footprint:

```text
48 x 48 x 16 blocks
```

That is a 3 x 3 x 1 voxel-chunk pocket around the podium's anchor section.

## Mob heads inside the pocket

Place eligible mob heads inside the podium pocket to shape what the podium does:

- Heads inside the pocket are counted by the podium.
- More eligible heads strengthen distant suppression / attraction around the podium.
- If enough eligible heads are present, spawned mobs can convert into matching types.
- Dimension restrictions from the head registry still apply.

Heads outside an active podium pocket are still tracked, but they do not create a full farm pocket by themselves.

## Distant attraction

The podium does more than allow local spawns. Around an active podium, Civillis can suppress eligible hostile spawn attempts farther away, with chance shaped by distance and the number of eligible heads in the podium pocket. This concentrates activity back toward your podium instead of letting mobs fill caves around the base.

The chance is shaped by:

- **Distance** - farther attempts are more likely to be suppressed than attempts close to the podium.
- **Head count in the pocket** - more eligible heads strengthen the effect, with diminishing returns.
- **Attract Range** - the maximum radius for the attraction stage (default 128 blocks).

## Mob conversion

When at least **1 eligible converting head** is present in a podium pocket, spawned mobs can convert into matching types. The current chance is:

```text
conversion chance = min(100%, head count x 10%)
```

| Heads in pocket | Conversion chance |
|-----------------|-------------------|
| 0 | No conversion (local bypass only) |
| 1 | 10% |
| 3 | 30% |
| 5 | 50% |
| 7 | 70% |
| 10+ | 100% |

Conversion picks a random type from the eligible converting heads present. Place skeleton skulls for skeleton pressure, mix skeleton and zombie heads for a blended farm, or use datapacks to tune which skulls participate.

## Default head types

Head types are data-driven. The default registry ships with these vanilla skull types:

| Skull | Attracts / converts | Active dimensions |
|-------|---------------------|-------------------|
| Skeleton Skull | Skeleton | All |
| Zombie Head | Zombie | All |
| Creeper Head | Creeper | All |
| Piglin Head | Piglin | Nether only |
| Wither Skeleton Skull | Wither Skeleton | Nether only |

Dimension-restricted skulls are purely decorative outside their designated dimensions.

!!! warning "Player-texture heads are not distinct mob types"
    Mods that use `PLAYER` heads with custom textures are not automatically distinguishable by Civillis. Registering many visual player-head variants as separate mobs is not possible unless the mod exposes distinct skull types.

## In-game configuration

Open **Civillis Settings** -> **Civilization** -> **Podium of Spawning**:

| Setting | Type | Range / default | What it controls |
|---------|------|-----------------|------------------|
| **Attract Strength** | Slider | 1-10, default 5 | How strongly podiums suppress distant spawns. |
| **Attract Range** | Slider | 48-160 blocks, default 128 | Maximum radius for distant attraction. |

## Deactivation

A podium is removed from the tracker when the anchor is broken or changed. Relighting the soul campfire also deactivates the podium.

## Datapack customization

Pack authors can customize recognized head types through `civil_heads`, including entity mapping, conversion participation, enabled state, and dimension restrictions. See [Data-Driven Registries](../modpack/data-driven.md#head-types).

## Notes

- The podium only affects **natural** spawn attempts. Spawn eggs, spawners, and commands are not controlled by this system.
- Dimension policies can disable head mechanics, which also disables podium head behavior in that dimension.
- Civil maps show active podium pockets with the purple monster tint.

## See also

- [Civil Maps](civil-maps.md)
- [Configuration](../configuration.md)
- [Data-Driven Registries](../modpack/data-driven.md) - head and spawn-gate registries
