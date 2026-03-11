package civil.civilization.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Single-threaded IO executor for Cold (NBT) region reads and writes.
 * All region bulk load and flush tasks run here to avoid concurrent file access.
 */
public final class ColdIOQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-storage");

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Civil-NBT-IO");
        t.setDaemon(true);
        return t;
    });

    /**
     * Submit a task for serial execution. Never blocks the caller.
     */
    public void submit(Runnable task) {
        executor.execute(task);
    }

    /**
     * Submit a callable for serial execution. Returns CompletableFuture for the result.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                f.complete(task.call());
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        });
        return f;
    }

    /**
     * Shutdown and await termination.
     */
    public void shutdown(long timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                LOGGER.warn("[civil-storage] ColdIOQueue did not terminate within {}s", timeoutSeconds);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
