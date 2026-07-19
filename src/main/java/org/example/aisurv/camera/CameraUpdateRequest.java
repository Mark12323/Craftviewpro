package org.example.aisurv.camera;

public record CameraUpdateRequest(
        String displayName, String replacementRtspUrl, String onvifServiceUrl,
        String manufacturer, String model, String host, String location,
        String building, String floor, String zone, CameraPriority priority
) {
}
