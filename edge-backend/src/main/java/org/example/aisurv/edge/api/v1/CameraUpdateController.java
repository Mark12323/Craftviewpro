package org.example.aisurv.edge.api.v1;

import org.example.aisurv.edge.update.CameraUpdateBroker;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/camera-updates")
public class CameraUpdateController {
    private final CameraUpdateBroker updates;

    public CameraUpdateController(CameraUpdateBroker updates) {
        this.updates = updates;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter updates() {
        return updates.subscribe();
    }
}
