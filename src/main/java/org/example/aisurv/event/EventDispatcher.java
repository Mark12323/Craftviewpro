package org.example.aisurv.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDispatcher.class);
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();

    public EventDispatcher() {
    }

    public EventDispatcher(EventListener... listeners) {
        for (EventListener listener : listeners) {
            addListener(listener);
        }
    }

    public void addListener(EventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public boolean removeListener(EventListener listener) {
        return listeners.remove(listener);
    }

    public void dispatch(SurveillanceEvent event) {
        Objects.requireNonNull(event, "event");
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                LOGGER.error("Surveillance event listener failed for camera {}", event.cameraName(), e);
            }
        }
    }

    @FunctionalInterface
    public interface EventListener {
        void onEvent(SurveillanceEvent event);
    }
}
