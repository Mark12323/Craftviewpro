package org.example.aisurv.contract.v1;

import java.util.UUID;

public record RegisterCameraResponseV1(
        UUID id,
        String displayName,
        CameraConfigurationStateV1 configurationState,
        long version
) {
}
