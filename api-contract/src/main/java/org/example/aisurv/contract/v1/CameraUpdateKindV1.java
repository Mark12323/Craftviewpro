package org.example.aisurv.contract.v1;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum CameraUpdateKindV1 {
    RESET,
    UPSERTED,
    DELETED,
    HEALTH_CHANGED,
    @JsonEnumDefaultValue UNKNOWN
}
