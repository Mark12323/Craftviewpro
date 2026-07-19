package org.example.aisurv.contract.v1;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum ComponentStatusV1 {
    STARTING,
    UP,
    DOWN,
    @JsonEnumDefaultValue UNKNOWN
}
