package org.example.aisurv.edgeclient;

import org.example.aisurv.contract.v1.ApiVersionV1;
import org.example.aisurv.contract.v1.CameraDiscoveryRequestV1;
import org.example.aisurv.contract.v1.CameraDiscoveryResponseV1;
import org.example.aisurv.contract.v1.CameraListResponseV1;
import org.example.aisurv.contract.v1.EdgeHealthResponseV1;
import org.example.aisurv.contract.v1.EdgeStatusV1;
import org.example.aisurv.contract.v1.RegisterCameraRequestV1;
import org.example.aisurv.contract.v1.RegisterCameraResponseV1;
import org.example.aisurv.contract.v1.EdgeCapabilityV1;
import org.example.aisurv.contract.v1.CameraUpdateEventV1;
import org.example.aisurv.contract.v1.CameraUpdateKindV1;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EdgeConnectionServiceTest {
    @Test
    void classifiesReadyAndUnavailableEdges() {
        EdgeApiClient ready = client(new EdgeHealthResponseV1(
                ApiVersionV1.CURRENT, "1", EdgeStatusV1.READY, Set.of(), Map.of(), Instant.now()));
        EdgeApiClient unavailable = new EdgeApiClient() {
            @Override public EdgeHealthResponseV1 health() {
                throw new EdgeUnavailableException("offline", new java.io.IOException("refused"));
            }
            @Override public CameraListResponseV1 listCameras() { throw new AssertionError(); }
            @Override public CameraDiscoveryResponseV1 discoverCameras(CameraDiscoveryRequestV1 request) {
                throw new AssertionError();
            }
            @Override public RegisterCameraResponseV1 registerCamera(RegisterCameraRequestV1 request) {
                throw new AssertionError();
            }
            @Override public org.example.aisurv.contract.v1.CameraDetailV1 getCamera(java.util.UUID id) { throw new AssertionError(); }
            @Override public org.example.aisurv.contract.v1.CameraDetailV1 updateCamera(java.util.UUID id, org.example.aisurv.contract.v1.UpdateCameraRequestV1 request) { throw new AssertionError(); }
            @Override public org.example.aisurv.contract.v1.CameraSummaryV1 setCameraState(java.util.UUID id, org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1 request) { throw new AssertionError(); }
            @Override public void deleteCamera(java.util.UUID id, long version) { throw new AssertionError(); }
            @Override public byte[] snapshot(java.util.UUID id) { throw new AssertionError(); }
        };

        assertEquals(EdgeConnectionState.AVAILABLE, new EdgeConnectionService(ready).probe().state());
        assertEquals(EdgeConnectionState.UNAVAILABLE, new EdgeConnectionService(unavailable).probe().state());
    }

    @Test
    void treatsUnknownFutureV1StateAsDegraded() {
        EdgeApiClient future = client(new EdgeHealthResponseV1(
                ApiVersionV1.CURRENT, "future", EdgeStatusV1.UNKNOWN, Set.of(), Map.of(), Instant.now()));

        assertEquals(EdgeConnectionState.DEGRADED, new EdgeConnectionService(future).probe().state());
    }

    @Test
    void reconnectsAndRefreshesInventoryAfterStreamFailureAndUpdate() {
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger inventoryReads = new AtomicInteger();
        EdgeConnectionService[] service = new EdgeConnectionService[1];
        EdgeApiClient client = new EdgeApiClient() {
            @Override public EdgeHealthResponseV1 health() {
                return new EdgeHealthResponseV1(ApiVersionV1.CURRENT, "test", EdgeStatusV1.READY,
                        Set.of(EdgeCapabilityV1.CAMERA_UPDATE_STREAM), Map.of(), Instant.now());
            }
            @Override public CameraListResponseV1 listCameras() {
                inventoryReads.incrementAndGet();
                return new CameraListResponseV1(List.of(), Instant.now());
            }
            @Override public CameraUpdateSubscription openCameraUpdates() {
                int attempt = subscriptions.incrementAndGet();
                return new CameraUpdateSubscription() {
                    @Override public void consume(java.util.function.Consumer<CameraUpdateEventV1> consumer) {
                        if (attempt == 1) {
                            throw new EdgeUnavailableException("disconnected", new java.io.IOException("closed"));
                        }
                        consumer.accept(new CameraUpdateEventV1(UUID.randomUUID(), 1,
                                CameraUpdateKindV1.UPSERTED, UUID.randomUUID(), 1L, Instant.now()));
                        service[0].close();
                    }
                    @Override public void close() { }
                };
            }
            @Override public CameraDiscoveryResponseV1 discoverCameras(CameraDiscoveryRequestV1 request) { throw new AssertionError(); }
            @Override public RegisterCameraResponseV1 registerCamera(RegisterCameraRequestV1 request) { throw new AssertionError(); }
            @Override public org.example.aisurv.contract.v1.CameraDetailV1 getCamera(UUID id) { throw new AssertionError(); }
            @Override public org.example.aisurv.contract.v1.CameraDetailV1 updateCamera(UUID id, org.example.aisurv.contract.v1.UpdateCameraRequestV1 request) { throw new AssertionError(); }
            @Override public org.example.aisurv.contract.v1.CameraSummaryV1 setCameraState(UUID id, org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1 request) { throw new AssertionError(); }
            @Override public void deleteCamera(UUID id, long version) { throw new AssertionError(); }
            @Override public byte[] snapshot(UUID id) { throw new AssertionError(); }
        };
        service[0] = new EdgeConnectionService(
                client, Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofMillis(1));
        ArrayList<EdgeConnectionState> states = new ArrayList<>();

        service[0].monitorCameraUpdates(connection -> states.add(connection.state()), ignored -> { });

        assertEquals(2, subscriptions.get());
        assertEquals(3, inventoryReads.get());
        assertEquals(List.of(EdgeConnectionState.AVAILABLE, EdgeConnectionState.UNAVAILABLE,
                EdgeConnectionState.AVAILABLE), states);
    }

    private static EdgeApiClient client(EdgeHealthResponseV1 health) {
        return new EdgeApiClient() {
            @Override public EdgeHealthResponseV1 health() { return health; }
            @Override public CameraListResponseV1 listCameras() { throw new AssertionError(); }
            @Override public CameraDiscoveryResponseV1 discoverCameras(CameraDiscoveryRequestV1 request) {
                throw new AssertionError();
            }
            @Override public RegisterCameraResponseV1 registerCamera(RegisterCameraRequestV1 request) {
                throw new AssertionError();
            }
            @Override public org.example.aisurv.contract.v1.CameraDetailV1 getCamera(java.util.UUID id) { throw new AssertionError(); }
            @Override public org.example.aisurv.contract.v1.CameraDetailV1 updateCamera(java.util.UUID id, org.example.aisurv.contract.v1.UpdateCameraRequestV1 request) { throw new AssertionError(); }
            @Override public org.example.aisurv.contract.v1.CameraSummaryV1 setCameraState(java.util.UUID id, org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1 request) { throw new AssertionError(); }
            @Override public void deleteCamera(java.util.UUID id, long version) { throw new AssertionError(); }
            @Override public byte[] snapshot(java.util.UUID id) { throw new AssertionError(); }
        };
    }
}
