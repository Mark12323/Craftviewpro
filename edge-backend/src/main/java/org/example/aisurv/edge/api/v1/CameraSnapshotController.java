package org.example.aisurv.edge.api.v1;

import org.example.aisurv.edge.runtime.CameraRuntimeSupervisor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cameras")
public class CameraSnapshotController {
    private final CameraRuntimeSupervisor runtime;
    public CameraSnapshotController(CameraRuntimeSupervisor runtime) { this.runtime = runtime; }

    @GetMapping(value = "/{id}/snapshot", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> snapshot(@PathVariable("id") UUID id) {
        return runtime.snapshot(id)
                .map(bytes -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bytes))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
