# Commands

Civillis currently exposes operator maintenance and visualization helpers under `/civil`.

!!! note "Permissions"
    `/civil rebuild` and `/civil ring` require game-master permission (OP level 2+, or cheats enabled in singleplayer). They are not regular survival-player commands.

## `/civil rebuild`

After major updates or if civilization data in a world ever feels **stale or inconsistent**, operators can run:

```text
/civil rebuild
```

This is a **maintenance helper**: it rebuilds derived civilization storage from a clean baseline so scores, caches, heads, podiums, and on-disk layout match what the current mod version expects. It is recommended only when a changelog or support answer tells you to use it, or when a world clearly has stale Civillis state.

!!! tip "After `/civil rebuild`"
    **Podiums of Undying** — already-activated altars can reset; you may need to activate them again with a totem.

    **Podiums of Spawning / mob heads** — skull and podium state in chunks that stay loaded may not match rebuilt data until those chunks reload. Unload the area (walk far enough that the chunks unload, then come back) or restart the server if behavior looks stale.

## `/civil ring`

```text
/civil ring
```

Triggers a civilization sonar pulse at your current position. This is useful for operators and pack authors checking aura walls, boundaries, or screenshots without placing and ringing a bell.

It uses the same visualization pipeline as sonar; it does not change civilization score.

## See also

- [Civilization Sonar](../sonar/index.md)
- [Podium of Undying](podium-of-undying.md)
- [Podium of Spawning](podium-of-spawning.md)
- [Architecture](../technical/architecture.md) — persistence overview
