# Built-in Compatibility

Civillis ships **default datapack JSON** under `data/civil/…`. At load time the game only applies entries whose block IDs (or dimensions, etc.) exist — if a mod is missing, its rows are skipped with **no errors** and **no runtime cost**.

- **Overrides:** drop your own files under `data/<namespace>/…` — see [Data-Driven Registries](data-driven.md).
- **Human-readable vanilla weights:** full table in [Blocks & Scoring](../blocks-and-scoring.md).

## Block weights (`civil_blocks`)

**Vanilla:** see the full table on **[Blocks & Scoring](../blocks-and-scoring.md)**; bundled data is `data/civil/civil_blocks/defaults.json` (**36** entries — block IDs and tags such as `#minecraft:beds`) plus `data/civil/civil_blocks/default_tags.json` for tier tags such as `#civil:high_civilized`.

Each **mod** below uses an **expandable block** so wide ID tables do not force a horizontal tab strip.

??? info "Create — compat_create.json (56 entries)"

    Ships as `data/civil/civil_blocks/compat_create.json`. **56** entries.

    | Block | Weight |
    |-------|--------|
    | `create:steam_engine` | 1.0 |
    | `create:mechanical_crafter` | 0.9 |
    | `create:blaze_burner` | 0.8 |
    | `create:deployer` | 0.8 |
    | `create:mechanical_arm` | 0.8 |
    | `create:clockwork_bearing` | 0.7 |
    | `create:sequenced_gearshift` | 0.7 |
    | `create:elevator_pulley` | 0.6 |
    | `create:rotation_speed_controller` | 0.6 |
    | `create:schematicannon` | 0.6 |
    | `create:track_station` | 0.6 |
    | `create:basin` | 0.5 |
    | `create:crushing_wheel` | 0.5 |
    | `create:cuckoo_clock` | 0.5 |
    | `create:display_link` | 0.5 |
    | `create:encased_fan` | 0.5 |
    | `create:flywheel` | 0.5 |
    | `create:haunted_bell` | 0.5 |
    | `create:hose_pulley` | 0.5 |
    | `create:mechanical_bearing` | 0.5 |
    | `create:mechanical_drill` | 0.5 |
    | `create:mechanical_mixer` | 0.5 |
    | `create:mechanical_press` | 0.5 |
    | `create:mechanical_pump` | 0.5 |
    | `create:mechanical_saw` | 0.5 |
    | `create:millstone` | 0.5 |
    | `create:nixie_tube` | 0.5 |
    | `create:peculiar_bell` | 0.5 |
    | `create:rose_quartz_lamp` | 0.5 |
    | `create:spout` | 0.5 |
    | `create:brass_funnel` | 0.4 |
    | `create:brass_tunnel` | 0.4 |
    | `create:depot` | 0.4 |
    | `create:flap_display` | 0.4 |
    | `create:fluid_tank` | 0.4 |
    | `create:item_drain` | 0.4 |
    | `create:item_vault` | 0.4 |
    | `create:large_water_wheel` | 0.4 |
    | `create:mechanical_harvester` | 0.4 |
    | `create:mechanical_piston` | 0.4 |
    | `create:mechanical_plough` | 0.4 |
    | `create:redstone_link` | 0.4 |
    | `create:smart_chute` | 0.4 |
    | `create:steam_whistle` | 0.4 |
    | `create:sticky_mechanical_piston` | 0.4 |
    | `create:water_wheel` | 0.4 |
    | `create:weighted_ejector` | 0.4 |
    | `create:windmill_bearing` | 0.4 |
    | `create:andesite_funnel` | 0.3 |
    | `create:chute` | 0.3 |
    | `create:clipboard` | 0.3 |
    | `create:desk_bell` | 0.3 |
    | `create:gearbox` | 0.3 |
    | `create:hand_crank` | 0.3 |
    | `create:placard` | 0.3 |
    | `create:schematic_table` | 0.3 |


