package org.example.aisurv.app;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundTaskTrackerTest {
    @Test
    void interruptsAndJoinsCooperativeTasks() throws Exception {
        BackgroundTaskTracker tracker = new BackgroundTaskTracker();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        tracker.start(() -> {
            started.countDown();
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }, "cooperative-task");
        started.await();

        tracker.requestStop();
        tracker.awaitUntil(System.nanoTime() + Duration.ofSeconds(2).toNanos());

        assertTrue(interrupted.get());
        assertEquals(0, tracker.activeTaskCount());
        assertThrows(IllegalStateException.class, () -> tracker.start(() -> { }, "late-task"));
    }

    @Test
    void boundedWaitReportsTasksThatIgnoreInterrupts() throws Exception {
        BackgroundTaskTracker tracker = new BackgroundTaskTracker();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean release = new AtomicBoolean();
        Thread task = tracker.start(() -> {
            started.countDown();
            while (!release.get()) {
                Thread.interrupted();
                Thread.onSpinWait();
            }
        }, "stubborn-task");
        started.await();

        try {
            tracker.requestStop();
            long startedAt = System.nanoTime();
            tracker.awaitUntil(startedAt + Duration.ofMillis(100).toNanos());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            assertTrue(task.isDaemon());
            assertTrue(elapsedMillis < 250, "wait should respect the shared deadline");
            assertEquals(1, tracker.activeTaskCount());
        } finally {
            release.set(true);
            task.join(Duration.ofSeconds(2));
        }
        assertEquals(0, tracker.activeTaskCount());
    }
}
