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
import java.util.UUID;

public final class LocalCameraApplicationService implements CameraApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCameraApplicationService.class);
    private final CameraRepositoryProvider repositories;
    private final CameraDiscoveryService discoveryService;
    private final RtspConnectivityProbe connectivityProbe;

    public LocalCameraApplicationService(CameraRepositoryProvider repositories) {
        this(repositories, new OnvifCameraDiscoveryService(), new SocketRtspConnectivityProbe());
    }

    LocalCameraApplicationService(CameraRepositoryProvider repositories, CameraDiscoveryService discoveryService) {
        this(repositories, discoveryService, ignored -> { });
    }

    LocalCameraApplicationService(CameraRepositoryProvider repositories, CameraDiscoveryService discoveryService,
                                  RtspConnectivityProbe connectivityProbe) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService");
        this.connectivityProbe = Objects.requireNonNull(connectivityProbe, "connectivityProbe");
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
        if (request.credentialReference() != null && !request.credentialReference().isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated cameras are deferred to the secure credential milestone");
        }
        var repository = repositories.cameraRepository();
        rejectDuplicates(repository, request.displayName(), request.rtspUrl(), null);
        connectivityProbe.validate(request.rtspUrl());
        CameraEntity camera = new CameraRegistrationService(repository).register(request);
        return new CameraRegistrationResult(camera.id(), camera.displayName(), camera.enabled(), camera.version());
    }

    @Override
    public CameraDetails getCamera(UUID id) {
        return details(repositories.cameraRepository().findById(id).orElseThrow(CameraNotFoundException::new));
    }

    @Override
    public CameraDetails updateCamera(UUID id, long expectedVersion, CameraUpdateRequest request) {
        var repository = repositories.cameraRepository();
        CameraEntity existing = repository.findById(id).orElseThrow(CameraNotFoundException::new);
        String stream = request.replacementRtspUrl() == null ? existing.rtspUrl() : request.replacementRtspUrl();
        rejectDuplicates(repository, request.displayName(), stream, id);
        if (request.replacementRtspUrl() != null) connectivityProbe.validate(stream);
        return details(repository.update(
                id, expectedVersion, CameraAuditAction.UPDATED, camera -> camera.applyUpdate(request)));
    }

    @Override
    public CameraSummary setEnabled(UUID id, long expectedVersion, boolean enabled) {
        if (enabled) {
            CameraEntity existing = repositories.cameraRepository().findById(id)
                    .orElseThrow(CameraNotFoundException::new);
            connectivityProbe.validate(existing.rtspUrl());
        }
        return summary(repositories.cameraRepository().update(id, expectedVersion,
                enabled ? CameraAuditAction.ENABLED : CameraAuditAction.DISABLED,
                camera -> camera.setEnabled(enabled)));
    }

    @Override
    public void deleteCamera(UUID id, long expectedVersion) {
        repositories.cameraRepository().delete(id, expectedVersion);
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
                camera.enabled(),
                camera.version()
        );
    }

    private static CameraDetails details(CameraEntity camera) {
        return new CameraDetails(camera.id(), camera.displayName(), camera.onvifServiceUrl(),
                camera.manufacturer(), camera.model(), camera.host(), camera.location(), camera.building(),
                camera.floor(), camera.zone(), camera.priority(), camera.enabled(), camera.version());
    }

    private static void rejectDuplicates(org.example.aisurv.persistence.repositories.CameraRepository repository,
                                         String name, String stream, UUID excludedId) {
        if (name != null && repository.duplicateName(name, excludedId))
            throw new DuplicateCameraException(DuplicateCameraException.Field.DISPLAY_NAME);
        if (stream != null && repository.duplicateStream(stream, excludedId))
            throw new DuplicateCameraException(DuplicateCameraException.Field.STREAM);
    }
}
