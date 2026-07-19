package org.example.aisurv.contract.v1;

public record SetCameraConfigurationStateRequestV1(
        Long expectedVersion, CameraConfigurationStateV1 configurationState
) { }
