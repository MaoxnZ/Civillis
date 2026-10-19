package civil.aura;

import civil.civilization.VoxelChunkKey;
import net.minecraft.core.Direction;

/**
 * A boundary face between a HIGH-score chunk and a LOW-score chunk.
 *
 * <p>The face lies on the shared plane between {@code highSide} and {@code lowSide}.
 * {@code axis} indicates which axis the boundary is perpendicular to, and
 * {@code positiveDirection} is true if lowSide is in the positive direction
 * along that axis relative to highSide.
 *
 * <p>Used by {@link SonarScan} to record discovered protection zone boundaries.
 */
public record BoundaryFace(
        VoxelChunkKey highSide,
        VoxelChunkKey lowSide,
        Direction.Axis axis,
        boolean positiveDirection
) {

    /**
     * Get the world coordinate of the boundary plane.
     *
     * <p>For X axis: the plane is at highSide.blockMaxX + 0.5 or highSide.blockMinX - 0.5
     * depending on direction. This returns the exact block-edge coordinate.
     */
    public double planeCoordinate() {
        return switch (axis) {
            case X -> positiveDirection
                    ? (highSide.getCx() + 1) * 16.0   // boundary at right edge of highSide
                    : highSide.getCx() * 16.0;          // boundary at left edge of highSide
            case Z -> positiveDirection
                    ? (highSide.getCz() + 1) * 16.0
                    : highSide.getCz() * 16.0;
            case Y -> positiveDirection
                    ? (highSide.getSy() + 1) * 16.0
                    : highSide.getSy() * 16.0;
        };
    }

    /**
     * Get the minimum world coordinates of the face rectangle (U, V axes).
     * Returns [minU, minV] where U and V are the two axes perpendicular to the boundary axis.
     */
    public double[] faceMinUV() {
        return switch (axis) {
            case X -> new double[]{
                    Math.min(highSide.getCz(), lowSide.getCz()) * 16.0,
                    Math.min(highSide.getSy(), lowSide.getSy()) * 16.0
            };
            case Z -> new double[]{
                    Math.min(highSide.getCx(), lowSide.getCx()) * 16.0,
                    Math.min(highSide.getSy(), lowSide.getSy()) * 16.0
            };
            case Y -> new double[]{
                    Math.min(highSide.getCx(), lowSide.getCx()) * 16.0,
                    Math.min(highSide.getCz(), lowSide.getCz()) * 16.0
            };
        };
    }
}
