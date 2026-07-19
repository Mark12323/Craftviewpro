package org.example.aisurv.edge.api.v1;

import org.example.aisurv.contract.v1.EdgeHealthResponseV1;
import org.example.aisurv.edge.service.EdgeRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EdgeHealthController {
    private final EdgeRuntimeService runtime;

    public EdgeHealthController(EdgeRuntimeService runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/health")
    public EdgeHealthResponseV1 health() {
        return runtime.health();
    }
}
