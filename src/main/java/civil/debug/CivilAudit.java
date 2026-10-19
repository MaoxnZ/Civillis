package civil.debug;

import civil.CivilMod;
import civil.civilization.VoxelChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Lightweight debug audit filter for reproducibility tests.
 *
 * <p>Scopes verbose logs to a bounded VC area around the user's test zone,
 * so we can inspect cache/flush/setBlock behavior without flooding logs.
 */
public final class CivilAudit {

    private CivilAudit() {}

    /** Master switch for audit logs (requires CivilMod.DEBUG=true). */
    public static final boolean ENABLED = CivilMod.DEBUG && true;

    /**
     * Global anomaly mode (non-hardcoded): log suspicious transitions regardless
     * of fixed watch coordinates.
     */
    public static final boolean AUTO_ANOMALY_ENABLED = ENABLED;
    public static final double AUTO_DELTA_ALERT = 0.30;
    public static final double AUTO_SUSPICIOUS_JUMP = 0.60;
    private static final int AUTO_CONTEXT_BUFFER_SIZE = 240;
    private static final int AUTO_CONTEXT_DUMP_SIZE = 60;
    private static final Deque<String> AUTO_CONTEXT = new ArrayDeque<>(AUTO_CONTEXT_BUFFER_SIZE);

    // Default watch window: the currently investigated area.
    private static final String WATCH_DIM = "minecraft:overworld";
    private static final int MIN_CX = -14;
    private static final int MAX_CX = -6;
    private static final int MIN_CZ = 15;
    private static final int MAX_CZ = 25;
    private static final int WATCH_SY = -4;

    public static boolean isWatched(String dim, VoxelChunkKey key) {
        if (!ENABLED) return false;
        if (!WATCH_DIM.equals(dim)) return false;
        return key.getCx() >= MIN_CX && key.getCx() <= MAX_CX
                && key.getCz() >= MIN_CZ && key.getCz() <= MAX_CZ
                && key.getSy() == WATCH_SY;
    }

    public static boolean isWatched(ServerLevel world, BlockPos pos) {
        String dim = world.dimension().identifier().toString();
        return isWatched(dim, VoxelChunkKey.from(pos));
    }

    public static String watchDim() { return WATCH_DIM; }
    public static int minCx() { return MIN_CX; }
    public static int maxCx() { return MAX_CX; }
    public static int minCz() { return MIN_CZ; }
    public static int maxCz() { return MAX_CZ; }
    public static int watchSy() { return WATCH_SY; }

    public static boolean isAnomalousTransition(double oldScore, double newScore, double delta) {
        if (!AUTO_ANOMALY_ENABLED) return false;
        if (Math.abs(delta) >= AUTO_DELTA_ALERT) return true;
        return oldScore <= 0.25 && newScore >= AUTO_SUSPICIOUS_JUMP;
    }

    public static boolean isNonCivilBlockChange(BlockState oldState, BlockState newState) {
        return !civil.civilization.BlockScanner.isTargetBlock(oldState)
                && !civil.civilization.BlockScanner.isTargetBlock(newState);
    }

    public static void recordAutoContext(String line) {
        if (!AUTO_ANOMALY_ENABLED || line == null || line.isEmpty()) return;
        synchronized (AUTO_CONTEXT) {
            if (AUTO_CONTEXT.size() >= AUTO_CONTEXT_BUFFER_SIZE) {
                AUTO_CONTEXT.removeFirst();
            }
            AUTO_CONTEXT.addLast(line);
        }
    }

    public static List<String> snapshotRecentAutoContext() {
        if (!AUTO_ANOMALY_ENABLED) return List.of();
        synchronized (AUTO_CONTEXT) {
            int size = AUTO_CONTEXT.size();
            int skip = Math.max(0, size - AUTO_CONTEXT_DUMP_SIZE);
            List<String> out = new ArrayList<>(Math.min(size, AUTO_CONTEXT_DUMP_SIZE));
            int i = 0;
            for (String s : AUTO_CONTEXT) {
                if (i++ < skip) continue;
                out.add(s);
            }
            return out;
        }
    }
}
