package org.example.aisurv.camera;

public final class CameraVersionConflictException extends RuntimeException {
    public CameraVersionConflictException() { super("Camera was changed by another operation"); }
}
