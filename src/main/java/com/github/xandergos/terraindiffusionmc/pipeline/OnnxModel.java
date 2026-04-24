package com.github.xandergos.terraindiffusionmc.pipeline;

import ai.onnxruntime.*;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin wrapper around ONNX Runtime with aggressive VRAM optimization.
 *
 * <p>Only one model is resident in GPU VRAM at a time (GPU-slot swapping).
 * Model weights are kept in CPU RAM between inference calls and uploaded to
 * GPU on demand. This keeps peak VRAM to a single model's footprint instead
 * of all three simultaneously.
 */
public final class OnnxModel implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OnnxModel.class);
    private static final String OPTIMIZED_MODELS_DIR_NAME = "onnx-cache";

    private static volatile String resolvedInferenceProvider = null;
    private static final AtomicBoolean providerLoggedOnce = new AtomicBoolean(false);
    private static final AtomicBoolean cudaWarnLoggedOnce = new AtomicBoolean(false);
    private static final AtomicBoolean dmlWarnLoggedOnce = new AtomicBoolean(false);
    private static final AtomicBoolean noGpuWarnLoggedOnce = new AtomicBoolean(false);

    // GPU slot: when offload_models=true, only one session is alive at a time.
    private static final Object GPU_SLOT_LOCK = new Object();
    private static OnnxModel gpuSlotHolder = null;
    private static OrtSession activeGpuSession = null;

    private final OrtEnvironment env;
    private final byte[] optimizedModelBytes;
    private final String name;
    private OrtSession cpuSession;    // non-null in CPU-only mode
    private OrtSession gpuSession;    // non-null when offload_models=false

    private static final class OptimizedModelLoadResult {
        private final byte[] modelBytes;
        private final Path optimizedModelPath;
        private final boolean loadedFromCache;

        private OptimizedModelLoadResult(byte[] modelBytes, Path optimizedModelPath, boolean loadedFromCache) {
            this.modelBytes = modelBytes;
            this.optimizedModelPath = optimizedModelPath;
            this.loadedFromCache = loadedFromCache;
        }
    }

    public OnnxModel(Path modelFilePath, String name) {
        this.name = name;
        try {
            long start = System.currentTimeMillis();
            this.env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            byte[] sourceModelBytes = Files.readAllBytes(modelFilePath);
            OptimizedModelLoadResult initialOptimizedModelLoadResult = optimizeModelAtRuntime(sourceModelBytes, false);
            byte[] loadedModelBytes;
            try {
                initializeModelSession(initialOptimizedModelLoadResult.modelBytes, start);
                loadedModelBytes = initialOptimizedModelLoadResult.modelBytes;
            } catch (Exception initialLoadException) {
                if (!initialOptimizedModelLoadResult.loadedFromCache) {
                    throw initialLoadException;
                }
                closeLoadedSessions();
                LOG.warn("Cached optimized ONNX model '{}' failed to load. Rebuilding cache: {}",
                        name, initialLoadException.getMessage());
                deleteOptimizedCacheFile(initialOptimizedModelLoadResult.optimizedModelPath);
                OptimizedModelLoadResult rebuiltOptimizedModelLoadResult = optimizeModelAtRuntime(sourceModelBytes, true);
                initializeModelSession(rebuiltOptimizedModelLoadResult.modelBytes, start);
                loadedModelBytes = rebuiltOptimizedModelLoadResult.modelBytes;
            }
            this.optimizedModelBytes = loadedModelBytes;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ONNX model: " + modelFilePath, e);
        }
    }

    /**
     * Optimizes model bytes and caches the optimized file in the config directory.
     * Falls back to the source model bytes if optimization or cache I/O fails.
     */
    private OptimizedModelLoadResult optimizeModelAtRuntime(byte[] sourceModelBytes, boolean forceRebuildFromSource) {
        Path optimizedModelPath = resolveOptimizedModelPath(sourceModelBytes);
        try {
            if (!forceRebuildFromSource && Files.exists(optimizedModelPath)) {
                byte[] cachedOptimizedModelBytes = Files.readAllBytes(optimizedModelPath);
                return new OptimizedModelLoadResult(cachedOptimizedModelBytes, optimizedModelPath, true);
            }

            Files.createDirectories(optimizedModelPath.getParent());
            Path temporaryOptimizedModelPath = optimizedModelPath.resolveSibling(optimizedModelPath.getFileName() + ".tmp");
            Files.deleteIfExists(temporaryOptimizedModelPath);
            OrtSession.SessionOptions optimizationOptions = new OrtSession.SessionOptions();
            optimizationOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
            optimizationOptions.setOptimizedModelFilePath(temporaryOptimizedModelPath.toAbsolutePath().toString());
            try (OrtSession ignored = env.createSession(sourceModelBytes, optimizationOptions)) {
                // Session creation materializes the optimized model on disk.
            }
            byte[] optimizedModelBytesFromDisk = Files.readAllBytes(temporaryOptimizedModelPath);
            Files.move(
                    temporaryOptimizedModelPath,
                    optimizedModelPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
            LOG.info("Optimized ONNX model '{}' at runtime ({} KB -> {} KB)",
                    name, sourceModelBytes.length / 1024, optimizedModelBytesFromDisk.length / 1024);
            return new OptimizedModelLoadResult(optimizedModelBytesFromDisk, optimizedModelPath, false);
        } catch (Exception optimizationException) {
            LOG.warn("Runtime ONNX optimization failed for '{}', using source model bytes: {}",
                    name, optimizationException.getMessage());
            return new OptimizedModelLoadResult(sourceModelBytes, optimizedModelPath, false);
        }
    }

    /** Returns the resolved inference provider name, or {@code "unknown"} if not yet determined. */
    public static String getResolvedInferenceProvider() {
        String provider = resolvedInferenceProvider;
        return provider != null ? provider : "unknown";
    }

    private static void setResolvedProviderOnce(String provider) {
        if (resolvedInferenceProvider == null) {
            resolvedInferenceProvider = provider;
        }
        if (providerLoggedOnce.compareAndSet(false, true)) {
            LOG.info("Terrain diffusion inference: {}", provider);
        }
    }

    /**
     * Loads model sessions for the active inference device configuration.
     */
    private void initializeModelSession(byte[] modelBytes, long startMillis) throws OrtException {
        if ("cpu".equals(TerrainDiffusionConfig.inferenceDevice())) {
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            this.cpuSession = env.createSession(modelBytes, sessionOptions);
            this.gpuSession = null;
            setResolvedProviderOnce("CPU");
            LOG.info("ONNX model '{}' loaded on CPU ({} KB) in {} ms",
                    name, modelBytes.length / 1024, System.currentTimeMillis() - startMillis);
            return;
        }
        if (!TerrainDiffusionConfig.offloadModels()) {
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            addGpuProvider(sessionOptions);
            this.gpuSession = env.createSession(modelBytes, sessionOptions);
            this.cpuSession = null;
            LOG.info("ONNX model '{}' loaded on GPU ({} KB) in {} ms",
                    name, modelBytes.length / 1024, System.currentTimeMillis() - startMillis);
            return;
        }
        this.cpuSession = null;
        this.gpuSession = null;
        LOG.info("ONNX model '{}' bytes cached in CPU RAM ({} KB) in {} ms",
                name, modelBytes.length / 1024, System.currentTimeMillis() - startMillis);
    }

    private void closeLoadedSessions() {
        if (cpuSession != null) {
            try { cpuSession.close(); } catch (OrtException ignored) {}
            cpuSession = null;
        }
        if (gpuSession != null) {
            try { gpuSession.close(); } catch (OrtException ignored) {}
            gpuSession = null;
        }
    }

    private void deleteOptimizedCacheFile(Path optimizedModelPath) {
        try {
            Files.deleteIfExists(optimizedModelPath);
        } catch (Exception deleteException) {
            LOG.warn("Failed to delete optimized cache '{}' for '{}': {}",
                    optimizedModelPath, name, deleteException.getMessage());
        }
    }

    /**
     * Resolves a deterministic cache file path for an optimized model.
     */
    private Path resolveOptimizedModelPath(byte[] sourceModelBytes) {
        String sourceModelHashPrefix = sha256Hex(sourceModelBytes).substring(0, 16);
        String runtimeVersionTag = resolveOnnxRuntimeVersionTag();
        String optimizedFileName = name + "-" + runtimeVersionTag + "-" + sourceModelHashPrefix + ".onnx";
        return ModelAssetManager.resolveAssetPath(OPTIMIZED_MODELS_DIR_NAME)
                .resolve(optimizedFileName);
    }

    /**
     * Returns the ONNX Runtime version used as part of the optimization cache key.
     */
    private static String resolveOnnxRuntimeVersionTag() {
        Package onnxRuntimePackage = OrtEnvironment.class.getPackage();
        String implementationVersion = onnxRuntimePackage == null ? null : onnxRuntimePackage.getImplementationVersion();
        return implementationVersion == null ? "unknown" : implementationVersion;
    }

    /**
     * Computes a lowercase SHA-256 hex string for deterministic cache naming.
     */
    private static String sha256Hex(byte[] inputBytes) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digestBytes = messageDigest.digest(inputBytes);
            StringBuilder hexBuilder = new StringBuilder(digestBytes.length * 2);
            for (byte digestByte : digestBytes) {
                hexBuilder.append(String.format("%02x", digestByte));
            }
            return hexBuilder.toString();
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("Missing SHA-256 algorithm", noSuchAlgorithmException);
        }
    }

    /**
     * Run the model with a flat float array for each named input.
     * Each entry in {@code inputs} is (name, float[] data, long[] shape).
     *
     * @return the output tensor as a flat float array
     */
    public float[] run(Object[][] inputs) {
        if (cpuSession != null) {
            return runWithSession(cpuSession, inputs);
        }
        if (gpuSession != null) {
            return runWithSession(gpuSession, inputs);
        }
        synchronized (GPU_SLOT_LOCK) {
            claimGpuSlot();
            return runWithSession(activeGpuSession, inputs);
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
        for (int i = 0; i < nCond; i++)
            inputs[2 + i] = new Object[]{"cond_" + i, condInputs[i], condShapes[i]};
        return run(inputs);
    }

    /**
     * Evicts the current GPU session if this model doesn't hold the slot,
     * then creates a fresh GPU session from CPU-cached weights.
     * Must be called under GPU_SLOT_LOCK.
     */
    private void claimGpuSlot() {
        if (gpuSlotHolder == this) return;

        if (activeGpuSession != null) {
            LOG.debug("Evicting '{}' from GPU, loading '{}'",
                    gpuSlotHolder != null ? gpuSlotHolder.name : "?", name);
            try { activeGpuSession.close(); } catch (OrtException ignored) {}
            activeGpuSession = null;
            gpuSlotHolder = null;
        }

        try {
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            addGpuProvider(opts);
            activeGpuSession = env.createSession(optimizedModelBytes, opts);
            gpuSlotHolder = this;
            LOG.debug("GPU session ready for '{}'", name);
        } catch (OrtException e) {
            throw new RuntimeException("Failed to create GPU session for: " + name, e);
        }
    }

    private static void addGpuProvider(OrtSession.SessionOptions opts) throws OrtException {
        boolean gpuRequired = "gpu".equals(TerrainDiffusionConfig.inferenceDevice());
        boolean added = false;

        try {
            OrtCUDAProviderOptions cudaOpts = new OrtCUDAProviderOptions(0);
            // Only grow the BFC arena by exactly what is needed, never pre-allocate.
            cudaOpts.add("arena_extend_strategy", "kSameAsRequested");
            // Heuristic: fast startup, no exhaustive benchmarking, workspace-efficient.
            cudaOpts.add("cudnn_conv_algo_search", "HEURISTIC");
            cudaOpts.add("do_copy_in_default_stream", "1");
            opts.addCUDA(cudaOpts);
            cudaOpts.close();
            added = true;
            setResolvedProviderOnce("CUDA");
        } catch (Throwable t) {
            if (cudaWarnLoggedOnce.compareAndSet(false, true)) {
                LOG.warn("CUDA not available: {} - {}. This is expected if you are not using a CUDA build.",
                        t.getClass().getSimpleName(), t.getMessage());
            }
        }

        if (!added) {
            try {
                opts.addDirectML(0);
                added = true;
                setResolvedProviderOnce("DirectML");
            } catch (Throwable t) {
                if (dmlWarnLoggedOnce.compareAndSet(false, true)) {
                    LOG.warn("DirectML not available: {} - {}. This is expected if you are not using a DirectML build.",
                            t.getClass().getSimpleName(), t.getMessage());
                }
            }
        }
        if (gpuRequired && !added) {
            throw new OrtException(
                    "inference.device=gpu but neither CUDA nor DirectML is available. " +
                    "Use the GPU build or set inference.device=cpu.");
        }
        if (!added) {
            setResolvedProviderOnce("CPU");
            if (noGpuWarnLoggedOnce.compareAndSet(false, true)) {
                LOG.warn("No GPU provider loaded. Check drivers and that the mod jar is the GPU build.");
            }
        }
    }

    private static float[] runWithSession(OrtSession session, Object[][] inputs) {
        Map<String, OnnxTensor> feed = new LinkedHashMap<>();
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try {
            for (Object[] inp : inputs) {
                feed.put((String) inp[0],
                        OnnxTensor.createTensor(env, FloatBuffer.wrap((float[]) inp[1]), (long[]) inp[2]));
            }
            try (OrtSession.Result result = session.run(feed)) {
                OnnxTensor output = (OnnxTensor) result.get(0);
                FloatBuffer buf = output.getFloatBuffer();
                float[] out = new float[buf.remaining()];
                buf.get(out);
                return out;
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX inference failed", e);
        } finally {
            for (OnnxTensor t : feed.values()) t.close();
        }
    }

    @Override
    public void close() {
        synchronized (GPU_SLOT_LOCK) {
            if (gpuSlotHolder == this && activeGpuSession != null) {
                try { activeGpuSession.close(); } catch (OrtException ignored) {}
                activeGpuSession = null;
                gpuSlotHolder = null;
            }
        }
        if (cpuSession != null) {
            try { cpuSession.close(); } catch (OrtException ignored) {}
            cpuSession = null;
        }
        if (gpuSession != null) {
            try { gpuSession.close(); } catch (OrtException ignored) {}
            gpuSession = null;
        }
    }
}
