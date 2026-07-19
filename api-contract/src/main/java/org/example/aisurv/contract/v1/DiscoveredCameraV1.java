package org.example.aisurv.contract.v1;

import java.time.Instant;

public record DiscoveredCameraV1(
        String deviceId,
        String manufacturer,
        String model,
        String host,
        String onvifServiceUrl,
        boolean requiresAuthentication,
        Instant discoveredAt
) {
}
