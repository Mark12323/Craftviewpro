package org.example.aisurv.edge.update;

import org.example.aisurv.contract.v1.CameraUpdateKindV1;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CameraUpdateBrokerTest {
    @Test
    void sendsResetAndUpdatesThroughTheSerializedDeliveryExecutor() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        CameraUpdateBroker broker = new CameraUpdateBroker(Runnable::run, timeout -> emitter);

        broker.subscribe();
        broker.publish(CameraUpdateKindV1.UPSERTED, UUID.randomUUID(), 2L);

        assertEquals(1, broker.subscriberCount());
        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        broker.stop();
    }

    @Test
    void removesSubscribersThatCannotReceiveAnUpdate() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doNothing().doThrow(new IOException("disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        CameraUpdateBroker broker = new CameraUpdateBroker(Runnable::run, timeout -> emitter);
        broker.subscribe();

        broker.publish(CameraUpdateKindV1.DELETED, UUID.randomUUID(), 3L);

        assertEquals(0, broker.subscriberCount());
        verify(emitter).complete();
        broker.stop();
    }

    @Test
    void sendsResetBeforeUpdatesVisibleToANewSubscriber() throws Exception {
        ArrayDeque<Runnable> deliveries = new ArrayDeque<>();
        SseEmitter emitter = mock(SseEmitter.class);
        CameraUpdateBroker broker = new CameraUpdateBroker(deliveries::add, timeout -> emitter);
        broker.publish(CameraUpdateKindV1.UPSERTED, UUID.randomUUID(), 1L);
        broker.subscribe();

        deliveries.removeFirst().run();
        deliveries.removeFirst().run();

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        broker.publish(CameraUpdateKindV1.DELETED, UUID.randomUUID(), 2L);
        deliveries.removeFirst().run();
        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        broker.stop();
    }
}
