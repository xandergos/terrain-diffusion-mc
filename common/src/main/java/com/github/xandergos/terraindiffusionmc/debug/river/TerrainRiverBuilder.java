package com.github.xandergos.terraindiffusionmc.debug.river;

import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowTile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public final class TerrainRiverBuilder {
    private static final int[] DIR_X = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DIR_Z = {-1, -1, 0, 1, 1, 1, 0, -1};

    private TerrainRiverBuilder() {
    }

    public static TerrainRiverTile build(TerrainFlowTile flow) {
        int width = flow.width();
        int height = flow.height();
        int len = width * height;

        int[] surfaceY = new int[len];
        boolean[] river = new boolean[len];
        boolean[] lake = new boolean[len];
        boolean[] ocean = new boolean[len];
        boolean[] source = new boolean[len];
        boolean[] confluence = new boolean[len];
        boolean[] outlet = new boolean[len];
        byte[] direction = new byte[len];
        byte[] upstreamRiverCount = new byte[len];
        byte[] traceRejectReason = new byte[len];
        float[] discharge = new float[len];
        float[] logStrength = new float[len];
        float[] riverPotential = new float[len];
        float[] widthBlocks = new float[len];
        float[] depthBlocks = new float[len];
        float[] waterLevelY = new float[len];
        float[] meanderStrength = new float[len];
        float[] terrainHeight = new float[len];
        float[] filledHeight = new float[len];
        float[] localWaterInput = new float[len];
        float[] sourceSignal = new float[len];
        int[] downstream = new int[len];

        Arrays.fill(direction, TerrainFlowTile.SINK_DIRECTION);
        Arrays.fill(downstream, -1);

        boolean hasOcean = false;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                short biome = flow.biomeAtLocal(x, z);
                TerrainBiomeHydrologyProfile profile = TerrainBiomeHydrologyProfile.forBiome(biome);
                int y = flow.surfaceYAtLocal(x, z);

                surfaceY[idx] = y;
                terrainHeight[idx] = y;
                ocean[idx] = profile.ocean();
                hasOcean |= ocean[idx];

                float slope = localRelief(flow, x, z);
                float precipScore = precipitationScore(flow.precipitationMmAtLocal(x, z));
                float altitudeScore = smoothstep(70.0F, 165.0F, y);
                float slopeScore = 1.0F - smoothstep(0.05F, 3.5F, slope);
                sourceSignal[idx] = clamp01(altitudeScore * precipScore * slopeScore * profile.sourceMultiplier());
                localWaterInput[idx] = waterInput(flow, x, z, profile, sourceSignal[idx]);
            }
        }

        buildPriorityFloodRouting(width, height, terrainHeight, ocean, hasOcean, filledHeight, direction, downstream);
        accumulateWater(width, height, downstream, localWaterInput, discharge);

        float maxDischarge = 1.0F;
        for (int idx = 0; idx < len; idx++) {
            if (!ocean[idx]) {
                maxDischarge = Math.max(maxDischarge, discharge[idx]);
            }
        }

        double logDenominator = Math.log1p(maxDischarge);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                TerrainBiomeHydrologyProfile profile = TerrainBiomeHydrologyProfile.forBiome(flow.biomeAtLocal(x, z));
                float waterDepth = Math.max(0.0F, filledHeight[idx] - terrainHeight[idx]);
                float precipScore = precipitationScore(flow.precipitationMmAtLocal(x, z));
                float dischargeScore = logDenominator <= 0.0D
                        ? 0.0F
                        : clamp01((float) (Math.log1p(Math.max(0.0F, discharge[idx])) / logDenominator));
                boolean persistentLake = isPersistentLake(waterDepth, discharge[idx], precipScore, profile);
                boolean visibleRiver = !ocean[idx]
                        && downstream[idx] >= 0
                        && discharge[idx] >= TerrainRiverConfig.MIN_VISIBLE_RIVER_DISCHARGE;

                lake[idx] = !ocean[idx] && persistentLake;
                river[idx] = visibleRiver;
                logStrength[idx] = dischargeScore;
                riverPotential[idx] = riverPotential(dischargeScore, sourceSignal[idx], lake[idx]);
                waterLevelY[idx] = lake[idx] ? filledHeight[idx] : terrainHeight[idx];
            }
        }

        postProcessRiverNetwork(width, height, river, lake, ocean, direction, downstream, discharge, terrainHeight, filledHeight, traceRejectReason);

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                TerrainBiomeHydrologyProfile profile = TerrainBiomeHydrologyProfile.forBiome(flow.biomeAtLocal(x, z));
                float dischargeScore = logDenominator <= 0.0D
                        ? 0.0F
                        : clamp01((float) (Math.log1p(Math.max(0.0F, discharge[idx])) / logDenominator));

                widthBlocks[idx] = river[idx] ? widthFromDischarge(discharge[idx], maxDischarge) : 0.0F;
                depthBlocks[idx] = river[idx] ? depthFromDischarge(discharge[idx], maxDischarge) : 0.0F;
                meanderStrength[idx] = river[idx] ? meanderStrength(flow, x, z, dischargeScore, profile) : 0.0F;
                if (traceRejectReason[idx] == TerrainRiverTile.TRACE_REJECT_NONE) {
                    traceRejectReason[idx] = rejectReasonForCell(ocean[idx], lake[idx], river[idx], discharge[idx], downstream[idx]);
                }
            }
        }

        classifyNetwork(river, ocean, direction, upstreamRiverCount, source, confluence, outlet, width, height);

        return new TerrainRiverTile(
                flow.blockStartX(),
                flow.blockStartZ(),
                width,
                height,
                surfaceY,
                river,
                lake,
                ocean,
                source,
                confluence,
                outlet,
                direction,
                upstreamRiverCount,
                traceRejectReason,
                discharge,
                logStrength,
                riverPotential,
                widthBlocks,
                depthBlocks,
                waterLevelY,
                meanderStrength,
                maxDischarge
        );
    }

    private static void buildPriorityFloodRouting(
            int width,
            int height,
            float[] terrainHeight,
            boolean[] ocean,
            boolean hasOcean,
            float[] filledHeight,
            byte[] direction,
            int[] downstream
    ) {
        int len = width * height;
        boolean[] visited = new boolean[len];
        PriorityQueue<FloodCell> queue = new PriorityQueue<>();

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                if (ocean[idx]) {
                    seed(queue, visited, filledHeight, direction, downstream, idx, terrainHeight[idx] - 8.0F);
                } else if (!hasOcean && isBoundary(x, z, width, height)) {
                    seed(queue, visited, filledHeight, direction, downstream, idx, terrainHeight[idx] + TerrainRiverConfig.BOUNDARY_OUTLET_PENALTY_BLOCKS);
                }
            }
        }

        if (queue.isEmpty()) {
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    if (isBoundary(x, z, width, height)) {
                        int idx = index(x, z, width);
                        seed(queue, visited, filledHeight, direction, downstream, idx, terrainHeight[idx]);
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            FloodCell current = queue.poll();
            int cx = current.index % width;
            int cz = current.index / width;

            for (byte neighborDir = 0; neighborDir < 8; neighborDir++) {
                int nx = cx + DIR_X[neighborDir];
                int nz = cz + DIR_Z[neighborDir];
                if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                    continue;
                }

                int nIdx = index(nx, nz, width);
                if (visited[nIdx]) {
                    continue;
                }

                visited[nIdx] = true;
                float nFilled = Math.max(terrainHeight[nIdx], current.priority + TerrainRiverConfig.FLAT_ROUTING_EPSILON);
                filledHeight[nIdx] = nFilled;
                downstream[nIdx] = current.index;
                direction[nIdx] = directionFromDelta(cx - nx, cz - nz);
                queue.add(new FloodCell(nIdx, nFilled));
            }
        }
    }

    private static void seed(
            PriorityQueue<FloodCell> queue,
            boolean[] visited,
            float[] filledHeight,
            byte[] direction,
            int[] downstream,
            int idx,
            float priority
    ) {
        if (visited[idx]) {
            return;
        }
        visited[idx] = true;
        filledHeight[idx] = priority;
        direction[idx] = TerrainFlowTile.SINK_DIRECTION;
        downstream[idx] = -1;
        queue.add(new FloodCell(idx, priority));
    }

    private static void accumulateWater(int width, int height, int[] downstream, float[] localWaterInput, float[] discharge) {
        int len = width * height;
        int[] indegree = new int[len];
        for (int idx = 0; idx < len; idx++) {
            int next = downstream[idx];
            if (next >= 0) {
                indegree[next]++;
            }
            discharge[idx] = localWaterInput[idx];
        }

        Queue<Integer> queue = new ArrayDeque<>();
        for (int idx = 0; idx < len; idx++) {
            if (indegree[idx] == 0) {
                queue.add(idx);
            }
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            int idx = queue.remove();
            processed++;
            int next = downstream[idx];
            if (next >= 0) {
                discharge[next] += discharge[idx];
                indegree[next]--;
                if (indegree[next] == 0) {
                    queue.add(next);
                }
            }
        }

        // Defensive fallback. Priority-flood should create an acyclic drainage forest; if a future
        // change breaks that invariant, do not leave cells without their own local water.
        if (processed < len) {
            for (int idx = 0; idx < len; idx++) {
                discharge[idx] = Math.max(discharge[idx], localWaterInput[idx]);
            }
        }
    }


    private static void postProcessRiverNetwork(
            int width,
            int height,
            boolean[] river,
            boolean[] lake,
            boolean[] ocean,
            byte[] direction,
            int[] downstream,
            float[] discharge,
            float[] terrainHeight,
            float[] filledHeight,
            byte[] traceRejectReason
    ) {
        closeShortGaps(width, height, river, lake, ocean, downstream, discharge, terrainHeight, traceRejectReason);

        for (int pass = 0; pass < TerrainRiverConfig.NETWORK_CLEANUP_PASSES; pass++) {
            byte[] upstreamCounts = upstreamCounts(width, height, river, downstream);
            boolean changed = false;
            changed |= pruneShortHeadwaters(width, height, river, ocean, downstream, discharge, upstreamCounts, traceRejectReason);

            upstreamCounts = upstreamCounts(width, height, river, downstream);
            changed |= resolveOverloadedConfluences(width, height, river, downstream, discharge, upstreamCounts, traceRejectReason);

            if (!changed) {
                break;
            }
        }

        closeShortGaps(width, height, river, lake, ocean, downstream, discharge, terrainHeight, traceRejectReason);
        markUnresolvedGaps(width, height, river, lake, ocean, direction, downstream, discharge, filledHeight, traceRejectReason);
    }

    private static void closeShortGaps(
            int width,
            int height,
            boolean[] river,
            boolean[] lake,
            boolean[] ocean,
            int[] downstream,
            float[] discharge,
            float[] terrainHeight,
            byte[] traceRejectReason
    ) {
        int len = width * height;
        float minGapDischarge = TerrainRiverConfig.MIN_VISIBLE_RIVER_DISCHARGE * TerrainRiverConfig.GAP_CLOSE_MIN_DISCHARGE_FRACTION;

        for (int start = 0; start < len; start++) {
            if (!river[start]) {
                continue;
            }

            int current = start;
            List<Integer> gap = new ArrayList<>(TerrainRiverConfig.GAP_CLOSE_MAX_CELLS);
            for (int step = 0; step < TerrainRiverConfig.GAP_CLOSE_MAX_CELLS; step++) {
                int next = downstream[current];
                if (next < 0 || ocean[next] || lake[next]) {
                    break;
                }

                if (river[next]) {
                    if (!gap.isEmpty()) {
                        for (int cell : gap) {
                            river[cell] = true;
                            traceRejectReason[cell] = TerrainRiverTile.TRACE_REJECT_NONE;
                        }
                    }
                    break;
                }

                if (discharge[next] < minGapDischarge) {
                    break;
                }

                float rise = terrainHeight[next] - terrainHeight[current];
                if (rise > TerrainRiverConfig.GAP_CLOSE_MAX_TERRAIN_RISE_BLOCKS) {
                    break;
                }

                gap.add(next);
                current = next;
            }
        }
    }

    private static boolean pruneShortHeadwaters(
            int width,
            int height,
            boolean[] river,
            boolean[] ocean,
            int[] downstream,
            float[] discharge,
            byte[] upstreamCounts,
            byte[] traceRejectReason
    ) {
        boolean changed = false;
        float minBranchDischarge = TerrainRiverConfig.MIN_VISIBLE_RIVER_DISCHARGE
                * TerrainRiverConfig.MIN_HEADWATER_BRANCH_DISCHARGE_FRACTION;

        for (int idx = 0; idx < river.length; idx++) {
            if (!river[idx] || ocean[idx] || (upstreamCounts[idx] & 255) != 0) {
                continue;
            }

            Branch branch = traceHeadwaterBranch(idx, width, height, river, downstream, discharge, upstreamCounts);
            if (branch.cells().size() < TerrainRiverConfig.MIN_HEADWATER_BRANCH_LENGTH_CELLS
                    && branch.maxDischarge() < minBranchDischarge) {
                for (int cell : branch.cells()) {
                    river[cell] = false;
                    traceRejectReason[cell] = TerrainRiverTile.TRACE_REJECT_PRUNED_BRANCH;
                }
                changed = true;
            }
        }

        return changed;
    }

    private static boolean resolveOverloadedConfluences(
            int width,
            int height,
            boolean[] river,
            int[] downstream,
            float[] discharge,
            byte[] upstreamCounts,
            byte[] traceRejectReason
    ) {
        boolean changed = false;
        for (int idx = 0; idx < river.length; idx++) {
            int upstream = upstreamCounts[idx] & 255;
            if (!river[idx] || upstream <= TerrainRiverConfig.MAX_UPSTREAMS_PER_CONFLUENCE) {
                continue;
            }

            List<Integer> incoming = incomingRiverCells(idx, width, height, river, downstream);
            incoming.sort(Comparator.comparingDouble((Integer cell) -> -branchJunctionScore(cell, river, downstream, discharge, upstreamCounts)));

            for (int i = TerrainRiverConfig.MAX_UPSTREAMS_PER_CONFLUENCE; i < incoming.size(); i++) {
                removeUpstreamBranch(incoming.get(i), width, height, river, downstream, traceRejectReason);
                changed = true;
            }
        }
        return changed;
    }

    private static void markUnresolvedGaps(
            int width,
            int height,
            boolean[] river,
            boolean[] lake,
            boolean[] ocean,
            byte[] direction,
            int[] downstream,
            float[] discharge,
            float[] filledHeight,
            byte[] traceRejectReason
    ) {
        for (int idx = 0; idx < river.length; idx++) {
            if (!river[idx] || downstream[idx] < 0) {
                continue;
            }

            int next = downstream[idx];
            if (river[next] || lake[next] || ocean[next]) {
                continue;
            }

            if (discharge[next] >= TerrainRiverConfig.MIN_VISIBLE_RIVER_DISCHARGE * TerrainRiverConfig.GAP_CLOSE_MIN_DISCHARGE_FRACTION
                    && filledHeight[idx] >= filledHeight[next]) {
                traceRejectReason[next] = TerrainRiverTile.TRACE_REJECT_UNRESOLVED_GAP;
            }
        }
    }

    private static Branch traceHeadwaterBranch(
            int start,
            int width,
            int height,
            boolean[] river,
            int[] downstream,
            float[] discharge,
            byte[] upstreamCounts
    ) {
        List<Integer> cells = new ArrayList<>();
        float maxDischarge = 0.0F;
        int current = start;
        int maxSteps = width * height;

        for (int step = 0; step < maxSteps && current >= 0 && river[current]; step++) {
            cells.add(current);
            maxDischarge = Math.max(maxDischarge, discharge[current]);

            int next = downstream[current];
            if (next < 0 || !river[next]) {
                break;
            }
            if ((upstreamCounts[next] & 255) >= 2) {
                break;
            }
            current = next;
        }

        return new Branch(cells, maxDischarge);
    }

    private static float branchJunctionScore(
            int branchEnd,
            boolean[] river,
            int[] downstream,
            float[] discharge,
            byte[] upstreamCounts
    ) {
        // Immediate discharge is the most stable signal at confluences: it already contains all upstream runoff.
        // A small continuity bonus prevents tiny one-cell trickles from beating a slightly weaker but established branch.
        int downstreamCell = downstream[branchEnd];
        float continuity = downstreamCell >= 0 && river[downstreamCell] ? 1.12F : 1.0F;
        float confluencePenalty = (upstreamCounts[branchEnd] & 255) >= 2 ? 1.05F : 1.0F;
        return discharge[branchEnd] * continuity * confluencePenalty;
    }

    private static void removeUpstreamBranch(
            int start,
            int width,
            int height,
            boolean[] river,
            int[] downstream,
            byte[] traceRejectReason
    ) {
        Queue<Integer> queue = new ArrayDeque<>();
        boolean[] queued = new boolean[river.length];
        queue.add(start);
        queued[start] = true;

        while (!queue.isEmpty()) {
            int cell = queue.remove();
            if (!river[cell]) {
                continue;
            }

            river[cell] = false;
            traceRejectReason[cell] = TerrainRiverTile.TRACE_REJECT_PRUNED_BRANCH;

            for (int upstream : incomingRiverCells(cell, width, height, river, downstream)) {
                if (!queued[upstream]) {
                    queued[upstream] = true;
                    queue.add(upstream);
                }
            }
        }
    }

    private static List<Integer> incomingRiverCells(int idx, int width, int height, boolean[] river, int[] downstream) {
        int x = idx % width;
        int z = idx / width;
        List<Integer> incoming = new ArrayList<>(8);
        for (int dir = 0; dir < 8; dir++) {
            int nx = x + DIR_X[dir];
            int nz = z + DIR_Z[dir];
            if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                continue;
            }
            int nIdx = index(nx, nz, width);
            if (river[nIdx] && downstream[nIdx] == idx) {
                incoming.add(nIdx);
            }
        }
        return incoming;
    }

    private static byte[] upstreamCounts(int width, int height, boolean[] river, int[] downstream) {
        byte[] counts = new byte[river.length];
        for (int idx = 0; idx < river.length; idx++) {
            if (!river[idx]) {
                continue;
            }
            int next = downstream[idx];
            if (next >= 0 && river[next]) {
                int previous = counts[next] & 255;
                counts[next] = (byte) Math.min(255, previous + 1);
            }
        }
        return counts;
    }

    private static float waterInput(TerrainFlowTile flow, int x, int z, TerrainBiomeHydrologyProfile profile, float sourceSignal) {
        if (profile.ocean()) {
            return 0.0F;
        }
        float precipitation = Math.max(0, flow.precipitationMmAtLocal(x, z));
        float precipitationInput = precipitation * TerrainRiverConfig.PRECIPITATION_RUNOFF_SCALE * profile.runoffCoefficient();
        float springInput = TerrainRiverConfig.MAX_SOURCE_INPUT * sourceSignal;
        return Math.max(0.0F, TerrainRiverConfig.BASE_RUNOFF_INPUT * profile.runoffCoefficient() + precipitationInput + springInput);
    }

    private static boolean isPersistentLake(float waterDepth, float discharge, float precipitationScore, TerrainBiomeHydrologyProfile profile) {
        if (waterDepth < TerrainRiverConfig.MIN_LAKE_DEPTH_BLOCKS || profile.ocean()) {
            return false;
        }
        float inflowScore = smoothstep(TerrainRiverConfig.MIN_POND_DISCHARGE, TerrainRiverConfig.MIN_VISIBLE_RIVER_DISCHARGE * 1.35F, discharge);
        float waterBalance = (0.65F * inflowScore + 0.35F * precipitationScore) * profile.lakePersistence();
        float evaporationCost = profile.evaporationMultiplier() * TerrainRiverConfig.LAKE_BALANCE_EVAPORATION_WEIGHT;
        return waterBalance >= evaporationCost;
    }

    private static void classifyNetwork(
            boolean[] river,
            boolean[] ocean,
            byte[] direction,
            byte[] upstreamRiverCount,
            boolean[] source,
            boolean[] confluence,
            boolean[] outlet,
            int width,
            int height
    ) {
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                if (!river[idx]) {
                    continue;
                }

                byte dir = direction[idx];
                if (dir < 0) {
                    outlet[idx] = true;
                    continue;
                }

                int nx = x + DIR_X[dir];
                int nz = z + DIR_Z[dir];
                if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                    outlet[idx] = true;
                    continue;
                }

                int nextIdx = index(nx, nz, width);
                if (ocean[nextIdx]) {
                    outlet[idx] = true;
                    continue;
                }
                if (!river[nextIdx]) {
                    outlet[idx] = true;
                    continue;
                }

                int previous = upstreamRiverCount[nextIdx] & 255;
                upstreamRiverCount[nextIdx] = (byte) Math.min(255, previous + 1);
            }
        }

        for (int idx = 0; idx < river.length; idx++) {
            if (!river[idx]) {
                continue;
            }

            int upstream = upstreamRiverCount[idx] & 255;
            source[idx] = upstream == 0;
            confluence[idx] = upstream >= 2;
        }
    }

    private static byte rejectReasonForCell(boolean ocean, boolean lake, boolean river, float discharge, int downstream) {
        if (ocean || river || lake) {
            return TerrainRiverTile.TRACE_REJECT_NONE;
        }
        if (downstream < 0) {
            return TerrainRiverTile.TRACE_REJECT_BROKEN_FLOW;
        }
        if (discharge < TerrainRiverConfig.MIN_POND_DISCHARGE) {
            return TerrainRiverTile.TRACE_REJECT_DRY_BASIN;
        }
        return TerrainRiverTile.TRACE_REJECT_WEAK_SIGNAL;
    }

    private static float riverPotential(float dischargeScore, float sourceSignal, boolean lake) {
        return clamp01(
                TerrainRiverConfig.RIVER_POTENTIAL_DISCHARGE_WEIGHT * dischargeScore
                        + TerrainRiverConfig.RIVER_POTENTIAL_SOURCE_WEIGHT * sourceSignal
                        + TerrainRiverConfig.RIVER_POTENTIAL_LAKE_WEIGHT * (lake ? 1.0F : 0.0F)
        );
    }

    private static float widthFromDischarge(float discharge, float maxDischarge) {
        float t = logNormalized(discharge, maxDischarge);
        float navigableGate = smoothstep(TerrainRiverConfig.MIN_VISIBLE_RIVER_DISCHARGE, TerrainRiverConfig.MIN_NAVIGABLE_DISCHARGE, discharge);
        float baseWidth = TerrainRiverConfig.MIN_WIDTH_BLOCKS * (0.72F + 0.28F * navigableGate);
        return baseWidth + (TerrainRiverConfig.MAX_WIDTH_BLOCKS - baseWidth) * (float) Math.pow(t, 0.62D);
    }

    private static float depthFromDischarge(float discharge, float maxDischarge) {
        float t = logNormalized(discharge, maxDischarge);
        float navigableGate = smoothstep(TerrainRiverConfig.MIN_VISIBLE_RIVER_DISCHARGE, TerrainRiverConfig.MIN_NAVIGABLE_DISCHARGE, discharge);
        float baseDepth = TerrainRiverConfig.MIN_DEPTH_BLOCKS * (0.70F + 0.30F * navigableGate);
        return baseDepth + (TerrainRiverConfig.MAX_DEPTH_BLOCKS - baseDepth) * (float) Math.pow(t, 0.70D);
    }

    private static float meanderStrength(TerrainFlowTile flow, int x, int z, float dischargeScore, TerrainBiomeHydrologyProfile profile) {
        float relief = localRelief(flow, x, z);
        float flatness = 1.0F - smoothstep(0.35F, 3.0F, relief);
        float altitudePenalty = smoothstep(120.0F, 210.0F, flow.surfaceYAtLocal(x, z));
        return clamp01(flatness * dischargeScore * profile.meanderMultiplier() * (1.0F - 0.70F * altitudePenalty));
    }

    private static float localRelief(TerrainFlowTile flow, int x, int z) {
        float center = flow.surfaceYAtLocal(x, z);
        float maxDelta = 0.0F;
        for (int dir = 0; dir < 8; dir++) {
            int nx = clamp(x + DIR_X[dir], 0, flow.width() - 1);
            int nz = clamp(z + DIR_Z[dir], 0, flow.height() - 1);
            maxDelta = Math.max(maxDelta, Math.abs(flow.surfaceYAtLocal(nx, nz) - center));
        }
        return maxDelta;
    }

    private static float precipitationScore(short precipitationMm) {
        return smoothstep(120.0F, 1600.0F, Math.max(0, precipitationMm));
    }

    private static float logNormalized(float value, float maxValue) {
        double denominator = Math.log1p(Math.max(1.0F, maxValue));
        return denominator <= 0.0D
                ? 0.0F
                : clamp01((float) (Math.log1p(Math.max(0.0F, value)) / denominator));
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isBoundary(int x, int z, int width, int height) {
        return x == 0 || z == 0 || x == width - 1 || z == height - 1;
    }

    private static byte directionFromDelta(int dx, int dz) {
        for (byte dir = 0; dir < 8; dir++) {
            if (DIR_X[dir] == dx && DIR_Z[dir] == dz) {
                return dir;
            }
        }
        return TerrainFlowTile.SINK_DIRECTION;
    }

    private static int index(int x, int z, int width) {
        return z * width + x;
    }

    private record Branch(List<Integer> cells, float maxDischarge) {
    }

    private static final class FloodCell implements Comparable<FloodCell> {
        private final int index;
        private final float priority;

        private FloodCell(int index, float priority) {
            this.index = index;
            this.priority = priority;
        }

        @Override
        public int compareTo(FloodCell other) {
            int byPriority = Float.compare(this.priority, other.priority);
            return byPriority != 0 ? byPriority : Integer.compare(this.index, other.index);
        }
    }
}
