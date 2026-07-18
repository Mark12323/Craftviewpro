package org.example.aisurv.app;

import java.net.URI;
import java.net.URISyntaxException;

public record CameraDefinition(String name, String rtspUrl) {
    public CameraDefinition {
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
    }
}
