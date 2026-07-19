package org.example.aisurv.camera;

import java.util.UUID;

public record CameraDetails(
        UUID id, String displayName, String onvifServiceUrl, String manufacturer,
        String model, String host, String location, String building, String floor,
        String zone, CameraPriority priority, boolean enabled, long version
) {
}
