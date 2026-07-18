package org.example.aisurv.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CameraDefinitionTest {
    @ParameterizedTest
    @ValueSource(strings = {"http://camera/live", "udp://camera/live", "file:///camera/live"})
    void rejectsNonRtspSchemes(String url) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new CameraDefinition("Entrance", url)
        );

        assertEquals("Camera URL must use the rtsp or rtsps scheme", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rtsp://camera host/live", "rtsp://[camera/live"})
    void rejectsInvalidUriSyntax(String url) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new CameraDefinition("Entrance", url)
        );

        assertEquals("Camera RTSP URL is invalid", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rtsp:///live", "rtsp:live", "rtsps:/live"})
    void rejectsUrisWithoutANetworkHost(String url) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new CameraDefinition("Entrance", url)
        );

        assertEquals("Camera RTSP URL must include a network host", error.getMessage());
    }

    @Test
    void acceptsAndNormalizesRtspAndRtspsUrlsWithHosts() {
        CameraDefinition rtsp = new CameraDefinition("  Entrance  ", "  rtsp://camera/live  ");
        CameraDefinition rtsps = new CameraDefinition("Secure", "rtsps://camera.example/live");

        assertEquals(new CameraDefinition("Entrance", "rtsp://camera/live"), rtsp);
        assertEquals("rtsps://camera.example/live", rtsps.rtspUrl());
    }
}
