package org.example.aisurv.persistence;

import org.example.aisurv.persistence.repositories.CameraRepository;

@FunctionalInterface
public interface CameraRepositoryProvider {
    CameraRepository cameraRepository();
}
