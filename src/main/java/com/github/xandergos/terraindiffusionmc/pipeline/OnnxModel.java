package com.github.xandergos.terraindiffusionmc.pipeline;

import ai.onnxruntime.*;
import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin wrapper around ONNX Runtime for running WorldPipeline EDMUnet2D models.
 *
 * <p>Input/output naming follows the convention from terrain_diffusion/onnx/export.py:
 * inputs are "x", "noise_labels", "cond_0", "cond_1", ... and output is "output".
 */
public final class OnnxModel implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OnnxModel.class);

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String name;

    public OnnxModel(String resourcePath, String name) {
        this.name = name;
        try {
            LOG.debug("Loading ONNX model '{}' from {}", name, resourcePath);
            long start = System.currentTimeMillis();
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            applyExecutionProviders(opts);
            byte[] modelBytes;
            try (InputStream is = OnnxModel.class.getResourceAsStream(resourcePath)) {
                if (is == null) throw new RuntimeException("Model not found: " + resourcePath);
                modelBytes = is.readAllBytes();
            }
            this.session = env.createSession(modelBytes, opts);
            long elapsed = System.currentTimeMillis() - start;
            int sizeKb = modelBytes.length / 1024;
            LOG.info("ONNX model '{}' loaded ({} KB) in {} ms", name, sizeKb, elapsed);
        } catch (Exception e) {
            LOG.error("Failed to load ONNX model '{}' from {}", name, resourcePath, e);
            throw new RuntimeException("Failed to load ONNX model: " + resourcePath, e);
        }
    }

    /**
     * Run the model with a flat float array for each named input.
     * Each entry in {@code inputs} is (name, float[] data, long[] shape).
     *
     * @return the output tensor as a flat float array
     */
    public float[] run(Object[][] inputs) {
        Map<String, OnnxTensor> feed = new LinkedHashMap<>();
        try {
            for (Object[] inp : inputs) {
                String inputName = (String) inp[0];
                float[] data = (float[]) inp[1];
                long[] shape = (long[]) inp[2];
                FloatBuffer buf = FloatBuffer.wrap(data);
                feed.put(inputName, OnnxTensor.createTensor(env, buf, shape));
            }
            try (OrtSession.Result result = session.run(feed)) {
                OnnxTensor output = (OnnxTensor) result.get(0);
                return flattenOnnxOutput(output);
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX inference failed for model: " + name, e);
        } finally {
            for (OnnxTensor t : feed.values()) t.close();
        }
    }

    /** Convenience: run with x, noise_labels, and optional cond tensors. */
    public float[] runModel(float[] x, long[] xShape,
                            float[] noiseLabels,
                            float[][] condInputs, long[][] condShapes) {
        int nCond = condInputs == null ? 0 : condInputs.length;
        Object[][] inputs = new Object[2 + nCond][3];
        inputs[0] = new Object[]{"x", x, xShape};
        inputs[1] = new Object[]{"noise_labels", noiseLabels, new long[]{noiseLabels.length}};
        for (int i = 0; i < nCond; i++) {
            inputs[2 + i] = new Object[]{"cond_" + i, condInputs[i], condShapes[i]};
        }
        return run(inputs);
    }

    private static void applyExecutionProviders(OrtSession.SessionOptions opts) throws OrtException {
        String device = TerrainDiffusionConfig.inferenceDevice();
        if ("cpu".equals(device)) {
            LOG.info("Terrain diffusion inference: CPU");
            return;
        }

        boolean gpuRequired = "gpu".equals(device);
        boolean added = false;

        try {
            opts.addCUDA(0);
            added = true;
            LOG.info("Terrain diffusion inference: GPU (CUDA)");
        } catch (Throwable t) {
            LOG.warn("CUDA not available: {} - {}", t.getClass().getSimpleName(), t.getMessage());
        }
        if (!added) {
            try {
                opts.addDirectML(0);
                added = true;
                LOG.info("Terrain diffusion inference: GPU (DirectML)");
            } catch (Throwable t) {
                LOG.warn("DirectML not available: {} - {}", t.getClass().getSimpleName(), t.getMessage());
            }
        }
        if (gpuRequired && !added) {
            throw new OrtException("inference.device=gpu but neither CUDA nor DirectML is available. Use the GPU build or set inference.device=cpu.");
        }
        if (!added) {
            LOG.info("Terrain diffusion inference: CPU (fallback)");
            LOG.warn("No GPU provider loaded. Check that the mod jar is the GPU build (no -cpu suffix) and drivers are installed (NVIDIA: CUDA; Windows: DirectX 12).");
        }
    }

    private static float[] flattenOnnxOutput(OnnxTensor tensor) throws OrtException {
        FloatBuffer buf = tensor.getFloatBuffer();
        float[] out = new float[buf.remaining()];
        buf.get(out);
        return out;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            // ignore on close
        }
    }
}
