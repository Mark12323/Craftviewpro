package org.example.aisurv.pipeline;

import org.example.aisurv.detection.PersonDetector;

import java.util.List;

public class HumanAnalysisBranch implements PipelineStage {
    private final PersonDetector personDetector;

    public HumanAnalysisBranch(PersonDetector personDetector) {
        this.personDetector = personDetector;
    }

    @Override
    public void process(PipelineContext context) {
        if (!context.motionDetected() || personDetector == null) {
            context.setPersons(List.of());
            return;
        }
        try {
            context.recordPersonDetectionResult(personDetector.detect(context.framePacket().frame()));
        } catch (PersonDetector.DetectionException e) {
            context.recordPersonDetectionFailure(e.getMessage());
        }
    }
}
