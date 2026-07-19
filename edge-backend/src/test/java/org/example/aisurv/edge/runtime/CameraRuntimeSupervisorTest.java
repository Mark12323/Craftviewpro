package org.example.aisurv.edge.runtime;

import org.example.aisurv.app.CameraDefinition;
import org.example.aisurv.camera.CameraApplicationService;
import org.example.aisurv.stream.StreamManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

class CameraRuntimeSupervisorTest {
    @Test
    void replacesWorkersAndPublishesFramesByStableCameraId() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CameraDefinition entrance = camera("Entrance");
        CameraDefinition yard = camera("Yard");
        when(cameras.listEnabledCameras()).thenReturn(List.of(entrance)).thenReturn(List.of(yard));
        EdgeStreamSurface surface = new EdgeStreamSurface();
        StreamManager manager = mock(StreamManager.class);
        when(manager.start(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(manager.stop(entrance.id())).thenReturn(true);
        CameraRuntimeSupervisor supervisor = new CameraRuntimeSupervisor(cameras, surface,
                ignored -> manager);

        supervisor.reconcile();
        surface.updateFrame(entrance.name(), TestImages.jpegSource());
        byte[] snapshot = supervisor.snapshot(entrance.id()).orElseThrow();
        supervisor.reconcile();

        verify(manager).start(entrance, 1);
        verify(manager).stop(entrance.id());
        verify(manager).start(yard, 1);
        assertTrue(snapshot.length > 0);
        assertThrows(java.util.NoSuchElementException.class,
                () -> supervisor.snapshot(entrance.id()).orElseThrow());
    }

    @Test
    void keepsCurrentWorkersWhenTheRegistryCannotBeRead() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CameraDefinition entrance = camera("Entrance");
        when(cameras.listEnabledCameras()).thenReturn(List.of(entrance))
                .thenThrow(new IllegalStateException("database unavailable"));
        StreamManager manager = mock(StreamManager.class);
        when(manager.start(entrance, 1)).thenReturn(true);
        CameraRuntimeSupervisor supervisor = new CameraRuntimeSupervisor(cameras, new EdgeStreamSurface(),
                ignored -> manager);
        supervisor.reconcile();

        assertThrows(IllegalStateException.class, supervisor::reconcile);

        verify(manager, never()).stopAll();
    }

    @Test
    void stopsPartiallyStartedReplacementAfterStartupFailure() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CameraDefinition entrance = camera("Entrance");
        when(cameras.listEnabledCameras()).thenReturn(List.of(entrance));
        StreamManager replacement = mock(StreamManager.class);
        doThrow(new IllegalStateException("worker startup failed")).when(replacement).start(entrance, 1);
        CameraRuntimeSupervisor supervisor = new CameraRuntimeSupervisor(cameras, new EdgeStreamSurface(),
                ignored -> replacement);

        assertThrows(IllegalStateException.class, supervisor::reconcile);

        verify(replacement).stop(entrance.id());
    }

    @Test
    void retainsAnUnchangedCameraWorkerAcrossReconciliation() {
        CameraApplicationService cameras = mock(CameraApplicationService.class);
        CameraDefinition entrance = camera("Entrance");
        when(cameras.listEnabledCameras()).thenReturn(List.of(entrance));
        StreamManager manager = mock(StreamManager.class);
        when(manager.start(entrance, 1)).thenReturn(true);
        CameraRuntimeSupervisor supervisor = new CameraRuntimeSupervisor(
                cameras, new EdgeStreamSurface(), ignored -> manager);

        supervisor.reconcile();
        supervisor.reconcile();

        verify(manager, times(1)).start(entrance, 1);
        verify(manager, never()).stop(any(UUID.class));
    }

    private static CameraDefinition camera(String name) {
        return new CameraDefinition(UUID.randomUUID(), name, "rtsp://camera.local/" + name.toLowerCase());
    }
}
