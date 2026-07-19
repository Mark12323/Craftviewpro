package org.example.aisurv.contract.v1;

import java.time.Instant;

public record CameraRuntimeHealthV1(
        CameraOperationalStateV1 state,
        String reasonCode,
        Instant stateChangedAt,
        Instant lastSeenAt,
        long accumulatedUptimeMillis,
        long reconnectAttempts,
        Instant observedAt
) {
}
