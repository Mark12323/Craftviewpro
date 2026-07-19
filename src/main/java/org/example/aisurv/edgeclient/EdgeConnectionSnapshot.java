package org.example.aisurv.edgeclient;

import org.example.aisurv.contract.v1.EdgeHealthResponseV1;

public record EdgeConnectionSnapshot(
        EdgeConnectionState state,
        String message,
        EdgeHealthResponseV1 health
) {
}
