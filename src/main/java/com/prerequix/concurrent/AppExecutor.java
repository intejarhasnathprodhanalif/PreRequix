package com.prerequix.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton application-wide thread-pool manager.
 *
 * <ul>
 *   <li>{@link #computePool()} – cached pool for CPU-bound graph computations
 *       (cycle detection, topological sort, prerequisite queries).</li>
 *   <li>{@link #ioExecutor()} – single-thread executor so JSON reads/writes
 *       are always serialised and never corrupt the data file.</li>
 * </ul>
 *
 * Call {@link #shutdown()} once from {@link javafx.application.Application#stop()}
 * to clean up both executors before the JVM exits.
 */
public final class AppExecutor {

    private static final AppExecutor INSTANCE = new AppExecutor();

    /** Creates daemon threads with a human-readable name prefix. */
    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(true);   // won't block JVM shutdown
            return t;
        };
    }

    /** Unbounded cached pool – threads reused; expire after 60 s idle. */
    private final ExecutorService computePool =
            Executors.newCachedThreadPool(daemonFactory("prerequix-compute"));

    /** Single-threaded executor – serialises all disk I/O. */
    private final ExecutorService ioExecutor =
            Executors.newSingleThreadExecutor(daemonFactory("prerequix-io"));

    private AppExecutor() {}

    public static AppExecutor getInstance() {
        return INSTANCE;
    }

    /** Shared compute thread pool for background CPU tasks. */
    public ExecutorService computePool() {
        return computePool;
    }

    /** Shared single-thread executor for sequential file I/O. */
    public ExecutorService ioExecutor() {
        return ioExecutor;
    }

    /**
     * Gracefully shuts down both executors.
     * Must be called from {@code Application.stop()}.
     */
    public void shutdown() {
        computePool.shutdownNow();
        ioExecutor.shutdownNow();
    }
}
