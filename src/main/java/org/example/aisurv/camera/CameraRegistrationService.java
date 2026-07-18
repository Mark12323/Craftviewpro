package org.example.aisurv.camera;

import org.example.aisurv.persistence.entities.CameraEntity;
import org.example.aisurv.persistence.repositories.CameraRepository;

public class CameraRegistrationService {
    private final CameraRepository cameraRepository;

    public CameraRegistrationService(CameraRepository cameraRepository) {
        this.cameraRepository = cameraRepository;
    }

    public CameraEntity register(CameraRegistrationRequest request) {
        return cameraRepository.save(CameraEntity.from(request));
    }
}
