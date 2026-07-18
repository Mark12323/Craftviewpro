package org.example.aisurv.camera;

public record CameraRegistrationRequest(
        String displayName,
        String rtspUrl,
        String onvifServiceUrl,
        String manufacturer,
        String model,
        String host,
        String location,
        String building,
        String floor,
        String zone,
        CameraPriority priority,
        boolean enabled,
        String credentialReference
) {
}
