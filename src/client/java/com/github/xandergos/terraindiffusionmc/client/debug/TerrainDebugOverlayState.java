package com.github.xandergos.terraindiffusionmc.client.debug;

public final class TerrainDebugOverlayState {
    public enum OverlayCategory {
        TERRAIN("Terrain overlays"),
        COST("Cost overlays"),
        FLOW("Flow overlays"),
        RIVER("River overlays"),
        RIVER_APPLICATION("River application overlays"),
        GLOBAL_RIVER("Global river overlays");

        private final String label;

        OverlayCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public OverlayCategory next() {
            return switch (this) {
                case TERRAIN -> COST;
                case COST -> FLOW;
                case FLOW -> RIVER;
                case RIVER -> RIVER_APPLICATION;
                case RIVER_APPLICATION -> GLOBAL_RIVER;
                case GLOBAL_RIVER -> TERRAIN;
            };
        }
    }

    private static OverlayCategory category = OverlayCategory.TERRAIN;
    private static TerrainDebugOverlayMode terrainMode = TerrainDebugOverlayMode.OFF;
    private static TerrainDebugOverlayMode costMode = TerrainDebugOverlayMode.COST_TOTAL;
    private static TerrainDebugOverlayMode flowMode = TerrainDebugOverlayMode.FLOW_DIRECTION;
    private static TerrainDebugOverlayMode riverMode = TerrainDebugOverlayMode.RIVER_CELLS;
    private static TerrainDebugOverlayMode riverApplicationMode = TerrainDebugOverlayMode.RIVER_CHUNK_DISTANCE;
    private static TerrainDebugOverlayMode globalRiverMode = TerrainDebugOverlayMode.GLOBAL_RIVER_LINES;
    private static int stride = 4;
    private static int radiusTiles = 1;
    private static double yOffset = 0.35D;
    private static int fillAlpha = 220;

    private TerrainDebugOverlayState() {
    }

    public static OverlayCategory category() {
        return category;
    }

    public static void cycleCategory() {
        category = category.next();
    }

    public static TerrainDebugOverlayMode mode() {
        return switch (category) {
            case TERRAIN -> terrainMode;
            case COST -> costMode;
            case FLOW -> flowMode;
            case RIVER -> riverMode;
            case RIVER_APPLICATION -> riverApplicationMode;
            case GLOBAL_RIVER -> globalRiverMode;
        };
    }

    public static void cycleMode() {
        switch (category) {
            case TERRAIN -> terrainMode = nextTerrainMode(terrainMode);
            case COST -> costMode = nextCostMode(costMode);
            case FLOW -> flowMode = nextFlowMode(flowMode);
            case RIVER -> riverMode = nextRiverMode(riverMode);
            case RIVER_APPLICATION -> riverApplicationMode = nextRiverApplicationMode(riverApplicationMode);
            case GLOBAL_RIVER -> globalRiverMode = nextGlobalRiverMode(globalRiverMode);
        }
    }

    public static void setMode(TerrainDebugOverlayMode newMode) {
        if (newMode == null) {
            return;
        }

        if (newMode.isGlobalRiverMode()) {
            globalRiverMode = newMode;
            category = OverlayCategory.GLOBAL_RIVER;
        } else if (newMode.isRiverChunkOverlayMode()) {
            riverApplicationMode = newMode;
            category = OverlayCategory.RIVER_APPLICATION;
        } else if (newMode.isCostMode()) {
            costMode = newMode;
            category = OverlayCategory.COST;
        } else if (newMode.isFlowMode()) {
            flowMode = newMode;
            category = OverlayCategory.FLOW;
        } else if (newMode.isRiverMode()) {
            riverMode = newMode;
            category = OverlayCategory.RIVER;
        } else {
            terrainMode = newMode;
            category = OverlayCategory.TERRAIN;
        }
    }

    public static int stride() {
        return stride;
    }

    public static void cycleStride() {
        if (stride == 2) {
            stride = 4;
        } else if (stride == 4) {
            stride = 8;
        } else if (stride == 8) {
            stride = 16;
        } else {
            stride = 2;
        }
    }

