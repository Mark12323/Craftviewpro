package org.example.aisurv.edgeclient;

import org.example.aisurv.contract.v1.CameraListResponseV1;
import org.example.aisurv.contract.v1.CameraDiscoveryRequestV1;
import org.example.aisurv.contract.v1.CameraDiscoveryResponseV1;
import org.example.aisurv.contract.v1.EdgeHealthResponseV1;
import org.example.aisurv.contract.v1.RegisterCameraRequestV1;
import org.example.aisurv.contract.v1.RegisterCameraResponseV1;
import org.example.aisurv.contract.v1.CameraDetailV1;
import org.example.aisurv.contract.v1.CameraSummaryV1;
import org.example.aisurv.contract.v1.UpdateCameraRequestV1;
import org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1;
import java.util.UUID;

public interface EdgeApiClient {
    EdgeHealthResponseV1 health();

    CameraListResponseV1 listCameras();

    CameraDiscoveryResponseV1 discoverCameras(CameraDiscoveryRequestV1 request);

    RegisterCameraResponseV1 registerCamera(RegisterCameraRequestV1 request);
    CameraDetailV1 getCamera(UUID id);
    CameraDetailV1 updateCamera(UUID id, UpdateCameraRequestV1 request);
    CameraSummaryV1 setCameraState(UUID id, SetCameraConfigurationStateRequestV1 request);
    void deleteCamera(UUID id, long version);
    byte[] snapshot(UUID id);

    default CameraUpdateSubscription openCameraUpdates() {
        throw new IncompatibleEdgeException("The edge service does not provide camera updates");
    }
}
