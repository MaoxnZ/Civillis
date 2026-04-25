# Dimension rules

Some dimensions are not meant to play by **overworld-style civilization**: dungeon worlds, instanced maps, modded “pocket” dimensions, etc. Civillis can treat those dimensions differently so **civilization scoring** (and optionally **head-related spawn stages**) do not run there.

## What you experience

**When civilization is off for a dimension**, the mod effectively treats that world as **uncivilized for spawn math** — your base’s score in another dimension does not protect you here, and building here does not create the same suppression loop as in the overworld unless the pack author designed it that way.

**When head mechanics are off for a dimension**, podium/head bypass, attraction, and conversion stages are skipped there (skulls may still be decorative).

You can have **civilization off** but **heads on**, or tune per pack — the important part for players is: **behavior depends on dimension**, not only on blocks under your feet.

## How this differs from structure rules

**Dimension rules** apply to a **whole dimension**. **[Structure spawn rules](structure-spawn-rules.md)** only carve out **specific vanilla structures** (monuments, fortresses, …) inside worlds where civilization **is** active.

## Shipped defaults

Without any datapack changes, the mod already ships **dimension overrides** for several known modded dimensions (so dungeon-style worlds are not accidentally “pacified” by overworld logic). Exact dimension IDs and tables: **[Built-in Compatibility](../modpack/compatibility.md)** (Dimension policies).

## Customizing (datapacks)

To add or change per-dimension behavior, use JSON registries as described on **[Data-Driven Registries](../modpack/data-driven.md)** → **Dimension policies (`civil_dimension_policies`)**. Path layout, `replace`, merge order, and field reference live there — not duplicated on this page.

## See also

- [Structure spawn rules](structure-spawn-rules.md)
- [Podium of Spawning](podium-of-spawning.md)
- [Built-in Compatibility](../modpack/compatibility.md)
- [Data-Driven Registries](../modpack/data-driven.md)
