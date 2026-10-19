# Built-in Compatibility

Civillis ships with built-in civilization block weights for several popular mods. These are included as standard datapack JSON files — if the mod is installed, its blocks contribute to civilization scoring automatically. If the mod is absent, the entries are silently skipped at load time.

No configuration needed. Just install both mods and it works.

---

## Farmer's Delight

Functional kitchen and storage blocks that represent a thriving settlement. 5 entries, weights 0.3–0.5.

??? info "View all blocks"

    | Block | Weight |
    |-------|--------|
    | Stove | 0.5 |
    | Cooking Pot | 0.5 |
    | Cabinets (all 11 wood types) | 0.4 |
    | Cutting Board | 0.3 |
    | Basket | 0.3 |

---

## Supplementaries

Decorative and functional blocks spanning multiple material tiers. 46 entries, weights 0.2–0.8.

??? info "View all blocks"

    **Advanced tier (0.7–0.8)**

    | Block | Weight |
    |-------|--------|
    | Netherite Door | 0.8 |
    | Netherite Trapdoor | 0.7 |
    | Cannon | 0.7 |

    **Iron–Advanced tier (0.4–0.5)**

    | Block | Weight |
    |-------|--------|
    | Safe | 0.5 |
    | Globe / Globe (Sepia) | 0.5 |
    | Hourglass | 0.5 |
    | Gold Door | 0.5 |
    | End Stone Lamp | 0.5 |
    | Gold Trapdoor / Gold Gate | 0.4 |
    | Clock Block | 0.4 |
    | Pulley Block | 0.4 |
    | Stone / Blackstone / Deepslate Lamp | 0.4 |
    | Fire Pit | 0.4 |
    | Goblet | 0.4 |
    | Iron Gate | 0.4 |
    | Cog Block | 0.4 |

    **Iron tier (0.3)**

    | Block | Weight |
    |-------|--------|
    | Sconce / Soul Sconce / Green Sconce / Sconce Lever | 0.3 |
    | Pedestal | 0.3 |
    | Statue | 0.3 |
    | Notice Board | 0.3 |
    | Bellows | 0.3 |
    | Wind Vane | 0.3 |
    | Turn Table | 0.3 |
    | Speaker Block | 0.3 |
    | Faucet | 0.3 |
    | Crystal Display | 0.3 |

    **Stone Age (0.2)**

    | Block | Weight |
    |-------|--------|
    | Jar | 0.2 |
    | Cage | 0.2 |
    | Flower Box | 0.2 |
    | Item Shelf | 0.2 |
    | Blackboard | 0.2 |
    | Planter | 0.2 |
    | Book Pile / Book Pile (Horizontal) | 0.2 |
    | Doormat | 0.2 |

---

## Create

Mechanical and industrial blocks, weighted by Create's own material tier system. 63 entries, weights 0.3–1.0.

??? info "View all blocks"

    **Precision tier (0.8–1.0)**

    | Block | Weight |
    |-------|--------|
    | Steam Engine | 1.0 |
    | Mechanical Crafter | 0.9 |
    | Blaze Burner | 0.8 |
    | Deployer | 0.8 |
    | Mechanical Arm | 0.8 |

    **Brass tier (0.6–0.7)**

    | Block | Weight |
    |-------|--------|
    | Clockwork Bearing | 0.7 |
    | Sequenced Gearshift | 0.7 |
    | Rotation Speed Controller | 0.6 |
    | Schematicannon | 0.6 |
    | Track Station | 0.6 |
    | Elevator Pulley | 0.6 |

    **Andesite tier (0.4–0.5)**

    | Block | Weight |
    |-------|--------|
    | Mechanical Press | 0.5 |
    | Mechanical Mixer | 0.5 |
    | Mechanical Saw | 0.5 |
    | Mechanical Drill | 0.5 |
    | Crushing Wheel | 0.5 |
    | Basin | 0.5 |
    | Millstone | 0.5 |
    | Encased Fan | 0.5 |
    | Spout | 0.5 |
    | Mechanical Pump | 0.5 |
    | Hose Pulley | 0.5 |
    | Flywheel | 0.5 |
    | Mechanical Bearing | 0.5 |
    | Nixie Tube | 0.5 |
    | Cuckoo Clock | 0.5 |
    | Display Link | 0.5 |
    | Rose Quartz Lamp | 0.5 |
    | Peculiar Bell | 0.5 |
    | Haunted Bell | 0.5 |
    | Water Wheel | 0.4 |
    | Large Water Wheel | 0.4 |
    | Windmill Bearing | 0.4 |
    | Fluid Tank | 0.4 |
    | Item Vault | 0.4 |
    | Depot | 0.4 |
    | Item Drain | 0.4 |
    | Mechanical Piston / Sticky Mechanical Piston | 0.4 |
    | Brass Funnel / Brass Tunnel | 0.4 |
    | Smart Chute | 0.4 |
    | Mechanical Harvester | 0.4 |
    | Mechanical Plough | 0.4 |
    | Weighted Ejector | 0.4 |
    | Redstone Link | 0.4 |
    | Flap Display | 0.4 |
    | Steam Whistle | 0.4 |

    **Basic tier (0.3)**

    | Block | Weight |
    |-------|--------|
    | Hand Crank | 0.3 |
    | Andesite Funnel | 0.3 |
    | Chute | 0.3 |
    | Gearbox | 0.3 |
    | Schematic Table | 0.3 |
    | Placard | 0.3 |
    | Desk Bell | 0.3 |
    | Clipboard | 0.3 |

---

## How It Works

Compatibility files are standard `civil_blocks` JSON located at:

```
data/civil/civil_blocks/compat_farmersdelight.json
data/civil/civil_blocks/compat_supplementaries.json
data/civil/civil_blocks/compat_create.json
```

The block weight loader resolves each block ID against the game registry at load time. If the mod isn't installed, `Registries.BLOCK.containsId()` returns false and the entry is skipped with a debug log warning. There is no mod-presence check — it's simply "if the block exists, register its weight."

This means:

- **Zero overhead** when the mod is absent
- **Automatic activation** when the mod is present
- **Fully overridable** via your own datapack — just create entries for the same block IDs with your preferred weights

---

## Adding Support for Other Mods

If you use a mod not listed here, you can add civilization weights yourself via a datapack. See [Data-Driven Registries](data-driven.md) for the full JSON format and examples.

We welcome pull requests adding compatibility files for popular mods — follow the weight framework in [Blocks & Scoring](../blocks-and-scoring.md) and submit to the [GitHub repository](https://github.com/MaoxnZ/Civillis).
