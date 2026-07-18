package org.example.aisurv.camera;

import org.example.aisurv.persistence.entities.CameraEntity;
import org.example.aisurv.persistence.repositories.CameraRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CameraRegistrationServiceTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsMissingDisplayNameBeforePersistence(String displayName) {
        CameraRepository repository = mock(CameraRepository.class);
        CameraRegistrationService service = new CameraRegistrationService(repository);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.register(request(displayName, "rtsp://camera/live", true))
        );

        assertEquals("Camera display name is required", error.getMessage());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsMissingRtspUrlBeforePersistence(String rtspUrl) {
        CameraRepository repository = mock(CameraRepository.class);
        CameraRegistrationService service = new CameraRegistrationService(repository);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.register(request("Entrance", rtspUrl, true))
        );

        assertEquals("Camera RTSP URL is required", error.getMessage());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://camera/live",
            "udp://camera/live",
            "rtsp://camera host/live",
            "rtsp://[camera/live",
            "rtsp:///live",
            "rtsp:live"
    })
    void rejectsInvalidNonblankRtspUrlsBeforePersistence(String rtspUrl) {
        CameraRepository repository = mock(CameraRepository.class);
        CameraRegistrationService service = new CameraRegistrationService(repository);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.register(request("Entrance", rtspUrl, true))
        );

        verify(repository, never()).save(any());
    }

    @Test
    void normalizesAndPersistsAValidRegistration() {
        CameraRepository repository = mock(CameraRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CameraRegistrationService service = new CameraRegistrationService(repository);

        CameraEntity camera = service.register(request("  Entrance  ", "  rtsp://camera/live  ", true));

        assertNotNull(camera.id());
        assertEquals("Entrance", camera.displayName());
        assertTrue(camera.enabled());
        assertEquals("rtsp://camera/live", camera.toCameraDefinition().rtspUrl());
        verify(repository).save(camera);
    }

    private static CameraRegistrationRequest request(String displayName, String rtspUrl, boolean enabled) {
        return new CameraRegistrationRequest(
                displayName,
                rtspUrl,
                null,
                null,
                null,
                "camera.local",
                null,
                null,
                null,
                null,
                null,
                enabled,
                null
        );
    }
}
