package com.github.xandergos.terraindiffusionmc.debug;

import com.github.xandergos.terraindiffusionmc.pipeline.OnnxModel;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight telemetry for the terrain pipeline and river pass.
 *
 * <p>Everything is static and lock-free on purpose: the terrain provider is used by
 * chunk workers, the integrated server and the client debug HUD in the same JVM.
 * These counters are diagnostic only and must never block world generation.</p>
 */
public final class TerrainDebugStats {
    private TerrainDebugStats() {}

    private static final AtomicLong terrainRequests = new AtomicLong();
    private static final AtomicLong cacheHits = new AtomicLong();
    private static final AtomicLong cacheMisses = new AtomicLong();
    private static final AtomicLong pendingWaits = new AtomicLong();
    private static final AtomicLong completedTiles = new AtomicLong();
    private static final AtomicLong failedTiles = new AtomicLong();
    private static final AtomicLong currentPending = new AtomicLong();
    private static final AtomicLong maxPending = new AtomicLong();
    private static final AtomicLong activeInference = new AtomicLong();

    private static final AtomicLong totalTileMs = new AtomicLong();
    private static final AtomicLong lastTileMs = new AtomicLong();
    private static final AtomicLong maxTileMs = new AtomicLong();
    private static final AtomicLong lastComputedWindows = new AtomicLong();
    private static final AtomicLong totalComputedWindows = new AtomicLong();
    private static final AtomicLong cacheBytes = new AtomicLong();

    private static final AtomicLong riverCarves = new AtomicLong();
    private static final AtomicLong totalRiverMs = new AtomicLong();
    private static final AtomicLong lastRiverMs = new AtomicLong();
    private static final AtomicLong maxRiverMs = new AtomicLong();
    private static final AtomicLong lastRiverWaterCells = new AtomicLong();
    private static final AtomicLong lastRiverBankCells = new AtomicLong();
    private static final AtomicLong lastRiverMaxArea = new AtomicLong();

    private static final AtomicLong riverPlacementRuns = new AtomicLong();
    private static final AtomicLong riverPlacementSkipped = new AtomicLong();
    private static final AtomicLong riverPlacementColumns = new AtomicLong();

    private static final AtomicReference<String> lastTile = new AtomicReference<>("-");
    private static final AtomicReference<String> lastStage = new AtomicReference<>("idle");
    private static final AtomicReference<String> lastError = new AtomicReference<>("");

    public static void recordCacheHit() {
        terrainRequests.incrementAndGet();
        cacheHits.incrementAndGet();
    }

    public static void recordCacheMissQueued(String key, long pendingSize) {
        terrainRequests.incrementAndGet();
        cacheMisses.incrementAndGet();
        lastTile.set(key);
        updatePending(pendingSize);
    }

    public static void recordPendingWait(String key, long pendingSize) {
        terrainRequests.incrementAndGet();
        pendingWaits.incrementAndGet();
        lastTile.set(key);
        updatePending(pendingSize);
    }

    public static void recordInferenceStart(String key, long pendingSize) {
        activeInference.incrementAndGet();
        lastTile.set(key);
        lastStage.set("inference");
        updatePending(pendingSize);
    }

    public static void recordInferenceSuccess(long elapsedMs, long newlyComputedWindows, long cacheBytesNow, long pendingSize) {
        activeInference.updateAndGet(v -> Math.max(0L, v - 1L));
        completedTiles.incrementAndGet();
        totalTileMs.addAndGet(elapsedMs);
        lastTileMs.set(elapsedMs);
        maxTileMs.updateAndGet(v -> Math.max(v, elapsedMs));
        lastComputedWindows.set(newlyComputedWindows);
        totalComputedWindows.addAndGet(Math.max(0L, newlyComputedWindows));
        cacheBytes.set(Math.max(0L, cacheBytesNow));
        lastStage.set("idle");
        lastError.set("");
        updatePending(pendingSize);
    }

    public static void recordInferenceFailure(long elapsedMs, Throwable error, long pendingSize) {
        activeInference.updateAndGet(v -> Math.max(0L, v - 1L));
        failedTiles.incrementAndGet();
        lastTileMs.set(elapsedMs);
        maxTileMs.updateAndGet(v -> Math.max(v, elapsedMs));
        lastStage.set("failed");
        lastError.set(shortError(error));
        updatePending(pendingSize);
    }

    public static void recordRiverCarve(long elapsedMs, int waterCells, int bankCells, float maxAreaCells) {
        riverCarves.incrementAndGet();
        totalRiverMs.addAndGet(elapsedMs);
        lastRiverMs.set(elapsedMs);
        maxRiverMs.updateAndGet(v -> Math.max(v, elapsedMs));
        lastRiverWaterCells.set(waterCells);
        lastRiverBankCells.set(bankCells);
        lastRiverMaxArea.set(Math.round(maxAreaCells));
    }

    public static void recordRiverPlacementSkipped() {
        riverPlacementSkipped.incrementAndGet();
    }

    public static void recordRiverPlacementDone(int touchedColumns) {
        riverPlacementRuns.incrementAndGet();
        riverPlacementColumns.addAndGet(Math.max(0, touchedColumns));
    }

    public static List<String> f3Lines() {
        List<String> lines = new ArrayList<>(10);
        long done = completedTiles.get();
        long avgTile = done == 0L ? 0L : totalTileMs.get() / done;
        long rc = riverCarves.get();
        long avgRiver = rc == 0L ? 0L : totalRiverMs.get() / rc;

        lines.add("Terrain Diffusion " + OnnxModel.getResolvedInferenceProvider());
        lines.add(String.format(Locale.ROOT,
                "TD tiles: req=%d hit=%d miss=%d wait=%d pending=%d/%d active=%d fail=%d",
                terrainRequests.get(), cacheHits.get(), cacheMisses.get(), pendingWaits.get(),
                currentPending.get(), maxPending.get(), activeInference.get(), failedTiles.get()));
        lines.add(String.format(Locale.ROOT,
                "TD tile ms: last=%d avg=%d max=%d windows=%d/%d cache=%.1f MB",
                lastTileMs.get(), avgTile, maxTileMs.get(), lastComputedWindows.get(), totalComputedWindows.get(),
                cacheBytes.get() / 1048576.0));
        lines.add(String.format(Locale.ROOT,
                "TD river: carve last=%d avg=%d max=%d water=%d bank=%d maxA=%d",
                lastRiverMs.get(), avgRiver, maxRiverMs.get(), lastRiverWaterCells.get(),
                lastRiverBankCells.get(), lastRiverMaxArea.get()));
        lines.add(String.format(Locale.ROOT,
                "TD river place: runs=%d skipped=%d columns=%d",
                riverPlacementRuns.get(), riverPlacementSkipped.get(), riverPlacementColumns.get()));
        lines.add("TD last: " + lastStage.get() + " " + lastTile.get());
        String err = lastError.get();
        if (!err.isEmpty()) lines.add("TD error: " + err);
        return lines;
    }

    private static void updatePending(long pendingSize) {
        currentPending.set(Math.max(0L, pendingSize));
        maxPending.updateAndGet(v -> Math.max(v, pendingSize));
    }

    private static String shortError(Throwable error) {
        if (error == null) return "unknown";
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getClass().getSimpleName();
        if (root.getMessage() != null && !root.getMessage().isBlank()) {
            msg += ": " + root.getMessage();
        }
        if (msg.length() <= 180) return msg;
        return msg.substring(0, 180) + "...";
    }

    public static String fullStack(Throwable error) {
        if (error == null) return "unknown";
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
