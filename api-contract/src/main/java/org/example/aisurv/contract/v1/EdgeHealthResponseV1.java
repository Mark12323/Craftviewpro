package org.example.aisurv.contract.v1;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record EdgeHealthResponseV1(
        ApiVersionV1 apiVersion,
        String edgeVersion,
        EdgeStatusV1 status,
        Set<EdgeCapabilityV1> capabilities,
        Map<String, ComponentHealthV1> components,
        Instant observedAt
) {
}
