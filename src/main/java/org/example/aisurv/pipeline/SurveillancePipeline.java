package org.example.aisurv.pipeline;

import org.example.aisurv.detection.MotionDetector;
import org.example.aisurv.detection.PersonDetector;
import org.example.aisurv.event.EventDispatcher;
import org.example.aisurv.event.EventEngine;
import org.example.aisurv.event.SurveillanceEvent;

import java.util.ArrayList;
import java.util.List;

public class SurveillancePipeline implements AutoCloseable {
    private final List<PipelineStage> stages;
    private final MotionDetector motionDetector;
    private final EventEngine eventEngine;
    private final EventDispatcher eventDispatcher;

    public SurveillancePipeline(MotionDetector motionDetector, PersonDetector personDetector) {
        this(motionDetector, personDetector, new EventDispatcher());
    }

    public SurveillancePipeline(MotionDetector motionDetector, PersonDetector personDetector,
                                EventDispatcher eventDispatcher) {
        this.motionDetector = motionDetector;
        this.stages = new ArrayList<>();
        this.stages.add(new ActivityGateStage(motionDetector));
        this.stages.add(new HumanAnalysisBranch(personDetector));
        this.eventEngine = new EventEngine();
        this.eventDispatcher = eventDispatcher;
    }

    public void process(PipelineContext context) {
        for (PipelineStage stage : stages) {
            stage.process(context);
        }
        List<SurveillanceEvent> events = eventEngine.evaluate(context);
        for (SurveillanceEvent event : events) {
            context.emitEvent(event);
            eventDispatcher.dispatch(event);
        }
    }

    public void resetStreamState() {
        motionDetector.reset();
    }

    @Override
    public void close() {
        motionDetector.close();
    }
}
