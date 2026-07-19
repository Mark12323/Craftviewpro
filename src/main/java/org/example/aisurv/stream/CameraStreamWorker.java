package org.example.aisurv.stream;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.example.aisurv.detection.MotionDetector;
import org.example.aisurv.detection.PersonDetector;
import org.example.aisurv.event.CameraHealthEvent;
import org.example.aisurv.event.CameraHealthState;
import org.example.aisurv.event.EventDispatcher;
import org.example.aisurv.frame.FrameDropPolicy;
import org.example.aisurv.frame.FramePacket;
import org.example.aisurv.frame.FrameQueue;
import org.example.aisurv.frame.FrameRateController;
import org.example.aisurv.frame.FrameTimestampService;
import org.example.aisurv.pipeline.PipelineContext;
import org.example.aisurv.pipeline.SurveillancePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CameraStreamWorker implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraStreamWorker.class);

    public enum State {
        NEW,
        RUNNING,
        STOP_REQUESTED,
        TERMINATED
    }

    private final String cameraName;
    private final String rtspUrl;
    private final StreamMonitorSurface monitor;
    private final FrameRateController frameRateController = new FrameRateController(Duration.ofMillis(500));
    private final FrameQueue frameQueue = new FrameQueue(20, FrameDropPolicy.DROP_OLDEST);
    private final FrameTimestampService frameTimestampService = new FrameTimestampService();
    private final DetectorInitialization configuredDetectorInitialization;
    private volatile SurveillancePipeline pipeline;
    private volatile PersonDetector personDetector;
    private volatile String detectorFailure;
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private boolean detectorRuntimeImpaired;
    private String detectorRuntimeFailure;
    private boolean processingImpaired;
    private CameraHealthState lastHealthState;
    private String lastHealthDetail;

    public CameraStreamWorker(String cameraName, String rtspUrl, StreamMonitorSurface monitor) {
        this(cameraName, rtspUrl, monitor, null);
    }

    CameraStreamWorker(String cameraName, String rtspUrl, StreamMonitorSurface monitor,
                       DetectorInitialization detectorInitialization) {
        this.cameraName = cameraName;
        this.rtspUrl = rtspUrl;
        this.monitor = monitor;
        this.configuredDetectorInitialization = detectorInitialization;
    }

    private static DetectorInitialization createPersonDetector() {
        try {
            return new DetectorInitialization(new PersonDetector("models/yolov8n.onnx"), null);
        } catch (Exception e) {
            String failure = e.getMessage();
            if (failure == null || failure.isBlank()) {
                failure = e.getClass().getSimpleName();
            }
            return new DetectorInitialization(null, failure);
        }
    }

    @Override
    public void run() {
        if (!state.compareAndSet(State.NEW, State.RUNNING)) {
            closeResources();
            state.set(State.TERMINATED);
            publishHealth(CameraHealthState.STOPPED, "Stopped");
            return;
        }

        int retryDelay = 1000;
        try {
            initializePipeline();
            try (Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
                 OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat()) {
                while (!shouldStop()) {
                publishHealth(CameraHealthState.CONNECTING, "Connecting");
                FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspUrl);
                try {
                    if (shouldStop()) {
                        break;
                    }
                    configure(grabber);
                    grabber.start();
                    retryDelay = 1000;
                    frameRateController.reset();
                    pipeline.resetStreamState();
                    publishConnectedHealth();
                    processStream(grabber, matConverter, java2DConverter);
                } catch (Exception e) {
                    if (!shouldStop()) {
                        publishHealth(CameraHealthState.OFFLINE, "Connection failed");
                        LOGGER.warn("{} stream connection failed: {}", cameraName, safeMessage(e));
                    }
                } finally {
                    closeGrabber(grabber);
                }

                if (shouldStop()) {
                    break;
                }
                publishHealth(CameraHealthState.RECONNECTING,
                        "Reconnecting in " + (retryDelay / 1000) + "s");
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    requestStop();
                    break;
                }
                    retryDelay = Math.min(retryDelay * 2, 30000);
                }
            }
        } catch (Exception e) {
            if (!shouldStop()) {
                publishHealth(CameraHealthState.IMPAIRED, "Stream worker failed: " + safeMessage(e));
                LOGGER.error("{} stream worker failed: {}", cameraName, safeMessage(e));
            }
        } finally {
            frameQueue.clear();
            closeResources();
            state.set(State.TERMINATED);
            publishHealth(CameraHealthState.STOPPED, "Stopped");
        }
    }

    public void requestStop() {
        stopRequested.set(true);
        state.compareAndSet(State.NEW, State.STOP_REQUESTED);
        state.compareAndSet(State.RUNNING, State.STOP_REQUESTED);
        if (personDetector != null) {
            personDetector.cancelActiveInference();
        }
    }

    public State state() {
        return state.get();
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    private void processStream(FFmpegFrameGrabber grabber, OpenCVFrameConverter.ToMat matConverter,
                               Java2DFrameConverter java2DConverter) throws Exception {
        FrozenStreamDetector frozenStream = new FrozenStreamDetector(java.time.Duration.ofSeconds(30), System::nanoTime);
        while (!shouldStop()) {
            try (Frame frame = grabber.grabImage()) {
                if (frame == null) {
                    publishHealth(CameraHealthState.OFFLINE, "Stream unavailable");
                    return;
                }
                monitor.onFrameObserved(cameraName, Instant.now());
                if (frozenStream.observe(frame.timestamp)) {
                    publishHealth(CameraHealthState.FROZEN, "Stream frozen");
                    return;
                }
                if (!frameRateController.shouldProcessCurrentFrame()) {
                    continue;
                }
                try {
                    processFrame(frame, matConverter, java2DConverter);
                    clearProcessingImpairment();
                } catch (RuntimeException e) {
                    markProcessingImpaired(e);
                }
            }
        }
    }

    private void processFrame(Frame frame, OpenCVFrameConverter.ToMat matConverter,
                               Java2DFrameConverter java2DConverter) {
        Mat ownedFrame = copyFrameForProcessing(frame, matConverter);
        if (ownedFrame == null) {
            return;
        }

        FramePacket offeredPacket = new FramePacket(cameraName, frameTimestampService.now(), ownedFrame);
        if (!frameQueue.offer(offeredPacket)) {
            offeredPacket.close();
            LOGGER.debug("{} frame dropped by queue policy", cameraName);
            return;
        }

        try (FramePacket queuedPacket = frameQueue.poll()) {
            if (queuedPacket == null) {
                return;
            }
            PipelineContext pipelineContext = new PipelineContext(queuedPacket);
            pipeline.process(pipelineContext);
            updateDetectorHealth(pipelineContext);

            BufferedImage displayImage = copyDisplayFrame(frame, java2DConverter);
            if (displayImage != null) {
                drawBoxes(displayImage, pipelineContext.persons());
                try {
                    monitor.updateFrame(cameraName, displayImage);
                } catch (RuntimeException e) {
                    LOGGER.error("Live-frame listener failed for {}", cameraName, e);
                }
            }
        }
    }

    static Mat copyFrameForProcessing(Frame frame, OpenCVFrameConverter.ToMat matConverter) {
        Mat converted = matConverter.convert(frame);
        return converted == null || converted.empty() ? null : converted.clone();
    }

    static BufferedImage copyDisplayFrame(Frame frame, Java2DFrameConverter java2DConverter) {
        return Java2DFrameConverter.cloneBufferedImage(java2DConverter.convert(frame));
    }

    private boolean shouldStop() {
        return stopRequested.get() || Thread.currentThread().isInterrupted();
    }

    private void publishHealth(CameraHealthState healthState, String detail) {
        if (healthState == lastHealthState && detail.equals(lastHealthDetail)) {
            return;
        }
        lastHealthState = healthState;
        lastHealthDetail = detail;
        try {
            monitor.onCameraHealthEvent(new CameraHealthEvent(cameraName, healthState, detail, Instant.now()));
        } catch (RuntimeException e) {
            LOGGER.error("Camera health listener failed for {}", cameraName, e);
        }
    }

    private static void configure(FFmpegFrameGrabber grabber) {
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("stimeout", "5000000");
        grabber.setOption("rw_timeout", "5000000");
        grabber.setOption("buffer_size", "1024000");
        grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);
    }

    private static void closeGrabber(FFmpegFrameGrabber grabber) {
        try {
            grabber.release();
        } catch (Exception e) {
            LOGGER.debug("Error releasing frame grabber", e);
        }
    }

    private void closeDetector() {
        PersonDetector detector = personDetector;
        if (detector == null && configuredDetectorInitialization != null) {
            detector = configuredDetectorInitialization.detector();
        }
        if (detector != null) {
            detector.close();
        }
    }

    private void closeResources() {
        if (pipeline != null) {
            pipeline.close();
        }
        closeDetector();
    }

    private void initializePipeline() {
        DetectorInitialization initialization = configuredDetectorInitialization == null
                ? createPersonDetector()
                : configuredDetectorInitialization;
        personDetector = initialization.detector();
        detectorFailure = initialization.failure();
        EventDispatcher dispatcher = new EventDispatcher(monitor::onSurveillanceEvent);
        pipeline = new SurveillancePipeline(new MotionDetector(), personDetector, dispatcher);
    }

    private void updateDetectorHealth(PipelineContext context) {
        String failure = context.personDetectionFailure();
        if (failure != null) {
            boolean changed = !detectorRuntimeImpaired || !failure.equals(detectorRuntimeFailure);
            detectorRuntimeImpaired = true;
            detectorRuntimeFailure = failure;
            if (changed) {
                publishConnectedHealth();
            }
        } else if (context.personDetectionAttempted() && detectorRuntimeImpaired) {
            detectorRuntimeImpaired = false;
            detectorRuntimeFailure = null;
            publishConnectedHealth();
        }
    }

    private void markProcessingImpaired(RuntimeException failure) {
        if (!processingImpaired) {
            processingImpaired = true;
            publishConnectedHealth();
        }
        LOGGER.warn("{} frame processing failed: {}", cameraName, safeMessage(failure));
        LOGGER.debug("{} frame processing failure", cameraName, failure);
    }

    private void clearProcessingImpairment() {
        if (processingImpaired) {
            processingImpaired = false;
            publishConnectedHealth();
        }
    }

    private void publishConnectedHealth() {
        if (detectorFailure != null) {
            publishHealth(CameraHealthState.IMPAIRED,
                    "Connected; person detection unavailable: " + detectorFailure);
        } else if (detectorRuntimeImpaired) {
            publishHealth(CameraHealthState.IMPAIRED,
                    "Connected; person detection impaired: " + detectorRuntimeFailure);
        } else if (processingImpaired) {
            publishHealth(CameraHealthState.IMPAIRED, "Connected; frame processing impaired");
        } else {
            publishHealth(CameraHealthState.ONLINE, "Connected");
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        message = message.replace(rtspUrl, "[redacted RTSP URL]");
        return message.replaceAll("(?i)(rtsps?://)[^/@\\s]+@", "$1[redacted]@");
    }

    private void drawBoxes(BufferedImage image, List<PersonDetector.BoundingBox> boxes) {
        if (boxes.isEmpty()) {
            return;
        }
        Graphics2D g2d = image.createGraphics();
        try {
            g2d.setStroke(new BasicStroke(3));
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 14));
            for (PersonDetector.BoundingBox box : boxes) {
                int x = (int) box.x;
                int y = (int) box.y;
                int w = (int) box.width;
                int h = (int) box.height;
                g2d.setColor(new Color(0, 255, 0, 200));
                g2d.drawRect(x, y, w, h);

                String label = String.format("Person %.0f%%", box.confidence * 100);
                FontMetrics fm = g2d.getFontMetrics();
                int labelW = fm.stringWidth(label) + 8;
                int labelH = fm.getHeight();
                int labelY = y - labelH - 2;
                if (labelY < 0) {
                    labelY = y + h + 2;
                }

                g2d.setColor(new Color(0, 200, 0, 220));
                g2d.fillRect(x, labelY, labelW, labelH);
                g2d.setColor(Color.WHITE);
                g2d.drawString(label, x + 4, labelY + fm.getAscent());
            }
        } finally {
            g2d.dispose();
        }
    }

    record DetectorInitialization(PersonDetector detector, String failure) {
    }
}
