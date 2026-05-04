package com.github.xandergos.terraindiffusionmc.pipeline;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ensures model assets exist locally and match the expected SHA-256 hashes.
 *
 * <p>Assets are downloaded from a pinned Hugging Face commit into the game directory :
 * {@code .minecraft/terrain-diffusion-models}.
 */
public final class ModelAssetManager {
    private static final Logger LOG = LoggerFactory.getLogger(ModelAssetManager.class);
    private static final String MANIFEST_RESOURCE_PATH = "/model-assets-manifest.json";
    private static final long PROGRESS_LOG_THRESHOLD_BYTES = 100L * 1024L * 1024L;
    private static final Path MODEL_DIRECTORY = FabricLoader.getInstance()
            .getGameDir()
            .resolve("terrain-diffusion-models");
    private static final AtomicBoolean READY = new AtomicBoolean(false);
    private static final Gson GSON = new Gson();
    private static final Type MANIFEST_TYPE = new TypeToken<ModelAssetManifest>() {}.getType();

    private ModelAssetManager() {
    }

    /**
     * Ensures all required assets are present and verified.
     */
    public static void ensureAssetsReady() {
        if (READY.get()) {
            return;
        }
        synchronized (ModelAssetManager.class) {
            if (READY.get()) {
                return;
            }
            try {
                Files.createDirectories(MODEL_DIRECTORY);
                ModelAssetManifest manifest = loadManifest();
                String offlineHelpUrl = buildOfflineHelpUrl(manifest);
                boolean shouldValidatePreExistingModels = TerrainDiffusionConfig.validateModel();
                LOG.info("Preparing terrain diffusion model assets in {}", MODEL_DIRECTORY);
                for (Map.Entry<String, ManifestAsset> assetEntry : manifest.assets.entrySet()) {
                    String fileName = assetEntry.getKey();
                    ManifestAsset assetMetadata = assetEntry.getValue();
                    Path localAssetPath = MODEL_DIRECTORY.resolve(fileName);
                    ensureSingleAsset(localAssetPath, assetMetadata, manifest.revision, offlineHelpUrl, shouldValidatePreExistingModels);
                }
                LOG.info("Terrain diffusion model assets ready");
                READY.set(true);
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to prepare terrain diffusion model assets", exception);
            }
        }
    }

    /**
     * Returns the local path for an asset in the model directory.
     */
    public static Path resolveAssetPath(String fileName) {
        return MODEL_DIRECTORY.resolve(fileName);
    }

    private static void ensureSingleAsset(
            Path localAssetPath,
            ManifestAsset assetMetadata,
            String revision,
            String offlineHelpUrl,
            boolean shouldValidatePreExistingModels
    ) throws IOException, InterruptedException {
        if (Files.exists(localAssetPath)) {
            if (!shouldValidatePreExistingModels) {
                LOG.info("Using pre-existing model asset '{}' without SHA validation (validate_model=false)",
                        localAssetPath.getFileName());
                return;
            }
            String existingHash = sha256Hex(localAssetPath);
            if (existingHash.equalsIgnoreCase(assetMetadata.sha256)) {
                LOG.info("Model asset '{}' already present and verified", localAssetPath.getFileName());
                return;
            }
            LOG.warn("Model asset '{}' hash mismatch. Re-downloading expected revision.", localAssetPath.getFileName());
            Files.delete(localAssetPath);
        }
        downloadAndVerifyAsset(localAssetPath, assetMetadata, revision, offlineHelpUrl);
    }

    private static void downloadAndVerifyAsset(Path localAssetPath, ManifestAsset assetMetadata, String revision, String offlineHelpUrl) throws IOException, InterruptedException {
        Path temporaryAssetPath = localAssetPath.resolveSibling(localAssetPath.getFileName() + ".tmp");
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(assetMetadata.url)).GET().build();

