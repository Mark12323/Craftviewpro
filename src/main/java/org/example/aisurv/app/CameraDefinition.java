package org.example.aisurv.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record CameraDefinition(UUID id, String name, String rtspUrl) {
    public CameraDefinition(String name, String rtspUrl) {
        this(developmentId(rtspUrl), name, rtspUrl);
    }

    public CameraDefinition {
        if (id == null) throw new IllegalArgumentException("Camera ID is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Camera name is required");
        }
        if (rtspUrl == null || rtspUrl.isBlank()) {
            throw new IllegalArgumentException("Camera RTSP URL is required");
        }

        name = name.trim();
        rtspUrl = rtspUrl.trim();

        URI uri;
        try {
            uri = new URI(rtspUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Camera RTSP URL is invalid", e);
        }

        String scheme = uri.getScheme();
        if (!"rtsp".equalsIgnoreCase(scheme) && !"rtsps".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Camera URL must use the rtsp or rtsps scheme");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Camera RTSP URL must include a network host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Camera RTSP URL must not contain credentials; use a credential reference");
        }
    }

    private static UUID developmentId(String rtspUrl) {
        return rtspUrl == null ? new UUID(0, 0)
                : UUID.nameUUIDFromBytes(rtspUrl.trim().getBytes(StandardCharsets.UTF_8));
    }
}
