package civil.aura;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Boundary face for a sonar-visible overlay volume on the current scan layer.
 *
 * <p>Similar to {@link BoundaryFaceData} but carries per-face vertical extent
 * ({@code faceMinY/faceMaxY}) because zone/shrine overlays are transmitted as
 * scan-layer-derived vertical bands with their own padding, rather than using the
 * payload-wide civilization wall height.
 *
 * @param axis         0 = X, 2 = Z
 * @param planeCoord   world coordinate of the boundary plane (block edge)
 * @param minU         face rectangle min U (16-wide; Z for X-axis, X for Z-axis)
 * @param positiveDir  true if the "outside" is in the positive axis direction
 * @param faceMinY     bottom of this face's vertical extent (world Y)
 * @param faceMaxY     top of this face's vertical extent (world Y)
 */
public record ShrineFaceData(
        byte axis,
        double planeCoord,
        double minU,
        boolean positiveDir,
        double faceMinY,
        double faceMaxY
) {

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(axis);
        buf.writeDouble(planeCoord);
        buf.writeDouble(minU);
        buf.writeBoolean(positiveDir);
        buf.writeDouble(faceMinY);
        buf.writeDouble(faceMaxY);
    }

    public static ShrineFaceData read(RegistryFriendlyByteBuf buf) {
        return new ShrineFaceData(
                buf.readByte(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readDouble()
        );
    }
}
