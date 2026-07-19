package org.example.aisurv.edgeclient;

import org.example.aisurv.contract.v1.EdgeHealthResponseV1;
import org.example.aisurv.contract.v1.EdgeStatusV1;
import org.example.aisurv.contract.v1.EdgeCapabilityV1;
import org.example.aisurv.contract.v1.CameraListResponseV1;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class EdgeConnectionService implements AutoCloseable {
    private final EdgeApiClient client;
    private final Duration initialBackoff;
    private final Duration maximumBackoff;
    private final Duration compatibilityPollDelay;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile CameraUpdateSubscription activeSubscription;

    public EdgeConnectionService(EdgeApiClient client) {
        this(client, Duration.ofMillis(250), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    EdgeConnectionService(EdgeApiClient client, Duration initialBackoff,
                          Duration maximumBackoff, Duration compatibilityPollDelay) {
        this.client = client;
        this.initialBackoff = initialBackoff;
        this.maximumBackoff = maximumBackoff;
        this.compatibilityPollDelay = compatibilityPollDelay;
    }

    public EdgeConnectionSnapshot probe() {
        try {
            EdgeHealthResponseV1 health = client.health();
            if (health.status() == EdgeStatusV1.READY) {
                return new EdgeConnectionSnapshot(
                        EdgeConnectionState.AVAILABLE, "Edge ready (API v1)", health);
            }
            if (health.status() == EdgeStatusV1.STARTING || health.status() == EdgeStatusV1.DEGRADED) {
                return new EdgeConnectionSnapshot(
                        EdgeConnectionState.DEGRADED, "Edge " + health.status().name().toLowerCase(), health);
            }
            return new EdgeConnectionSnapshot(
                    EdgeConnectionState.DEGRADED, "Edge reported an unrecognized service state", health);
        } catch (IncompatibleEdgeException e) {
            return new EdgeConnectionSnapshot(EdgeConnectionState.INCOMPATIBLE, e.getMessage(), null);
        } catch (EdgeUnavailableException e) {
            return new EdgeConnectionSnapshot(EdgeConnectionState.UNAVAILABLE, "Edge service unavailable", null);
        } catch (EdgeRequestException e) {
            return new EdgeConnectionSnapshot(
                    EdgeConnectionState.DEGRADED, "Edge health request failed (HTTP " + e.statusCode() + ")", null);
        }
    }

    public void monitorCameraUpdates(Consumer<EdgeConnectionSnapshot> connectionConsumer,
                                     Consumer<CameraListResponseV1> inventoryConsumer) {
        Objects.requireNonNull(connectionConsumer, "connectionConsumer");
        Objects.requireNonNull(inventoryConsumer, "inventoryConsumer");
        Duration backoff = initialBackoff;
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            EdgeConnectionSnapshot connection = probe();
            connectionConsumer.accept(connection);
            if (connection.state() == EdgeConnectionState.AVAILABLE) {
                try {
                    inventoryConsumer.accept(client.listCameras());
                    if (!connection.health().capabilities().contains(EdgeCapabilityV1.CAMERA_UPDATE_STREAM)) {
                        if (!pause(compatibilityPollDelay)) return;
                        continue;
                    }
                    consumeUpdates(inventoryConsumer);
                    if (closed.get()) return;
                    connectionConsumer.accept(new EdgeConnectionSnapshot(
                            EdgeConnectionState.UNAVAILABLE, "Edge update stream disconnected", null));
                } catch (IncompatibleEdgeException failure) {
                    connectionConsumer.accept(new EdgeConnectionSnapshot(
                            EdgeConnectionState.INCOMPATIBLE, failure.getMessage(), null));
                } catch (EdgeUnavailableException failure) {
                    connectionConsumer.accept(new EdgeConnectionSnapshot(
                            EdgeConnectionState.UNAVAILABLE, "Edge service unavailable", null));
                } catch (EdgeRequestException failure) {
                    connectionConsumer.accept(new EdgeConnectionSnapshot(
                            EdgeConnectionState.DEGRADED,
                            "Edge update request failed (HTTP " + failure.statusCode() + ")", null));
                }
            }
            if (!pause(backoff)) return;
            backoff = doubledUpTo(backoff, maximumBackoff);
        }
    }

    private void consumeUpdates(Consumer<CameraListResponseV1> inventoryConsumer) {
        try (CameraUpdateSubscription subscription = client.openCameraUpdates()) {
            activeSubscription = subscription;
            if (closed.get()) return;
            subscription.consume(event -> {
                if (!closed.get()) inventoryConsumer.accept(client.listCameras());
            });
        } finally {
            activeSubscription = null;
        }
    }

    private boolean pause(Duration delay) {
        if (closed.get()) return false;
        try {
            Thread.sleep(delay.toMillis());
            return !closed.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Duration doubledUpTo(Duration value, Duration maximum) {
        long doubled = value.toMillis() > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : value.toMillis() * 2;
        return Duration.ofMillis(Math.min(doubled, maximum.toMillis()));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        CameraUpdateSubscription subscription = activeSubscription;
        if (subscription != null) subscription.close();
    }
}
