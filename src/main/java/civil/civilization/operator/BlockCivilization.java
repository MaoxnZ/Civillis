package civil.civilization.operator;

import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SkullBlock;
import net.minecraft.entity.EntityType;

/**
 * Single block civilization judgment: suspicion (non-air=1), weight, monster head, head type.
 * Only does block-level judgment, does not normalize or aggregate; normalization is defined by each operator.
 */
public final class BlockCivilization {

    private BlockCivilization() {
    }

    /** Non-air counts as 1, air counts as 0; used for corner suspicion and priority curve. */
    public static int suspect(BlockState state) {
        return state.isAir() ? 0 : 1;
    }

    /** Single block civilization weight [0, 5], not normalized; normalization handled by operators using NORMALIZATION_FACTOR etc. */
    public static double getBlockWeight(Block block) {
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) return 0.0;
        if (block == Blocks.BEACON || block == Blocks.CONDUIT) return 5.0;
        if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN
                || block == Blocks.SEA_LANTERN || block == Blocks.REDSTONE_LAMP) return 1.0;
        if (block == Blocks.WHITE_BED || block == Blocks.ORANGE_BED || block == Blocks.MAGENTA_BED
                || block == Blocks.LIGHT_BLUE_BED || block == Blocks.YELLOW_BED || block == Blocks.LIME_BED
                || block == Blocks.PINK_BED || block == Blocks.GRAY_BED || block == Blocks.LIGHT_GRAY_BED
                || block == Blocks.CYAN_BED || block == Blocks.PURPLE_BED || block == Blocks.BLUE_BED
                || block == Blocks.BROWN_BED || block == Blocks.GREEN_BED || block == Blocks.RED_BED
                || block == Blocks.BLACK_BED) return 0.7;
        if (block == Blocks.CRAFTING_TABLE || block == Blocks.FURNACE || block == Blocks.STONECUTTER
                || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.SMITHING_TABLE
                || block == Blocks.FLETCHING_TABLE || block == Blocks.LOOM || block == Blocks.GRINDSTONE
                || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL
                || block == Blocks.ENCHANTING_TABLE || block == Blocks.BREWING_STAND
                || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER || block == Blocks.LECTERN) return 0.5;
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST
                || block == Blocks.BARREL || block == Blocks.SHULKER_BOX) return 0.4;
        if (block == Blocks.FARMLAND || block == Blocks.COMPOSTER || block == Blocks.BEEHIVE
                || block == Blocks.BEE_NEST) return 0.3;
        return 0.0;
    }

    /** Whether it is a monster head (non-player head). */
    public static boolean isMonsterHead(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof AbstractSkullBlock skull)) return false;
        return skull.getSkullType() != SkullBlock.Type.PLAYER;
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
