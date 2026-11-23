package civil.civilization;

import civil.registry.TownCenterLevelRegistry;
import net.minecraft.core.BlockPos;

/**
 * VC-aligned AABB for a town center influence region (symmetric around anchor VC).
 */
public final class TownCenterAabb {

    private final VoxelChunkKey minVc;
    private final VoxelChunkKey maxVc;

    private TownCenterAabb(VoxelChunkKey minVc, VoxelChunkKey maxVc) {
        this.minVc = minVc;
        this.maxVc = maxVc;
    }

    public static TownCenterAabb fromLectern(VoxelChunkKey anchor, int horizRadius, int vertRadius) {
        return new TownCenterAabb(
                anchor.offset(-horizRadius, -horizRadius, -vertRadius),
                anchor.offset(horizRadius, horizRadius, vertRadius));
    }

    public static TownCenterAabb atLectern(BlockPos lecternPos, int level) {
        VoxelChunkKey anchor = VoxelChunkKey.from(lecternPos);
        int horiz = TownCenterLevelRegistry.getStepForCurrentLevel(level)
                .map(s -> s.upgrade().horizRadiusVc())
                .orElse(5);
        int vert = TownCenterLevelRegistry.getStepForCurrentLevel(level)
                .map(s -> s.upgrade().vertRadiusVc())
                .orElse(1);
        return fromLectern(anchor, horiz, vert);
    }

    public VoxelChunkKey minVc() {
        return minVc;
    }

    public VoxelChunkKey maxVc() {
        return maxVc;
    }

    public boolean contains(VoxelChunkKey vc) {
        return vc.getCx() >= minVc.getCx() && vc.getCx() <= maxVc.getCx()
                && vc.getCz() >= minVc.getCz() && vc.getCz() <= maxVc.getCz()
                && vc.getSy() >= minVc.getSy() && vc.getSy() <= maxVc.getSy();
    }

    public boolean contains(BlockPos pos) {
        return contains(VoxelChunkKey.from(pos));
    }

    /** Union bounding box of two regions (for upgrade / update refresh). */
    public static TownCenterAabb union(TownCenterAabb a, TownCenterAabb b) {
        if (a == null) return b;
        if (b == null) return a;
        return new TownCenterAabb(
                new VoxelChunkKey(
                        Math.min(a.minVc.getCx(), b.minVc.getCx()),
                        Math.min(a.minVc.getCz(), b.minVc.getCz()),
                        Math.min(a.minVc.getSy(), b.minVc.getSy())),
                new VoxelChunkKey(
                        Math.max(a.maxVc.getCx(), b.maxVc.getCx()),
                        Math.max(a.maxVc.getCz(), b.maxVc.getCz()),
                        Math.max(a.maxVc.getSy(), b.maxVc.getSy())));
    }
}
