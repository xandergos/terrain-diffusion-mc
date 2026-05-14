package com.github.xandergos.terraindiffusionmc.debug.flow;

import com.github.xandergos.terraindiffusionmc.debug.TerrainBaseTile;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostTile;

import java.util.ArrayDeque;
import java.util.Queue;

public final class TerrainFlowBuilder {
    private static final int[] DIR_X = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DIR_Z = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final float SQRT_2 = 1.41421356237F;
    private static final float[] DIR_LENGTH = {1.0F, SQRT_2, 1.0F, SQRT_2, 1.0F, SQRT_2, 1.0F, SQRT_2};
    private static final float SHAPE_SIGNAL_SMOOTHING_BLEND = 0.45F;


    private TerrainFlowBuilder() {
    }

    public static TerrainFlowTile buildCropped(
            TerrainBaseTile source,
            TerrainCostTile costSource,
            int blockStartX,
            int blockStartZ,
            int width,
            int height
    ) {
        int sourceWidth = source.width();
        int sourceHeight = source.height();
        int sourceLen = sourceWidth * sourceHeight;

        byte[] pass0Direction = new byte[sourceLen];
        float[] pass0Accumulation = new float[sourceLen];
        byte[] finalDirection = new byte[sourceLen];
        float[] finalAccumulation = new float[sourceLen];

        int sourceToCostOffsetX = source.blockStartX() - costSource.blockStartX();
        int sourceToCostOffsetZ = source.blockStartZ() - costSource.blockStartZ();

        for (int z = 0; z < sourceHeight; z++) {
            for (int x = 0; x < sourceWidth; x++) {
                int idx = index(x, z, sourceWidth);
                FlowChoice choice = chooseDirection(
                        source,
                        costSource,
                        x,
                        z,
                        x + sourceToCostOffsetX,
                        z + sourceToCostOffsetZ,
                        null,
                        false
                );
                pass0Direction[idx] = choice.direction();
            }
        }

        float maxAccumulationPreviewExpanded = computeAccumulation(pass0Direction, sourceWidth, sourceHeight, pass0Accumulation);
        float[] convergencePotential = buildConvergencePotential(pass0Accumulation, maxAccumulationPreviewExpanded);

        for (int z = 0; z < sourceHeight; z++) {
            for (int x = 0; x < sourceWidth; x++) {
                int idx = index(x, z, sourceWidth);
                FlowChoice choice = chooseDirection(
                        source,
                        costSource,
                        x,
                        z,
                        x + sourceToCostOffsetX,
                        z + sourceToCostOffsetZ,
                        convergencePotential,
                        true
                );
                finalDirection[idx] = choice.direction();
            }
        }

        float maxAccumulationFinalExpanded = computeAccumulation(finalDirection, sourceWidth, sourceHeight, finalAccumulation);

        int len = width * height;
        int[] surfaceY = new int[len];
        short[] biomeIds = new short[len];
        short[] precipitationMm = new short[len];
        byte[] initialDirection = new byte[len];
        byte[] direction = new byte[len];
        byte[] costDirection = new byte[len];
        float[] dropMeters = new float[len];
        float[] uphillMeters = new float[len];
        float[] selectedCost = new float[len];
        float[] costDrop = new float[len];
        float[] score = new float[len];
        boolean[] sink = new boolean[len];
        float[] accumulationPreview = new float[len];
        float[] accumulationFinalCropped = new float[len];
        float[] convergenceBonus = new float[len];
        boolean[] changedByConvergence = new boolean[len];
        float[] directionAlignment = new float[len];
        float[] rawRiverShapeScore = new float[len];
        float[] rawMissingRiverCostSignal = new float[len];
        float[] riverShapeScore = new float[len];
        float[] missingRiverCostSignal = new float[len];

        int sourceOffsetX = blockStartX - source.blockStartX();
        int sourceOffsetZ = blockStartZ - source.blockStartZ();
        int costOffsetX = blockStartX - costSource.blockStartX();
        int costOffsetZ = blockStartZ - costSource.blockStartZ();

        float maxAccumulationPreviewCropped = 1.0F;
        float maxAccumulationFinalCropped = 1.0F;

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = z * width + x;
                int sx = sourceOffsetX + x;
                int sz = sourceOffsetZ + z;
                int cx = costOffsetX + x;
                int cz = costOffsetZ + z;
                int sourceIdx = index(sx, sz, sourceWidth);

                FlowChoice finalChoice = chooseDirection(source, costSource, sx, sz, cx, cz, convergencePotential, true);
                CostChoice costChoice = chooseCostDirection(costSource, cx, cz);
                byte pass0Dir = pass0Direction[sourceIdx];
                byte finalDir = finalDirection[sourceIdx];
                float previewAccum = pass0Accumulation[sourceIdx];
                float finalAccum = finalAccumulation[sourceIdx];
                boolean isSink = finalDir == TerrainFlowTile.SINK_DIRECTION;
                float alignment = directionAlignment(finalDir, costChoice.direction());
                float shapeScore = riverShapeScore(finalChoice.drop(), finalAccum, maxAccumulationFinalExpanded, costChoice.drop(), alignment);
                float missingCost = missingRiverCostSignal(finalChoice.drop(), finalAccum, maxAccumulationFinalExpanded, costChoice.drop());

                surfaceY[idx] = generatedY(source, sx, sz);
                biomeIds[idx] = biome(source, sx, sz);
                precipitationMm[idx] = precipitationMm(source, sx, sz);
                initialDirection[idx] = pass0Dir;
                direction[idx] = finalDir;
                costDirection[idx] = costChoice.direction();
                dropMeters[idx] = finalChoice.drop();
                uphillMeters[idx] = finalChoice.uphill();
                selectedCost[idx] = finalChoice.selectedCost();
                costDrop[idx] = costChoice.drop();
                score[idx] = finalChoice.score() == Float.POSITIVE_INFINITY ? 0.0F : finalChoice.score();
                sink[idx] = isSink;
                accumulationPreview[idx] = previewAccum;
                accumulationFinalCropped[idx] = finalAccum;
                convergenceBonus[idx] = TerrainFlowConfig.CONVERGENCE_BONUS_WEIGHT * convergencePotential[sourceIdx];
                changedByConvergence[idx] = isMeaningfulConvergenceChange(
                        pass0Dir,
                        finalDir,
                        sx,
                        sz,
                        sourceWidth,
                        convergencePotential
                );
                directionAlignment[idx] = alignment;
                rawRiverShapeScore[idx] = isSink ? 0.0F : shapeScore;
                rawMissingRiverCostSignal[idx] = isSink ? 0.0F : missingCost;

                // Display max values should not be dominated by terminal sinks. Otherwise the
                // log accumulation and river preview overlays get compressed and look unrelated
                // to the cost map. Sink accumulation has its own overlay.
                if (!isSink && previewAccum > maxAccumulationPreviewCropped) {
                    maxAccumulationPreviewCropped = previewAccum;
                }
                if (!isSink && finalAccum > maxAccumulationFinalCropped) {
                    maxAccumulationFinalCropped = finalAccum;
                }
            }
        }

        smoothShapeSignal(rawRiverShapeScore, riverShapeScore, sink, width, height);
        smoothShapeSignal(rawMissingRiverCostSignal, missingRiverCostSignal, sink, width, height);

        return new TerrainFlowTile(
                blockStartX,
                blockStartZ,
                width,
                height,
                surfaceY,
                biomeIds,
                precipitationMm,
                initialDirection,
                direction,
                costDirection,
                dropMeters,
                uphillMeters,
                selectedCost,
                costDrop,
                score,
                sink,
                accumulationPreview,
                accumulationFinalCropped,
                convergenceBonus,
                changedByConvergence,
                directionAlignment,
                riverShapeScore,
                missingRiverCostSignal,
                maxAccumulationPreviewCropped,
                maxAccumulationFinalCropped
        );
    }

    private static FlowChoice chooseDirection(
            TerrainBaseTile source,
            TerrainCostTile costSource,
            int sx,
            int sz,
            int cx,
            int cz,
            float[] convergencePotential,
            boolean useConvergence
    ) {
        float centerHeight = baseMeters(source, sx, sz);
        float bestScore = Float.POSITIVE_INFINITY;
        byte bestDirection = TerrainFlowTile.SINK_DIRECTION;
        float bestDrop = 0.0F;
        float bestUphill = 0.0F;
        float bestCost = 0.0F;

        for (byte dir = 0; dir < 8; dir++) {
            int nx = sx + DIR_X[dir];
            int nz = sz + DIR_Z[dir];

            if (nx < 0 || nz < 0 || nx >= source.width() || nz >= source.height()) {
                continue;
            }

            int ncx = cx + DIR_X[dir];
            int ncz = cz + DIR_Z[dir];

            float neighborHeight = baseMeters(source, nx, nz);
            float delta = neighborHeight - centerHeight;

            // Strict gravity gate. Cost and convergence can choose between valid downhill exits
            // but cannot authorize flat cycles or uphill movement.
            if (delta >= -TerrainFlowConfig.MIN_DOWNHILL_METERS) {
                continue;
            }

            float neighborCost = totalCost(costSource, ncx, ncz);
            float downhill = -delta;
            float uphill = 0.0F;
            float diagonal = DIR_X[dir] != 0 && DIR_Z[dir] != 0 ? 1.0F : 0.0F;
            float convergence = 0.0F;

            if (useConvergence && convergencePotential != null) {
                convergence = convergencePotential[index(nx, nz, source.width())];
            }

            float downhillPreference = smoothstep(0.0F, TerrainFlowConfig.PREFERRED_DROP_METERS, downhill);
            float steepDropPenalty = smoothstep(
                    TerrainFlowConfig.STEEP_DROP_START_METERS,
                    TerrainFlowConfig.STEEP_DROP_END_METERS,
                    downhill
            );

            float candidateScore = TerrainFlowConfig.TERRAIN_COST_WEIGHT * neighborCost
                    - TerrainFlowConfig.DOWNHILL_BONUS * downhillPreference
                    + TerrainFlowConfig.STEEP_DROP_PENALTY * steepDropPenalty
                    + TerrainFlowConfig.DIAGONAL_PENALTY * diagonal
                    - TerrainFlowConfig.CONVERGENCE_BONUS_WEIGHT * convergence;

            if (candidateScore < bestScore) {
                bestScore = candidateScore;
                bestDirection = dir;
                bestDrop = downhill;
                bestUphill = uphill;
                bestCost = neighborCost;
            }
        }

        return new FlowChoice(bestDirection, bestDrop, bestUphill, bestCost, bestScore);
    }

    private static CostChoice chooseCostDirection(TerrainCostTile costSource, int cx, int cz) {
        float centerCost = totalCost(costSource, cx, cz);
        byte bestDirection = TerrainFlowTile.SINK_DIRECTION;
        float bestDrop = 0.0F;

        for (byte dir = 0; dir < 8; dir++) {
            int nx = cx + DIR_X[dir];
            int nz = cz + DIR_Z[dir];
            if (nx < 0 || nz < 0 || nx >= costSource.width() || nz >= costSource.height()) {
                continue;
            }

            float candidateDrop = centerCost - totalCost(costSource, nx, nz);
            if (candidateDrop > bestDrop) {
                bestDrop = candidateDrop;
                bestDirection = dir;
            }
        }

        if (bestDrop < TerrainFlowConfig.MIN_COST_DROP) {
            return new CostChoice(TerrainFlowTile.SINK_DIRECTION, 0.0F);
        }

        return new CostChoice(bestDirection, bestDrop);
    }

    private static float directionAlignment(byte flowDirection, byte costDirection) {
        if (flowDirection < 0 || costDirection < 0) {
            return 0.0F;
        }

        float dot = DIR_X[flowDirection] * DIR_X[costDirection] + DIR_Z[flowDirection] * DIR_Z[costDirection];
        float normalized = dot / (DIR_LENGTH[flowDirection] * DIR_LENGTH[costDirection]);
        return clamp01(normalized);
    }

    private static float riverShapeScore(float flowDrop, float accumulation, float maxAccumulation, float costDrop, float alignment) {
        float accumulationWeight = accumulationWeight(accumulation, maxAccumulation);
        float dropWeight = smoothstep(
                TerrainFlowConfig.RIVER_SHAPE_DROP_START_METERS,
                TerrainFlowConfig.RIVER_SHAPE_DROP_END_METERS,
                flowDrop
        );
        float costWeight = smoothstep(
                TerrainFlowConfig.MIN_COST_DROP,
                TerrainFlowConfig.STRONG_COST_DROP,
                costDrop
        );
        return clamp01(accumulationWeight * dropWeight * costWeight * alignment);
    }

    private static float missingRiverCostSignal(float flowDrop, float accumulation, float maxAccumulation, float costDrop) {
        float accumulationWeight = accumulationWeight(accumulation, maxAccumulation);
        float dropWeight = smoothstep(
                TerrainFlowConfig.RIVER_SHAPE_DROP_START_METERS,
                TerrainFlowConfig.RIVER_SHAPE_DROP_END_METERS,
                flowDrop
        );
        float costWeakness = 1.0F - smoothstep(
                TerrainFlowConfig.MIN_COST_DROP,
                TerrainFlowConfig.STRONG_COST_DROP,
                costDrop
        );
        return clamp01(accumulationWeight * dropWeight * costWeakness);
    }


    private static void smoothShapeSignal(float[] source, float[] target, boolean[] sink, int width, int height) {
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                if (sink[idx]) {
                    target[idx] = 0.0F;
                    continue;
                }

                float weightedSum = source[idx] * 4.0F;
                float weight = 4.0F;

                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }

                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                            continue;
                        }

                        int neighborIdx = index(nx, nz, width);
                        if (sink[neighborIdx]) {
                            continue;
                        }

                        float neighborWeight = dx == 0 || dz == 0 ? 2.0F : 1.0F;
                        weightedSum += source[neighborIdx] * neighborWeight;
                        weight += neighborWeight;
                    }
                }

                float localAverage = weight <= 0.0F ? source[idx] : weightedSum / weight;
                target[idx] = clamp01(source[idx] * (1.0F - SHAPE_SIGNAL_SMOOTHING_BLEND)
                        + localAverage * SHAPE_SIGNAL_SMOOTHING_BLEND);
            }
        }
    }

    private static float accumulationWeight(float accumulation, float maxAccumulation) {
        float denominator = (float) Math.log1p(Math.max(1.0F, maxAccumulation));
        float normalized = denominator <= 0.0F
                ? 0.0F
                : (float) (Math.log1p(Math.max(0.0F, accumulation)) / denominator);
        return smoothstep(
                TerrainFlowConfig.RIVER_SHAPE_LOG_START,
                TerrainFlowConfig.RIVER_SHAPE_LOG_END,
                normalized
        );
    }

    private static float computeAccumulation(byte[] direction, int width, int height, float[] accumulation) {
        int len = width * height;
        int[] indegree = new int[len];
        boolean[] processed = new boolean[len];
        Queue<Integer> queue = new ArrayDeque<>();

        for (int i = 0; i < len; i++) {
            accumulation[i] = 1.0F;
        }

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                byte dir = direction[idx];
                if (dir < 0) {
                    continue;
                }

                int nx = x + DIR_X[dir];
                int nz = z + DIR_Z[dir];
                if (nx >= 0 && nz >= 0 && nx < width && nz < height) {
                    indegree[index(nx, nz, width)]++;
                }
            }
        }

        for (int idx = 0; idx < len; idx++) {
            if (indegree[idx] == 0) {
                queue.add(idx);
            }
        }

        while (!queue.isEmpty()) {
            int idx = queue.remove();
            processed[idx] = true;

            byte dir = direction[idx];
            if (dir < 0) {
                continue;
            }

            int x = idx % width;
            int z = idx / width;
            int nx = x + DIR_X[dir];
            int nz = z + DIR_Z[dir];
            if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                continue;
            }

            int nextIdx = index(nx, nz, width);
            accumulation[nextIdx] += accumulation[idx];
            indegree[nextIdx]--;
            if (indegree[nextIdx] == 0) {
                queue.add(nextIdx);
            }
        }

        // Strictly downhill routing should avoid cycles. If a cycle still appears because of bad input
        // do not let it become an artificial mega-collector.
        float max = 1.0F;
        for (int idx = 0; idx < len; idx++) {
            if (!processed[idx] && indegree[idx] > 0) {
                accumulation[idx] = 1.0F;
            }
            if (accumulation[idx] > max) {
                max = accumulation[idx];
            }
        }

        return max;
    }

    private static boolean isMeaningfulConvergenceChange(
            byte pass0Direction,
            byte finalDirection,
            int sx,
            int sz,
            int sourceWidth,
            float[] convergencePotential
    ) {
        if (pass0Direction == finalDirection || finalDirection < 0 || convergencePotential == null) {
            return false;
        }

        float pass0Potential = targetConvergencePotential(pass0Direction, sx, sz, sourceWidth, convergencePotential);
        float finalPotential = targetConvergencePotential(finalDirection, sx, sz, sourceWidth, convergencePotential);
        return finalPotential - pass0Potential >= TerrainFlowConfig.MIN_CONVERGENCE_GAIN_FOR_CHANGE;
    }

    private static float targetConvergencePotential(byte direction, int sx, int sz, int sourceWidth, float[] convergencePotential) {
        if (direction < 0) {
            return 0.0F;
        }

        int nx = sx + DIR_X[direction];
        int nz = sz + DIR_Z[direction];
        int sourceHeight = convergencePotential.length / sourceWidth;
        if (nx < 0 || nz < 0 || nx >= sourceWidth || nz >= sourceHeight) {
            return 0.0F;
        }

        int idx = index(nx, nz, sourceWidth);
        if (idx < 0 || idx >= convergencePotential.length) {
            return 0.0F;
        }

        return convergencePotential[idx];
    }

    private static float[] buildConvergencePotential(float[] accumulation, float maxAccumulation) {
        float[] result = new float[accumulation.length];
        double denominator = Math.log1p(Math.max(1.0F, maxAccumulation));

        for (int i = 0; i < accumulation.length; i++) {
            float normalized = denominator <= 0.0D
                    ? 0.0F
                    : (float) (Math.log1p(Math.max(0.0F, accumulation[i])) / denominator);
            // Damp low-level noise. Convergence should only pull toward clearly emerging channels
            // not repaint the whole D8 field.
            result[i] = smoothstep(0.18F, 0.88F, normalized);
        }

        return result;
    }

    private static float baseMeters(TerrainBaseTile tile, int localX, int localZ) {
        return tile.baseMetersAtLocal(
                clamp(localX, 0, tile.width() - 1),
                clamp(localZ, 0, tile.height() - 1)
        );
    }

    private static int generatedY(TerrainBaseTile tile, int localX, int localZ) {
        return tile.generatedYAtLocal(
                clamp(localX, 0, tile.width() - 1),
                clamp(localZ, 0, tile.height() - 1)
        );
    }

    private static short biome(TerrainBaseTile tile, int localX, int localZ) {
        return tile.biomeAtLocal(
                clamp(localX, 0, tile.width() - 1),
                clamp(localZ, 0, tile.height() - 1)
        );
    }

    private static short precipitationMm(TerrainBaseTile tile, int localX, int localZ) {
        return tile.precipitationMmAtLocal(
                clamp(localX, 0, tile.width() - 1),
                clamp(localZ, 0, tile.height() - 1)
        );
    }

    private static float totalCost(TerrainCostTile tile, int localX, int localZ) {
        return tile.totalAt(
                clamp(localX, 0, tile.width() - 1),
                clamp(localZ, 0, tile.height() - 1)
        );
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0F : 1.0F;
        }
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static int index(int x, int z, int width) {
        return z * width + x;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record FlowChoice(byte direction, float drop, float uphill, float selectedCost, float score) {
    }

    private record CostChoice(byte direction, float drop) {
    }
}
