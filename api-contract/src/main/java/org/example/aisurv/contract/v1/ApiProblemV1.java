package org.example.aisurv.contract.v1;

import java.time.Instant;

public record ApiProblemV1(String code, String message, String path, Instant occurredAt) {
}