    public static int radiusTiles() {
        return radiusTiles;
    }

    public static void cycleRadius() {
        radiusTiles = radiusTiles == 1 ? 2 : 1;
    }

    public static double yOffset() {
        return yOffset;
    }

    public static int fillAlpha() {
        return fillAlpha;
    }

    public static void cycleFillAlpha() {
        if (fillAlpha == 180) {
            fillAlpha = 220;
        } else if (fillAlpha == 220) {
            fillAlpha = 255;
        } else {
            fillAlpha = 180;
        }
    }

    private static TerrainDebugOverlayMode nextTerrainMode(TerrainDebugOverlayMode current) {
        return switch (current) {
            case OFF -> TerrainDebugOverlayMode.BASE_HEIGHT;
            case BASE_HEIGHT -> TerrainDebugOverlayMode.GENERATED_HEIGHT;
            case GENERATED_HEIGHT -> TerrainDebugOverlayMode.DELTA;
            case DELTA -> TerrainDebugOverlayMode.BIOME;
            case BIOME -> TerrainDebugOverlayMode.OFF;
            default -> TerrainDebugOverlayMode.OFF;
        };
    }

    private static TerrainDebugOverlayMode nextCostMode(TerrainDebugOverlayMode current) {
        return switch (current) {
            case OFF -> TerrainDebugOverlayMode.COST_TOTAL;
            case COST_TOTAL -> TerrainDebugOverlayMode.COST_SLOPE;
            case COST_SLOPE -> TerrainDebugOverlayMode.COST_RIDGE;
            case COST_RIDGE -> TerrainDebugOverlayMode.COST_VALLEY;
            case COST_VALLEY -> TerrainDebugOverlayMode.COST_BIOME;
            case COST_BIOME -> TerrainDebugOverlayMode.COST_SOIL;
            case COST_SOIL -> TerrainDebugOverlayMode.COST_FORBIDDEN;
            case COST_FORBIDDEN -> TerrainDebugOverlayMode.OFF;
            default -> TerrainDebugOverlayMode.COST_TOTAL;
        };
    }

    private static TerrainDebugOverlayMode nextFlowMode(TerrainDebugOverlayMode current) {
        return switch (current) {
            case OFF -> TerrainDebugOverlayMode.FLOW_DIRECTION;
            case FLOW_DIRECTION -> TerrainDebugOverlayMode.FLOW_DROP;
            case FLOW_DROP -> TerrainDebugOverlayMode.FLOW_SINKS;
            case FLOW_SINKS -> TerrainDebugOverlayMode.FLOW_SELECTED_COST;
            case FLOW_SELECTED_COST -> TerrainDebugOverlayMode.FLOW_ACCUMULATION_PREVIEW;
            case FLOW_ACCUMULATION_PREVIEW -> TerrainDebugOverlayMode.FLOW_CONVERGENCE_BONUS;
            case FLOW_CONVERGENCE_BONUS -> TerrainDebugOverlayMode.FLOW_CHANGED_BY_CONVERGENCE;
            case FLOW_CHANGED_BY_CONVERGENCE -> TerrainDebugOverlayMode.FLOW_ACCUMULATION_FINAL;
            case FLOW_ACCUMULATION_FINAL -> TerrainDebugOverlayMode.FLOW_ACCUMULATION_LOG;
            case FLOW_ACCUMULATION_LOG -> TerrainDebugOverlayMode.FLOW_DRAINAGE_AREA;
            case FLOW_DRAINAGE_AREA -> TerrainDebugOverlayMode.FLOW_RIVER_PREVIEW;
            case FLOW_RIVER_PREVIEW -> TerrainDebugOverlayMode.FLOW_SINK_ACCUMULATION;
            case FLOW_SINK_ACCUMULATION -> TerrainDebugOverlayMode.OFF;
            default -> TerrainDebugOverlayMode.FLOW_DIRECTION;
        };
    }

