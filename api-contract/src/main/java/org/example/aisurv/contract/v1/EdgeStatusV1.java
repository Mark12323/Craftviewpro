package org.example.aisurv.contract.v1;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum EdgeStatusV1 {
    STARTING,
    READY,
    DEGRADED,
    @JsonEnumDefaultValue UNKNOWN
}
