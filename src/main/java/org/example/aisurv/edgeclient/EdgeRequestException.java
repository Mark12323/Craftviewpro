package org.example.aisurv.edgeclient;

public final class EdgeRequestException extends RuntimeException {
    private final int statusCode;
    private final String problemCode;

    public EdgeRequestException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public EdgeRequestException(String message, int statusCode, String problemCode) {
        super(message);
        this.statusCode = statusCode;
        this.problemCode = problemCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public String problemCode() {
        return problemCode;
    }
}
