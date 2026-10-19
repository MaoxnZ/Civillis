# Podium of Undying

In **high-civilization** areas you can build a small **altar-style structure** and activate it with a **totem**. If you would die, you may be **teleported to the podium** and revived with **totem-like effects** (regeneration, absorption, fire resistance) **without consuming** a held totem — **once per activation** per altar.

Layout uses vanilla-flavored blocks (gold, waxed oxidized cut copper stairs, emerald, etc.); discovering the exact shape is part of the progression.

## How it interacts with civilization

Rescue only considers podiums in chunks whose civilization score meets an internal threshold. The exact ratio is controlled by **`undyingAnchor.civRatio`** in `civil.properties` (default **0.8**) — not shown in the in-game screen.

## In-game configuration

**Civillis Settings** → **Civilization** → expand **Podium of Undying**:

| Setting | Type | Range | Default | What it controls |
|---------|------|-------|---------|------------------|
| **Enable Podium of Undying** | Toggle | on / off | on | Feature master switch. |
| **Max Resurrection Distance** | Slider | 32–256 blocks | 128 blocks | How far the mod searches for a valid podium when a death rescue fires. |
| **Rescue Cooldown** | Slider | 1–300 seconds | 10 s | Global cooldown between rescues. |

!!! warning "Advanced: civil.properties"
    **`undyingAnchor.civRatio`** (0–1, default **0.8**) sets how strong civilization must be at the podium chunk. Other internal keys may appear in your generated file — see [Configuration](../configuration.md). If rescues misbehave, reset `civil.properties`.

## Operators note

Running [`/civil rebuild`](commands.md) may **reset already-activated** altars; see the command page for what to expect for podiums and loaded chunks.

## See also

- [Commands](commands.md) — `/civil rebuild`
- [Civilization Decay](../civilization-decay.md) — staying near your builds
- [Configuration](../configuration.md) — full GUI + properties map
