package net.hollowcube.posthog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventQueueTest {

    @Test
    void manualFlush() {
        var processor = new CountingProcessor();
        var queue = new EventQueue(processor, Duration.ofDays(10), 100);

        for (int i = 0; i < 10; i++)
            queue.enqueue(new JsonObject());
        processor.assertBatchCount(0);

        queue.flush();
        sleep(); // See note on sleep

        processor.assertBatchCount(1);
        processor.assertEventCount(10);
    }

    @Test
    void timedFlush() {
        var processor = new CountingProcessor();
        var queue = new EventQueue(processor, Duration.ofMillis(10), 100);

        for (int i = 0; i < 10; i++)
            queue.enqueue(new JsonObject());
        processor.assertBatchCount(0);

        sleep(); // See note on sleep

        processor.assertBatchCount(1);
        processor.assertEventCount(10);
    }

    @Test
    void batchSizeFlush() {
        var processor = new CountingProcessor();
        var queue = new EventQueue(processor, Duration.ofDays(10), 100);

        // Flushing at batch size should consume all available events
        for (int i = 0; i < 150; i++)
            queue.enqueue(new JsonObject());
        processor.assertBatchCount(0);

        sleep(); // See note on sleep

        processor.assertBatchCount(2);
        processor.assertEventCount(150);
    }

    @Test
    void concurrentEventSpam() {
        var processor = new CountingProcessor();
        var queue = new EventQueue(processor, Duration.ofMillis(10), 100);

        for (int i = 0; i < 20; i++) {
            Thread.startVirtualThread(() -> {
                for (int j = 0; j < 1000; j++) {
                    queue.enqueue(new JsonObject());
                    // Basically just want to pay the scheduling cost of pausing to make this less consistent.
                    LockSupport.parkNanos(10);
                }
            });
        }

        sleep(); // See note on sleep

        // Dont care how many batches this actually ends up being
        processor.assertEventCount(20000);
    }

    private static void sleep() {
        // Kinda gross to do a sleep here, but we need to wait for the consumer thread and don't immediately want to
        // expose any of those details even for tests.

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static class CountingProcessor implements Consumer<JsonArray> {
        private int batchCount = 0;
        private int eventCount = 0;

        @Override
        public void accept(JsonArray jsonElements) {
            batchCount++;
            eventCount += jsonElements.size();
        }

        public void assertBatchCount(int expected) {
            assertEquals(expected, batchCount);
        }

        public void assertEventCount(int expected) {
            assertEquals(expected, eventCount);
        }
    }
}
