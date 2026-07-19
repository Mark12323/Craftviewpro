package org.example.aisurv.edge.service;

public final class OperationInProgressException extends RuntimeException {
    public OperationInProgressException(String message) {
        super(message);
    }
}
