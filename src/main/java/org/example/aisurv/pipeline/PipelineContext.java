package org.example.aisurv.pipeline;

import org.example.aisurv.event.SurveillanceEvent;
import org.example.aisurv.frame.FramePacket;
import org.example.aisurv.detection.PersonDetector;

import java.util.ArrayList;
import java.util.List;

public class PipelineContext {
    private final FramePacket framePacket;
    private boolean motionDetected;
    private List<PersonDetector.BoundingBox> persons = List.of();
    private boolean personDetectionAttempted;
    private String personDetectionFailure;
    private final List<SurveillanceEvent> emittedEvents = new ArrayList<>();

    public PipelineContext(FramePacket framePacket) {
        this.framePacket = framePacket;
    }

    public FramePacket framePacket() {
        return framePacket;
    }

    public boolean motionDetected() {
        return motionDetected;
    }

    public void setMotionDetected(boolean motionDetected) {
        this.motionDetected = motionDetected;
    }

    public List<PersonDetector.BoundingBox> persons() {
        return persons;
    }

    public void setPersons(List<PersonDetector.BoundingBox> persons) {
        this.persons = persons;
    }

    public boolean personDetectionAttempted() {
        return personDetectionAttempted;
    }

    public String personDetectionFailure() {
        return personDetectionFailure;
    }

    public void recordPersonDetectionResult(List<PersonDetector.BoundingBox> persons) {
        this.personDetectionAttempted = true;
        this.personDetectionFailure = null;
        this.persons = List.copyOf(persons);
    }

    public void recordPersonDetectionFailure(String failure) {
        this.personDetectionAttempted = true;
        this.personDetectionFailure = failure;
        this.persons = List.of();
    }

    public void emitEvent(SurveillanceEvent event) {
        emittedEvents.add(event);
    }

    public List<SurveillanceEvent> emittedEvents() {
        return List.copyOf(emittedEvents);
    }
}
