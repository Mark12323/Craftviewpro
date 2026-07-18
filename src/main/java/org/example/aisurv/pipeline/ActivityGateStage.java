package org.example.aisurv.pipeline;

import org.example.aisurv.detection.MotionDetector;

public class ActivityGateStage implements PipelineStage {
    private final MotionDetector motionDetector;

    public ActivityGateStage(MotionDetector motionDetector) {
        this.motionDetector = motionDetector;
    }

    @Override
    public void process(PipelineContext context) {
        boolean motion = motionDetector.detect(context.framePacket().frame());
        context.setMotionDetected(motion);
    }
}
