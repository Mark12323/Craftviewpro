package org.example.aisurv.edge.update;

import jakarta.annotation.PreDestroy;
import org.example.aisurv.contract.v1.CameraUpdateEventV1;
import org.example.aisurv.contract.v1.CameraUpdateKindV1;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

@Component
public class CameraUpdateBroker {
    static final String EVENT_NAME = "camera-update-v1";
    private final UUID streamInstanceId = UUID.randomUUID();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<UUID, SseEmitter> emitters = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> readySubscribers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Executor executor;
    private final LongFunction<SseEmitter> emitterFactory;

    public CameraUpdateBroker() {
        this(new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256),
                        Thread.ofPlatform().daemon().name("camera-update-broadcaster").factory(),
                        new ThreadPoolExecutor.AbortPolicy()),
                SseEmitter::new);
    }

    CameraUpdateBroker(Executor executor, LongFunction<SseEmitter> emitterFactory) {
        this.executor = executor;
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = emitterFactory.apply(0L);
        if (closed.get()) {
            emitter.complete();
            return emitter;
        }
        UUID subscriberId = UUID.randomUUID();
        emitter.onCompletion(() -> forget(subscriberId));
        emitter.onTimeout(() -> forget(subscriberId));
        emitter.onError(failure -> forget(subscriberId));
        emitters.put(subscriberId, emitter);
        enqueue(() -> {
            send(subscriberId, emitter, event(CameraUpdateKindV1.RESET, null, null));
            if (emitters.get(subscriberId) == emitter) readySubscribers.add(subscriberId);
        });
        return emitter;
    }

    public void publish(CameraUpdateKindV1 kind, UUID cameraId, Long cameraVersion) {
        if (closed.get()) return;
        enqueue(() -> broadcast(event(kind, cameraId, cameraVersion)));
    }

    @Scheduled(fixedDelayString = "${aisurv.edge.camera-update-heartbeat-ms:15000}")
    void heartbeat() {
        if (closed.get() || emitters.isEmpty()) return;
        enqueue(() -> emitters.forEach((id, emitter) -> {
            if (!readySubscribers.contains(id)) return;
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException failure) {
                remove(id, emitter);
            }
        }));
    }

    int subscriberCount() {
        return emitters.size();
    }

    private CameraUpdateEventV1 event(CameraUpdateKindV1 kind, UUID cameraId, Long cameraVersion) {
        return new CameraUpdateEventV1(
                streamInstanceId, sequence.incrementAndGet(), kind, cameraId, cameraVersion, Instant.now());
    }

    private void broadcast(CameraUpdateEventV1 event) {
        emitters.forEach((id, emitter) -> {
            if (readySubscribers.contains(id)) send(id, emitter, event);
        });
    }

    private void send(UUID subscriberId, SseEmitter emitter, CameraUpdateEventV1 event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.streamInstanceId() + ":" + event.sequence())
                    .name(EVENT_NAME)
                    .data(event));
        } catch (IOException | IllegalStateException failure) {
            remove(subscriberId, emitter);
        }
    }

    private void enqueue(Runnable delivery) {
        try {
            executor.execute(delivery);
        } catch (RejectedExecutionException failure) {
            disconnectAll();
        }
    }

    private void remove(UUID subscriberId, SseEmitter emitter) {
        readySubscribers.remove(subscriberId);
        if (emitters.remove(subscriberId, emitter)) emitter.complete();
    }

    private void forget(UUID subscriberId) {
        readySubscribers.remove(subscriberId);
        emitters.remove(subscriberId);
    }

    private void disconnectAll() {
        emitters.forEach(this::remove);
    }

    @PreDestroy
    void stop() {
        if (!closed.compareAndSet(false, true)) return;
        disconnectAll();
        if (executor instanceof ExecutorService service) service.shutdownNow();
    }
}
