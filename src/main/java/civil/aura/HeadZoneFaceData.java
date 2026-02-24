package civil.aura;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Boundary face for a mob head "Force Allow" zone (3×3×1 VC neighborhood).
 *
 * <p>Similar to {@link BoundaryFaceData} but carries per-face vertical extent
 * ({@code faceMinY/faceMaxY}) because different heads may sit at different Y levels,
 * producing zones of different heights.
 *
 * <p>Vertical extent includes ±8 blocks padding around the head's voxel chunk
 * (16 blocks) for a total of 32 blocks visible height per head Y-level.
 *
 * @param axis         0 = X, 2 = Z
 * @param planeCoord   world coordinate of the boundary plane (block edge)
 * @param minU         face rectangle min U (16-wide; Z for X-axis, X for Z-axis)
 * @param positiveDir  true if the "outside" is in the positive axis direction
 * @param faceMinY     bottom of this face's vertical extent (world Y)
 * @param faceMaxY     top of this face's vertical extent (world Y)
 */
public record HeadZoneFaceData(
        byte axis,
        double planeCoord,
        double minU,
        boolean positiveDir,
        double faceMinY,
        double faceMaxY
) {

    /** Serialize to a network buffer. */
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(axis);
        buf.writeDouble(planeCoord);
        buf.writeDouble(minU);
        buf.writeBoolean(positiveDir);
        buf.writeDouble(faceMinY);
        buf.writeDouble(faceMaxY);
    }

    /** Deserialize from a network buffer. */
    public static HeadZoneFaceData read(RegistryFriendlyByteBuf buf) {
        return new HeadZoneFaceData(
                buf.readByte(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readDouble()
        );
    }
}
