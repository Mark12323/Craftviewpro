package org.example.aisurv.event;

import java.time.Instant;

public class SurveillanceEvent {
    private final String cameraName;
    private final EventType eventType;
    private final EventSeverity severity;
    private final String message;
    private final Instant occurredAt;

    public SurveillanceEvent(String cameraName, EventType eventType, EventSeverity severity, String message, Instant occurredAt) {
        this.cameraName = cameraName;
        this.eventType = eventType;
        this.severity = severity;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public String cameraName() {
        return cameraName;
    }

    public EventType eventType() {
        return eventType;
    }

    public EventSeverity severity() {
        return severity;
    }

    public String message() {
        return message;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
