package org.example.aisurv.edgeclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.aisurv.contract.v1.CameraListResponseV1;
import org.example.aisurv.contract.v1.CameraDiscoveryRequestV1;
import org.example.aisurv.contract.v1.CameraDiscoveryResponseV1;
import org.example.aisurv.contract.v1.EdgeHealthResponseV1;
import org.example.aisurv.contract.v1.RegisterCameraRequestV1;
import org.example.aisurv.contract.v1.RegisterCameraResponseV1;
import org.example.aisurv.contract.v1.ApiProblemV1;
import org.example.aisurv.contract.v1.CameraDetailV1;
import org.example.aisurv.contract.v1.CameraSummaryV1;
import org.example.aisurv.contract.v1.UpdateCameraRequestV1;
import org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1;
import org.example.aisurv.contract.v1.CameraUpdateEventV1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class HttpEdgeApiClient implements EdgeApiClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private final EdgeEndpoint endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpEdgeApiClient(EdgeEndpoint endpoint) {
        this(endpoint, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), defaultObjectMapper());
    }

    HttpEdgeApiClient(EdgeEndpoint endpoint, HttpClient httpClient, ObjectMapper objectMapper) {
        this.endpoint = endpoint;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public static HttpEdgeApiClient fromEnvironment() {
        return new HttpEdgeApiClient(EdgeEndpoint.fromEnvironment());
    }

    @Override
    public EdgeHealthResponseV1 health() {
        HttpResponse<String> response = get("/api/v1/health");
        if (response.statusCode() == 404) {
            throw new IncompatibleEdgeException("The edge service does not provide API v1");
        }
        requireSuccess(response, "Edge health request failed");
        EdgeHealthResponseV1 health = read(response.body(), EdgeHealthResponseV1.class, true);
        if (health.apiVersion() == null || !health.apiVersion().isSupportedByV1Client()) {
            throw new IncompatibleEdgeException("The edge service uses an unsupported API version");
        }
        return health;
    }

    @Override
    public CameraListResponseV1 listCameras() {
        HttpResponse<String> response = get("/api/v1/cameras");
        if (response.statusCode() == 404) {
            throw new IncompatibleEdgeException("The edge service does not provide the camera query API");
        }
        requireSuccess(response, "Camera registry request failed");
        return read(response.body(), CameraListResponseV1.class, false);
    }

    @Override
    public CameraDiscoveryResponseV1 discoverCameras(CameraDiscoveryRequestV1 request) {
        if (request == null || request.timeoutSeconds() < 1 || request.timeoutSeconds() > 15) {
            throw new IllegalArgumentException("Discovery timeout must be between 1 and 15 seconds");
        }
        HttpResponse<String> response = post(
                "/api/v1/camera-discovery", request, Duration.ofSeconds(request.timeoutSeconds() + 3L));
        if (response.statusCode() == 404) {
            throw new IncompatibleEdgeException("The edge service does not provide the camera discovery API");
        }
        requireSuccess(response, "Camera discovery request failed");
        return read(response.body(), CameraDiscoveryResponseV1.class, false);
    }

    @Override
    public RegisterCameraResponseV1 registerCamera(RegisterCameraRequestV1 request) {
        if (request == null) throw new IllegalArgumentException("Camera registration request is required");
        HttpResponse<String> response = post("/api/v1/cameras", request, Duration.ofSeconds(15));
        if (response.statusCode() == 404) {
            throw new IncompatibleEdgeException("The edge service does not provide the camera registration API");
        }
        requireSuccess(response, "Camera registration request failed");
        return read(response.body(), RegisterCameraResponseV1.class, false);
    }

    @Override public CameraDetailV1 getCamera(UUID id) {
        HttpResponse<String> response = get("/api/v1/cameras/" + id);
        requireSuccess(response, "Camera detail request failed");
        return read(response.body(), CameraDetailV1.class, false);
    }

    @Override public CameraDetailV1 updateCamera(UUID id, UpdateCameraRequestV1 request) {
        HttpResponse<String> response = sendJson("PUT", "/api/v1/cameras/" + id, request, Duration.ofSeconds(15));
        requireSuccess(response, "Camera update failed");
        return read(response.body(), CameraDetailV1.class, false);
    }

    @Override public CameraSummaryV1 setCameraState(UUID id, SetCameraConfigurationStateRequestV1 request) {
        HttpResponse<String> response = sendJson("PUT", "/api/v1/cameras/" + id + "/configuration-state", request, Duration.ofSeconds(15));
        requireSuccess(response, "Camera state update failed");
        return read(response.body(), CameraSummaryV1.class, false);
    }

    @Override public void deleteCamera(UUID id, long version) {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/api/v1/cameras/" + id + "?version=" + version))
                .timeout(Duration.ofSeconds(15)).DELETE().build();
        requireSuccess(send(request), "Camera deletion failed");
    }

    @Override public byte[] snapshot(UUID id) {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/api/v1/cameras/" + id + "/snapshot"))
                .timeout(Duration.ofSeconds(3)).header("Accept", "image/jpeg").GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 204) return new byte[0];
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new EdgeRequestException("Camera snapshot request failed", response.statusCode());
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EdgeUnavailableException("Edge snapshot request was interrupted", e);
        } catch (IOException e) {
            throw new EdgeUnavailableException("Edge service is unavailable", e);
        }
    }

    @Override
    public CameraUpdateSubscription openCameraUpdates() {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/api/v1/camera-updates"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) {
                response.body().close();
                throw new IncompatibleEdgeException("The edge service does not provide camera updates");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new EdgeRequestException("Camera update stream request failed", response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/event-stream")) {
                response.body().close();
                throw new IncompatibleEdgeException("The edge camera update response is not an event stream");
            }
            return new HttpCameraUpdateSubscription(response.body(), objectMapper);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EdgeUnavailableException("Camera update stream request was interrupted", e);
        } catch (IOException e) {
            throw new EdgeUnavailableException("Edge service is unavailable", e);
        }
    }

    private HttpResponse<String> get(String path) {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request);
    }

    private HttpResponse<String> post(String path, Object body, Duration timeout) {
        return sendJson("POST", path, body, timeout);
    }

    private HttpResponse<String> sendJson(String method, String path, Object body, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve(path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(write(body)))
                .build();
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EdgeUnavailableException("Edge request was interrupted", e);
        } catch (IOException e) {
            throw new EdgeUnavailableException("Edge service is unavailable", e);
        }
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new EdgeRequestException("The edge request could not be encoded", 0);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try {
                ApiProblemV1 problem = objectMapper.readValue(response.body(), ApiProblemV1.class);
                String detail = problem.message() == null || problem.message().isBlank()
                        ? message : problem.message();
                throw new EdgeRequestException(detail, response.statusCode(), problem.code());
            } catch (JsonProcessingException ignored) {
                throw new EdgeRequestException(message, response.statusCode());
            }
        }
    }

    private <T> T read(String json, Class<T> type, boolean compatibilityResponse) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            if (compatibilityResponse) {
                throw new IncompatibleEdgeException("The edge service returned an incompatible response", e);
            }
            throw new EdgeRequestException("The edge service returned an invalid response", 200);
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    private static final class HttpCameraUpdateSubscription implements CameraUpdateSubscription {
        private final BufferedReader reader;
        private final ObjectMapper objectMapper;
        private final AtomicBoolean closed = new AtomicBoolean();

        private HttpCameraUpdateSubscription(InputStream input, ObjectMapper objectMapper) {
            this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            this.objectMapper = objectMapper;
        }

        @Override
        public void consume(Consumer<CameraUpdateEventV1> consumer) {
            Objects.requireNonNull(consumer, "consumer");
            StringBuilder data = new StringBuilder();
            try {
                String line;
                while (!closed.get() && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        dispatch(data, consumer);
                    } else if (line.startsWith("data:")) {
                        if (!data.isEmpty()) data.append('\n');
                        data.append(line.substring(5).stripLeading());
                    }
                }
                if (!closed.get()) {
                    throw new EdgeUnavailableException(
                            "Camera update stream disconnected", new IOException("Unexpected end of stream"));
                }
            } catch (IOException failure) {
                if (!closed.get()) throw new EdgeUnavailableException("Camera update stream disconnected", failure);
            }
        }

        private void dispatch(StringBuilder data, Consumer<CameraUpdateEventV1> consumer) {
            if (data.isEmpty()) return;
            try {
                consumer.accept(objectMapper.readValue(data.toString(), CameraUpdateEventV1.class));
            } catch (JsonProcessingException failure) {
                throw new EdgeRequestException("The edge update stream returned an invalid event", 200);
            } finally {
                data.setLength(0);
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
    }
}
