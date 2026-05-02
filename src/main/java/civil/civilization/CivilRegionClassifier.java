package civil.civilization;

import civil.CivilServices;
import civil.config.CivilConfig;
import civil.registry.DimensionPolicyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Single source of truth for region priority: SHRINE &gt; ZONE &gt; HIGH &gt; NONE.
 */
public final class CivilRegionClassifier {

    private CivilRegionClassifier() {
    }

    public enum ScoreUnknownReason {
        NULL_CSCORE,
        LOOKUP_EXCEPTION
    }

    public record ClassifyResult(CivilRegionKind kind, @Nullable ScoreUnknownReason scoreUnknownReason) {}

    public static ClassifyResult classify(ServerLevel level, int cx, int cz, int sy) {
        VoxelChunkKey vc = new VoxelChunkKey(cx, cz, sy);
        BlockPos pos = resolveSamplePos(level, vc);
        return classifyWithPos(level, vc, pos);
    }

    public static ClassifyResult classify(ServerLevel level, VoxelChunkKey vc) {
        return classifyWithPos(level, vc, resolveSamplePos(level, vc));
    }

    private static BlockPos resolveSamplePos(ServerLevel level, VoxelChunkKey vc) {
        int dimMinY = level.dimensionType().minY();
        int dimMaxY = dimMinY + level.dimensionType().height() - 1;
        int yIdeal = vc.getSy() * 16 + 8;
        int y = Math.max(dimMinY, Math.min(dimMaxY, yIdeal));
        return new BlockPos(vc.getCx() * 16 + 8, y, vc.getCz() * 16 + 8);
    }

    private static ClassifyResult classifyWithPos(ServerLevel level, VoxelChunkKey vc, BlockPos pos) {
        if (!DimensionPolicyRegistry.policyFor(level).civilization()) {
            return new ClassifyResult(CivilRegionKind.NONE, null);
        }
        if (DimensionPolicyRegistry.policyFor(level).headMechanics()) {
            FarmShrineTracker shrines = CivilServices.getFarmShrineTracker();
            if (shrines != null && shrines.isInitialized()) {
                String dim = level.dimension().identifier().toString();
                if (shrines.isInsideAnyBypassBox(dim, pos)) {
                    return new ClassifyResult(CivilRegionKind.SHRINE, null);
                }
            }
        }
        ZonePolicyService zps = CivilServices.getZonePolicyService();
        if (zps != null && zps.treatAsNonCivilized(level, vc)) {
            return new ClassifyResult(CivilRegionKind.ZONE, null);
        }
        CScore cScore;
        var civ = CivilServices.getCivilizationService();
        if (civ == null) {
            return new ClassifyResult(CivilRegionKind.NONE, ScoreUnknownReason.NULL_CSCORE);
        }
        try {
            cScore = civ.getCScoreAt(level, pos);
        } catch (Exception e) {
            return new ClassifyResult(CivilRegionKind.NONE, ScoreUnknownReason.LOOKUP_EXCEPTION);
        }
        if (cScore == null) {
            return new ClassifyResult(CivilRegionKind.NONE, ScoreUnknownReason.NULL_CSCORE);
        }
        if (cScore.score() < CivilConfig.spawnThresholdMid) {
            return new ClassifyResult(CivilRegionKind.NONE, null);
        }
        return new ClassifyResult(CivilRegionKind.HIGH, null);
    }
}
