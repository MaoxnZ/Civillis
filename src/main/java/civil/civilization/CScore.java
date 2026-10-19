package civil.civilization;

/**
 * Civilization score for a single position or voxel chunk.
 *
 * <p>Score is in [0, 1]: 0 = completely wild, 1 = highly civilized.
 *
 * <p>Monster head presence is handled separately by {@link HeadTracker}
 * and queried directly in SpawnPolicy / CivilDetectorItem.
 */
public record CScore(double score) {
}
