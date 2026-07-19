package org.example.aisurv.contract.v1;

public record UpdateCameraRequestV1(
        Long expectedVersion, String displayName, String replacementRtspUrl,
        String onvifServiceUrl, String manufacturer, String model, String host,
        String location, String building, String floor, String zone, CameraPriorityV1 priority
) { }
