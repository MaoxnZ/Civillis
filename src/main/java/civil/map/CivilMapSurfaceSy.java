package civil.map;

import civil.CivilMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

/**
 * Representative {@code sy} (16-block vertical slice) for surface in a world chunk, using heightmaps and
 * {@code getChunkNow} only. Uses a fixed 4×4 subsample (16 columns at {@code dx,dz ∈ {0,5,10,15}}), not full 16×16.
 */
public final class CivilMapSurfaceSy {

    /** Full-span 4×4 grid in chunk-local XZ: corners through opposite corner ({@code 0,5,10,15}, 16 samples). */
    private static final int[] CHUNK_SUBSAMPLE_DXZ = {0, 5, 10, 15};

    /** Why {@link #representativeSy} returned empty; used for DEBUG aggregation only. */
    public enum SyMissingCause {
        /** Sy was computed successfully. */
        NONE,
        CHUNK_NOT_LOADED,
        INVALID_DIMENSION_SPAN,
        NO_SURFACE_SAMPLES
    }

    public record SurfaceSyComputation(OptionalInt sy, SyMissingCause missingCause) {
    }

    /**
     * Aggregates raw {@link LevelChunk#getHeight} values across sampled columns (one recompute). When
     * {@link CivilMod#DEBUG}, pass one instance through all {@link #compute} calls, then {@link #logSummary}.
     */
    public static final class WorldSurfaceHeightStats {

        private long n;
        private int minTopWs = Integer.MAX_VALUE;
        private int maxTopWs = Integer.MIN_VALUE;
        private long sumTopWs;
        private int wsYBelowMin;
        private int wsYAboveMax;
        private int motionBlockFallbackColumns;
        private int minYFinal = Integer.MAX_VALUE;
        private int maxYFinal = Integer.MIN_VALUE;
        private String firstChunkClass;

        public void noteChunkIfFirst(LevelChunk chunk) {
            if (firstChunkClass == null) {
                firstChunkClass = chunk.getClass().getSimpleName();
            }
        }

        public void addColumn(
                int dimMinY,
                int dimMaxY,
                int topWorldSurface,
                int spawnLayerWorldSurface,
                boolean usedMotionBlockingFallback,
                int topMotionBlocking,
                int spawnLayerMotionBlocking,
                int yFinal) {
            n++;
            minTopWs = Math.min(minTopWs, topWorldSurface);
            maxTopWs = Math.max(maxTopWs, topWorldSurface);
            sumTopWs += topWorldSurface;
            if (spawnLayerWorldSurface < dimMinY) {
                wsYBelowMin++;
            }
            if (spawnLayerWorldSurface > dimMaxY) {
                wsYAboveMax++;
            }
            if (usedMotionBlockingFallback) {
                motionBlockFallbackColumns++;
            }
            minYFinal = Math.min(minYFinal, yFinal);
            maxYFinal = Math.max(maxYFinal, yFinal);
        }

        public void logSummary(int mapId, int dimMinY, int dimMaxY) {
            if (!CivilMod.DEBUG || n == 0) {
                return;
            }
            double meanTopWs = sumTopWs / (double) n;
            CivilMod.LOGGER.info(
                    "[civil-map-trace] "
                            + "heightmapRaw mapId={} dimY=[{},{}] firstChunkClass={} columns={} "
                            + "WORLD_SURFACE top: min={} max={} mean={} | spawnLayerY(=top) belowMinY={} aboveMaxY={} "
                            + "| motionBlockFallbackColumns={} | finalY(afterClamp) min={} max={}",
                    mapId,
                    dimMinY,
                    dimMaxY,
                    firstChunkClass != null ? firstChunkClass : "?",
                    n,
                    minTopWs,
                    maxTopWs,
                    meanTopWs,
                    wsYBelowMin,
                    wsYAboveMax,
                    motionBlockFallbackColumns,
                    minYFinal,
                    maxYFinal);
        }
    }

    private CivilMapSurfaceSy() {
    }

    public static SurfaceSyComputation compute(ServerLevel level, int cx, int cz) {
        return compute(level, cx, cz, null);
    }

