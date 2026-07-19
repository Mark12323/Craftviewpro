package org.example.aisurv.contract.v1;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum CameraOperationalStateV1 {
    CONNECTING,
    ONLINE,
    RECONNECTING,
    IMPAIRED,
    OFFLINE,
    FROZEN,
    STOPPED,
    DISABLED,
    @JsonEnumDefaultValue UNKNOWN
}
