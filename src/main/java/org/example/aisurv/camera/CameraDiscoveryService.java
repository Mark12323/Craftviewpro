package org.example.aisurv.camera;

import java.time.Duration;
import java.util.List;

public interface CameraDiscoveryService {
    List<DiscoveredCamera> discover(Duration timeout);
}
