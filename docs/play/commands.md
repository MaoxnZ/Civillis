# Commands

## `/civil rebuild`

After major updates or if civilization data in a world ever feels **stale or inconsistent**, operators (or singleplayer) can run:

```text
/civil rebuild
```

This is a **maintenance helper**: it **rebuilds derived civilization storage** from a **clean baseline** so scores, caches, and on-disk layout match what the current mod version expects. It is **recommended** after certain storage-related upgrades; see the [changelog](https://github.com/MaoxnZ/Civillis/blob/main/CHANGELOG.md) for the version you are installing.

!!! tip "After `/civil rebuild`"
    **Podiums of Undying** — already-activated **altars can reset**; you may need to activate them again with a totem.

    **Mob heads** — skull state in **chunks that stay loaded** may not match the rebuilt data until those chunks **reload**. **Unload the area** (walk far enough that the chunks unload, then come back) or **close and reopen the world** (restart the server on multiplayer). Otherwise heads can look “wrong” until the region loads fresh.

    **Who can run it** — the command uses your server’s **op level / permission plugin** rules. There is no entry in **Civillis Settings**; that is normal.

## See also

- [Podium of Undying](podium-of-undying.md)
- [Architecture](../technical/architecture.md) — persistence overview
