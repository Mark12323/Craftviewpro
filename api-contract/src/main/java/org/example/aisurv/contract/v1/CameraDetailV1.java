package org.example.aisurv.contract.v1;

import java.util.UUID;

public record CameraDetailV1(
        UUID id, String displayName, String onvifServiceUrl, String manufacturer,
        String model, String host, String location, String building, String floor,
        String zone, CameraPriorityV1 priority, CameraConfigurationStateV1 configurationState,
        long version
) { }
