# Performance

Civillis evaluates millions of blocks per spawn attempt while keeping each check at near-constant time. This page explains the optimization strategy and its real-world cost profile.

---

## The Core Challenge

Every time a hostile mob tries to spawn naturally, the engine must answer: *"How civilized is this location?"* This requires aggregating block data across a configurable detection area (default 240×240×48 blocks = 675 voxel chunks). Doing this naively on every spawn attempt would be catastrophically expensive.

The solution is a **shard-based caching engine** built on three pillars:

1. **Pre-aggregated results** — The 675-chunk aggregation is computed once and cached. Subsequent spawn checks are a single map lookup: **O(1), ~50 ns**.
2. **Delta propagation** — When blocks change, only the affected shard is recomputed and the difference is applied to cached results. No full re-aggregation ever runs after the initial computation.
3. **Palette pre-filtering** — Before scanning 4,096 blocks in a chunk section, the engine checks Minecraft's internal block palette for recognized civilization blocks. Sections with no targets are skipped in ~1 μs instead of a ~100 μs full scan — in wilderness this eliminates virtually 100% of scanning work.

---

## Civilization Scoring Engine

The following diagram shows the full data flow through the scoring engine — the component responsible for the O(1) spawn checks. All key paths (spawn checks, block changes, cold loading, prefetching, TTL eviction) are shown with their typical costs.

```mermaid
flowchart TD
    SpawnCheck(["Spawn Check"])
    BlockChange(["Block Change"])

    SpawnCheck ~~~ BlockChange

    Miss{{"cache miss<br/>aggregate from L1<br/>~34 μs"}}
    Hit{{"cache hit<br/>~50 ns"}}
    Delta{{"recompute +<br/>propagate delta<br/>~13 μs"}}

    L1[("L1 Shards<br/>per-chunk scores")]
    Result[("Result Shards<br/>pre-aggregated")]

    SpawnCheck --> Miss --> L1
    SpawnCheck --> Hit --> Result
    BlockChange --> Delta --> Result

    L1 -->|"distance-weighted aggregate"| Result

    Prefetch{{"prefetch nearby<br/>~0.9 ms/s"}}
    Presence{{"decay recovery<br/>~10 μs/s"}}
    ColdLoad{{"cold read<br/>~0.1 ms"}}
    H2[("H2 Cold Store<br/>async I/O")]
    PlayerMove(["Player Move (1/s)"])

    PlayerMove --> Prefetch --> L1
    PlayerMove --> Presence --> Result
    H2 --> ColdLoad --> L1
    L1 -->|"TTL 5 min evict"| H2
    Result -->|"TTL 60 min evict"| H2
```

### Cost Summary

| Operation | Typical cost | Frequency |
|-----------|-------------|-----------|
| Spawn check (warm) | ~50 ns | Every natural spawn attempt |
| Spawn check (cold) | ~34 μs | First spawn in a new area |
| L1 compute (palette skip) | ~1 μs | Most chunk sections |
| L1 compute (full scan) | ~100 μs | Sections with civilization blocks |
| Block change + delta | ~13 μs | Every block placement/removal |
| Database cold read | ~0.1 ms | L1 evicted from memory |
| Prefetch per player (moved) | ~0.9 ms/s | Once per second when player moves |
| Prefetch per player (stationary) | ~0.01 ms/s | Once per second (presence only) |

The scoring engine alone scales comfortably to hundreds of players. The real cost story, however, depends on what happens *around* it.

---

## Mob Flee AI Performance Notes

Mob Flee AI is a behavior-layer system and is intentionally decoupled from the O(1) civilization score query path.

- It does not change the cache topology (L1/Result shards) or delta propagation math
- It runs on periodic evaluations per mob (interval + jitter), not every tick for every mob
- Its practical cost scales with active hostile mob count and configured flee cadence

If a server needs stricter performance limits, `mobFlee.enabled=false` fully disables this behavior without affecting spawn suppression, decay, or head attraction.

---

## Spawn Churn & Head Scan Impact

