package com.github.xandergos.terraindiffusionmc.pipeline;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Build-time only: reads ONNX files from a directory, runs graph optimization,
 * and writes optimized models to an output directory. Use CPU only so the build
 * does not require GPU. Run via Gradle task optimizeOnnxModels.
 */
public final class OptimizeOnnxModels {

    public static void main(String[] args) throws OrtException, IOException {
        if (args.length != 2) {
            System.err.println("Usage: OptimizeOnnxModels <inputDir> <outputDir>");
            System.exit(1);
        }
        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        Files.createDirectories(outputDir);

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (Stream<Path> list = Files.list(inputDir)) {
            for (Path p : list.filter(f -> f.toString().endsWith(".onnx")).toList()) {
                String name = p.getFileName().toString();
                System.out.println("Optimizing " + name + " ...");
                byte[] modelBytes = Files.readAllBytes(p);
                OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
                opts.setOptimizedModelFilePath(outputDir.resolve(name).toAbsolutePath().toString());
                try (OrtSession ignored = env.createSession(modelBytes, opts)) {
                    // Session creation writes the optimized model to the path above
                }
                System.out.println("  -> " + outputDir.resolve(name));
            }
        }
    }
}
