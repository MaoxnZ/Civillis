# Civil Maps

Civil maps are vanilla filled maps upgraded with a **Civil Detector**. They keep the normal terrain picture, but Civillis bakes extra region tints into the map as it updates.

## Crafting

Use a shapeless recipe:

```text
1 filled map + 1 Civil Detector -> 1 civil map
```

Rules:

- The map must already be a **filled map** with a map ID.
- An **empty map** will not work.
- A **locked map** will not work.
- A map that is already a civil map will not be upgraded again.

Civil maps are marked by lore:

```text
The sheet holds civility.
```

## Cartography table behavior

Civil maps follow vanilla map rules in the cartography table:

| Input | Result |
|-------|--------|
| Civil map + paper | Zoomed civil map |
| Civil map + glass pane | Locked civil map |
| Civil map + empty map | Duplicated civil map |

The civil marker stays on the output, so copied, zoomed, and locked maps remain civil maps.

## Tint meanings

Civil maps intentionally use a small palette:

| Tint | Meaning |
|------|---------|
| Washed white | Civilized territory: the sampled chunk is at or above the main civilization threshold. |
| Purple | Monster / spawning pocket: an activated [Podium of Spawning](podium-of-spawning.md) bypass box covers that sampled position. |
| No civil tint | Wilderness, frontier below the main threshold, disabled dimensions, or chunks whose score is not known yet. |

The tint is applied as the map reveals terrain. It is not a full-screen overlay and it does not constantly rescan every frame.

!!! note "Dimension policies"
    If civilization is disabled in a dimension, civil maps do not show civilization tints there. See [Dimension rules](dimension-rules.md).

## Updates and accuracy

Civil map tinting follows map updates. When the world changes — for example, you place recognized blocks, remove them, or activate a spawning podium — the map updates as Minecraft revisits those map pixels.

The server samples representative surface height per chunk for tint decisions. Recent builds use a sparse height sample grid to keep map work light while staying accurate enough for region-scale reading.

## Related overlays

Civil maps are the built-in map item. Some versions also support external map mod overlays for JourneyMap and Xaero's maps; see [Map mod overlays](map-overlays.md).

## See also

- [How It Works](../how-it-works.md)
- [Blocks & Scoring](../blocks-and-scoring.md)
- [Podium of Spawning](podium-of-spawning.md)
- [Configuration](../configuration.md) — `mapTint.*` values
