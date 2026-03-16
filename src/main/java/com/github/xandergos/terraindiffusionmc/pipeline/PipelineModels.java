package com.github.xandergos.terraindiffusionmc.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Holds the three ONNX models used by WorldPipeline. Loaded once at mod init
 * and shared across pipeline instances. Load runs on a background thread so
 * the game thread is not blocked during startup.
 */
public final class PipelineModels implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineModels.class);

    private static final String COARSE_PATH = "/onnx/coarse_model.onnx";
    private static final String BASE_PATH = "/onnx/base_model.onnx";
    private static final String DECODER_PATH = "/onnx/decoder_model.onnx";

    private static volatile PipelineModels INSTANCE;
    private static volatile CountDownLatch loadDone;
    private static volatile boolean loadStarted;

    private final OnnxModel coarseModel;
    private final OnnxModel baseModel;
    private final OnnxModel decoderModel;

    /** Starts loading models on a background thread. Returns immediately. */
    public static synchronized void load() {
        if (INSTANCE != null) return;
        if (loadStarted) return;
        loadStarted = true;
        loadDone = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                LOG.info("Loading terrain-diffusion ML models (background)...");
                long start = System.currentTimeMillis();
                INSTANCE = new PipelineModels();
                long elapsed = System.currentTimeMillis() - start;
                LOG.info("Terrain-diffusion ML models loaded in {} ms", elapsed);
            } catch (Throwable e) {
                LOG.error("Failed to load terrain-diffusion models", e);
            } finally {
                loadDone.countDown();
            }
        }, "terrain-diffusion-models");
        t.setDaemon(true);
        t.start();
    }

    /** Blocks until models are loaded (or load failed). Starts load if not started. */
    public static void awaitLoad() {
        synchronized (PipelineModels.class) {
            if (INSTANCE != null) return;
            if (!loadStarted) load();
        }
        CountDownLatch latch = loadDone;
        if (latch != null) {
            try {
                if (!latch.await(10, TimeUnit.MINUTES)) {
                    throw new RuntimeException("Timed out waiting for terrain-diffusion models");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted waiting for terrain-diffusion models", e);
            }
        }
    }

    public static PipelineModels getInstance() {
        return INSTANCE;
    }

    private PipelineModels() {
        this.coarseModel = new OnnxModel(COARSE_PATH, "coarse");
        this.baseModel = new OnnxModel(BASE_PATH, "base");
        this.decoderModel = new OnnxModel(DECODER_PATH, "decoder");
    }

    public OnnxModel getCoarseModel() { return coarseModel; }
    public OnnxModel getBaseModel() { return baseModel; }
    public OnnxModel getDecoderModel() { return decoderModel; }

    @Override
    public synchronized void close() {
        if (INSTANCE != this) return;
        LOG.info("Closing terrain-diffusion ML models");
        try {
            coarseModel.close();
            baseModel.close();
            decoderModel.close();
        } finally {
            INSTANCE = null;
            loadStarted = false;
        }
    }
}
