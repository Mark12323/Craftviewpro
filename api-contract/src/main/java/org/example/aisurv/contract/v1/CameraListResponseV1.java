package org.example.aisurv.contract.v1;

import java.time.Instant;
import java.util.List;

public record CameraListResponseV1(List<CameraSummaryV1> cameras, Instant observedAt) {
    public CameraListResponseV1 {
        cameras = List.copyOf(cameras);
    }
}
