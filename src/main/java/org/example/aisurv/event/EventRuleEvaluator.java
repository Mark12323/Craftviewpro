package org.example.aisurv.event;

import org.example.aisurv.pipeline.PipelineContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventRuleEvaluator {
    private final List<EventRule> rules;

    public EventRuleEvaluator(List<EventRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<SurveillanceEvent> evaluate(PipelineContext context) {
        List<SurveillanceEvent> events = new ArrayList<>();
        String camera = context.framePacket().cameraName();
        Instant at = context.framePacket().capturedAt();

        for (EventRule rule : rules) {
            if (!rule.matches(context)) {
                continue;
            }
            String message = buildMessage(rule, context);
            events.add(new SurveillanceEvent(camera, rule.eventType(), rule.severity(), message, at));
        }

        return events;
    }

    private String buildMessage(EventRule rule, PipelineContext context) {
        return rule.messageTemplate()
                .replace("{camera}", context.framePacket().cameraName())
                .replace("{personCount}", String.valueOf(context.persons().size()));
    }
}
