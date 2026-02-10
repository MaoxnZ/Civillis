package civil.civilization.storage;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.structure.L2Entry;
import civil.civilization.structure.L2Key;
import civil.civilization.structure.L3Entry;
import civil.civilization.structure.L3Key;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
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
 *   <li>l1_cache: L1 cache (single voxel chunk)</li>
 *   <li>l2_cache: L2 cache (3x3x1 voxel chunks)</li>
 *   <li>l3_cache: L3 cache (9x9x3 voxel chunks)</li>
 * </ul>
 */
public final class H2Storage {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-storage");
    private static final String DB_NAME = "civil_cache";

    /**
     * Current schema version.
     * Increment this when making breaking changes to table structure,
     * and add a corresponding migration case in {@link #runMigrations(int)}.
     */
    static final int CURRENT_SCHEMA_VERSION = 3;

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
            new ExpectedColumn("L2_CACHE", "PRESENCE_TIME", "BIGINT", "0"),
            new ExpectedColumn("L3_CACHE", "PRESENCE_TIME", "BIGINT", "0")
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
                            // v1 -> v2: add presence_time column for gradual decay
                            LOGGER.info("[civil-storage] Migrating v1 -> v2: adding presence_time to l2/l3_cache");
                            stmt.execute("ALTER TABLE l2_cache ADD COLUMN IF NOT EXISTS presence_time BIGINT DEFAULT 0");
                            stmt.execute("ALTER TABLE l3_cache ADD COLUMN IF NOT EXISTS presence_time BIGINT DEFAULT 0");
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

