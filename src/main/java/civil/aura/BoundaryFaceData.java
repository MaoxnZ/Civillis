package civil.aura;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Lightweight boundary face data for client-side rendering.
 *
 * <p>Contains only numeric values needed to draw the wall quad — no references
 * to server-side classes like {@link civil.civilization.VoxelChunkKey}.
 *
 * <p>Only X and Z axis faces are transmitted (vertical walls). The vertical extent
 * is defined globally per payload ({@code wallMinY/wallMaxY}) rather than per face,
 * because the BFS operates in 2D (XZ plane only) and the wall is projected vertically.
 *
 * @param axis         0 = X, 2 = Z (maps to {@link net.minecraft.util.math.Direction.Axis} ordinal)
 * @param planeCoord   world coordinate of the boundary plane (block edge)
 * @param minU         face rectangle min U (16-wide; Z for X-axis, X for Z-axis)
 * @param positiveDir  true if the LOW side is in the positive axis direction
 */
public record BoundaryFaceData(
        byte axis,
        double planeCoord,
        double minU,
        boolean positiveDir
) {

    /**
     * Convert a server-side {@link BoundaryFace} to a client-friendly data record.
     * Only call on the server (references VoxelChunkKey).
     */
    public static BoundaryFaceData fromBoundaryFace(BoundaryFace face) {
        double[] uv = face.faceMinUV();
        return new BoundaryFaceData(
                (byte) face.axis().ordinal(),
                face.planeCoordinate(),
                uv[0],
                face.positiveDirection()
        );
    }

    /** Serialize to a network buffer. */
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(axis);
        buf.writeDouble(planeCoord);
        buf.writeDouble(minU);
        buf.writeBoolean(positiveDir);
    }

    /** Deserialize from a network buffer. */
    public static BoundaryFaceData read(RegistryFriendlyByteBuf buf) {
        return new BoundaryFaceData(
                buf.readByte(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean()
        );
    }
}
