package org.example.aisurv.camera;

import org.example.aisurv.app.CameraDefinition;
import org.example.aisurv.persistence.entities.CameraEntity;
import org.example.aisurv.persistence.repositories.CameraRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalCameraApplicationServiceTest {
    @Test
    void listsRegisteredCamerasAsRedactedSummaries() {
        CameraRepository repository = mock(CameraRepository.class);
        CameraEntity entity = mock(CameraEntity.class);
        UUID id = UUID.randomUUID();
        when(entity.id()).thenReturn(id);
        when(entity.displayName()).thenReturn("Entrance");
        when(entity.location()).thenReturn("North Campus");
        when(entity.zone()).thenReturn("Gate");
        when(entity.priority()).thenReturn(CameraPriority.HIGH);
        when(entity.status()).thenReturn(CameraStatus.REGISTERED);
        when(entity.enabled()).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(entity));
        LocalCameraApplicationService service = service(repository, mock(CameraDiscoveryService.class));

        List<CameraSummary> cameras = service.listCameras();

        assertEquals(1, cameras.size());
        CameraSummary camera = cameras.getFirst();
        assertEquals(id, camera.id());
        assertEquals("Entrance", camera.displayName());
        assertEquals("North Campus", camera.location());
        assertEquals("Gate", camera.zone());
        assertEquals(CameraPriority.HIGH, camera.priority());
        assertEquals(CameraStatus.REGISTERED, camera.status());
        assertEquals(true, camera.enabled());
    }

    @Test
    void skipsOnlyMalformedEnabledCameraRows() {
        CameraRepository repository = mock(CameraRepository.class);
        CameraEntity malformed = mock(CameraEntity.class);
        CameraEntity valid = mock(CameraEntity.class);
        CameraDefinition definition = new CameraDefinition("Gate", "rtsp://gate/live");
        when(malformed.id()).thenReturn(UUID.randomUUID());
        when(malformed.toCameraDefinition()).thenThrow(new IllegalArgumentException("invalid persisted URL"));
        when(valid.toCameraDefinition()).thenReturn(definition);
        when(repository.findEnabled()).thenReturn(List.of(malformed, valid));
        LocalCameraApplicationService service = service(repository, mock(CameraDiscoveryService.class));

        assertEquals(List.of(definition), service.listEnabledCameras());

        IllegalStateException failure = new IllegalStateException("unexpected mapping failure");
        when(valid.toCameraDefinition()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, service::listEnabledCameras));
    }

    @Test
    void delegatesDiscoveryWithTheRequestedTimeout() {
        CameraRepository repository = mock(CameraRepository.class);
        CameraDiscoveryService discovery = mock(CameraDiscoveryService.class);
        Duration timeout = Duration.ofSeconds(5);
        DiscoveredCamera camera = new DiscoveredCamera(
                "device-1", "Vendor", "Model", "camera.local", "http://camera/onvif", true, Instant.now());
        when(discovery.discover(timeout)).thenReturn(List.of(camera));
        LocalCameraApplicationService service = service(repository, discovery);

        assertEquals(List.of(camera), service.discover(timeout));
        verify(discovery).discover(timeout);
    }

    @Test
    void registersWithoutExposingThePersistenceEntity() {
        CameraRepository repository = mock(CameraRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalCameraApplicationService service = service(repository, mock(CameraDiscoveryService.class));

        CameraRegistrationResult result = service.register(request());

        assertEquals("Entrance", result.displayName());
        assertEquals(true, result.enabled());
        verify(repository).save(any(CameraEntity.class));
    }

    @Test
    void rejectsCredentialReferencesUntilSecureStorageIsAvailable() {
        CameraRepository repository = mock(CameraRepository.class);
        LocalCameraApplicationService service = service(repository, mock(CameraDiscoveryService.class));
        CameraRegistrationRequest authenticated = new CameraRegistrationRequest(
                "Entrance", "rtsp://camera/live", null, null, null, "camera.local",
                null, null, null, null, CameraPriority.NORMAL, true, "secret:camera-1");

        assertThrows(IllegalArgumentException.class, () -> service.register(authenticated));
    }

    private static LocalCameraApplicationService service(CameraRepository repository,
                                                         CameraDiscoveryService discovery) {
        return new LocalCameraApplicationService(() -> repository, discovery);
    }

    private static CameraRegistrationRequest request() {
        return new CameraRegistrationRequest(
                "Entrance", "rtsp://camera/live", null, null, null, "camera.local",
                null, null, null, null, CameraPriority.NORMAL, true, null);
    }
}
