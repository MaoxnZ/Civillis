package civil.civilization.storage;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * H2 database storage layer: cache persistence with async I/O support.
 * 
 * <p>Features:
 * <ul>
 *   <li>Async read/write, does not block the main thread</li>
 *   <li>Supports batch operations</li>
 *   <li>Exact key lookups with range query support</li>
 * </ul>
 * 
 * <p>Table schema:
 * <ul>
 *   <li>civil_meta: schema version and metadata</li>
 *   <li>l1_cache: L1 information shards (single voxel chunk scores + decay state)</li>
 *   <li>mob_heads: registered mob head positions for attraction system</li>
 * </ul>
 *
 * <p>Legacy tables {@code l2_cache} and {@code l3_cache} may still exist in older
 * worlds but are no longer read or written by the Fusion Architecture.
 */
public final class H2Storage {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-storage");
    private static final String DB_NAME = "civil_cache";

    /**
     * Current schema version.
     * Increment this when making breaking changes to table structure,
     * and add a corresponding migration case in {@link #runMigrations(int)}.
     */
    static final int CURRENT_SCHEMA_VERSION = 5;

    /**
     * Declarative column registry: columns that may not exist in older databases.
     *
     * <p>The introspective ensure pass checks each of these against the actual
     * database schema on every startup, adding any that are missing. This is
     * independent of {@link #CURRENT_SCHEMA_VERSION} and self-healing.
     *
     * <p>When adding a new column to any table, add it here as well so that
     * existing worlds are automatically repaired regardless of their schema version.
     */
    private static final List<ExpectedColumn> EXPECTED_COLUMNS = List.of(
            // Fusion Architecture v4: persist decay state in l1_cache
            new ExpectedColumn("L1_CACHE", "PRESENCE_TIME", "BIGINT", "0"),
            new ExpectedColumn("L1_CACHE", "LAST_RECOVERY_TIME", "BIGINT", "0")
    );

    /** Describes a column that must exist in a given table. */
    private record ExpectedColumn(String table, String column, String sqlType, String defaultValue) {}

    private Connection connection;
    private final ExecutorService ioExecutor;
    private volatile boolean closed = false;

