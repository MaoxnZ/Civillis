package civil.civilization;

/**
 * Monotonic server-time clock that only advances while the server is running.
 *
 * <p>Used for civilization decay / recovery timing so that single-player
 * offline periods (game closed) do not count as "absence".
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #load(long)} — called once on world load with the value persisted in H2 {@code civil_meta}</li>
 *   <li>{@link #tick()} — called every server tick (+50 ms)</li>
 *   <li>{@link #now()} — read current server time from any thread</li>
 * </ol>
 *
 * <p>The value is periodically saved to {@code civil_meta} and on shutdown
 * so that it survives server restarts.
 */
public final class ServerClock {

    /** Milliseconds per tick. */
    private static final long MS_PER_TICK = 50L;

    /** Accumulated server-time in milliseconds (only advances while the server is running). */
    private static volatile long millis = 0;

    private ServerClock() { /* utility class */ }

    /**
     * Restore the clock from a previously persisted value.
     *
     * @param savedMillis value read from cold storage (0 for fresh worlds)
     */
    public static void load(long savedMillis) {
        millis = Math.max(0, savedMillis);
    }

    /**
     * Advance the clock by one tick (50 ms).
     * Called from {@code TtlCacheService.onServerTick()} on the server thread.
     */
    public static void tick() {
        millis += MS_PER_TICK;
    }

    /**
     * Current server-time in milliseconds.
     * Thread-safe (volatile read).
     */
    public static long now() {
        return millis;
    }
}