            // L2 cache table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS l2_cache (
                    dim VARCHAR(255) NOT NULL,
                    c2x INT NOT NULL,
                    c2z INT NOT NULL,
                    s2y INT NOT NULL,
                    scores BINARY(72) NOT NULL,
                    states BINARY(9) NOT NULL,
                    create_time BIGINT NOT NULL,
                    presence_time BIGINT DEFAULT 0 NOT NULL,
                    PRIMARY KEY (dim, c2x, c2z, s2y)
                )
            """);

            // L3 cache table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS l3_cache (
                    dim VARCHAR(255) NOT NULL,
                    c3x INT NOT NULL,
                    c3z INT NOT NULL,
                    s3y INT NOT NULL,
                    scores BINARY(1944) NOT NULL,
                    states BINARY(243) NOT NULL,
                    create_time BIGINT NOT NULL,
                    presence_time BIGINT DEFAULT 0 NOT NULL,
                    PRIMARY KEY (dim, c3x, c3z, s3y)
                )
            """);

            // Indexes for range queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_l1_range ON l1_cache (dim, cx, cz)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_l2_range ON l2_cache (dim, c2x, c2z)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_l3_range ON l3_cache (dim, c3x, c3z)");
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
                        return Optional.of(new StoredL1Entry(key, new CScore(score, List.of()), createTime));
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
                        CScore cScore = new CScore(rs.getDouble("score"), List.of());
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

    // ========== L2 operations ==========

    /**
     * Async load L2 entry.
     */
    public CompletableFuture<Optional<StoredL2Entry>> loadL2Async(String dim, L2Key key) {
        if (closed) return CompletableFuture.completedFuture(Optional.empty());

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String sql = "SELECT scores, states, create_time, presence_time FROM l2_cache WHERE dim=? AND c2x=? AND c2z=? AND s2y=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, key.getC2x());
                ps.setInt(3, key.getC2z());
                ps.setInt(4, key.getS2y());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        byte[] scoresBytes = rs.getBytes("scores");
                        byte[] states = rs.getBytes("states");
                        long createTime = rs.getLong("create_time");
                        long presenceTime = rs.getLong("presence_time");

                        double[] scores = bytesToDoubleArray(scoresBytes);
                        L2Entry l2Entry = new L2Entry(key, presenceTime);
                        l2Entry.restoreFromArrays(scores, states);

                        if (CivilMod.DEBUG) {
                            long latency = System.currentTimeMillis() - startTime;
                            LOGGER.info("[civil-ttl-io] type=LOAD_L2 dim={} key={},{},{} latency_ms={} hit=true presenceTime={}",
                                    dim, key.getC2x(), key.getC2z(), key.getS2y(), latency, presenceTime);
                        }
                        return Optional.of(new StoredL2Entry(key, l2Entry, createTime));
                    }
                }
                if (CivilMod.DEBUG) {
                    long latency = System.currentTimeMillis() - startTime;
                    LOGGER.info("[civil-ttl-io] type=LOAD_L2 dim={} key={},{},{} latency_ms={} hit=false",
                            dim, key.getC2x(), key.getC2z(), key.getS2y(), latency);
                }
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to load L2: {}", e.getMessage());
            }
            return Optional.empty();
        }, ioExecutor);
    }

    /**
     * Async save L2 entry.
     * 
     * @param lastAccessTime last access time (preserved for hot cache TTL on restore)
     */
    public CompletableFuture<Void> saveL2Async(String dim, L2Key key, L2Entry l2Entry, long lastAccessTime) {
        if (closed) return CompletableFuture.completedFuture(null);

        // Capture presenceTime before submitting to background thread
        long presenceTime = l2Entry.getPresenceTime();

        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            String sql = """
                MERGE INTO l2_cache (dim, c2x, c2z, s2y, scores, states, create_time, presence_time)
                KEY (dim, c2x, c2z, s2y)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, key.getC2x());
                ps.setInt(3, key.getC2z());
                ps.setInt(4, key.getS2y());
                ps.setBytes(5, doubleArrayToBytes(l2Entry.getScoresArray()));
                ps.setBytes(6, l2Entry.getStatesArray());
                ps.setLong(7, lastAccessTime);
                ps.setLong(8, presenceTime);
                ps.executeUpdate();
                
                if (CivilMod.DEBUG) {
                    long latency = System.currentTimeMillis() - startTime;
                    LOGGER.info("[civil-ttl-io] type=SAVE_L2 dim={} key={},{},{} latency_ms={} presenceTime={}",
                            dim, key.getC2x(), key.getC2z(), key.getS2y(), latency, presenceTime);
                }
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to save L2: {}", e.getMessage());
            }
        }, ioExecutor);
    }

    // ========== L3 operations ==========

    /**
     * Async load L3 entry.
     */
    public CompletableFuture<Optional<StoredL3Entry>> loadL3Async(String dim, L3Key key) {
        if (closed) return CompletableFuture.completedFuture(Optional.empty());

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String sql = "SELECT scores, states, create_time, presence_time FROM l3_cache WHERE dim=? AND c3x=? AND c3z=? AND s3y=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, key.getC3x());
                ps.setInt(3, key.getC3z());
                ps.setInt(4, key.getS3y());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        byte[] scoresBytes = rs.getBytes("scores");
                        byte[] states = rs.getBytes("states");
                        long createTime = rs.getLong("create_time");
                        long presenceTime = rs.getLong("presence_time");

                        double[] scores = bytesToDoubleArray(scoresBytes);
                        L3Entry l3Entry = new L3Entry(key, presenceTime);
                        l3Entry.restoreFromArrays(scores, states);

                        if (CivilMod.DEBUG) {
                            long latency = System.currentTimeMillis() - startTime;
                            LOGGER.info("[civil-ttl-io] type=LOAD_L3 dim={} key={},{},{} latency_ms={} hit=true presenceTime={}",
                                    dim, key.getC3x(), key.getC3z(), key.getS3y(), latency, presenceTime);
                        }
                        return Optional.of(new StoredL3Entry(key, l3Entry, createTime));
                    }
                }
                if (CivilMod.DEBUG) {
                    long latency = System.currentTimeMillis() - startTime;
                    LOGGER.info("[civil-ttl-io] type=LOAD_L3 dim={} key={},{},{} latency_ms={} hit=false",
                            dim, key.getC3x(), key.getC3z(), key.getS3y(), latency);
                }
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to load L3: {}", e.getMessage());
            }
            return Optional.empty();
        }, ioExecutor);
    }

    /**
     * Async save L3 entry.
     * 
     * @param lastAccessTime last access time (preserved for hot cache TTL on restore)
     */
    public CompletableFuture<Void> saveL3Async(String dim, L3Key key, L3Entry l3Entry, long lastAccessTime) {
        if (closed) return CompletableFuture.completedFuture(null);

        // Capture presenceTime before submitting to background thread
        long presenceTime = l3Entry.getPresenceTime();

        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            String sql = """
                MERGE INTO l3_cache (dim, c3x, c3z, s3y, scores, states, create_time, presence_time)
                KEY (dim, c3x, c3z, s3y)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, dim);
                ps.setInt(2, key.getC3x());
                ps.setInt(3, key.getC3z());
                ps.setInt(4, key.getS3y());
                ps.setBytes(5, doubleArrayToBytes(l3Entry.getScoresArray()));
                ps.setBytes(6, l3Entry.getStatesArray());
                ps.setLong(7, lastAccessTime);
                ps.setLong(8, presenceTime);
                ps.executeUpdate();
                
                if (CivilMod.DEBUG) {
                    long latency = System.currentTimeMillis() - startTime;
                    LOGGER.info("[civil-ttl-io] type=SAVE_L3 dim={} key={},{},{} latency_ms={} presenceTime={}",
                            dim, key.getC3x(), key.getC3z(), key.getS3y(), latency, presenceTime);
                }
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to save L3: {}", e.getMessage());
            }
        }, ioExecutor);
    }

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

        // Close database connection
        if (connection != null) {
            try {
                connection.close();
                if (CivilMod.DEBUG) {
                    LOGGER.info("[civil-storage] H2 database closed");
                }
            } catch (SQLException e) {
                LOGGER.warn("[civil-storage] Failed to close database: {}", e.getMessage());
            }
        }
    }

    // ========== Utility methods ==========

    private static byte[] doubleArrayToBytes(double[] doubles) {
        ByteBuffer buffer = ByteBuffer.allocate(doubles.length * 8);
        for (double d : doubles) {
            buffer.putDouble(d);
        }
        return buffer.array();
    }

    private static double[] bytesToDoubleArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        double[] doubles = new double[bytes.length / 8];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = buffer.getDouble();
        }
        return doubles;
    }

    // ========== Stored entry records ==========

    public record StoredL1Entry(VoxelChunkKey key, CScore cScore, long createTime) {}
    public record StoredL2Entry(L2Key key, L2Entry l2Entry, long createTime) {}
    public record StoredL3Entry(L3Key key, L3Entry l3Entry, long createTime) {}
    public record L1SaveRequest(String dim, VoxelChunkKey key, CScore cScore) {}
    public record StoredMobHead(String dim, int x, int y, int z, String skullType) {}
}