??? info "Farmer's Delight — compat_farmersdelight.json (15 entries)"

    Ships as `data/civil/civil_blocks/compat_farmersdelight.json`. **15** entries.

    | Block | Weight |
    |-------|--------|
    | `farmersdelight:cooking_pot` | 0.5 |
    | `farmersdelight:stove` | 0.5 |
    | `farmersdelight:acacia_cabinet` | 0.4 |
    | `farmersdelight:bamboo_cabinet` | 0.4 |
    | `farmersdelight:birch_cabinet` | 0.4 |
    | `farmersdelight:cherry_cabinet` | 0.4 |
    | `farmersdelight:crimson_cabinet` | 0.4 |
    | `farmersdelight:dark_oak_cabinet` | 0.4 |
    | `farmersdelight:jungle_cabinet` | 0.4 |
    | `farmersdelight:mangrove_cabinet` | 0.4 |
    | `farmersdelight:oak_cabinet` | 0.4 |
    | `farmersdelight:spruce_cabinet` | 0.4 |
    | `farmersdelight:warped_cabinet` | 0.4 |
    | `farmersdelight:basket` | 0.3 |
    | `farmersdelight:cutting_board` | 0.3 |


??? info "Supplementaries — compat_supplementaries.json (42 entries)"

    Ships as `data/civil/civil_blocks/compat_supplementaries.json`. **42** entries.

    | Block | Weight |
    |-------|--------|
    | `supplementaries:netherite_door` | 0.8 |
    | `supplementaries:cannon` | 0.7 |
    | `supplementaries:netherite_trapdoor` | 0.7 |
    | `supplementaries:end_stone_lamp` | 0.5 |
    | `supplementaries:globe` | 0.5 |
    | `supplementaries:globe_sepia` | 0.5 |
    | `supplementaries:gold_door` | 0.5 |
    | `supplementaries:hourglass` | 0.5 |
    | `supplementaries:safe` | 0.5 |
    | `supplementaries:blackstone_lamp` | 0.4 |
    | `supplementaries:clock_block` | 0.4 |
    | `supplementaries:cog_block` | 0.4 |
    | `supplementaries:deepslate_lamp` | 0.4 |
    | `supplementaries:fire_pit` | 0.4 |
    | `supplementaries:goblet` | 0.4 |
    | `supplementaries:gold_gate` | 0.4 |
    | `supplementaries:gold_trapdoor` | 0.4 |
    | `supplementaries:iron_gate` | 0.4 |
    | `supplementaries:pulley_block` | 0.4 |
    | `supplementaries:stone_lamp` | 0.4 |
    | `supplementaries:bellows` | 0.3 |
    | `supplementaries:crystal_display` | 0.3 |
    | `supplementaries:faucet` | 0.3 |
    | `supplementaries:notice_board` | 0.3 |
    | `supplementaries:pedestal` | 0.3 |
    | `supplementaries:sconce` | 0.3 |
    | `supplementaries:sconce_green` | 0.3 |
    | `supplementaries:sconce_lever` | 0.3 |
    | `supplementaries:sconce_soul` | 0.3 |
    | `supplementaries:speaker_block` | 0.3 |
    | `supplementaries:statue` | 0.3 |
    | `supplementaries:turn_table` | 0.3 |
    | `supplementaries:wind_vane` | 0.3 |
    | `supplementaries:blackboard` | 0.2 |
    | `supplementaries:book_pile` | 0.2 |
    | `supplementaries:book_pile_horizontal` | 0.2 |
    | `supplementaries:cage` | 0.2 |
    | `supplementaries:doormat` | 0.2 |
    | `supplementaries:flower_box` | 0.2 |
    | `supplementaries:item_shelf` | 0.2 |
    | `supplementaries:jar` | 0.2 |
    | `supplementaries:planter` | 0.2 |


