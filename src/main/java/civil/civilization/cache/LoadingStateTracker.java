package civil.civilization.cache;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Loading state tracker: tracks keys being loaded asynchronously to avoid duplicate loading.
 * 
 * <p>Only used for L2/L3 cold storage asynchronous loading (L1 does not use cold storage).
 * 
 * <p>Workflow:
 * <ol>
 *   <li>Check isLoading(key) when querying</li>
 *   <li>If not loading, call startLoading(key) to trigger loading</li>
 *   <li>Call finishLoading(key, result) after loading completes</li>
 *   <li>Can register callbacks via onLoadComplete(key, callback)</li>
 * </ol>
 */
public final class LoadingStateTracker {

    // Set of keys currently being loaded
    private final Set<String> loadingKeys = ConcurrentHashMap.newKeySet();

    // Callbacks for load completion
    private final ConcurrentHashMap<String, List<Consumer<Object>>> callbacks = new ConcurrentHashMap<>();

    /**
     * Check if the specified key is currently being loaded.
     */
    public boolean isLoading(String key) {
        return loadingKeys.contains(key);
    }

    /**
     * Start loading the specified key.
     * 
     * @param key The key to load
     * @return true if this call successfully marked it as loading (i.e., it was not loading before)
     */
    public boolean startLoading(String key) {
        return loadingKeys.add(key);
    }

    /**
     * Finish loading.
     * 
     * @param key The key that finished loading
     * @param result The loading result (for callbacks)
     */
    public void finishLoading(String key, Object result) {
        loadingKeys.remove(key);

        List<Consumer<Object>> cbs = callbacks.remove(key);
        if (cbs != null) {
            for (Consumer<Object> cb : cbs) {
                try {
                    cb.accept(result);
                } catch (Exception e) {
                    // Ignore callback exceptions
                }
            }
        }
    }

    /**
     * Register a callback for load completion.
     * 
     * @param key The key to listen for
     * @param callback The callback when loading completes
     */
    public void onLoadComplete(String key, Consumer<Object> callback) {
        callbacks.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    /**
     * Get the number of keys currently being loaded.
     */
    public int getLoadingCount() {
        return loadingKeys.size();
    }

    /**
     * Clear all loading state (for shutdown).
     */
    public void clear() {
        loadingKeys.clear();
        callbacks.clear();
    }
}
