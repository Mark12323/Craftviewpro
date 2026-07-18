package com.aegisvision.cctv;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardViewFrameCoalescerTest {
    @Test
    void retainsOnlyNewestPendingFramePerCamera() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        Map<String, BufferedImage> delivered = new java.util.HashMap<>();
        DashboardView.FrameCoalescer coalescer =
                new DashboardView.FrameCoalescer(scheduled::add, delivered::put);
        BufferedImage oldGateFrame = frame();
        BufferedImage newestGateFrame = frame();
        BufferedImage lobbyFrame = frame();

        coalescer.submit("Gate", oldGateFrame);
        coalescer.submit("Gate", newestGateFrame);
        coalescer.submit("Lobby", lobbyFrame);

        assertEquals(1, scheduled.size());
        scheduled.remove().run();
        assertSame(newestGateFrame, delivered.get("Gate"));
        assertSame(lobbyFrame, delivered.get("Lobby"));
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void schedulesAnotherDrainWhenAFrameArrivesDuringDelivery() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        List<BufferedImage> delivered = new ArrayList<>();
        AtomicReference<DashboardView.FrameCoalescer> coalescerReference = new AtomicReference<>();
        BufferedImage firstFrame = frame();
        BufferedImage newestFrame = frame();
        DashboardView.FrameCoalescer coalescer = new DashboardView.FrameCoalescer(scheduled::add, (camera, image) -> {
            delivered.add(image);
            if (image == firstFrame) coalescerReference.get().submit(camera, newestFrame);
        });
        coalescerReference.set(coalescer);

        coalescer.submit("Gate", firstFrame);
        scheduled.remove().run();

        assertEquals(1, scheduled.size());
        scheduled.remove().run();
        assertEquals(List.of(firstFrame, newestFrame), delivered);
    }

    @Test
    void concurrentSubmissionsStillScheduleOnlyOneDrain() throws Exception {
        AtomicInteger scheduleCount = new AtomicInteger();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        AtomicReference<BufferedImage> delivered = new AtomicReference<>();
        DashboardView.FrameCoalescer coalescer = new DashboardView.FrameCoalescer(task -> {
            scheduleCount.incrementAndGet();
            scheduled.set(task);
        }, (camera, image) -> delivered.set(image));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        List<Future<?>> submissions = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                submissions.add(workers.submit(() -> {
                    start.await();
                    for (int index = 0; index < 100; index++) coalescer.submit("Gate", frame());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> submission : submissions) submission.get();
        } finally {
            workers.shutdownNow();
        }
        BufferedImage newestFrame = frame();
        coalescer.submit("Gate", newestFrame);

        assertEquals(1, scheduleCount.get());
        scheduled.get().run();
        assertSame(newestFrame, delivered.get());
    }

    @Test
    void disposeDropsPendingFramesAndIgnoresLateCallbacks() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        List<BufferedImage> delivered = new ArrayList<>();
        DashboardView.FrameCoalescer coalescer =
                new DashboardView.FrameCoalescer(scheduled::add, (camera, image) -> delivered.add(image));

        coalescer.submit("Gate", frame());
        coalescer.dispose();
        scheduled.remove().run();
        coalescer.submit("Gate", frame());

        assertTrue(delivered.isEmpty());
        assertTrue(scheduled.isEmpty());
    }

    private static BufferedImage frame() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    }
}
