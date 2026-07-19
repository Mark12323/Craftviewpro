package org.example.aisurv.contract.v1;

import java.time.Instant;
import java.util.UUID;

public record CameraUpdateEventV1(
        UUID streamInstanceId,
        long sequence,
        CameraUpdateKindV1 kind,
        UUID cameraId,
        Long cameraVersion,
        Instant observedAt
) {
}
