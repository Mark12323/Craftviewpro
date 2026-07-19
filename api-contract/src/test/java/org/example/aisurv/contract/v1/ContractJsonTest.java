package org.example.aisurv.contract.v1;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContractJsonTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);

    @Test
    void roundTripsHealthAndCameraResponses() throws Exception {
        EdgeHealthResponseV1 health = new EdgeHealthResponseV1(
                ApiVersionV1.CURRENT,
                "1.0-SNAPSHOT",
                EdgeStatusV1.READY,
                Set.of(EdgeCapabilityV1.CAMERA_QUERY),
                Map.of("database", new ComponentHealthV1(ComponentStatusV1.UP, "AVAILABLE")),
                Instant.parse("2026-07-19T00:00:00Z"));
        CameraListResponseV1 cameras = new CameraListResponseV1(List.of(new CameraSummaryV1(
                UUID.fromString("ca1bce64-5715-4f4e-ab72-fafcb2c3cf33"),
                "Entrance", "Campus", null, null, "Gate", CameraPriorityV1.HIGH,
                CameraConfigurationStateV1.ENABLED, 0, new CameraRuntimeHealthV1(
                CameraOperationalStateV1.ONLINE, "STREAM_CONNECTED",
                Instant.parse("2026-07-19T00:00:00Z"), Instant.parse("2026-07-19T00:00:01Z"),
                1_000, 0, Instant.parse("2026-07-19T00:00:01Z")))),
                Instant.parse("2026-07-19T00:00:01Z"));

        assertEquals(health, mapper.readValue(mapper.writeValueAsString(health), EdgeHealthResponseV1.class));
        String cameraJson = mapper.writeValueAsString(cameras);
        assertEquals(cameras, mapper.readValue(cameraJson, CameraListResponseV1.class));
        assertFalse(cameraJson.toLowerCase().contains("rtsp"));
        assertFalse(cameraJson.toLowerCase().contains("credential"));
    }

    @Test
    void toleratesAdditiveFieldsAndUnknownEnums() throws Exception {
        String json = """
                {
                  "apiVersion":{"major":1,"minor":3},
                  "edgeVersion":"future",
                  "status":"READY",
                  "capabilities":["CAMERA_QUERY","FUTURE_CAPABILITY"],
                  "components":{},
                  "observedAt":"2026-07-19T00:00:00Z",
                  "futureField":true
                }
                """;

        EdgeHealthResponseV1 response = mapper.readValue(json, EdgeHealthResponseV1.class);

        assertEquals(Set.of(EdgeCapabilityV1.CAMERA_QUERY, EdgeCapabilityV1.UNKNOWN), response.capabilities());
        assertEquals(1, response.apiVersion().major());
    }

    @Test
    void roundTripsDiscoveryAndRegistrationContractsWithoutLeakingCredentialsInResponses() throws Exception {
        CameraDiscoveryResponseV1 discovery = new CameraDiscoveryResponseV1(List.of(new DiscoveredCameraV1(
                "device-1", "Vendor", "Model", "127.0.0.2", "http://127.0.0.2/onvif",
                true, Instant.parse("2026-07-19T00:00:00Z"))), Instant.parse("2026-07-19T00:00:01Z"));
        RegisterCameraResponseV1 registration = new RegisterCameraResponseV1(
                UUID.fromString("ca1bce64-5715-4f4e-ab72-fafcb2c3cf33"), "Entrance",
                CameraConfigurationStateV1.ENABLED, 0);

        assertEquals(discovery, mapper.readValue(
                mapper.writeValueAsString(discovery), CameraDiscoveryResponseV1.class));
        String registrationJson = mapper.writeValueAsString(registration);
        assertEquals(registration, mapper.readValue(registrationJson, RegisterCameraResponseV1.class));
        assertFalse(registrationJson.toLowerCase().contains("rtsp"));
        assertFalse(registrationJson.toLowerCase().contains("credential"));
    }

    @Test
    void roundTripsCameraUpdateEventsAndToleratesFutureKinds() throws Exception {
        CameraUpdateEventV1 event = new CameraUpdateEventV1(
                UUID.randomUUID(), 7, CameraUpdateKindV1.UPSERTED, UUID.randomUUID(), 3L,
                Instant.parse("2026-07-19T00:00:00Z"));

        assertEquals(event, mapper.readValue(mapper.writeValueAsString(event), CameraUpdateEventV1.class));
        CameraUpdateEventV1 future = mapper.readValue("""
                {"streamInstanceId":"ca1bce64-5715-4f4e-ab72-fafcb2c3cf33","sequence":8,
                "kind":"FUTURE_KIND","observedAt":"2026-07-19T00:00:01Z","futureField":true}
                """, CameraUpdateEventV1.class);
        assertEquals(CameraUpdateKindV1.UNKNOWN, future.kind());
    }
}
