package org.example.aisurv.pipeline;

import org.example.aisurv.detection.PersonDetector;
import org.example.aisurv.frame.FramePacket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HumanAnalysisBranchTest {
    @Test
    void recordsInferenceFailureSeparatelyFromAnEmptyDetection() {
        PersonDetector detector = mock(PersonDetector.class);
        when(detector.detect(null)).thenThrow(new PersonDetector.DetectionException("inference unavailable"));
        PipelineContext context = movingContext();

        new HumanAnalysisBranch(detector).process(context);

        assertTrue(context.personDetectionAttempted());
        assertEquals("inference unavailable", context.personDetectionFailure());
        assertTrue(context.persons().isEmpty());
    }

    @Test
    void recordsSuccessfulEmptyDetectionAsAvailable() {
        PersonDetector detector = mock(PersonDetector.class);
        when(detector.detect(null)).thenReturn(List.of());
        PipelineContext context = movingContext();

        new HumanAnalysisBranch(detector).process(context);

        assertTrue(context.personDetectionAttempted());
        assertEquals(null, context.personDetectionFailure());
        assertTrue(context.persons().isEmpty());
    }

    private static PipelineContext movingContext() {
        PipelineContext context = new PipelineContext(new FramePacket("Gate", Instant.EPOCH, null));
        context.setMotionDetected(true);
        return context;
    }
}
