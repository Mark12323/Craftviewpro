package org.example.aisurv.contract.v1;

import java.util.UUID;

public record CameraSummaryV1(
        UUID id,
        String displayName,
        String location,
        String building,
        String floor,
        String zone,
        CameraPriorityV1 priority,
        CameraConfigurationStateV1 configurationState,
        long version,
        CameraRuntimeHealthV1 runtimeHealth
) {
}
