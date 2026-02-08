package civil.civilization.operator;

import civil.civilization.structure.VoxelRegion;

/**
 * Civilization operator: based on neighborhood voxel data, outputs civilization score; optionally writes head types etc. via {@link CivilComputeContext}.
 */
public interface CivilizationOperator {

    /**
     * Calculate the civilization contribution of the given neighborhood, convention range [0, 1].
     * Monster head info should be written to {@link CivilComputeContext} if available, not encoded in the score.
     */
    double computeScore(VoxelRegion region);

    /**
     * Computation with context; defaults to delegating to {@link #computeScore(VoxelRegion)}, does not write to context.
     * Implementation can write head types in the neighborhood to context, used for matching type spawn bonuses.
     */
    default double computeScore(VoxelRegion region, CivilComputeContext context) {
        return computeScore(region);
    }
}
