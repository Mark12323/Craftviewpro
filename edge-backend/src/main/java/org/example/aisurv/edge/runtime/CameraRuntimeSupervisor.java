package org.example.aisurv.edge.runtime;

import jakarta.annotation.PreDestroy;
import org.example.aisurv.app.CameraDefinition;
import org.example.aisurv.camera.CameraApplicationService;
import org.example.aisurv.stream.StreamManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Component
public class CameraRuntimeSupervisor {
    private final CameraApplicationService cameras;
    private final EdgeStreamSurface surface;
    private final Function<EdgeStreamSurface, StreamManager> streamManagerFactory;
    private StreamManager streams;
    private Map<UUID, CameraDefinition> active = Map.of();

    @Autowired
    public CameraRuntimeSupervisor(CameraApplicationService cameras, EdgeStreamSurface surface) {
        this(cameras, surface, StreamManager::new);
    }

    CameraRuntimeSupervisor(CameraApplicationService cameras, EdgeStreamSurface surface,
                            Function<EdgeStreamSurface, StreamManager> streamManagerFactory) {
        this.cameras = cameras;
        this.surface = surface;
        this.streamManagerFactory = streamManagerFactory;
    }

    public synchronized void reconcile() {
        List<CameraDefinition> enabled = cameras.listEnabledCameras();
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (CameraDefinition camera : enabled) ids.put(camera.name(), camera.id());

        Map<UUID, CameraDefinition> desired = new LinkedHashMap<>();
        for (CameraDefinition camera : enabled) desired.put(camera.id(), camera);
        if (streams == null) streams = streamManagerFactory.apply(surface);
        surface.bind(ids);

        for (Map.Entry<UUID, CameraDefinition> entry : active.entrySet()) {
            if (!entry.getValue().equals(desired.get(entry.getKey())) && !streams.stop(entry.getKey())) {
                throw new IllegalStateException("Existing camera worker did not stop");
            }
        }
        int index = 1;
        for (CameraDefinition camera : enabled) {
            if (!camera.equals(active.get(camera.id()))) {
                try {
                    if (!streams.start(camera, index)) {
                        throw new IllegalStateException("Camera worker did not start");
                    }
                } catch (RuntimeException failure) {
                    streams.stop(camera.id());
                    throw failure;
                }
            }
            index++;
        }
        active = Map.copyOf(desired);
    }

    public Optional<byte[]> snapshot(UUID id) { return surface.snapshot(id); }

    @PreDestroy
    void stop() {
        if (streams != null) streams.stopAll();
        active = Map.of();
    }
}
