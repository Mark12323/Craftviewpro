package org.example.aisurv.contract.v1;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum CameraPriorityV1 {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
    @JsonEnumDefaultValue UNKNOWN
}
