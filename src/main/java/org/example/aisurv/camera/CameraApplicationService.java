package org.example.aisurv.camera;

import org.example.aisurv.app.CameraDefinition;

import java.time.Duration;
import java.util.List;

public interface CameraApplicationService {
    List<CameraSummary> listCameras();

    List<CameraDefinition> listEnabledCameras();

    List<DiscoveredCamera> discover(Duration timeout);

    CameraRegistrationResult register(CameraRegistrationRequest request);
}
