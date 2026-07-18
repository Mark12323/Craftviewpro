package org.example.aisurv.event;

import org.example.aisurv.pipeline.PipelineContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

public class EventEngine {
    private static final Duration DEFAULT_EVENT_COOLDOWN = Duration.ofSeconds(10);

    private final EventRuleEvaluator ruleEvaluator;
    private final long eventCooldownNanos;
    private final LongSupplier nanoTime;
    private final Map<EventKey, Long> lastEmittedAt = new HashMap<>();

    public EventEngine() {
        this(DEFAULT_EVENT_COOLDOWN, System::nanoTime);
    }

    EventEngine(Duration eventCooldown, LongSupplier nanoTime) {
        if (eventCooldown.isNegative() || eventCooldown.isZero()) {
            throw new IllegalArgumentException("eventCooldown must be positive");
        }
        this.ruleEvaluator = new EventRuleEvaluator(defaultRules());
        this.eventCooldownNanos = eventCooldown.toNanos();
        this.nanoTime = nanoTime;
    }

    public List<SurveillanceEvent> evaluate(PipelineContext context) {
        long now = nanoTime.getAsLong();
        return ruleEvaluator.evaluate(context).stream()
                .filter(event -> shouldEmit(event, now))
                .toList();
    }

    private boolean shouldEmit(SurveillanceEvent event, long now) {
        EventKey key = new EventKey(event.cameraName(), event.eventType());
        Long previous = lastEmittedAt.get(key);
        if (previous != null && now - previous < eventCooldownNanos) {
            return false;
        }
        lastEmittedAt.put(key, now);
        return true;
    }

    private List<EventRule> defaultRules() {
        return List.of(
                new EventRule(
                        "motion-activity",
                        EventType.MOTION_DETECTED,
                        EventSeverity.MEDIUM,
                        true,
                        0,
                        "Motion detected on {camera}"
                ),
                new EventRule(
                        "person-detected",
                        EventType.PERSON_DETECTED,
                        EventSeverity.HIGH,
                        true,
                        1,
                        "{personCount} person(s) detected on {camera}"
                )
        );
    }

    private record EventKey(String cameraName, EventType eventType) {
    }
}
