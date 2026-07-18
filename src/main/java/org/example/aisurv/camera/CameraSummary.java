package org.example.aisurv.camera;

import java.util.UUID;

public record CameraSummary(
        UUID id,
        String displayName,
        String location,
        String building,
        String floor,
        String zone,
        CameraPriority priority,
        CameraStatus status,
        boolean enabled
) {
}
