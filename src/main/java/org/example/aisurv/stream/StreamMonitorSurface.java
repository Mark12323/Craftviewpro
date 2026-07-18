package org.example.aisurv.stream;

import org.example.aisurv.event.CameraHealthEvent;
import org.example.aisurv.event.SurveillanceEvent;

import java.awt.image.BufferedImage;

public interface StreamMonitorSurface {
    void updateFrame(String cameraName, BufferedImage image);

    default void onSurveillanceEvent(SurveillanceEvent event) {
        logEvent(event.cameraName(), event.message());
    }

    default void onCameraHealthEvent(CameraHealthEvent event) {
        logEvent(event.cameraName(), event.detail());
    }

    /**
     * Legacy unstructured notification hook. New stream code uses the typed hooks above.
     */
    void logEvent(String cameraName, String message);
}
