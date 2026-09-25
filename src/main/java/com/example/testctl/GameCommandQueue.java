package com.example.testctl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Hands HTTP-handler-thread commands off to the main GL thread, where Game drains and runs them
 * once per frame - screenshots and any state read/mutation must happen there, since Game.init()
 * creates the GL context on that thread and there is no headless mode in this project.
 */
public final class GameCommandQueue {
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    /**
     * Called by an HTTP handler thread. Enqueues {@code task} and blocks until the main thread
     * has run it (or {@code timeoutMillis} elapses), returning whatever it produced. A timeout
     * means the main thread never got to it (e.g. it's stalled) rather than hanging the calling
     * test forever.
     */
    public <T> T submitAndWait(Supplier<T> task, long timeoutMillis) throws TimeoutException {
        CompletableFuture<T> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Tasks enqueued but not yet run (lets tests wait for a submission deterministically instead of sleeping). */
    public int pending() {
        return queue.size();
    }

    /** Called once per frame by Game's main loop, on the GL thread, before render(). */
    public void drain() {
        Runnable r;
        while ((r = queue.poll()) != null) {
            r.run();
        }
    }
}
