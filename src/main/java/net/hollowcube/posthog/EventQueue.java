package net.hollowcube.posthog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

final class EventQueue {
    private final ConcurrentLinkedQueue<JsonObject> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger(0);

    private final Consumer<JsonArray> batchProcessor;
    private final long maxFlushIntervalNs;
    private final int batchSize;

    private final Thread consumerThread;
    private volatile boolean closed = false;

    public EventQueue(@NotNull Consumer<JsonArray> batchProcessor, @NotNull Duration maxFlushInterval, int batchSize) {
        this.batchProcessor = batchProcessor;
        this.maxFlushIntervalNs = maxFlushInterval.toNanos();
        this.batchSize = batchSize;

        this.consumerThread = Thread.startVirtualThread(this::consumeLoop);
    }

    public void enqueue(@NotNull JsonObject event) {
        checkNotClosed();

        queue.add(event);
        if (count.incrementAndGet() >= batchSize) {
            LockSupport.unpark(consumerThread);
        }
    }

    public void flush() {
        checkNotClosed();

        LockSupport.unpark(consumerThread);
    }

    /**
     * Closes the event processor and waits "timeout" for it to finish.
     *
     * @param timeout The max wait time, or zero to wait indefinitely.
     */
    public void close(@NotNull Duration timeout) {
        checkNotClosed();

        closed = true;
        LockSupport.unpark(consumerThread);

        try {
            consumerThread.join(Math.max(0, timeout.toMillis()));
        } catch (InterruptedException ignored) {
            // Do nothing just exit
        }
    }

    private void consumeLoop() {
        do {
            LockSupport.parkNanos(maxFlushIntervalNs);

            int toProcess = count.getAndSet(0);
            if (toProcess == 0) continue;

            while (toProcess > 0) {
                JsonArray batch = new JsonArray(toProcess);
                for (int i = 0; i < batchSize && toProcess > 0; i++) {
                    var event = queue.poll();
                    if (event == null) break; // Impossible if this is the only thread reading

                    batch.add(event);
                    toProcess--;
                }

                batchProcessor.accept(batch);
            }
        } while (!closed);
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Event queue has been closed");
        }
    }
}
