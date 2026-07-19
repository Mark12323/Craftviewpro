package org.example.aisurv.edge.runtime;

import jakarta.annotation.PreDestroy;
import org.example.aisurv.camera.CameraRuntimeHealth;
import org.example.aisurv.contract.v1.CameraUpdateKindV1;
import org.example.aisurv.edge.update.CameraUpdateBroker;
import org.example.aisurv.event.CameraHealthEvent;
import org.example.aisurv.event.CameraHealthState;
import org.example.aisurv.persistence.ApplicationPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

@Service
public class CameraHealthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraHealthService.class);
    private static final Duration FRAME_CHECKPOINT_INTERVAL = Duration.ofSeconds(5);
    private final ApplicationPersistence persistence;
    private final CameraUpdateBroker updates;
    private final Map<UUID, CameraRuntimeHealth> current = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> frameCheckpoints = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1024),
            Thread.ofPlatform().daemon().name("camera-health-writer").factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public CameraHealthService(ApplicationPersistence persistence, CameraUpdateBroker updates) {
        this.persistence = persistence;
        this.updates = updates;
    }

    public void stateChanged(UUID cameraId, CameraHealthEvent event) {
        String reasonCode = reason(event.state());
        current.compute(cameraId, (id, existing) -> provisional(id, existing, event.state(), reasonCode, event.occurredAt()));
        updates.publish(CameraUpdateKindV1.HEALTH_CHANGED, cameraId, null);
        submit(() -> {
            while (!writer.isShutdown()) {
                try {
                    CameraRuntimeHealth health = persistence.cameraHealthRepository().recordState(
                            cameraId, event.state(), reasonCode, event.occurredAt());
                    current.merge(cameraId, health, CameraHealthService::newest);
                    return;
                } catch (RuntimeException failure) {
                    LOGGER.warn("Camera health transition could not be persisted for {}; retrying", cameraId);
                    LOGGER.debug("Camera health persistence failure", failure);
                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    public void configurationChanged(UUID cameraId, boolean enabled, Instant occurredAt) {
        stateChanged(cameraId, new CameraHealthEvent("configuration",
                enabled ? CameraHealthState.CONNECTING : CameraHealthState.DISABLED,
                enabled ? "Enabled" : "Disabled", occurredAt));
    }

    public void frameObserved(UUID cameraId, Instant observedAt) {
        CameraRuntimeHealth existing = current.get(cameraId);
        if (existing != null && (existing.lastSeenAt() == null || observedAt.isAfter(existing.lastSeenAt()))) {
            current.put(cameraId, new CameraRuntimeHealth(existing.cameraId(), existing.state(),
                    existing.reasonCode(), existing.stateChangedAt(), observedAt, existing.onlineSince(),
                    existing.accumulatedUptimeMillis(), existing.reconnectAttempts(), observedAt));
        }
        Instant previous = frameCheckpoints.get(cameraId);
        if (previous != null && observedAt.isBefore(previous.plus(FRAME_CHECKPOINT_INTERVAL))) return;
        frameCheckpoints.put(cameraId, observedAt);
        submit(() -> {
            try {
                persistence.cameraHealthRepository().recordFrame(cameraId, observedAt);
            } catch (RuntimeException failure) {
                frameCheckpoints.remove(cameraId, observedAt);
                LOGGER.warn("Camera last-seen checkpoint could not be persisted for {}", cameraId);
                LOGGER.debug("Camera last-seen persistence failure", failure);
            }
        });
    }

    public Map<UUID, CameraRuntimeHealth> health() {
        try {
            Map<UUID, CameraRuntimeHealth> persisted = persistence.cameraHealthRepository().findAll();
            persisted.forEach((id, health) -> current.merge(id, health, CameraHealthService::newest));
        } catch (RuntimeException failure) {
            LOGGER.debug("Persisted camera health is temporarily unavailable", failure);
        }
        return Map.copyOf(current);
    }

    private void submit(Runnable task) {
        try {
            writer.execute(task);
        } catch (RejectedExecutionException failure) {
            LOGGER.warn("Camera health writer queue is full; durable update will retry on the next observation");
        }
    }

    private static CameraRuntimeHealth provisional(UUID cameraId, CameraRuntimeHealth existing,
                                                    CameraHealthState state, String reasonCode, Instant at) {
        Instant lastSeen = existing == null ? null : existing.lastSeenAt();
        Instant onlineSince = state == CameraHealthState.ONLINE || state == CameraHealthState.IMPAIRED
                ? existing != null && existing.onlineSince() != null ? existing.onlineSince() : at : null;
        long uptime = existing == null ? 0 : existing.accumulatedUptimeMillis();
        long reconnects = (existing == null ? 0 : existing.reconnectAttempts())
                + (state == CameraHealthState.RECONNECTING ? 1 : 0);
        return new CameraRuntimeHealth(cameraId, state, reasonCode, at, lastSeen, onlineSince,
                uptime, reconnects, at);
    }

    private static CameraRuntimeHealth newest(CameraRuntimeHealth first, CameraRuntimeHealth second) {
        return !second.updatedAt().isBefore(first.updatedAt()) ? second : first;
    }

    private static String reason(CameraHealthState state) {
        return switch (state) {
            case CONNECTING -> "CONNECTING";
            case ONLINE -> "STREAM_CONNECTED";
            case RECONNECTING -> "RETRY_SCHEDULED";
            case IMPAIRED -> "PROCESSING_IMPAIRED";
            case OFFLINE -> "STREAM_UNAVAILABLE";
            case FROZEN -> "STREAM_FROZEN";
            case DISABLED -> "CONFIGURATION_DISABLED";
            case STOPPED -> "WORKER_STOPPED";
        };
    }

    @PreDestroy
    void stop() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(7, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException failure) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
