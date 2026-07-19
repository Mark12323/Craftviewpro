package org.example.aisurv.contract.v1;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum CameraConfigurationStateV1 {
    ENABLED,
    DISABLED,
    @JsonEnumDefaultValue UNKNOWN
}
