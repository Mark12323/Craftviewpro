package org.example.aisurv.edge.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import org.example.aisurv.event.CameraHealthEvent;
import org.example.aisurv.event.CameraHealthState;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EdgeStreamSurfaceTest {
    @Test
    void storesDefensiveSnapshotCopiesAndDropsRemovedCameraFrames() {
        UUID id = UUID.randomUUID();
        EdgeStreamSurface surface = new EdgeStreamSurface();
        surface.bind(Map.of("Entrance", id));
        surface.updateFrame("Entrance", TestImages.jpegSource());

        byte[] first = surface.snapshot(id).orElseThrow();
        byte original = first[0];
        first[0] = (byte) (original + 1);

        assertTrue(surface.snapshot(id).isPresent());
        assertArrayEquals(new byte[]{original}, new byte[]{surface.snapshot(id).orElseThrow()[0]});
        surface.onCameraHealthEvent(new CameraHealthEvent(
                "Entrance", CameraHealthState.OFFLINE, "Unavailable", Instant.now()));
        assertFalse(surface.snapshot(id).isPresent());
        surface.updateFrame("Entrance", TestImages.jpegSource());
        surface.bind(Map.of());
        surface.updateFrame("Entrance", TestImages.jpegSource());
        assertFalse(surface.snapshot(id).isPresent());
    }

    @Test
    void forwardsStructuredHealthAndLastSeenByStableCameraId() {
        UUID id = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-07-19T00:00:00Z");
        CameraHealthService health = mock(CameraHealthService.class);
        EdgeStreamSurface surface = new EdgeStreamSurface(health);
        surface.bind(Map.of("Entrance", id));
        CameraHealthEvent event = new CameraHealthEvent(
                "Entrance", CameraHealthState.ONLINE, "Connected", observedAt);

        surface.onCameraHealthEvent(event);
        surface.onFrameObserved("Entrance", observedAt);

        verify(health).stateChanged(id, event);
        verify(health).frameObserved(id, observedAt);
    }
}
