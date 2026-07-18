package org.example.aisurv.camera;

import java.util.UUID;

public record CameraRegistrationResult(UUID id, String displayName, boolean enabled) {
}
