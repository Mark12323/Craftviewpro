package org.example.aisurv.edge.runtime;

import org.example.aisurv.stream.StreamMonitorSurface;
import org.example.aisurv.event.CameraHealthEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

@Component
public class EdgeStreamSurface implements StreamMonitorSurface {
    private volatile Map<String, UUID> idsByName = Map.of();
    private final Map<UUID, byte[]> snapshots = new ConcurrentHashMap<>();
    private final CameraHealthService health;

    EdgeStreamSurface() {
        this(null);
    }

    @Autowired
    public EdgeStreamSurface(CameraHealthService health) {
        this.health = health;
    }

    synchronized void bind(Map<String, UUID> cameras) {
        idsByName = Map.copyOf(cameras);
        snapshots.keySet().retainAll(cameras.values());
    }

    @Override
    public void updateFrame(String cameraName, BufferedImage image) {
        UUID id = idsByName.get(cameraName);
        if (id == null) return;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", output);
            synchronized (this) {
                if (id.equals(idsByName.get(cameraName))) snapshots.put(id, output.toByteArray());
            }
        } catch (IOException ignored) {
        }
    }

    public Optional<byte[]> snapshot(UUID id) {
        byte[] image = snapshots.get(id);
        return image == null ? Optional.empty() : Optional.of(image.clone());
    }

    @Override
    public void onFrameObserved(String cameraName, Instant observedAt) {
        UUID id = idsByName.get(cameraName);
        if (id != null && health != null) health.frameObserved(id, observedAt);
    }

    @Override
    public void onCameraHealthEvent(CameraHealthEvent event) {
        UUID id = idsByName.get(event.cameraName());
        if (id == null) return;
        if (event.state() != org.example.aisurv.event.CameraHealthState.ONLINE
                && event.state() != org.example.aisurv.event.CameraHealthState.IMPAIRED) {
            snapshots.remove(id);
        }
        if (health != null) health.stateChanged(id, event);
    }

    @Override public void logEvent(String cameraName, String message) { }
}
