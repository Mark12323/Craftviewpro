package org.example.aisurv.camera;

import org.example.aisurv.event.CameraHealthState;

import java.time.Instant;
import java.util.UUID;

public record CameraRuntimeHealth(
        UUID cameraId,
        CameraHealthState state,
        String reasonCode,
        Instant stateChangedAt,
        Instant lastSeenAt,
        Instant onlineSince,
        long accumulatedUptimeMillis,
        long reconnectAttempts,
        Instant updatedAt
) {
}
