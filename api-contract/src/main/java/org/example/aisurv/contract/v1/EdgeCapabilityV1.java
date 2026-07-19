package org.example.aisurv.contract.v1;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum EdgeCapabilityV1 {
    CAMERA_QUERY,
    CAMERA_DISCOVERY,
    CAMERA_REGISTRATION,
    CAMERA_LIFECYCLE,
    EDGE_MONITORING,
    CAMERA_UPDATE_STREAM,
    CAMERA_RUNTIME_HEALTH,
    @JsonEnumDefaultValue UNKNOWN
}
