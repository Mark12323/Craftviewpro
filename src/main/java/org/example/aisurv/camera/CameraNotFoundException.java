package org.example.aisurv.camera;

public final class CameraNotFoundException extends RuntimeException {
    public CameraNotFoundException() { super("Camera was not found"); }
}
