package net.hollowcube.posthog;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

final class Timer {
    private final Runnable task;
    private final long maxFlushIntervalNs;

    private final Thread taskThread;
    private volatile boolean closed = false;

    public Timer(@NotNull Runnable task, @NotNull Duration flushInterval) {
        this.task = task;
        this.maxFlushIntervalNs = flushInterval.toNanos();

        this.taskThread = Thread.startVirtualThread(this::runLoop);
    }

    /**
     * Manually triggers this timer causing it to execute and requeue itself.
     */
    public void wakeup() {
        checkNotClosed();

        LockSupport.unpark(taskThread);
    }

    public void close() {
        checkNotClosed();

        closed = true;
        LockSupport.unpark(taskThread);
        // Don't care about joining it should not complete any work.
    }

    private void runLoop() {
        while (!closed) {
            task.run();

            LockSupport.parkNanos(maxFlushIntervalNs);
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Event queue has been closed");
        }
    }
}