??? info "Quark — compat_quark.json (23 entries)"

    Ships as `data/civil/civil_blocks/compat_quark.json`. **23** entries.

    | Block | Weight |
    |-------|--------|
    | `quark:matrix_enchanter` | 1.5 |
    | `quark:ender_watcher` | 0.7 |
    | `quark:acacia_chest` | 0.3 |
    | `quark:ancient_chest` | 0.3 |
    | `quark:azalea_chest` | 0.3 |
    | `quark:bamboo_chest` | 0.3 |
    | `quark:blackstone_furnace` | 0.3 |
    | `quark:blossom_chest` | 0.3 |
    | `quark:cherry_chest` | 0.3 |
    | `quark:crafter` | 0.3 |
    | `quark:crate` | 0.3 |
    | `quark:crimson_chest` | 0.3 |
    | `quark:dark_oak_chest` | 0.3 |
    | `quark:deepslate_furnace` | 0.3 |
    | `quark:jungle_chest` | 0.3 |
    | `quark:magnet` | 0.3 |
    | `quark:mangrove_chest` | 0.3 |
    | `quark:nether_brick_chest` | 0.3 |
    | `quark:oak_chest` | 0.3 |
    | `quark:prismarine_chest` | 0.3 |
    | `quark:purpur_chest` | 0.3 |
    | `quark:spruce_chest` | 0.3 |
    | `quark:warped_chest` | 0.3 |


??? info "Applied Energistics 2 — compat_ae2.json (21 entries)"

    Ships as `data/civil/civil_blocks/compat_ae2.json`. **21** entries.

    | Block | Weight |
    |-------|--------|
    | `ae2:controller` | 1.5 |
    | `ae2:cell_workbench` | 0.5 |
    | `ae2:charger` | 0.5 |
    | `ae2:inscriber` | 0.5 |
    | `ae2:crystal_resonance_generator` | 0.4 |
    | `ae2:interface` | 0.4 |
    | `ae2:io_port` | 0.4 |
    | `ae2:molecular_assembler` | 0.4 |
    | `ae2:vibration_chamber` | 0.4 |
    | `ae2:chest` | 0.3 |
    | `ae2:crafting_accelerator` | 0.3 |
    | `ae2:crafting_monitor` | 0.3 |
    | `ae2:crafting_unit` | 0.3 |
    | `ae2:dense_energy_cell` | 0.3 |
    | `ae2:drive` | 0.3 |
    | `ae2:energy_cell` | 0.3 |
    | `ae2:pattern_provider` | 0.3 |
    | `ae2:sky_stone_chest` | 0.3 |
    | `ae2:smooth_sky_stone_chest` | 0.3 |
    | `ae2:growth_accelerator` | 0.1 |
    | `ae2:sky_stone_tank` | 0.1 |


??? info "Storage Drawers — compat_drawers.json (6 entries)"

    Ships as `data/civil/civil_blocks/compat_drawers.json`. **6** entries.

    | Block | Weight |
    |-------|--------|
    | `storagedrawers:controller` | 0.5 |
    | `storagedrawers:framing_table` | 0.5 |
    | `storagedrawers:compacting_drawers_2` | 0.2 |
    | `storagedrawers:compacting_drawers_3` | 0.2 |
    | `storagedrawers:compacting_half_drawers_2` | 0.2 |
    | `storagedrawers:compacting_half_drawers_3` | 0.2 |


??? info "Iron Chests — compat_ironchests.json (6 entries)"

    Ships as `data/civil/civil_blocks/compat_ironchests.json`. **6** entries.

    | Block | Weight |
    |-------|--------|
    | `ironchest:copper_chest` | 0.3 |
    | `ironchest:crystal_chest` | 0.3 |
    | `ironchest:diamond_chest` | 0.3 |
    | `ironchest:gold_chest` | 0.3 |
    | `ironchest:iron_chest` | 0.3 |
    | `ironchest:obsidian_chest` | 0.3 |


