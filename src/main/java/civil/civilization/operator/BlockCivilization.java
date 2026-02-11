package civil.civilization.operator;

import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SkullBlock;
import net.minecraft.entity.EntityType;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Single block civilization judgment: suspicion (non-air=1), weight, monster head, head type.
 * Only does block-level judgment, does not normalize or aggregate; normalization is defined by each operator.
 */
public final class BlockCivilization {

    private static final Map<Block, Double> BLOCK_WEIGHTS = new IdentityHashMap<>();

    static {
        BLOCK_WEIGHTS.put(Blocks.BEACON, 5.0);
        BLOCK_WEIGHTS.put(Blocks.CONDUIT, 5.0);
        BLOCK_WEIGHTS.put(Blocks.CAMPFIRE, 1.0);
        BLOCK_WEIGHTS.put(Blocks.SOUL_CAMPFIRE, 1.0);
        BLOCK_WEIGHTS.put(Blocks.LANTERN, 1.0);
        BLOCK_WEIGHTS.put(Blocks.SOUL_LANTERN, 1.0);
        BLOCK_WEIGHTS.put(Blocks.SEA_LANTERN, 1.0);
        BLOCK_WEIGHTS.put(Blocks.REDSTONE_LAMP, 1.0);
        BLOCK_WEIGHTS.put(Blocks.WHITE_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.ORANGE_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.MAGENTA_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.LIGHT_BLUE_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.YELLOW_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.LIME_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.PINK_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.GRAY_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.LIGHT_GRAY_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.CYAN_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.PURPLE_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.BLUE_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.BROWN_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.GREEN_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.RED_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.BLACK_BED, 0.7);
        BLOCK_WEIGHTS.put(Blocks.CRAFTING_TABLE, 0.5);
        BLOCK_WEIGHTS.put(Blocks.FURNACE, 0.5);
        BLOCK_WEIGHTS.put(Blocks.STONECUTTER, 0.5);
        BLOCK_WEIGHTS.put(Blocks.CARTOGRAPHY_TABLE, 0.5);
        BLOCK_WEIGHTS.put(Blocks.SMITHING_TABLE, 0.5);
        BLOCK_WEIGHTS.put(Blocks.FLETCHING_TABLE, 0.5);
        BLOCK_WEIGHTS.put(Blocks.LOOM, 0.5);
        BLOCK_WEIGHTS.put(Blocks.GRINDSTONE, 0.5);
        BLOCK_WEIGHTS.put(Blocks.ANVIL, 0.5);
        BLOCK_WEIGHTS.put(Blocks.CHIPPED_ANVIL, 0.5);
        BLOCK_WEIGHTS.put(Blocks.DAMAGED_ANVIL, 0.5);
        BLOCK_WEIGHTS.put(Blocks.ENCHANTING_TABLE, 0.5);
        BLOCK_WEIGHTS.put(Blocks.BREWING_STAND, 0.5);
        BLOCK_WEIGHTS.put(Blocks.BLAST_FURNACE, 0.5);
        BLOCK_WEIGHTS.put(Blocks.SMOKER, 0.5);
        BLOCK_WEIGHTS.put(Blocks.LECTERN, 0.5);
        BLOCK_WEIGHTS.put(Blocks.CHEST, 0.4);
        BLOCK_WEIGHTS.put(Blocks.TRAPPED_CHEST, 0.4);
        BLOCK_WEIGHTS.put(Blocks.ENDER_CHEST, 0.4);
        BLOCK_WEIGHTS.put(Blocks.BARREL, 0.4);
        BLOCK_WEIGHTS.put(Blocks.SHULKER_BOX, 0.4);
        BLOCK_WEIGHTS.put(Blocks.FARMLAND, 0.3);
        BLOCK_WEIGHTS.put(Blocks.COMPOSTER, 0.3);
        BLOCK_WEIGHTS.put(Blocks.BEEHIVE, 0.3);
        BLOCK_WEIGHTS.put(Blocks.BEE_NEST, 0.3);
    }

    private BlockCivilization() {
    }

    /** Non-air counts as 1, air counts as 0; used for corner suspicion and priority curve. */
    public static int suspect(BlockState state) {
        return state.isAir() ? 0 : 1;
    }

    /** Single block civilization weight [0, 5], not normalized; normalization handled by operators using NORMALIZATION_FACTOR etc. */
    public static double getBlockWeight(Block block) {
        return BLOCK_WEIGHTS.getOrDefault(block, 0.0);
    }

    /** Whether it is a monster head (non-player head). */
    public static boolean isMonsterHead(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof AbstractSkullBlock skull)) return false;
        return skull.getSkullType() != SkullBlock.Type.PLAYER;
    }

    /**
     * Whether this block state is a "target" for civilization scanning:
     * positive civilization weight OR a monster head.
     * Used as the predicate for {@code ChunkSection.hasAny()} palette pre-filter.
     */
    public static boolean isTargetBlock(BlockState state) {
        return getBlockWeight(state.getBlock()) > 0 || isMonsterHead(state);
    }

    /** Head type → spawnable entity type; dragon head not mapped; PLAYER returns null. */
    public static EntityType<?> skullTypeToEntityType(SkullBlock.SkullType type) {
        if (type == SkullBlock.Type.ZOMBIE) return EntityType.ZOMBIE;
        if (type == SkullBlock.Type.SKELETON) return EntityType.SKELETON;
        if (type == SkullBlock.Type.WITHER_SKELETON) return EntityType.WITHER_SKELETON;
        if (type == SkullBlock.Type.CREEPER) return EntityType.CREEPER;
        if (type == SkullBlock.Type.PIGLIN) return EntityType.PIGLIN;
        return null;
    }
}
