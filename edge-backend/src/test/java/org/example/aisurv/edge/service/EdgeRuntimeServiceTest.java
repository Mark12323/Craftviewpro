package org.example.aisurv.edge.service;

import org.example.aisurv.camera.CameraApplicationService;
import org.example.aisurv.camera.CameraPriority;
import org.example.aisurv.camera.CameraRegistrationResult;
import org.example.aisurv.camera.CameraStatus;
import org.example.aisurv.camera.CameraSummary;
import org.example.aisurv.camera.DiscoveredCamera;
import org.example.aisurv.camera.CameraDetails;
import org.example.aisurv.contract.v1.CameraConfigurationStateV1;
import org.example.aisurv.contract.v1.CameraDiscoveryRequestV1;
import org.example.aisurv.contract.v1.CameraPriorityV1;
import org.example.aisurv.contract.v1.ComponentStatusV1;
import org.example.aisurv.contract.v1.EdgeStatusV1;
import org.example.aisurv.contract.v1.RegisterCameraRequestV1;
import org.example.aisurv.contract.v1.UpdateCameraRequestV1;
import org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1;
import org.example.aisurv.edge.runtime.CameraRuntimeSupervisor;
import org.example.aisurv.edge.runtime.CameraHealthService;
import org.example.aisurv.edge.update.CameraUpdateBroker;
import org.example.aisurv.contract.v1.CameraUpdateKindV1;
import org.example.aisurv.contract.v1.CameraOperationalStateV1;
import org.example.aisurv.camera.CameraRuntimeHealth;
import org.example.aisurv.event.CameraHealthState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class EdgeRuntimeServiceTest {
    @Test
    void exposesRedactedDeterministicCameraInventoryWhenDatabaseIsReady() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CameraSummary yard = summary("Yard", false);
        CameraSummary entrance = summary("Entrance", true);
        when(cameras.listCameras()).thenReturn(List.of(yard, entrance));
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");
        runtime.probeNow();

        assertEquals(EdgeStatusV1.READY, runtime.health().status());
        assertEquals(ComponentStatusV1.UP, runtime.health().components().get("database").status());
        assertEquals(List.of("Entrance", "Yard"), runtime.cameras().cameras().stream()
                .map(camera -> camera.displayName()).toList());
        assertEquals(CameraConfigurationStateV1.ENABLED,
                runtime.cameras().cameras().getFirst().configurationState());
        runtime.stop();
    }

    @Test
    void remainsHealthyOverHttpButRejectsQueriesWhenDatabaseIsDown() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        when(cameras.listCameras()).thenThrow(new IllegalStateException("jdbc:postgresql://secret/password"));
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");
        runtime.probeNow();

        assertEquals(EdgeStatusV1.DEGRADED, runtime.health().status());
        assertThrows(DependencyUnavailableException.class, runtime::cameras);
        runtime.stop();
    }

    @Test
    void retriesInitialMonitoringStartupAfterReconciliationFails() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        when(cameras.listCameras()).thenReturn(List.of());
        CameraRuntimeSupervisor supervisor = mock(CameraRuntimeSupervisor.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("registry changed during startup");
        }).doNothing().when(supervisor).reconcile();
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test", supervisor);

        runtime.probeNow();
        assertEquals(EdgeStatusV1.DEGRADED, runtime.health().status());
        runtime.probeNow();

        assertEquals(EdgeStatusV1.READY, runtime.health().status());
        verify(supervisor, times(2)).reconcile();
        runtime.stop();
    }

    @Test
    void shutdownInterruptsAndJoinsAnActiveDatabaseProbe() throws Exception {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CountDownLatch probeStarted = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        doAnswer(invocation -> {
            probeStarted.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                throw new IllegalStateException("probe cancelled", e);
            }
            return List.of();
        }).when(cameras).listCameras();
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");
        runtime.start();
        assertTrue(probeStarted.await(2, TimeUnit.SECONDS));

        runtime.stop();

        assertTrue(interrupted.get());
    }

    @Test
    void mapsDiscoveryAndRegistrationWithoutExposingDomainTypes() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        when(cameras.listCameras()).thenReturn(List.of());
        when(cameras.discover(Duration.ofSeconds(5))).thenReturn(List.of(new DiscoveredCamera(
                "device-1", "Vendor", "Model", "127.0.0.2", "http://127.0.0.2/onvif",
                true, Instant.parse("2026-07-19T00:00:00Z"))));
        UUID id = UUID.randomUUID();
        when(cameras.register(any())).thenReturn(new CameraRegistrationResult(id, "Entrance", true, 0));
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");
        runtime.probeNow();

        assertEquals("device-1", runtime.discover(new CameraDiscoveryRequestV1(5)).cameras().getFirst().deviceId());
        var registered = runtime.register(new RegisterCameraRequestV1(
                "Entrance", "rtsp://camera/live", null, null, null, "camera.local",
                "Campus", null, null, "Gate", CameraPriorityV1.HIGH, true, null));

        assertEquals(id, registered.id());
        assertEquals(CameraConfigurationStateV1.ENABLED, registered.configurationState());
        verify(cameras).register(any());
        runtime.stop();
    }

    @Test
    void rejectsInvalidDiscoveryTimeoutAndUnknownPriority() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        when(cameras.listCameras()).thenReturn(List.of());
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");
        runtime.probeNow();

        assertThrows(IllegalArgumentException.class,
                () -> runtime.discover(new CameraDiscoveryRequestV1(0)));
        assertThrows(IllegalArgumentException.class, () -> runtime.register(new RegisterCameraRequestV1(
                "Entrance", "rtsp://camera/live", null, null, null, null,
                null, null, null, null, CameraPriorityV1.UNKNOWN, true, null)));
        runtime.stop();
    }

    @Test
    void rejectsOversizedRegistrationWithoutDegradingDatabaseHealth() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        when(cameras.listCameras()).thenReturn(List.of());
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");
        runtime.probeNow();
        RegisterCameraRequestV1 oversized = new RegisterCameraRequestV1(
                "x".repeat(161), "rtsp://camera/live", null, null, null, null,
                null, null, null, null, CameraPriorityV1.NORMAL, true, null);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> runtime.register(oversized));

        assertEquals("Camera display name must not exceed 160 characters", failure.getMessage());
        assertEquals(EdgeStatusV1.READY, runtime.health().status());
        verify(cameras, never()).register(any());
        runtime.stop();
    }

    @Test
    void permitsOnlyOneDiscoveryAtATime() throws Exception {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(cameras.discover(Duration.ofSeconds(5))).thenAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return List.of();
        });
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(() -> runtime.discover(new CameraDiscoveryRequestV1(5)));
            assertTrue(started.await(2, TimeUnit.SECONDS));

            assertThrows(OperationInProgressException.class,
                    () -> runtime.discover(new CameraDiscoveryRequestV1(5)));
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            runtime.stop();
        }
    }

    @Test
    void returnsSanitizedDiscoveryFailure() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        when(cameras.discover(Duration.ofSeconds(5)))
                .thenThrow(new IllegalStateException("socket permission details"));
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");

        CameraDiscoveryFailedException failure = assertThrows(
                CameraDiscoveryFailedException.class,
                () -> runtime.discover(new CameraDiscoveryRequestV1(5)));

        assertEquals("Camera discovery could not access the local network", failure.getMessage());
        runtime.stop();
    }

    @Test
    void classifiesPostgresShutdownDuringRegistrationAsUnavailable() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        when(cameras.listCameras()).thenReturn(List.of());
        when(cameras.register(any())).thenThrow(new IllegalStateException(
                "persistence failed", new SQLException("database is starting", "57P03")));
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test");
        runtime.probeNow();

        assertThrows(DependencyUnavailableException.class, () -> runtime.register(new RegisterCameraRequestV1(
                "Entrance", "rtsp://camera/live", null, null, null, null,
                null, null, null, null, CameraPriorityV1.NORMAL, true, null)));

        assertEquals(EdgeStatusV1.DEGRADED, runtime.health().status());
        runtime.stop();
    }

    @Test
    void mapsVersionedLifecycleCommands() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CameraUpdateBroker updates = mock(CameraUpdateBroker.class);
        UUID id = UUID.randomUUID();
        CameraDetails details = new CameraDetails(id, "Entrance", null, null, null, "camera.local",
                "Campus", null, null, "Gate", CameraPriority.HIGH, true, 2);
        when(cameras.listCameras()).thenReturn(List.of());
        when(cameras.getCamera(id)).thenReturn(details);
        when(cameras.updateCamera(any(), anyLong(), any())).thenReturn(details);
        when(cameras.setEnabled(id, 2, false)).thenReturn(new CameraSummary(id, "Entrance", "Campus",
                null, null, "Gate", CameraPriority.HIGH, CameraStatus.DISABLED, false, 3));
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test", null, updates);
        runtime.probeNow();

        assertEquals(2, runtime.camera(id).version());
        assertEquals(2, runtime.update(id, new UpdateCameraRequestV1(2L, "Entrance", null, null,
                null, null, "camera.local", "Campus", null, null, "Gate", CameraPriorityV1.HIGH)).version());
        assertEquals(3, runtime.setConfigurationState(id,
                new SetCameraConfigurationStateRequestV1(2L, CameraConfigurationStateV1.DISABLED)).version());
        runtime.delete(id, 3);
        verify(cameras).deleteCamera(id, 3);
        verify(updates).publish(CameraUpdateKindV1.UPSERTED, id, 2L);
        verify(updates).publish(CameraUpdateKindV1.UPSERTED, id, 3L);
        verify(updates).publish(CameraUpdateKindV1.DELETED, id, 3L);
        runtime.stop();
    }

    @Test
    void retriesReconciliationAfterARegistryMutationStartupFailure() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        UUID id = UUID.randomUUID();
        when(cameras.listCameras()).thenReturn(List.of());
        when(cameras.setEnabled(id, 2, false)).thenReturn(new CameraSummary(id, "Entrance", "Campus",
                null, null, "Gate", CameraPriority.HIGH, CameraStatus.DISABLED, false, 3));
        CameraRuntimeSupervisor supervisor = mock(CameraRuntimeSupervisor.class);
        doAnswer(invocation -> null)
                .doThrow(new IllegalStateException("worker startup failed"))
                .doAnswer(invocation -> null)
                .when(supervisor).reconcile();
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test", supervisor);
        runtime.probeNow();

        runtime.setConfigurationState(id,
                new SetCameraConfigurationStateRequestV1(2L, CameraConfigurationStateV1.DISABLED));
        runtime.probeNow();

        verify(supervisor, times(3)).reconcile();
        assertEquals(EdgeStatusV1.READY, runtime.health().status());
        runtime.stop();
    }

    @Test
    void exposesPersistedRuntimeHealthInCameraInventory() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CameraSummary entrance = summary("Entrance", true);
        when(cameras.listCameras()).thenReturn(List.of(entrance));
        CameraHealthService health = mock(CameraHealthService.class);
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        when(health.health()).thenReturn(java.util.Map.of(entrance.id(), new CameraRuntimeHealth(
                entrance.id(), CameraHealthState.ONLINE, "STREAM_CONNECTED", now, now,
                now, 2_000, 1, now)));
        EdgeRuntimeService runtime = new EdgeRuntimeService(cameras, "test", null, null, health);
        runtime.probeNow();

        var response = runtime.cameras().cameras().getFirst();

        assertEquals(CameraOperationalStateV1.ONLINE, response.runtimeHealth().state());
        assertEquals(now, response.runtimeHealth().lastSeenAt());
        assertEquals(2_000, response.runtimeHealth().accumulatedUptimeMillis());
        runtime.stop();
    }

    private static CameraSummary summary(String name, boolean enabled) {
        return new CameraSummary(UUID.randomUUID(), name, "Campus", null, null, "Gate",
                CameraPriority.HIGH, enabled ? CameraStatus.REGISTERED : CameraStatus.DISABLED, enabled, 0);
    }
}
