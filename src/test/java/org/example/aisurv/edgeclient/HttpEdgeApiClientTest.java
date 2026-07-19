package org.example.aisurv.edgeclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.aisurv.contract.v1.EdgeStatusV1;
import org.example.aisurv.contract.v1.CameraDiscoveryRequestV1;
import org.example.aisurv.contract.v1.CameraPriorityV1;
import org.example.aisurv.contract.v1.RegisterCameraRequestV1;
import org.example.aisurv.contract.v1.UpdateCameraRequestV1;
import org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1;
import org.example.aisurv.contract.v1.CameraConfigurationStateV1;
import org.example.aisurv.contract.v1.CameraUpdateKindV1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpEdgeApiClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void readsVersionedHealthAndCameraInventory() throws Exception {
        startServer();
        server.createContext("/api/v1/health", exchange -> respond(exchange, 200, """
                {"apiVersion":{"major":1,"minor":0},"edgeVersion":"1.0-SNAPSHOT","status":"READY",
                "capabilities":["CAMERA_QUERY"],"components":{},"observedAt":"2026-07-19T00:00:00Z"}
                """));
        server.createContext("/api/v1/cameras", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 201, """
                        {"id":"ca1bce64-5715-4f4e-ab72-fafcb2c3cf33","displayName":"Entrance",
                        "configurationState":"ENABLED"}
                        """);
            } else {
                respond(exchange, 200, """
                        {"cameras":[{"id":"ca1bce64-5715-4f4e-ab72-fafcb2c3cf33","displayName":"Entrance",
                        "location":"Campus","building":null,"floor":null,"zone":"Gate","priority":"HIGH",
                        "configurationState":"ENABLED","futureField":true}],"observedAt":"2026-07-19T00:00:01Z"}
                        """);
            }
        });
        server.createContext("/api/v1/camera-discovery", exchange -> respond(exchange, 200, """
                {"cameras":[{"deviceId":"device-1","manufacturer":"Vendor","model":"Model",
                "host":"127.0.0.2","onvifServiceUrl":"http://127.0.0.2/onvif",
                "requiresAuthentication":true,"discoveredAt":"2026-07-19T00:00:00Z"}],
                "observedAt":"2026-07-19T00:00:01Z"}
                """));
        HttpEdgeApiClient client = client();

        assertEquals(EdgeStatusV1.READY, client.health().status());
        assertEquals("Entrance", client.listCameras().cameras().getFirst().displayName());
        assertEquals("device-1", client.discoverCameras(new CameraDiscoveryRequestV1(5))
                .cameras().getFirst().deviceId());
        assertEquals("Entrance", client.registerCamera(registration()).displayName());
    }

    @Test
    void distinguishesIncompatibleAndDegradedResponses() throws Exception {
        startServer();
        server.createContext("/api/v1/health", exchange -> respond(exchange, 200, """
                {"apiVersion":{"major":2,"minor":0},"edgeVersion":"2","status":"READY",
                "capabilities":[],"components":{},"observedAt":"2026-07-19T00:00:00Z"}
                """));
        server.createContext("/api/v1/cameras", exchange -> respond(exchange, 503, "{}"));
        HttpEdgeApiClient client = client();

        assertThrows(IncompatibleEdgeException.class, client::health);
        assertEquals(503, assertThrows(EdgeRequestException.class, client::listCameras).statusCode());
    }

    @Test
    void reportsStoppedEdgeAsUnavailable() throws Exception {
        startServer();
        HttpEdgeApiClient client = client();
        server.stop(0);
        server = null;

        assertThrows(EdgeUnavailableException.class, client::health);
    }

    @Test
    void endpointAcceptsOnlyLiteralLoopbackHosts() {
        new EdgeEndpoint(URI.create("http://127.0.0.2:8080"));
        new EdgeEndpoint(URI.create("http://[::1]:8080"));
        assertThrows(IllegalArgumentException.class,
                () -> new EdgeEndpoint(URI.create("http://localhost:8080")));
        assertThrows(IllegalArgumentException.class,
                () -> new EdgeEndpoint(URI.create("http://192.0.2.1:8080")));
    }

    @Test
    void preservesStructuredRegistrationErrors() throws Exception {
        startServer();
        server.createContext("/api/v1/cameras", exchange -> respond(exchange, 400, """
                {"code":"INVALID_REQUEST","message":"Camera RTSP URL is invalid",
                "path":"/api/v1/cameras","occurredAt":"2026-07-19T00:00:00Z"}
                """));

        EdgeRequestException failure = assertThrows(
                EdgeRequestException.class, () -> client().registerCamera(registration()));

        assertEquals(400, failure.statusCode());
        assertEquals("INVALID_REQUEST", failure.problemCode());
        assertEquals("Camera RTSP URL is invalid", failure.getMessage());
    }

    @Test
    void executesVersionedLifecycleCommands() throws Exception {
        startServer();
        String id = "ca1bce64-5715-4f4e-ab72-fafcb2c3cf33";
        server.createContext("/api/v1/cameras/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("DELETE".equals(exchange.getRequestMethod())) { respond(exchange, 204, ""); return; }
            if (path.endsWith("/configuration-state")) {
                respond(exchange, 200, "{\"id\":\"" + id + "\",\"displayName\":\"Entrance\",\"priority\":\"HIGH\",\"configurationState\":\"DISABLED\",\"version\":3}");
                return;
            }
            respond(exchange, 200, "{\"id\":\"" + id + "\",\"displayName\":\"Entrance\",\"priority\":\"HIGH\",\"configurationState\":\"ENABLED\",\"version\":2}");
        });
        HttpEdgeApiClient client = client();
        java.util.UUID cameraId = java.util.UUID.fromString(id);

        assertEquals(2, client.getCamera(cameraId).version());
        assertEquals(2, client.updateCamera(cameraId, new UpdateCameraRequestV1(2L, "Entrance", null,
                null, null, null, null, null, null, null, null, CameraPriorityV1.HIGH)).version());
        assertEquals(3, client.setCameraState(cameraId,
                new SetCameraConfigurationStateRequestV1(2L, CameraConfigurationStateV1.DISABLED)).version());
        client.deleteCamera(cameraId, 3);
    }

    @Test
    void readsSnapshotsAndRepresentsAFrameNotYetAvailableAsEmpty() throws Exception {
        startServer();
        java.util.UUID available = java.util.UUID.randomUUID();
        java.util.UUID pending = java.util.UUID.randomUUID();
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9};
        server.createContext("/api/v1/cameras/" + available + "/snapshot",
                exchange -> respond(exchange, 200, "image/jpeg", jpeg));
        server.createContext("/api/v1/cameras/" + pending + "/snapshot",
                exchange -> respond(exchange, 204, "image/jpeg", new byte[0]));
        HttpEdgeApiClient client = client();

        assertArrayEquals(jpeg, client.snapshot(available));
        assertArrayEquals(new byte[0], client.snapshot(pending));
    }

    @Test
    void classifiesSnapshotHttpFailures() throws Exception {
        startServer();
        java.util.UUID id = java.util.UUID.randomUUID();
        server.createContext("/api/v1/cameras/" + id + "/snapshot",
                exchange -> respond(exchange, 503, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));

        EdgeRequestException failure = assertThrows(EdgeRequestException.class, () -> client().snapshot(id));

        assertEquals(503, failure.statusCode());
    }

    @Test
    void consumesVersionedCameraUpdateEvents() throws Exception {
        startServer();
        String cameraId = java.util.UUID.randomUUID().toString();
        server.createContext("/api/v1/camera-updates", exchange -> {
            byte[] event = ("event: camera-update-v1\n"
                    + "data: {\"streamInstanceId\":\"ca1bce64-5715-4f4e-ab72-fafcb2c3cf33\","
                    + "\"sequence\":4,\"kind\":\"UPSERTED\",\"cameraId\":\"" + cameraId + "\","
                    + "\"cameraVersion\":2,\"observedAt\":\"2026-07-19T00:00:00Z\"}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(event);
            exchange.getResponseBody().flush();
            exchange.close();
        });
        ArrayList<org.example.aisurv.contract.v1.CameraUpdateEventV1> events = new ArrayList<>();

        try (CameraUpdateSubscription subscription = client().openCameraUpdates()) {
            subscription.consume(event -> {
                events.add(event);
                subscription.close();
            });
        }

        assertEquals(1, events.size());
        assertEquals(CameraUpdateKindV1.UPSERTED, events.getFirst().kind());
        assertEquals(cameraId, events.getFirst().cameraId().toString());
    }

    @Test
    void rejectsSuccessfulCameraUpdateResponsesWithTheWrongMediaType() throws Exception {
        startServer();
        server.createContext("/api/v1/camera-updates", exchange -> respond(exchange, 200, "{}"));

        assertThrows(IncompatibleEdgeException.class, () -> client().openCameraUpdates());
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    private HttpEdgeApiClient client() {
        return new HttpEdgeApiClient(new EdgeEndpoint(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort())));
    }

    private static RegisterCameraRequestV1 registration() {
        return new RegisterCameraRequestV1(
                "Entrance", "rtsp://camera/live", null, null, null, "camera.local",
                "Campus", null, null, "Gate", CameraPriorityV1.HIGH, true, null);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
