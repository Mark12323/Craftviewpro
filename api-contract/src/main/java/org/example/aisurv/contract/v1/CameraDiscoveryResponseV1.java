package org.example.aisurv.contract.v1;

import java.time.Instant;
import java.util.List;

public record CameraDiscoveryResponseV1(List<DiscoveredCameraV1> cameras, Instant observedAt) {
    public CameraDiscoveryResponseV1 {
        cameras = List.copyOf(cameras);
    }
}
