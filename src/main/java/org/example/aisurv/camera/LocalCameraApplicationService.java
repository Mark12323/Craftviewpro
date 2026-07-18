package org.example.aisurv.camera;

import org.example.aisurv.app.CameraDefinition;
import org.example.aisurv.persistence.CameraRepositoryProvider;
import org.example.aisurv.persistence.entities.CameraEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LocalCameraApplicationService implements CameraApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCameraApplicationService.class);
    private final CameraRepositoryProvider repositories;
    private final CameraDiscoveryService discoveryService;

    public LocalCameraApplicationService(CameraRepositoryProvider repositories) {
        this(repositories, new OnvifCameraDiscoveryService());
    }

    LocalCameraApplicationService(CameraRepositoryProvider repositories, CameraDiscoveryService discoveryService) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService");
    }

    @Override
    public List<CameraSummary> listCameras() {
        return repositories.cameraRepository().findAll().stream()
                .map(LocalCameraApplicationService::summary)
                .toList();
    }

    @Override
    public List<CameraDefinition> listEnabledCameras() {
        List<CameraDefinition> cameras = new ArrayList<>();
        for (CameraEntity camera : repositories.cameraRepository().findEnabled()) {
            try {
                cameras.add(camera.toCameraDefinition());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Skipping malformed persisted camera row {}", camera.id());
            }
        }
        return List.copyOf(cameras);
    }

    @Override
    public List<DiscoveredCamera> discover(Duration timeout) {
        return discoveryService.discover(timeout);
    }

    @Override
    public CameraRegistrationResult register(CameraRegistrationRequest request) {
        CameraEntity camera = new CameraRegistrationService(repositories.cameraRepository()).register(request);
        return new CameraRegistrationResult(camera.id(), camera.displayName(), camera.enabled());
    }

    private static CameraSummary summary(CameraEntity camera) {
        return new CameraSummary(
                camera.id(),
                camera.displayName(),
                camera.location(),
                camera.building(),
                camera.floor(),
                camera.zone(),
                camera.priority(),
                camera.status(),
                camera.enabled()
        );
    }
}
