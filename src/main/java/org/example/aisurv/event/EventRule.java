package org.example.aisurv.event;

import org.example.aisurv.pipeline.PipelineContext;

public class EventRule {
    private final String name;
    private final EventType eventType;
    private final EventSeverity severity;
    private final boolean requiresMotion;
    private final int minPersonCount;
    private final String messageTemplate;

    public EventRule(
            String name,
            EventType eventType,
            EventSeverity severity,
            boolean requiresMotion,
            int minPersonCount,
            String messageTemplate
    ) {
        this.name = name;
        this.eventType = eventType;
        this.severity = severity;
        this.requiresMotion = requiresMotion;
        this.minPersonCount = minPersonCount;
        this.messageTemplate = messageTemplate;
    }

    public String name() {
        return name;
    }

    public EventType eventType() {
        return eventType;
    }

    public EventSeverity severity() {
        return severity;
    }

    public String messageTemplate() {
        return messageTemplate;
    }

    public boolean matches(PipelineContext context) {
        if (requiresMotion && !context.motionDetected()) {
            return false;
        }
        return context.persons().size() >= minPersonCount;
    }
}
