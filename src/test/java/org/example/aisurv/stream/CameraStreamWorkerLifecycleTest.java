package org.example.aisurv.stream;

import org.example.aisurv.detection.PersonDetector;
import org.example.aisurv.event.CameraHealthEvent;
import org.example.aisurv.event.CameraHealthState;
import org.example.aisurv.event.SurveillanceEvent;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CameraStreamWorkerLifecycleTest {
    @Test
    void stopBeforeStartClosesConfiguredDetectorAndTerminates() {
        PersonDetector detector = mock(PersonDetector.class);
        AtomicReference<CameraHealthState> health = new AtomicReference<>();
        CameraStreamWorker worker = new CameraStreamWorker(
                "Gate",
                "rtsp://gate/live",
                new HealthMonitor(health),
                new CameraStreamWorker.DetectorInitialization(detector, null)
        );

        worker.requestStop();
        worker.run();

        verify(detector).close();
        assertTrue(worker.isStopRequested());
        assertEquals(CameraStreamWorker.State.TERMINATED, worker.state());
        assertEquals(CameraHealthState.STOPPED, health.get());
    }

    private record HealthMonitor(AtomicReference<CameraHealthState> health) implements StreamMonitorSurface {
        @Override
        public void updateFrame(String cameraName, BufferedImage image) {
        }

        @Override
        public void onSurveillanceEvent(SurveillanceEvent event) {
        }

        @Override
        public void onCameraHealthEvent(CameraHealthEvent event) {
            health.set(event.state());
        }

        @Override
        public void logEvent(String cameraName, String message) {
        }
    }
}