    private static TerrainDebugOverlayMode nextRiverMode(TerrainDebugOverlayMode current) {
        return switch (current) {
            case OFF -> TerrainDebugOverlayMode.RIVER_CELLS;
            case RIVER_CELLS -> TerrainDebugOverlayMode.RIVER_SOURCES;
            case RIVER_SOURCES -> TerrainDebugOverlayMode.RIVER_CONFLUENCES;
            case RIVER_CONFLUENCES -> TerrainDebugOverlayMode.RIVER_OUTLETS;
            case RIVER_OUTLETS -> TerrainDebugOverlayMode.RIVER_WIDTH_PREVIEW;
            case RIVER_WIDTH_PREVIEW -> TerrainDebugOverlayMode.RIVER_VECTOR_LINES;
            case RIVER_VECTOR_LINES -> TerrainDebugOverlayMode.RIVER_VECTOR_NODES;
            case RIVER_VECTOR_NODES -> TerrainDebugOverlayMode.RIVER_VECTOR_WIDTH;
            case RIVER_VECTOR_WIDTH -> TerrainDebugOverlayMode.RIVER_VECTOR_FLOW;
            case RIVER_VECTOR_FLOW -> TerrainDebugOverlayMode.RIVER_SPATIAL_INDEX_CELLS;
            case RIVER_SPATIAL_INDEX_CELLS -> TerrainDebugOverlayMode.RIVER_CHUNK_QUERY;
            case RIVER_CHUNK_QUERY -> TerrainDebugOverlayMode.OFF;
            default -> TerrainDebugOverlayMode.RIVER_CELLS;
        };
    }

    private static TerrainDebugOverlayMode nextRiverApplicationMode(TerrainDebugOverlayMode current) {
        return switch (current) {
            case OFF -> TerrainDebugOverlayMode.RIVER_CHUNK_DISTANCE;
            case RIVER_CHUNK_DISTANCE -> TerrainDebugOverlayMode.RIVER_CHUNK_BED;
            case RIVER_CHUNK_BED -> TerrainDebugOverlayMode.RIVER_CHUNK_WATER;
            case RIVER_CHUNK_WATER -> TerrainDebugOverlayMode.RIVER_CHUNK_BANKS;
            case RIVER_CHUNK_BANKS -> TerrainDebugOverlayMode.RIVER_CHUNK_MATERIALS;
            case RIVER_CHUNK_MATERIALS -> TerrainDebugOverlayMode.RIVER_CHUNK_VEGETATION;
            case RIVER_CHUNK_VEGETATION -> TerrainDebugOverlayMode.RIVER_CHUNK_TERRAIN_CORRECTION;
            case RIVER_CHUNK_TERRAIN_CORRECTION -> TerrainDebugOverlayMode.OFF;
            default -> TerrainDebugOverlayMode.RIVER_CHUNK_DISTANCE;
        };
    }
    private static TerrainDebugOverlayMode nextGlobalRiverMode(TerrainDebugOverlayMode current) {
        return switch (current) {
            case OFF -> TerrainDebugOverlayMode.GLOBAL_RIVER_LINES;
            case GLOBAL_RIVER_LINES -> TerrainDebugOverlayMode.GLOBAL_RIVER_NODES;
            case GLOBAL_RIVER_NODES -> TerrainDebugOverlayMode.GLOBAL_RIVER_DISCHARGE;
            case GLOBAL_RIVER_DISCHARGE -> TerrainDebugOverlayMode.GLOBAL_RIVER_WIDTH;
            case GLOBAL_RIVER_WIDTH -> TerrainDebugOverlayMode.GLOBAL_RIVER_DEPTH;
            case GLOBAL_RIVER_DEPTH -> TerrainDebugOverlayMode.GLOBAL_RIVER_REGION_BORDERS;
            case GLOBAL_RIVER_REGION_BORDERS -> TerrainDebugOverlayMode.OFF;
            default -> TerrainDebugOverlayMode.GLOBAL_RIVER_LINES;
        };
    }

}
