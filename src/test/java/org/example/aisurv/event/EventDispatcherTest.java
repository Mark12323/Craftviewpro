package org.example.aisurv.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;

class EventDispatcherTest {
    @Test
    void deliversTheOriginalStructuredEventToListeners() {
        AtomicReference<SurveillanceEvent> received = new AtomicReference<>();
        EventDispatcher dispatcher = new EventDispatcher(received::set);
        SurveillanceEvent event = new SurveillanceEvent(
                "Gate", EventType.PERSON_DETECTED, EventSeverity.HIGH,
                "Person detected", Instant.parse("2026-07-14T00:00:00Z"));

        dispatcher.dispatch(event);

        assertSame(event, received.get());
    }
}