    /**
     * @param heightStats when non-null and {@link CivilMod#DEBUG}, records raw heightmap tops per column
     *                    for {@link WorldSurfaceHeightStats#logSummary(int, int, int)} after the map grid loop.
     */
    public static SurfaceSyComputation compute(
            ServerLevel level, int cx, int cz, WorldSurfaceHeightStats heightStats) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) {
            return new SurfaceSyComputation(OptionalInt.empty(), SyMissingCause.CHUNK_NOT_LOADED);
        }
        if (heightStats != null) {
            heightStats.noteChunkIfFirst(chunk);
        }
        var dimType = level.dimensionType();
        int dimMinY = dimType.minY();
        int dimMaxY = dimMinY + dimType.height() - 1;
        int syMin = Math.floorDiv(dimMinY, 16);
        int syMax = Math.floorDiv(dimMaxY, 16);
        int span = syMax - syMin + 1;
        if (span <= 0) {
            return new SurfaceSyComputation(OptionalInt.empty(), SyMissingCause.INVALID_DIMENSION_SPAN);
        }
        int[] count = new int[span];
        for (int dx : CHUNK_SUBSAMPLE_DXZ) {
            for (int dz : CHUNK_SUBSAMPLE_DXZ) {
                int surfaceY = surfaceYForColumn(chunk, dimMinY, dimMaxY, dx, dz, heightStats);
                int sy = Math.floorDiv(surfaceY, 16);
                int idx = sy - syMin;
                if (idx >= 0 && idx < span) {
                    count[idx]++;
                }
            }
        }
        int bestCount = 0;
        for (int i = 0; i < span; i++) {
            if (count[i] > bestCount) {
                bestCount = count[i];
            }
        }
        if (bestCount <= 0) {
            return new SurfaceSyComputation(OptionalInt.empty(), SyMissingCause.NO_SURFACE_SAMPLES);
        }
        List<Integer> ties = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            if (count[i] == bestCount) {
                ties.add(syMin + i);
            }
        }
        Collections.sort(ties);
        int chosenSy = ties.get(ties.size() / 2);
        return new SurfaceSyComputation(OptionalInt.of(chosenSy), SyMissingCause.NONE);
    }

    public static OptionalInt representativeSy(ServerLevel level, int cx, int cz) {
        return compute(level, cx, cz).sy();
    }

    /**
     * Picks a world Y for representative {@code sy} voting: {@link Heightmap.Types#WORLD_SURFACE} {@code top},
     * then {@link Heightmap.Types#MOTION_BLOCKING} {@code top} if that spawn layer Y is outside the dimension bounds,
     * then clamps to {@code [dimMinY, dimMaxY]}.
     *
     * <p>Uses {@code top} (not {@code top - 1}) so the chosen vertical slice aligns with typical natural-spawn
     * {@link net.minecraft.world.entity.Entity#blockPosition()} semantics on flat ground (feet in the air cell
     * above the top solid block), avoiding an off-by-one {@code sy} at 16-block boundaries versus spawn checks.
     * <p>
     * Clamping is <strong>defensive</strong> (avoids dropping every column). Interpretation of Mojang heightmap
     * semantics must rely on {@link WorldSurfaceHeightStats} logs when {@link CivilMod#DEBUG}, not on clamp
     * alone.
     */
    private static int surfaceYForColumn(
            LevelChunk chunk,
            int dimMinY,
            int dimMaxY,
            int dx,
            int dz,
            WorldSurfaceHeightStats stats) {
        int topWs = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz) + 1;
        int yWs = topWs;
        boolean usedMb = false;
        int topMb = topWs;
        int yMb = topWs;
        if (yWs < dimMinY || yWs > dimMaxY) {
            usedMb = true;
            topMb = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, dx, dz);
            yMb = topMb;
        }
        int yBeforeClamp = usedMb ? yMb : yWs;
        int yFinal = Math.max(dimMinY, Math.min(dimMaxY, yBeforeClamp));
        if (stats != null) {
            stats.addColumn(dimMinY, dimMaxY, topWs, yWs, usedMb, topMb, yMb, yFinal);
        }
        return yFinal;
    }
}
