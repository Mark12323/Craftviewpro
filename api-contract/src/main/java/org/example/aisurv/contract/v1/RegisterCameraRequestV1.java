package org.example.aisurv.contract.v1;

public record RegisterCameraRequestV1(
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
        CameraPriorityV1 priority,
        boolean enabled,
        String credentialReference
) {
}
