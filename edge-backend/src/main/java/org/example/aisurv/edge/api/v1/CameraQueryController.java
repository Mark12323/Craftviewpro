package org.example.aisurv.edge.api.v1;

import org.example.aisurv.contract.v1.CameraListResponseV1;
import org.example.aisurv.contract.v1.RegisterCameraRequestV1;
import org.example.aisurv.contract.v1.RegisterCameraResponseV1;
import org.example.aisurv.edge.service.EdgeRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.example.aisurv.contract.v1.CameraDetailV1;
import org.example.aisurv.contract.v1.CameraSummaryV1;
import org.example.aisurv.contract.v1.UpdateCameraRequestV1;
import org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cameras")
public class CameraQueryController {
    private final EdgeRuntimeService runtime;

    public CameraQueryController(EdgeRuntimeService runtime) {
        this.runtime = runtime;
    }

    @GetMapping
    public CameraListResponseV1 cameras() {
        return runtime.cameras();
    }

    @PostMapping
    public ResponseEntity<RegisterCameraResponseV1> register(@RequestBody RegisterCameraRequestV1 request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(runtime.register(request));
    }

    @GetMapping("/{id}") public CameraDetailV1 camera(@PathVariable("id") UUID id) { return runtime.camera(id); }
    @PutMapping("/{id}") public CameraDetailV1 update(@PathVariable("id") UUID id, @RequestBody UpdateCameraRequestV1 request) { return runtime.update(id, request); }
    @PutMapping("/{id}/configuration-state")
    public CameraSummaryV1 setState(@PathVariable("id") UUID id, @RequestBody SetCameraConfigurationStateRequestV1 request) { return runtime.setConfigurationState(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @RequestParam("version") long version) {
        runtime.delete(id, version); return ResponseEntity.noContent().build();
    }
}
