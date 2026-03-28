# Zone transition HUD

When you move across large spans of **civilized** versus **wilderness** chunks, short on-screen hints can appear (**Civilized**, **Wilderness**) with smooth fades so you can read the frontier without using sonar.

**Caution** appears in **structure-tinted** areas — for example **ocean monuments** — where [structure spawn rules](structure-spawn-rules.md) apply and natural hostiles may still spawn despite nearby civilization.

## In-game configuration

**Civillis Settings** → **Civilization** → expand **Miscellaneous**:

| Setting | Type | Default | What it controls |
|---------|------|---------|------------------|
| **Zone Transition HUD** | Toggle | on | Shows civilized / wilderness / caution labels when zone semantics change. |

Requires [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) on Fabric; NeoForge/Forge use the loader’s config entry for Civillis.

!!! warning "Advanced: civil.properties"
    The same toggle maps to **`ui.zoneTransitionHudEnabled`** (older worlds may still mention `ui.zoneTransitionMessageEnabled` as a legacy alias in comments). Prefer the GUI unless you know what you are doing.

## See also

- [Structure spawn rules](structure-spawn-rules.md) — why **Caution** appears
- [Dimension rules](dimension-rules.md) — when entire dimensions skip civilization
- [Civilization Sonar](../sonar/index.md) — precise boundary visualization
- [Configuration](../configuration.md) — multiplayer scope
