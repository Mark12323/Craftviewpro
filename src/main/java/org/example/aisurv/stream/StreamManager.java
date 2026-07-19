package org.example.aisurv.stream;

import org.example.aisurv.app.CameraDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public class StreamManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamManager.class);
    private static final Duration DEFAULT_JOIN_TIMEOUT = Duration.ofSeconds(6);

    private final StreamMonitorSurface monitor;
    private final WorkerFactory workerFactory;
    private final Duration joinTimeout;
    private final Map<UUID, WorkerHandle> workers = new LinkedHashMap<>();
    private Lifecycle lifecycle = Lifecycle.RUNNING;

    public StreamManager(StreamMonitorSurface monitor) {
        this(monitor, CameraStreamWorker::new, DEFAULT_JOIN_TIMEOUT);
    }

    public StreamManager(StreamMonitorSurface monitor, WorkerFactory workerFactory, Duration joinTimeout) {
        this.monitor = Objects.requireNonNull(monitor, "monitor");
        this.workerFactory = Objects.requireNonNull(workerFactory, "workerFactory");
        this.joinTimeout = Objects.requireNonNull(joinTimeout, "joinTimeout");
        if (joinTimeout.isNegative() || joinTimeout.isZero()) {
            throw new IllegalArgumentException("joinTimeout must be positive");
        }
    }

    public void startAll(List<CameraDefinition> cameras) {
        int index = 1;
        for (CameraDefinition camera : cameras) {
            start(camera, index++);
        }
    }

    public synchronized boolean start(CameraDefinition camera, int index) {
        Objects.requireNonNull(camera, "camera");
        if (lifecycle != Lifecycle.RUNNING) {
            LOGGER.warn("{} stream worker cannot start while manager is {}", camera.name(), lifecycle);
            return false;
        }
        WorkerHandle existing = workers.get(camera.id());
        if (existing != null && existing.thread().isAlive()) {
            LOGGER.warn("{} stream worker is already running", camera.name());
            return false;
        }
        if (existing != null) {
            workers.remove(camera.id());
        }

        CameraStreamWorker worker = workerFactory.create(camera.name(), camera.rtspUrl(), monitor);
        Thread thread = new Thread(worker, "camera-stream-" + index);
        thread.setDaemon(true);
        workers.put(camera.id(), new WorkerHandle(camera.name(), worker, thread));
        thread.start();
        LOGGER.info("{} stream worker started", camera.name());
        return true;
    }

    public void stopAll() {
        stopAllUntil(System.nanoTime() + joinTimeout.toNanos());
    }

    public void stopAllUntil(long deadlineNanos) {
        List<Map.Entry<UUID, WorkerHandle>> snapshot;
        synchronized (this) {
            if (lifecycle != Lifecycle.RUNNING) {
                return;
            }
            lifecycle = Lifecycle.STOPPING;
            snapshot = new ArrayList<>(workers.entrySet());
        }

        for (Map.Entry<UUID, WorkerHandle> entry : snapshot) {
            entry.getValue().worker().requestStop();
            entry.getValue().thread().interrupt();
        }

        boolean interrupted = false;
        for (Map.Entry<UUID, WorkerHandle> entry : snapshot) {
            Thread thread = entry.getValue().thread();
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
            try {
                thread.join(millis, nanos);
            } catch (InterruptedException e) {
                interrupted = true;
                break;
            }
        }

        synchronized (this) {
            workers.entrySet().removeIf(entry -> !entry.getValue().thread().isAlive());
            lifecycle = Lifecycle.STOPPED;
        }
        for (Map.Entry<UUID, WorkerHandle> entry : snapshot) {
            if (entry.getValue().thread().isAlive()) {
                LOGGER.warn("{} stream worker remains active after the shutdown deadline", entry.getValue().name());
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized int activeStreamCount() {
        return (int) workers.values().stream().filter(handle -> handle.thread().isAlive()).count();
    }

    public synchronized boolean isRunning(String cameraName) {
        return workers.values().stream()
                .anyMatch(handle -> handle.name().equals(cameraName) && handle.thread().isAlive());
    }

    public boolean stop(UUID cameraId) {
        WorkerHandle handle;
        synchronized (this) {
            handle = workers.get(cameraId);
        }
        if (handle == null) return true;
        handle.worker().requestStop();
        handle.thread().interrupt();
        try {
            handle.thread().join(joinTimeout.toMillis());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (handle.thread().isAlive()) {
            LOGGER.warn("{} stream worker remains active after the shutdown deadline", handle.name());
            return false;
        }
        synchronized (this) {
            workers.remove(cameraId, handle);
        }
        return true;
    }

    @FunctionalInterface
    public interface WorkerFactory {
        CameraStreamWorker create(String cameraName, String rtspUrl, StreamMonitorSurface monitor);
    }

    private record WorkerHandle(String name, CameraStreamWorker worker, Thread thread) {
    }

    private enum Lifecycle {
        RUNNING,
        STOPPING,
        STOPPED
    }
}
