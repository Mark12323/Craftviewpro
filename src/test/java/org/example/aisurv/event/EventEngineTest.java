package org.example.aisurv.event;

import org.example.aisurv.detection.PersonDetector;
import org.example.aisurv.frame.FramePacket;
import org.example.aisurv.pipeline.PipelineContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventEngineTest {
    @Test
    void emitsMotionAndPersonEventsWhenBothDefaultRulesMatch() {
        PipelineContext context = context(true, 1);

        List<SurveillanceEvent> events = new EventEngine().evaluate(context);

        assertEquals(List.of(EventType.MOTION_DETECTED, EventType.PERSON_DETECTED),
                events.stream().map(SurveillanceEvent::eventType).toList());
        assertEquals(List.of("Motion detected on Yard", "1 person(s) detected on Yard"),
                events.stream().map(SurveillanceEvent::message).toList());
    }

    @Test
    void emitsNoDefaultEventsWithoutMotion() {
        assertTrue(new EventEngine().evaluate(context(false, 1)).isEmpty());
    }

    @Test
    void suppressesDuplicateEventsByElapsedTimeWithoutChangingMotionState() {
        AtomicLong now = new AtomicLong();
        EventEngine engine = new EventEngine(Duration.ofSeconds(10), now::get);

        assertEquals(2, engine.evaluate(context(true, 1)).size());
        now.set(Duration.ofSeconds(9).toNanos());
        assertTrue(engine.evaluate(context(true, 1)).isEmpty());
        now.set(Duration.ofSeconds(10).toNanos());
        assertEquals(2, engine.evaluate(context(true, 1)).size());
    }

    private static PipelineContext context(boolean motion, int personCount) {
        PipelineContext context = new PipelineContext(new FramePacket("Yard", Instant.EPOCH, null));
        context.setMotionDetected(motion);
        context.setPersons(personCount == 0
                ? List.of()
                : List.of(new PersonDetector.BoundingBox(0, 0, 1, 1, 0.9f)));
        return context;
    }
}
