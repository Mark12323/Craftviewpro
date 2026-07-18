package org.example.aisurv.camera;

import java.time.Instant;

public record DiscoveredCamera(
        String deviceId,
        String manufacturer,
        String model,
        String host,
        String onvifServiceUrl,
        boolean requiresAuthentication,
        Instant discoveredAt
) {
}
