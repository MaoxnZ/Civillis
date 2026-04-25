# Map mod overlays

Civil maps are built into Civillis, but some builds also integrate with external map mods so civilization regions can appear on those maps directly.

## Supported overlays

| Overlay | Status |
|---------|--------|
| **JourneyMap** | Available on selected port lines; currently documented for **Minecraft 1.20.1** and **1.21.1** builds. |
| **Xaero's Minimap + World Map** | Available on selected port lines; both Xaero mods are expected for the full experience. Currently documented for **Minecraft 1.20.1** and **1.21.1** builds. |
| **Supplementaries map rendering** | Compatibility fixes exist for selected lines where Supplementaries previously overrode civil map tint drawing. |

Check the changelog for the exact build you installed; overlay support is port-specific.

## What the overlay shows

The overlay uses the same high-level meaning as civil maps:

- Civilized regions are shown as a light / white civil tint.
- Podium of Spawning pockets are shown as a monster / purple tint.
- Dimensions with civilization disabled do not show civil region coloring.

## Performance controls

External map overlays scan server-side data and can be more expensive at large ranges than a single handheld map update. On supported JourneyMap / Xaero builds, overlays start **disabled** by default. Turn on only the overlays you want and tune the **Exploration Radius** setting if your pack or server needs a smaller scan footprint.

## When to use which map

| Need | Best tool |
|------|-----------|
| A vanilla-feeling item you can craft and copy | [Civil Maps](civil-maps.md) |
| A live minimap / world-map region overlay | JourneyMap / Xaero overlay, if supported on your build |
| Real-time boundary reading around your character | [Civilization Sonar](../sonar/index.md) |

## See also

- [Civil Maps](civil-maps.md)
- [Configuration](../configuration.md)
- [Built-in Compatibility](../modpack/compatibility.md)
- [Performance](../technical/performance.md)
