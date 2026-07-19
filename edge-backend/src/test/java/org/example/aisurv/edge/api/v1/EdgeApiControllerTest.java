package org.example.aisurv.edge.api.v1;

import org.example.aisurv.contract.v1.ApiVersionV1;
import org.example.aisurv.contract.v1.CameraConfigurationStateV1;
import org.example.aisurv.contract.v1.CameraDiscoveryResponseV1;
import org.example.aisurv.contract.v1.CameraListResponseV1;
import org.example.aisurv.contract.v1.EdgeHealthResponseV1;
import org.example.aisurv.contract.v1.EdgeStatusV1;
import org.example.aisurv.contract.v1.RegisterCameraResponseV1;
import org.example.aisurv.edge.service.DependencyUnavailableException;
import org.example.aisurv.edge.service.CameraDiscoveryFailedException;
import org.example.aisurv.edge.service.OperationInProgressException;
import org.example.aisurv.edge.service.EdgeRuntimeService;
import org.example.aisurv.edge.runtime.CameraRuntimeSupervisor;
import org.example.aisurv.edge.update.CameraUpdateBroker;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import org.example.aisurv.contract.v1.CameraDetailV1;
import org.example.aisurv.contract.v1.CameraPriorityV1;
import org.example.aisurv.contract.v1.CameraSummaryV1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import org.springframework.http.MediaType;

class EdgeApiControllerTest {
    @Test
    void returnsVersionedHealthAndSanitizedDependencyFailure() throws Exception {
        EdgeRuntimeService runtime = mock(EdgeRuntimeService.class);
        when(runtime.health()).thenReturn(new EdgeHealthResponseV1(
                ApiVersionV1.CURRENT, "test", EdgeStatusV1.READY, Set.of(), Map.of(), Instant.now()));
        when(runtime.cameras()).thenThrow(
                new DependencyUnavailableException("Camera registry is unavailable"));
        MockMvc mvc = standaloneSetup(new EdgeHealthController(runtime), new CameraQueryController(runtime))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiVersion.major").value(1))
                .andExpect(jsonPath("$.status").value("READY"));
        mvc.perform(get("/api/v1/cameras"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Camera registry is unavailable"));
    }

    @Test
    void returnsAnEmptySuccessfulCameraList() throws Exception {
        EdgeRuntimeService runtime = mock(EdgeRuntimeService.class);
        when(runtime.cameras()).thenReturn(new CameraListResponseV1(List.of(), Instant.now()));
        MockMvc mvc = standaloneSetup(new CameraQueryController(runtime))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/cameras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cameras").isArray())
                .andExpect(jsonPath("$.cameras.length()").value(0));
    }

    @Test
    void discoversAndRegistersCamerasThroughVersionedCommands() throws Exception {
        EdgeRuntimeService runtime = mock(EdgeRuntimeService.class);
        when(runtime.discover(any())).thenReturn(new CameraDiscoveryResponseV1(List.of(), Instant.now()));
        UUID id = UUID.randomUUID();
        when(runtime.register(any())).thenReturn(new RegisterCameraResponseV1(
                id, "Entrance", CameraConfigurationStateV1.ENABLED, 0));
        MockMvc mvc = standaloneSetup(new CameraDiscoveryController(runtime), new CameraQueryController(runtime))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/camera-discovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSeconds\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cameras").isArray());
        mvc.perform(post("/api/v1/cameras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Entrance","rtspUrl":"rtsp://camera/live",
                                "priority":"HIGH","enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.configurationState").value("ENABLED"));
    }

    @Test
    void returnsStructuredInvalidRequestErrors() throws Exception {
        EdgeRuntimeService runtime = mock(EdgeRuntimeService.class);
        when(runtime.register(any())).thenThrow(new IllegalArgumentException("Camera RTSP URL is invalid"));
        MockMvc mvc = standaloneSetup(new CameraQueryController(runtime))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/cameras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Entrance\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Camera RTSP URL is invalid"));
    }

    @Test
    void returnsStructuredDiscoveryFailuresAndConflicts() throws Exception {
        EdgeRuntimeService failedRuntime = mock(EdgeRuntimeService.class);
        when(failedRuntime.discover(any())).thenThrow(
                new CameraDiscoveryFailedException("Camera discovery could not access the local network"));
        MockMvc failedMvc = standaloneSetup(new CameraDiscoveryController(failedRuntime))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        failedMvc.perform(post("/api/v1/camera-discovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSeconds\":5}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DISCOVERY_FAILED"));

        EdgeRuntimeService busyRuntime = mock(EdgeRuntimeService.class);
        when(busyRuntime.discover(any())).thenThrow(
                new OperationInProgressException("Camera discovery is already in progress"));
        MockMvc busyMvc = standaloneSetup(new CameraDiscoveryController(busyRuntime))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        busyMvc.perform(post("/api/v1/camera-discovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSeconds\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATION_IN_PROGRESS"));
    }

    @Test
    void exposesVersionedLifecycleEndpoints() throws Exception {
        EdgeRuntimeService runtime = mock(EdgeRuntimeService.class);
        UUID id = UUID.randomUUID();
        CameraDetailV1 detail = new CameraDetailV1(id, "Entrance", null, null, null,
                "camera.local", "Campus", null, null, "Gate", CameraPriorityV1.HIGH,
                CameraConfigurationStateV1.ENABLED, 2);
        when(runtime.camera(id)).thenReturn(detail);
        when(runtime.update(any(), any())).thenReturn(detail);
        when(runtime.setConfigurationState(any(), any())).thenReturn(new CameraSummaryV1(id, "Entrance",
                "Campus", null, null, "Gate", CameraPriorityV1.HIGH,
                CameraConfigurationStateV1.DISABLED, 3, null));
        MockMvc mvc = standaloneSetup(new CameraQueryController(runtime)).setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(get("/api/v1/cameras/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(put("/api/v1/cameras/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":2,\"displayName\":\"Entrance\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/cameras/" + id + "/configuration-state").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":2,\"configurationState\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                 "/api/v1/cameras/" + id + "?version=3")).andExpect(status().isNoContent());
    }

    @Test
    void returnsLatestSnapshotWithoutCachingOrNoContentBeforeFirstFrame() throws Exception {
        CameraRuntimeSupervisor runtime = mock(CameraRuntimeSupervisor.class);
        UUID id = UUID.randomUUID();
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
        when(runtime.snapshot(id)).thenReturn(Optional.of(jpeg)).thenReturn(Optional.empty());
        MockMvc mvc = standaloneSetup(new CameraSnapshotController(runtime)).build();

        mvc.perform(get("/api/v1/cameras/" + id + "/snapshot"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(jpeg));
        mvc.perform(get("/api/v1/cameras/" + id + "/snapshot"))
                .andExpect(status().isNoContent());
    }

    @Test
    void opensVersionedCameraUpdateStreamAsAnAsyncRequest() throws Exception {
        CameraUpdateBroker updates = mock(CameraUpdateBroker.class);
        when(updates.subscribe()).thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L));
        MockMvc mvc = standaloneSetup(new CameraUpdateController(updates)).build();

        mvc.perform(get("/api/v1/camera-updates").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }
}