    public H2Storage() {
        // Use 2 threads for async I/O
        this.ioExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Civil-Storage-IO");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialize the database connection.
     * 
     * @param world server world (used to determine storage path)
     */
    public void initialize(ServerWorld world) {
        try {
            // Database file placed under the world save directory
            String dbPath = world.getServer()
                    .getSavePath(WorldSavePath.ROOT)
                    .resolve(DB_NAME)
                    .toAbsolutePath()
                    .toString();

            // H2 connection URL
            // MODE=MySQL provides better compatibility
            // AUTO_SERVER=TRUE allows multi-process access (though we don't need it)
            String url = "jdbc:h2:" + dbPath + ";MODE=MySQL;AUTO_RECONNECT=TRUE";

            connection = DriverManager.getConnection(url);

            // Schema version management: create meta table, run migrations if needed
            initializeSchema();

            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-storage] H2 database initialized: {} (schema v{})", dbPath, CURRENT_SCHEMA_VERSION);
            }
        } catch (SQLException e) {
            LOGGER.error("[civil-storage] H2 database initialization failed", e);
            throw new RuntimeException("Failed to initialize H2 database", e);
        }
    }

    // ========== Schema version management ==========

    /**
     * Initialize schema: create meta table, detect version, create or migrate tables.
     */
    private void initializeSchema() throws SQLException {
        createMetaTable();

        int version = getSchemaVersion();

        if (version == 0) {
            // Fresh database: create all tables with current schema
            LOGGER.info("[civil-storage] New database detected, creating schema v{}", CURRENT_SCHEMA_VERSION);
            createTablesV1();
            setSchemaVersion(CURRENT_SCHEMA_VERSION);
        } else if (version < CURRENT_SCHEMA_VERSION) {
            // Existing database with older schema: run migrations
            LOGGER.info("[civil-storage] Schema v{} detected, migrating to v{}", version, CURRENT_SCHEMA_VERSION);
            runMigrations(version);
            setSchemaVersion(CURRENT_SCHEMA_VERSION);
            LOGGER.info("[civil-storage] Schema migration complete (v{} -> v{})", version, CURRENT_SCHEMA_VERSION);
        } else if (version > CURRENT_SCHEMA_VERSION) {
            // Database is from a newer version of the mod (downgrade scenario)
            LOGGER.warn("[civil-storage] Database schema v{} is newer than mod schema v{}. " +
                    "Data may be incompatible if you downgraded the mod.", version, CURRENT_SCHEMA_VERSION);
        } else {
            // Already up to date
            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-storage] Schema v{} is up to date", version);
            }
        }

        // Introspective ensure pass: verify actual table columns match expectations.
        // Self-healing regardless of schema_version — handles intermediate builds,
        // branch switches, version mismatches, or any other schema drift.
        ensureExpectedColumns();

        // Table-level self-healing: ensure required tables exist regardless of
        // schema_version. Uses CREATE TABLE IF NOT EXISTS — fully idempotent.
        ensureExpectedTables();
    }

    /**
     * Introspective schema repair: query the actual database metadata and add
     * any columns listed in {@link #EXPECTED_COLUMNS} that are missing.
     *
     * <p>This runs on every startup and is fully idempotent. It does not depend
     * on {@code schema_version} being accurate — it checks reality, not bookkeeping.
     *
     * <p>Performance: one metadata query per table, plus one {@code ALTER TABLE}
     * per missing column. Typically completes in sub-millisecond time.
     */
    private void ensureExpectedColumns() throws SQLException {
        // Collect the distinct tables we need to check
        Set<String> tablesToCheck = new HashSet<>();
        for (ExpectedColumn col : EXPECTED_COLUMNS) {
            tablesToCheck.add(col.table());
        }

        // For each table, load its actual column set from INFORMATION_SCHEMA
        for (String table : tablesToCheck) {
            Set<String> actualColumns = getActualColumns(table);
            if (actualColumns.isEmpty()) {
                // Table doesn't exist yet (should not happen after createTablesV1, but safe to skip)
                continue;
            }

            for (ExpectedColumn col : EXPECTED_COLUMNS) {
                if (!col.table().equals(table)) continue;
                if (actualColumns.contains(col.column())) continue;

                // Column is missing — repair it
                String alterSql = String.format(
                        "ALTER TABLE %s ADD COLUMN %s %s DEFAULT %s",
                        col.table(), col.column(), col.sqlType(), col.defaultValue()
                );
                LOGGER.info("[civil-storage] Schema repair: adding missing column {}.{} ({})",
                        col.table(), col.column(), col.sqlType());
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(alterSql);
                }
            }
        }
    }

    /**
     * Table-level schema repair: ensure all required tables exist, regardless of
     * {@code schema_version}. Uses {@code CREATE TABLE IF NOT EXISTS} — fully
     * idempotent and self-healing.
     *
     * <p>This catches edge cases where the migration ran but the table was not
     * created (intermediate builds, branch switches, etc.).
     */
    private void ensureExpectedTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mob_heads (
                    dim VARCHAR(255) NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    skull_type VARCHAR(64) NOT NULL,
                    PRIMARY KEY (dim, x, y, z)
                )
            """);
        }
    }

    /**
     * Query the actual column names of a table from H2's INFORMATION_SCHEMA.
     *
     * @return set of uppercase column names, or empty set if the table does not exist
     */
    private Set<String> getActualColumns(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }

    /**
     * Create the meta table (always safe to call, idempotent).
     */
    private void createMetaTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS civil_meta (
                    meta_key VARCHAR(64) NOT NULL,
                    meta_value VARCHAR(255) NOT NULL,
                    PRIMARY KEY (meta_key)
                )
            """);
        }
    }

    /**
     * Read current schema version from meta table.
     *
     * @return schema version, or 0 if not set (fresh database)
     */
    private int getSchemaVersion() throws SQLException {
        String sql = "SELECT meta_value FROM civil_meta WHERE meta_key = 'schema_version'";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Integer.parseInt(rs.getString("meta_value"));
            }
        }
        return 0;
    }

    /**
     * Write schema version to meta table.
     */
    private void setSchemaVersion(int version) throws SQLException {
        String sql = """
            MERGE INTO civil_meta (meta_key, meta_value)
            KEY (meta_key)
            VALUES ('schema_version', ?)
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(version));
            ps.executeUpdate();
        }
    }

    /**
     * Run sequential migrations from the given version up to {@link #CURRENT_SCHEMA_VERSION}.
     *
     * <p>Each migration brings the schema from version N to N+1.
     * Add new cases here when incrementing CURRENT_SCHEMA_VERSION.
     *
     * <p>Example for a future v1 -> v2 migration:
     * <pre>{@code
     * case 1:
     *     LOGGER.info("[civil-storage] Migrating v1 -> v2: adding heads column to l1_cache");
     *     stmt.execute("ALTER TABLE l1_cache ADD COLUMN IF NOT EXISTS heads VARCHAR(1024) DEFAULT ''");
     *     break;
     * }</pre>
     */
    private void runMigrations(int fromVersion) throws SQLException {
        boolean wasAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            try (Statement stmt = connection.createStatement()) {
                for (int v = fromVersion; v < CURRENT_SCHEMA_VERSION; v++) {
                    switch (v) {
                        case 1:
                            // v1 -> v2: legacy (originally added presence_time to l2/l3_cache).
                            // L2/L3 tables are retired by Fusion Architecture; this case is
                            // kept as a no-op so that v1 databases can still migrate to v4.
                            break;
                        case 2:
                            // v2 -> v3: create mob_heads table for head attraction system
                            LOGGER.info("[civil-storage] Migrating v2 -> v3: creating mob_heads table");
                            stmt.execute("""
                                CREATE TABLE IF NOT EXISTS mob_heads (
                                    dim VARCHAR(255) NOT NULL,
                                    x INT NOT NULL,
                                    y INT NOT NULL,
                                    z INT NOT NULL,
                                    skull_type VARCHAR(64) NOT NULL,
                                    PRIMARY KEY (dim, x, y, z)
                                )
                            """);
                            break;
                        case 3:
                            // v3 -> v4: persist presenceTime / lastRecoveryTime in l1_cache
                            LOGGER.info("[civil-storage] Migrating v3 -> v4: adding presence_time, last_recovery_time to l1_cache");
                            stmt.execute("ALTER TABLE l1_cache ADD COLUMN IF NOT EXISTS presence_time BIGINT DEFAULT 0");
                            stmt.execute("ALTER TABLE l1_cache ADD COLUMN IF NOT EXISTS last_recovery_time BIGINT DEFAULT 0");
                            break;
                        case 4:
                            // v4 -> v5: storage cleanup
                            // 1. Remove zero-score L1 entries — these are empty sections that
                            //    no longer need persistence (palette re-derives them in ~1μs).
                            //    Typically removes 75-80% of L1 rows.
                            LOGGER.info("[civil-storage] Migrating v4 -> v5: purging zero-score L1 entries");
                            int purged = stmt.executeUpdate("DELETE FROM l1_cache WHERE score = 0.0");
                            LOGGER.info("[civil-storage]   purged {} zero-score L1 entries", purged);
                            // 2. Drop retired L2/L3 tables (Fusion Architecture made them obsolete).
                            stmt.execute("DROP TABLE IF EXISTS l2_cache");
                            stmt.execute("DROP TABLE IF EXISTS l3_cache");
                            LOGGER.info("[civil-storage]   dropped legacy l2_cache, l3_cache tables");
                            break;
                        default:
                            throw new SQLException(
                                "Unknown schema version " + v + ", cannot migrate to v" + CURRENT_SCHEMA_VERSION +
                                ". Database may be from a newer version of the mod.");
                    }
                }
            }
            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(wasAutoCommit); } catch (SQLException ignored) {}
        }
    }

    /**
     * Create all tables for schema v1 (initial release).
     */
    private void createTablesV1() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // L1 cache table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS l1_cache (
                    dim VARCHAR(255) NOT NULL,
                    cx INT NOT NULL,
                    cz INT NOT NULL,
                    sy INT NOT NULL,
                    score DOUBLE NOT NULL,
                    create_time BIGINT NOT NULL,
                    PRIMARY KEY (dim, cx, cz, sy)
                )
            """);

            // L2/L3 cache tables removed — retired by Fusion Architecture.
            // Legacy l2_cache / l3_cache tables may still exist in old worlds but are
            // no longer created for new databases.

            // Indexes for range queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_l1_range ON l1_cache (dim, cx, cz)");
        }
    }

    // ========== L1 operations ==========

    /**
     * Async load L1 entry.
     */
    public CompletableFuture<Optional<StoredL1Entry>> loadL1Async(String dim, VoxelChunkKey key) {
        if (closed) return CompletableFuture.completedFuture(Optional.empty());

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT score, create_time FROM l1_cache WHERE dim=? AND cx=? AND cz=? AND sy=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, key.getCx());
                ps.setInt(3, key.getCz());
                ps.setInt(4, key.getSy());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double score = rs.getDouble("score");
                        long createTime = rs.getLong("create_time");
                        return Optional.of(new StoredL1Entry(key, new CScore(score), createTime));
                    }
                }
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to load L1: {}", e.getMessage());
            }
            return Optional.empty();
        }, ioExecutor);
    }

    /**
     * Async save L1 entry.
     */
    public CompletableFuture<Void> saveL1Async(String dim, VoxelChunkKey key, CScore cScore) {
        if (closed) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String sql = """
                MERGE INTO l1_cache (dim, cx, cz, sy, score, create_time)
                KEY (dim, cx, cz, sy)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, key.getCx());
                ps.setInt(3, key.getCz());
                ps.setInt(4, key.getSy());
                ps.setDouble(5, cScore.score());
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to save L1: {}", e.getMessage());
            }
        }, ioExecutor);
    }

    /**
     * Async batch load L1 (for prefetching).
     */
    public CompletableFuture<List<StoredL1Entry>> loadL1RangeAsync(
            String dim, int minCx, int maxCx, int minCz, int maxCz, int sy) {
        if (closed) return CompletableFuture.completedFuture(List.of());

        return CompletableFuture.supplyAsync(() -> {
            List<StoredL1Entry> results = new ArrayList<>();
            String sql = """
                SELECT cx, cz, score, create_time FROM l1_cache
                WHERE dim=? AND cx BETWEEN ? AND ? AND cz BETWEEN ? AND ? AND sy=?
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, minCx);
                ps.setInt(3, maxCx);
                ps.setInt(4, minCz);
                ps.setInt(5, maxCz);
                ps.setInt(6, sy);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        VoxelChunkKey key = new VoxelChunkKey(rs.getInt("cx"), rs.getInt("cz"), sy);
                        CScore cScore = new CScore(rs.getDouble("score"));
                        long createTime = rs.getLong("create_time");
                        results.add(new StoredL1Entry(key, cScore, createTime));
                    }
                }
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to batch load L1: {}", e.getMessage());
            }
            return results;
        }, ioExecutor);
    }

    // ========== L2/L3 retired (Fusion Architecture) ==========
    // All L2/L3 read/write operations have been removed.
    // Legacy tables may still exist in old worlds but are never accessed.

    // ========== ServerClock persistence ==========

    /**
     * Load the persisted ServerClock value from civil_meta.
     *
     * @return saved millis, or 0 if not present (fresh world)
     */
    public long loadServerClockMillis() {
        String sql = "SELECT meta_value FROM civil_meta WHERE meta_key = 'server_clock_millis'";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Long.parseLong(rs.getString("meta_value"));
            }
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to load server_clock_millis: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Persist the current ServerClock value to civil_meta (synchronous, called on IO thread).
     */
    public CompletableFuture<Void> saveServerClockAsync(long millis) {
        if (closed) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String sql = """
                MERGE INTO civil_meta (meta_key, meta_value)
                KEY (meta_key)
                VALUES ('server_clock_millis', ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, String.valueOf(millis));
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to save server_clock_millis: {}", e.getMessage());
            }
        }, ioExecutor);
    }

    // ========== Mob heads operations ==========

    /**
     * Load all mob head positions from H2. Synchronous — called once at startup.
     *
     * @return list of all stored mob heads (typically 10-100 entries; empty for fresh/upgraded worlds)
     */
    public List<StoredMobHead> loadAllMobHeads() {
        List<StoredMobHead> results = new ArrayList<>();
        String sql = "SELECT dim, x, y, z, skull_type FROM mob_heads";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new StoredMobHead(
                        rs.getString("dim"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("skull_type")
                ));
            }
        } catch (SQLException e) {
            LOGGER.warn("[civil-storage] Failed to load mob heads: {}", e.getMessage());
        }
        return results;
    }

    /**
     * Persist a newly discovered or placed mob head. Async — does not block server thread.
     * Uses MERGE (upsert) so duplicate inserts are harmless.
     */
    public CompletableFuture<Void> saveMobHeadAsync(String dim, int x, int y, int z, String skullType) {
        if (closed) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String sql = """
                MERGE INTO mob_heads (dim, x, y, z, skull_type)
                KEY (dim, x, y, z)
                VALUES (?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.setString(5, skullType);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to save mob head at ({},{},{}): {}", x, y, z, e.getMessage());
            }
        }, ioExecutor);
    }

    /**
     * Remove a mob head that was broken. Async — does not block server thread.
     * No-op if the head does not exist in H2.
     */
    public CompletableFuture<Void> deleteMobHeadAsync(String dim, int x, int y, int z) {
        if (closed) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM mob_heads WHERE dim=? AND x=? AND y=? AND z=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to delete mob head at ({},{},{}): {}", x, y, z, e.getMessage());
            }
        }, ioExecutor);
    }

    // ========== Batch save ==========

    /**
     * Batch save L1 entries (transaction).
     */
    public CompletableFuture<Void> batchSaveL1Async(List<L1SaveRequest> requests) {
        if (closed || requests.isEmpty()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String sql = """
                MERGE INTO l1_cache (dim, cx, cz, sy, score, create_time)
                KEY (dim, cx, cz, sy)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (L1SaveRequest req : requests) {
                        ps.setString(1, req.dim());
                        ps.setInt(2, req.key().getCx());
                        ps.setInt(3, req.key().getCz());
                        ps.setInt(4, req.key().getSy());
                        ps.setDouble(5, req.cScore().score());
                        ps.setLong(6, System.currentTimeMillis());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException ignored) {}
                LOGGER.warn("[civil-storage] Failed to batch save L1: {}", e.getMessage());
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }, ioExecutor);
    }

    // ========== Lifecycle ==========

    /**
     * Close the database connection.
     */
    public void close() {
        closed = true;

        // Wait for all async tasks to complete
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close database with compaction to reclaim freed pages (purged rows, dropped tables).
        // SHUTDOWN COMPACT rewrites the .mv.db file, then the engine closes internally.
        // Next startup opens the compacted file normally — no special handling needed.
        if (connection != null) {
            try {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SHUTDOWN COMPACT");
                }
                LOGGER.info("[civil-storage] H2 database closed with compaction");
            } catch (SQLException e) {
                // Fallback: close without compaction
                LOGGER.warn("[civil-storage] SHUTDOWN COMPACT failed ({}), closing normally", e.getMessage());
                try {
                    connection.close();
                } catch (SQLException ex) {
                    LOGGER.warn("[civil-storage] Failed to close database: {}", ex.getMessage());
                }
            }
        }
    }

    // doubleArrayToBytes / bytesToDoubleArray removed — only used by retired L2/L3 operations.

    // ========== Fusion Architecture: L1 sync/bulk operations ==========

    /**
     * Synchronous L1 load for chunk-load pre-fill.
     * Called on the main thread when a chunk is loaded.
     *
     * @return the stored score, or null if not in H2
     */
    public Double loadL1Sync(String dim, VoxelChunkKey key) {
        if (closed || connection == null) return null;

        String sql = "SELECT score FROM l1_cache WHERE dim=? AND cx=? AND cz=? AND sy=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, dim);
            ps.setInt(2, key.getCx());
            ps.setInt(3, key.getCz());
            ps.setInt(4, key.getSy());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("score");
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("[civil-storage] Failed to sync-load L1: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Load all L1 entries from H2 (used on server startup for bulk restore).
     *
     * @return list of all stored L1 entries
     */
    public List<StoredL1Entry> loadAllL1() {
        List<StoredL1Entry> results = new ArrayList<>();
        if (closed || connection == null) return results;

        String sql = "SELECT dim, cx, cz, sy, score, create_time FROM l1_cache";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String dim = rs.getString("dim");
                VoxelChunkKey key = new VoxelChunkKey(rs.getInt("cx"), rs.getInt("cz"), rs.getInt("sy"));
                CScore cScore = new CScore(rs.getDouble("score"));
                long createTime = rs.getLong("create_time");
                results.add(new StoredL1Entry(key, cScore, createTime, dim));
            }
        } catch (SQLException e) {
            LOGGER.warn("[civil-storage] Failed to load all L1: {}", e.getMessage());
        }
        return results;
    }

    // ========== Fusion Architecture: presenceTime persistence ==========

    /**
     * Synchronous load of persisted presenceTime for a VC.
     * Called when a ResultEntry is rebuilt from cold (cache miss).
     *
     * @return [presenceTime, lastRecoveryTime], or null if row doesn't exist or columns are 0
     */
    public long[] loadPresenceSync(String dim, VoxelChunkKey key) {
        if (closed || connection == null) return null;

        String sql = "SELECT presence_time, last_recovery_time FROM l1_cache WHERE dim=? AND cx=? AND cz=? AND sy=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, dim);
            ps.setInt(2, key.getCx());
            ps.setInt(3, key.getCz());
            ps.setInt(4, key.getSy());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long pt = rs.getLong("presence_time");
                    long lrt = rs.getLong("last_recovery_time");
                    if (pt > 0) {
                        return new long[]{pt, lrt};
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("[civil-storage] Failed to load presence: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Batch save presenceTime / lastRecoveryTime for active result entries.
     * Called every 30 seconds from TtlCacheService + on shutdown.
     *
     * <p>Uses MERGE so rows that already exist (from L1 save) get updated,
     * and rows that don't exist yet (edge case) get inserted with score=0.
     */
    public CompletableFuture<Void> batchSavePresenceAsync(List<PresenceSaveRequest> requests) {
        if (closed || requests.isEmpty()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String sql = """
                UPDATE l1_cache SET presence_time=?, last_recovery_time=?
                WHERE dim=? AND cx=? AND cz=? AND sy=?
            """;
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (PresenceSaveRequest req : requests) {
                        ps.setLong(1, req.presenceTime());
                        ps.setLong(2, req.lastRecoveryTime());
                        ps.setString(3, req.dim());
                        ps.setInt(4, req.key().getCx());
                        ps.setInt(5, req.key().getCz());
                        ps.setInt(6, req.key().getSy());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException ignored) {}
                LOGGER.warn("[civil-storage] Failed to batch save presence: {}", e.getMessage());
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }, ioExecutor);
    }

    // ========== Stored entry records ==========

    public record StoredL1Entry(VoxelChunkKey key, CScore cScore, long createTime, String dim) {
        /** Legacy constructor without dim for backward compat. */
        public StoredL1Entry(VoxelChunkKey key, CScore cScore, long createTime) {
            this(key, cScore, createTime, null);
        }
    }
    public record L1SaveRequest(String dim, VoxelChunkKey key, CScore cScore) {}
    public record StoredMobHead(String dim, int x, int y, int z, String skullType) {}
    public record PresenceSaveRequest(String dim, VoxelChunkKey key, long presenceTime, long lastRecoveryTime) {}
}
