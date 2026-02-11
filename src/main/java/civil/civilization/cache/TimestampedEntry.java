package civil.civilization.cache;

/**
 * Cache entry wrapper with wall-clock timestamps for TTL management.
 *
 * <p>TTL strategy: entries expire when {@code now - lastTouchTime > ttlMillis}.
 * Calling {@link #touch()} refreshes the TTL countdown, so entries actively
 * used by the prefetcher (near online players) never expire.
 *
 * @param <T> Cache value type
 */
public final class TimestampedEntry<T> {

    private volatile T value;

    /**
     * Wall-clock millis of last TTL refresh.
     * Updated by {@link #touch()} and used by {@link #isExpired(long)}.
     * Initially set to the creation/restore time.
     */
    private volatile long lastTouchTime;

    /** Wall-clock millis of last read access (statistics only, not used for TTL). */
    private volatile long lastAccessTime;

    public TimestampedEntry(T value) {
        this(value, System.currentTimeMillis());
    }

    public TimestampedEntry(T value, long createTime) {
        this.value = value;
        this.lastTouchTime = createTime;
        this.lastAccessTime = createTime;
    }

    /**
     * Get the cached value.
     */
    public T getValue() {
        return value;
    }

    /**
     * Update the cached value (does not reset TTL timer).
     */
    public void setValue(T value) {
        this.value = value;
    }

    /**
     * Get the last touch time (TTL baseline).
     * <p>Named {@code getCreateTime} for backward compatibility with cleanup code
     * that uses {@code now - entry.getValue().getCreateTime() > ttlMillis}.
     */
    public long getCreateTime() {
        return lastTouchTime;
    }

    /**
     * Get the last access time (statistics only).
     */
    public long getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * Refresh the TTL countdown AND update access time.
     *
     * <p>After calling touch(), the entry will survive for another full TTL period.
     * Called by the prefetcher every second for entries near online players.
     */
    public void touch() {
        long now = System.currentTimeMillis();
        this.lastTouchTime = now;
        this.lastAccessTime = now;
    }

    /**
     * Check if the entry has expired (not touched for longer than TTL).
     *
     * @param ttlMillis TTL time (milliseconds)
     * @return true if expired
     */
    public boolean isExpired(long ttlMillis) {
        return System.currentTimeMillis() - lastTouchTime > ttlMillis;
    }

    /**
     * Get the time since last touch (milliseconds).
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - lastTouchTime;
    }
}
