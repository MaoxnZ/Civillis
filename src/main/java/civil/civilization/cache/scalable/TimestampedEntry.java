package civil.civilization.cache.scalable;

/**
 * Cache entry wrapper with timestamps.
 * 
 * <p>Used to implement TTL eviction strategy:
 * <ul>
 *   <li>createTime: Entry creation/update time, used for TTL judgment</li>
 *   <li>lastAccessTime: Last access time, used for statistics</li>
 * </ul>
 * 
 * @param <T> Cache value type
 */
public final class TimestampedEntry<T> {

    private volatile T value;
    private final long createTime;
    private volatile long lastAccessTime;

    public TimestampedEntry(T value) {
        this(value, System.currentTimeMillis());
    }

    public TimestampedEntry(T value, long createTime) {
        this.value = value;
        this.createTime = createTime;
        this.lastAccessTime = createTime;
    }

    /**
     * Get the cached value.
     */
    public T getValue() {
        return value;
    }

    /**
     * Update the cached value (does not change createTime).
     */
    public void setValue(T value) {
        this.value = value;
    }

    /**
     * Get the creation time.
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * Get the last access time.
     */
    public long getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * Update the last access time.
     */
    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * Check if the entry has expired.
     * 
     * @param ttlMillis TTL time (milliseconds)
     * @return true if expired
     */
    public boolean isExpired(long ttlMillis) {
        return System.currentTimeMillis() - createTime > ttlMillis;
    }

    /**
     * Get the entry age (milliseconds).
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - createTime;
    }
}
