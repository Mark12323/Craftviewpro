package org.example.aisurv;

import org.example.aisurv.contract.v1.ApiVersionV1;
import org.example.aisurv.contract.v1.EdgeCapabilityV1;
import org.example.aisurv.contract.v1.EdgeHealthResponseV1;
import org.example.aisurv.contract.v1.EdgeStatusV1;
import org.example.aisurv.edgeclient.EdgeConnectionSnapshot;
import org.example.aisurv.edgeclient.EdgeConnectionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AISurvApplicationTest {
    @Test
    void usesEdgeMonitoringOnlyForAnAvailableCapableEdge() {
        EdgeHealthResponseV1 capable = health(Set.of(EdgeCapabilityV1.EDGE_MONITORING));
        EdgeHealthResponseV1 queryOnly = health(Set.of(EdgeCapabilityV1.CAMERA_QUERY));

        assertTrue(AISurvApplication.shouldUseEdgeMonitoring(
                new EdgeConnectionSnapshot(EdgeConnectionState.AVAILABLE, "ready", capable)));
        assertFalse(AISurvApplication.shouldUseEdgeMonitoring(
                new EdgeConnectionSnapshot(EdgeConnectionState.AVAILABLE, "old edge", queryOnly)));
        assertFalse(AISurvApplication.shouldUseEdgeMonitoring(
                new EdgeConnectionSnapshot(EdgeConnectionState.DEGRADED, "degraded", capable)));
        assertFalse(AISurvApplication.shouldUseEdgeMonitoring(null));
    }

    private static EdgeHealthResponseV1 health(Set<EdgeCapabilityV1> capabilities) {
        return new EdgeHealthResponseV1(
                ApiVersionV1.CURRENT, "test", EdgeStatusV1.READY, capabilities, Map.of(), Instant.now());
    }
}
