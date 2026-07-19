package org.example.aisurv.edgeclient;

public final class IncompatibleEdgeException extends RuntimeException {
    public IncompatibleEdgeException(String message) {
        super(message);
    }

    public IncompatibleEdgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
