# Zone transition HUD

When you move across large spans of **civilized** versus **wilderness** chunks, short on-screen hints can appear with smooth fades so you can read the frontier without using sonar.

Default labels:

- **Civilized** — you entered a region that reads as civilization.
- **Wilderness** — you left that protection span.
- **Caution** — you are in a structure-tinted area where [structure spawn rules](structure-spawn-rules.md) may allow hostile spawns despite nearby civilization.

## In-game configuration

**Civillis Settings** -> **Civilization** -> expand **Zone Transition HUD**:

| Setting | Type | Default | What it controls |
|---------|------|---------|------------------|
| **Enable Zone Transition HUD** | Toggle | on | Shows civilized / wilderness / caution labels when zone semantics change. |
| **Zone HUD: Civilized label** | Text | empty | Optional custom Civilized message. Empty uses the language file default. |
| **Zone HUD: Wilderness label** | Text | empty | Optional custom Wilderness message. |
| **Zone HUD: Caution label** | Text | empty | Optional custom Caution message. |
| **Minimum interval** | Slider | 10 s | Cooldown between starting HUD messages. Set to Off for no cooldown. |
| **Horizontal offset** | Slider | 0% | Moves the message anchor left / right as a percentage of screen width. |
| **Vertical offset** | Slider | 30% | Moves the message anchor up / down as a percentage of screen height. |

Requires [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) on Fabric; NeoForge/Forge use the loader's config entry for Civillis.

!!! warning "Advanced: civil.properties"
    The same settings map to `ui.zoneTransitionHudEnabled`, `ui.zoneTransitionLabel.*`, `ui.zoneTransitionHudCooldownSeconds`, and `ui.zoneTransitionHudAnchorOffset*Percent`. Prefer the GUI unless you know what you are doing.

## See also

- [Structure spawn rules](structure-spawn-rules.md) — why **Caution** appears
- [Dimension rules](dimension-rules.md) — when entire dimensions skip civilization
- [Civilization Sonar](../sonar/index.md) — precise boundary visualization
- [Configuration](../configuration.md) — multiplayer scope
