package org.example.aisurv.event;

import org.example.aisurv.detection.PersonDetector;
import org.example.aisurv.frame.FramePacket;
import org.example.aisurv.pipeline.PipelineContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventRuleEvaluatorTest {
    private static final Instant CAPTURED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void emitsMatchingRulesWithExpandedMessageAndFrameMetadata() {
        EventRule rule = new EventRule(
                "two-people",
                EventType.PERSON_DETECTED,
                EventSeverity.CRITICAL,
                true,
                2,
                "{personCount} people at {camera}"
        );
        PipelineContext context = context(true, 2);

        List<SurveillanceEvent> events = new EventRuleEvaluator(List.of(rule)).evaluate(context);

        assertEquals(1, events.size());
        SurveillanceEvent event = events.getFirst();
        assertEquals("Lobby", event.cameraName());
        assertEquals(EventType.PERSON_DETECTED, event.eventType());
        assertEquals(EventSeverity.CRITICAL, event.severity());
        assertEquals("2 people at Lobby", event.message());
        assertEquals(CAPTURED_AT, event.occurredAt());
    }

    @Test
    void rejectsRulesWhenMotionOrPersonThresholdIsNotMet() {
        EventRule rule = new EventRule(
                "motion-and-person",
                EventType.PERSON_DETECTED,
                EventSeverity.HIGH,
                true,
                1,
                "detected"
        );
        EventRuleEvaluator evaluator = new EventRuleEvaluator(List.of(rule));

        assertTrue(evaluator.evaluate(context(false, 1)).isEmpty());
        assertTrue(evaluator.evaluate(context(true, 0)).isEmpty());
    }

    private static PipelineContext context(boolean motion, int personCount) {
        PipelineContext context = new PipelineContext(new FramePacket("Lobby", CAPTURED_AT, null));
        context.setMotionDetected(motion);
        context.setPersons(java.util.stream.IntStream.range(0, personCount)
                .mapToObj(i -> new PersonDetector.BoundingBox(i, i, 1, 1, 0.9f))
                .toList());
        return context;
    }
}
