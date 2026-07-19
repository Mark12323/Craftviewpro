package org.example.aisurv.persistence;

import org.example.aisurv.event.CameraHealthState;
import org.example.aisurv.persistence.entities.CameraRuntimeHealthEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraRuntimeHealthEntityTest {
    @Test
    void accumulatesConnectedUptimeAndReconnectAttemptsAcrossTransitions() {
        UUID id = UUID.randomUUID();
        Instant started = Instant.parse("2026-07-19T00:00:00Z");
        CameraRuntimeHealthEntity health = CameraRuntimeHealthEntity.initial(
                id, CameraHealthState.ONLINE, "STREAM_CONNECTED", started);

        assertTrue(health.transition(CameraHealthState.OFFLINE, "STREAM_UNAVAILABLE", started.plusSeconds(10)));
        assertTrue(health.transition(CameraHealthState.RECONNECTING, "RETRY_SCHEDULED", started.plusSeconds(11)));
        var snapshot = health.toRecord(started.plusSeconds(12));

        assertEquals(10_000, snapshot.accumulatedUptimeMillis());
        assertEquals(1, snapshot.reconnectAttempts());
    }
}