??? info "Sophisticated Storage — compat_sophisticatedstorage.json (14 entries)"

    Ships as `data/civil/civil_blocks/compat_sophisticatedstorage.json`. **14** entries.

    | Block | Weight |
    |-------|--------|
    | `sophisticatedstorage:controller` | 0.5 |
    | `sophisticatedstorage:decoration_table` | 0.5 |
    | `sophisticatedstorage:chest` | 0.3 |
    | `sophisticatedstorage:copper_barrel` | 0.3 |
    | `sophisticatedstorage:copper_chest` | 0.3 |
    | `sophisticatedstorage:diamond_barrel` | 0.3 |
    | `sophisticatedstorage:diamond_chest` | 0.3 |
    | `sophisticatedstorage:gold_barrel` | 0.3 |
    | `sophisticatedstorage:gold_chest` | 0.3 |
    | `sophisticatedstorage:iron_barrel` | 0.3 |
    | `sophisticatedstorage:iron_chest` | 0.3 |
    | `sophisticatedstorage:netherite_barrel` | 0.3 |
    | `sophisticatedstorage:netherite_chest` | 0.3 |
    | `sophisticatedstorage:storage_link` | 0.3 |


??? info "Functional Storage — compat_functionalstorage.json (6 entries)"

    Ships as `data/civil/civil_blocks/compat_functionalstorage.json`. **6** entries.

    | Block | Weight |
    |-------|--------|
    | `functionalstorage:storage_controller` | 0.5 |
    | `functionalstorage:controller_extension` | 0.4 |
    | `functionalstorage:armory_cabinet` | 0.2 |
    | `functionalstorage:compacting_drawer` | 0.2 |
    | `functionalstorage:ender_drawer` | 0.2 |
    | `functionalstorage:simple_compacting_drawer` | 0.2 |


## Head types (`civil_heads`)

??? info "Default registry — defaults.json"

    File: `data/civil/civil_heads/defaults.json`. **5** vanilla skull types.

    Gameplay is described on [Podium of Spawning](../play/podium-of-spawning.md).

    | `skull_type` | `entity_type` | `dimensions` (optional) |
    |--------------|---------------|---------------------------|
    | `ZOMBIE` | `minecraft:zombie` | *(all)* |
    | `SKELETON` | `minecraft:skeleton` | *(all)* |
    | `WITHER_SKELETON` | `minecraft:wither_skeleton` | `minecraft:the_nether` |
    | `CREEPER` | `minecraft:creeper` | *(all)* |
    | `PIGLIN` | `minecraft:piglin` | `minecraft:the_nether` |

    Override or extend via `data/<namespace>/civil_heads/`. See [Data-Driven Registries](data-driven.md#head-types).


## Zone policies (`civil_zone_policies`)

??? info "Default structure rules — defaults.json"

    File: `data/civil/civil_zone_policies/defaults.json`.

    Natural hostile spawns stay allowed inside matched vanilla structures; matched chunks are **structure-tinted** for the [zone HUD](../play/zone-transition-hud.md).

    ### `massive_structures`

    - **`allow_hostile_spawn`:** `True`
    - **Structures (8):** `minecraft:monument`, `minecraft:fortress`, `minecraft:bastion_remnant`, `minecraft:mansion`, `minecraft:ancient_city`, `minecraft:end_city`, `minecraft:trial_chambers`, `minecraft:stronghold`

    Extend with `data/<namespace>/civil_zone_policies/<name>.json`. Reload datapacks on the server after edits.


## Dimension policies (`civil_dimension_policies`)

??? info "Default dimension overrides — defaults.json"

    File: `data/civil/civil_dimension_policies/defaults.json`.

    Each shipped row sets **`civilization`: false** unless noted. **`head_mechanics`** defaults to true unless overridden per row.

    | Dimension | `civilization` | `head_mechanics` |
    |-----------|----------------|------------------|
    | `minecells:prison` | False | True |
    | `minecells:promenade` | False | True |
    | `minecells:insufferable_crypt` | False | True |
    | `minecells:black_bridge` | False | True |
    | `minecells:ramparts` | False | True |
    | `dimdungeons:dungeon_dimension` | False | True |
    | `dimdungeons:build_dimension` | False | True |

    Add more rows under `data/<namespace>/civil_dimension_policies/`. Later files override earlier ones for the same dimension.


## Contributing

Pull requests that add `civil_blocks` files for popular mods are welcome — follow the [weight framework](../blocks-and-scoring.md) and verify block IDs against the target mod version.
