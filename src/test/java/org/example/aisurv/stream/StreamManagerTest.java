package org.example.aisurv.stream;

import org.example.aisurv.app.CameraDefinition;
import org.example.aisurv.event.CameraHealthEvent;
import org.example.aisurv.event.SurveillanceEvent;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class StreamManagerTest {
    @Test
    void preventsDuplicateWorkersAndStopsTheActiveWorker() throws Exception {
        AtomicBoolean stopRequested = new AtomicBoolean();
        CameraStreamWorker worker = mock(CameraStreamWorker.class);
        doAnswer(invocation -> {
            while (!stopRequested.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return null;
        }).when(worker).run();
        doAnswer(invocation -> {
            stopRequested.set(true);
            return null;
        }).when(worker).requestStop();
        StreamManager manager = new StreamManager(new NoOpMonitor(),
                (name, url, monitor) -> worker,
                Duration.ofSeconds(2));
        CameraDefinition camera = new CameraDefinition("Gate", "rtsp://127.0.0.1:1/stream");

        assertTrue(manager.start(camera, 1));
        waitUntilRunning(manager, camera.name());
        assertFalse(manager.start(camera, 2));

        manager.stopAllUntil(System.nanoTime() + Duration.ofSeconds(2).toNanos());

        assertFalse(manager.isRunning(camera.name()));
    }

    @Test
    void appliesOneShutdownDeadlineAcrossAllWorkersAndRejectsRestarts() throws Exception {
        AtomicBoolean releaseWorkers = new AtomicBoolean();
        StreamManager manager = new StreamManager(new NoOpMonitor(), (name, url, monitor) -> {
            CameraStreamWorker worker = mock(CameraStreamWorker.class);
            doAnswer(invocation -> {
                while (!releaseWorkers.get()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
                return null;
            }).when(worker).run();
            return worker;
        }, Duration.ofMillis(150));
        List<CameraDefinition> cameras = List.of(
                new CameraDefinition("Gate", "rtsp://gate/live"),
                new CameraDefinition("Lobby", "rtsp://lobby/live")
        );
        manager.startAll(cameras);
        waitUntilRunning(manager, "Gate");
        waitUntilRunning(manager, "Lobby");

        try {
            long startedAt = System.nanoTime();
            manager.stopAllUntil(startedAt + Duration.ofMillis(150).toNanos());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            assertTrue(elapsedMillis < 275, "shutdown should use one global deadline");
            assertEquals(2, manager.activeStreamCount(), "surviving workers must remain visible");
            assertFalse(manager.start(new CameraDefinition("Yard", "rtsp://yard/live"), 3));
        } finally {
            releaseWorkers.set(true);
            waitUntilStopped(manager, "Gate");
            waitUntilStopped(manager, "Lobby");
        }
    }

    private void waitUntilRunning(StreamManager manager, String cameraName) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!manager.isRunning(cameraName) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(manager.isRunning(cameraName));
    }

    private void waitUntilStopped(StreamManager manager, String cameraName) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (manager.isRunning(cameraName) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertFalse(manager.isRunning(cameraName));
    }

    private static final class NoOpMonitor implements StreamMonitorSurface {
        @Override
        public void updateFrame(String cameraName, BufferedImage image) {
        }

        @Override
        public void onSurveillanceEvent(SurveillanceEvent event) {
        }

        @Override
        public void onCameraHealthEvent(CameraHealthEvent event) {
        }

        @Override
        public void logEvent(String cameraName, String message) {
        }
    }
}
