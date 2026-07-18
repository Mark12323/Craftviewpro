package org.example.aisurv.event;

import java.time.Instant;
import java.util.Objects;

public record CameraHealthEvent(
        String cameraName,
        CameraHealthState state,
        String detail,
        Instant occurredAt
) {
    public CameraHealthEvent {
        Objects.requireNonNull(cameraName, "cameraName");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
