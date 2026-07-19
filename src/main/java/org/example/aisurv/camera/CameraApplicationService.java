package org.example.aisurv.camera;

import org.example.aisurv.app.CameraDefinition;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface CameraApplicationService {
    List<CameraSummary> listCameras();

    List<CameraDefinition> listEnabledCameras();

    List<DiscoveredCamera> discover(Duration timeout);

    CameraRegistrationResult register(CameraRegistrationRequest request);

    CameraDetails getCamera(UUID id);
    CameraDetails updateCamera(UUID id, long expectedVersion, CameraUpdateRequest request);
    CameraSummary setEnabled(UUID id, long expectedVersion, boolean enabled);
    void deleteCamera(UUID id, long expectedVersion);
}