        try {
            LOG.info("Downloading model asset '{}' ({})",
                    localAssetPath.getFileName(), humanReadableBytes(assetMetadata.sizeBytes));
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (statusCode < HttpURLConnection.HTTP_OK || statusCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IllegalStateException("Failed to download model asset from " + assetMetadata.url + " (HTTP " + statusCode + ")");
            }

            try (InputStream responseStream = response.body();
                 OutputStream fileOutputStream = Files.newOutputStream(temporaryAssetPath)) {
                copyWithProgress(responseStream, fileOutputStream, localAssetPath.getFileName().toString(), assetMetadata.sizeBytes);
            }

            String downloadedHash = sha256Hex(temporaryAssetPath);
            if (!downloadedHash.equalsIgnoreCase(assetMetadata.sha256)) {
                Files.deleteIfExists(temporaryAssetPath);
                throw new IllegalStateException("SHA-256 mismatch for " + localAssetPath.getFileName()
                        + ". Expected " + assetMetadata.sha256 + " but got " + downloadedHash);
            }
            Files.move(temporaryAssetPath, localAssetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("Downloaded and verified model asset '{}'", localAssetPath.getFileName());
        } catch (Exception exception) {
            Files.deleteIfExists(temporaryAssetPath);
            if (isOfflineError(exception)) {
                throw new IllegalStateException(
                        "Terrain Diffusion models are missing and must be downloaded while online. " +
                                "Connect to the internet and restart Minecraft. Direct download: " + offlineHelpUrl +
                                " (revision " + revision + ")",
                        exception);
            }
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            if (exception instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            throw new IllegalStateException("Failed downloading model asset: " + localAssetPath.getFileName(), exception);
        }
    }

    private static boolean isOfflineError(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ModelAssetManifest loadManifest() {
        try (InputStream manifestStream = ModelAssetManager.class.getResourceAsStream(MANIFEST_RESOURCE_PATH);
             InputStreamReader manifestReader = new InputStreamReader(Objects.requireNonNull(manifestStream), StandardCharsets.UTF_8)) {
            ModelAssetManifest modelAssetManifest = GSON.fromJson(manifestReader, MANIFEST_TYPE);
            if (modelAssetManifest == null || modelAssetManifest.assets == null || modelAssetManifest.assets.isEmpty()) {
                throw new IllegalStateException("Model asset manifest is empty: " + MANIFEST_RESOURCE_PATH);
            }
            return modelAssetManifest;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load model asset manifest from " + MANIFEST_RESOURCE_PATH, exception);
        }
    }

    private static String buildOfflineHelpUrl(ModelAssetManifest manifest) {
        return "https://huggingface.co/" + manifest.repositorySlug + "/tree/" + manifest.revision;
    }

    private static void copyWithProgress(
            InputStream responseStream,
            OutputStream fileOutputStream,
            String fileName,
            long expectedSizeBytes
    ) throws IOException {
        boolean shouldLogProgress = expectedSizeBytes >= PROGRESS_LOG_THRESHOLD_BYTES;
        byte[] copyBuffer = new byte[256 * 1024];
        long downloadedBytes = 0L;
        int nextProgressPercent = 10;
        int readCount;
        while ((readCount = responseStream.read(copyBuffer)) != -1) {
            fileOutputStream.write(copyBuffer, 0, readCount);
            downloadedBytes += readCount;
            if (shouldLogProgress && expectedSizeBytes > 0) {
                int completedPercent = (int) ((downloadedBytes * 100L) / expectedSizeBytes);
                while (completedPercent >= nextProgressPercent && nextProgressPercent <= 100) {
                    LOG.info("Downloading '{}'... {}% ({}/{})",
                            fileName,
                            nextProgressPercent,
                            humanReadableBytes(downloadedBytes),
                            humanReadableBytes(expectedSizeBytes));
                    nextProgressPercent += 10;
                }
            }
        }
        if (expectedSizeBytes <= 0 || !shouldLogProgress) {
            LOG.info("Downloading '{}'... {} downloaded",
                    fileName, humanReadableBytes(downloadedBytes));
        }
    }

    private static String sha256Hex(Path localFilePath) throws IOException {
        MessageDigest messageDigest = createSha256Digest();
        try (InputStream fileStream = Files.newInputStream(localFilePath);
             DigestInputStream digestInputStream = new DigestInputStream(fileStream, messageDigest)) {
            byte[] readBuffer = new byte[64 * 1024];
            while (digestInputStream.read(readBuffer) != -1) {
                // Streaming digest update happens inside DigestInputStream.
            }
        }
        return toHex(messageDigest.digest());
    }

    private static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", noSuchAlgorithmException);
        }
    }

    private static String toHex(byte[] digestBytes) {
        StringBuilder hexBuilder = new StringBuilder(digestBytes.length * 2);
        for (byte digestByte : digestBytes) {
            hexBuilder.append(String.format("%02x", digestByte));
        }
        return hexBuilder.toString();
    }

    private static String humanReadableBytes(long byteCount) {
        if (byteCount < 1024L) {
            return byteCount + " B";
        }
        double kibibytes = byteCount / 1024.0;
        if (kibibytes < 1024.0) {
            return String.format("%.1f KiB", kibibytes);
        }
        double mebibytes = kibibytes / 1024.0;
        if (mebibytes < 1024.0) {
            return String.format("%.1f MiB", mebibytes);
        }
        double gibibytes = mebibytes / 1024.0;
        return String.format("%.2f GiB", gibibytes);
    }

    private static final class ModelAssetManifest {
        String repositorySlug;
        String revision;
        Map<String, ManifestAsset> assets;
    }

    private static final class ManifestAsset {
        String sha256;
        long sizeBytes;
        String url;
    }
}
