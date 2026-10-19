# Structure spawn rules

Civillis normally **suppresses natural hostile spawning** where civilization score is high. In a few **special places**, vanilla still expects hostiles to spawn even when the surrounding area is civilized — so the mod **does not** apply that suppression **inside** those structures.

## What you experience

**Inside** matched large vanilla structures, natural hostile spawns can still happen the way Minecraft intends: for example **guardians** in **ocean monuments**, **blazes** in **nether fortresses**, activity in **bastions**, **mansions**, **ancient cities**, **end cities**, **trial chambers**, and **strongholds**. **Outside** those footprints, normal civilization rules apply.

When you are in one of these **structure-tinted** areas, the [zone transition HUD](zone-transition-hud.md) may show **Caution** — a heads-up that spawn logic here is not the same as ordinary “safe” civilized ground.

For the full technical pipeline (order of checks, datapack hooks), see [How It Works](../how-it-works.md) and [Architecture](../technical/architecture.md).

## How this differs from dimension rules

**Structure rules** only tweak behavior **inside specific structures** in worlds where civilization scoring is otherwise active. **Per-dimension** on/off (whole dimensions with no civ scoring, or no head logic) is separate — see **[Dimension rules](dimension-rules.md)**.

## Defaults and reference

The mod ships built-in rules so the above structures stay dangerous without you configuring anything. Exact structure IDs and JSON: **[Built-in Compatibility](../modpack/compatibility.md)** (Zone policies) and, for authors, **[Data-Driven Registries](../modpack/data-driven.md)** under **Zone policies (`civil_zone_policies`)**.

## See also

- [Dimension rules](dimension-rules.md)
- [Zone transition HUD](zone-transition-hud.md)
- [Data-Driven Registries](../modpack/data-driven.md) — adding or overriding policies via datapack