The scoring engine's O(1) checks are cheap, but they don't run in isolation. Before each score lookup, the spawn pipeline also scans monster heads (O(N) per attempt). And the *number* of spawn attempts per tick depends on how quickly the mob cap fills — which Civillis directly influences.

Minecraft tries to spawn mobs until the hostile mob cap is filled. When spawns succeed quickly, the cycle is short. But when Civillis actively blocks spawns, the cap takes longer to fill and attempt volume rises. Two factors amplify this:

- **Civilized dark areas** — High civilization score blocks spawns, but darkness keeps Minecraft retrying. The mob cap fills slowly or not at all, so the spawn cycle runs at elevated intensity every tick.
- **Distant skull suppression** — Active heads suppress spawns across the entire dimension (distance-weighted). Even players far from any head farm see spawn attempts suppressed, further preventing cap-fill and amplifying attempt volume dimension-wide.

These effects compound. And crucially, every spawn attempt pays the **O(N) head scan cost** (where N = total heads in dimension) before the O(1) score check even runs. More churn means more scans.

| Situation | Attempts per tick per player | Head scans per attempt |
|-----------|----------------------------|----------------------|
| Wilderness, no heads | ~15–20 | 0 |
| Base ~30% dark, no heads | ~50–80 | 0 |
| Moderate heads (~20) in dimension | ~60–100 | 20 |
| Heavy dark + many heads (~40+) | ~150–300 | 40+ |

### Realistic Server Estimates

Combining all components. Assumptions per scenario stated below each table.

**Small server (10 players), ~5 shared heads:**

| Scenario | Prefetch | Spawn checks | Head scans | Total | Budget used |
|----------|----------|-------------|------------|-------|-------------|
| 3 exploring, 7 at base (~20% dark) | 0.14 ms | 0.02 ms | 0.08 ms | ~0.24 ms/tick | 0.5% |

**Medium server (50 players), ~20 shared heads:**

| Scenario | Prefetch | Spawn checks | Head scans | Total | Budget used |
|----------|----------|-------------|------------|-------|-------------|
| 10 moving, 40 at base (~25% dark) | 0.49 ms | 0.15 ms | 0.6 ms | ~1.2 ms/tick | 2.5% |
| Same + distant suppression active | 0.49 ms | 0.25 ms | 1.0 ms | ~1.7 ms/tick | 3.5% |

**Large server (100 players), ~40 shared heads:**

| Scenario | Prefetch | Spawn checks | Head scans | Total | Budget used |
|----------|----------|-------------|------------|-------|-------------|
| 20 moving, 80 at base (~25% dark) | 0.98 ms | 0.30 ms | 2.4 ms | ~3.7 ms/tick | 7.4% |
| Same + heavy dark + suppression | 0.98 ms | 0.56 ms | 4.5 ms | ~6.0 ms/tick | 12% |

**Massive server (200 players), ~80 shared heads:**

| Scenario | Prefetch | Spawn checks | Head scans | Total | Budget used |
|----------|----------|-------------|------------|-------|-------------|
| 40 moving, 160 at base (~25% dark) | 2.0 ms | 0.60 ms | 9.6 ms | ~12.2 ms/tick | 24% |
| Same + heavy dark + suppression | 2.0 ms | 1.0 ms | 16 ms | ~19 ms/tick | 38% |

!!! note "Head scans dominate at scale"
    On large servers, the O(N) per-attempt head scan becomes the primary cost driver. Distant suppression inflates spawn attempt volume across the entire dimension, and each attempt scans every head. Servers with 50+ heads should keep civilized areas well-lit and consolidate unused head farms to reduce churn.

!!! warning "Edge case: worst-case churn"
    The most expensive scenario is a dimension with many active heads (strong distant suppression) combined with large civilized areas that are mostly dark (civilization blocks spawns, darkness keeps Minecraft trying). The mob cap never fills, so the spawn cycle runs at maximum intensity every tick — and every attempt pays the O(N) head scan cost. If you notice TPS impact, lighting up builds and reducing head count are the most effective mitigations.
