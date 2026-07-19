package org.example.aisurv.edge.api.v1;

import org.example.aisurv.contract.v1.CameraDiscoveryRequestV1;
import org.example.aisurv.contract.v1.CameraDiscoveryResponseV1;
import org.example.aisurv.edge.service.EdgeRuntimeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/camera-discovery")
public class CameraDiscoveryController {
    private final EdgeRuntimeService runtime;

    public CameraDiscoveryController(EdgeRuntimeService runtime) {
        this.runtime = runtime;
    }

    @PostMapping
    public CameraDiscoveryResponseV1 discover(@RequestBody CameraDiscoveryRequestV1 request) {
        return runtime.discover(request);
    }
}
