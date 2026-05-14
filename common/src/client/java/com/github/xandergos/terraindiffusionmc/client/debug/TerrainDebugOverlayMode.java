package com.github.xandergos.terraindiffusionmc.client.debug;

public enum TerrainDebugOverlayMode {
    OFF("Off", false, false, false),
    BASE_HEIGHT("Base height", false, false, false),
    GENERATED_HEIGHT("Generated terrain", false, false, false),
    DELTA("Generated - base", false, false, false),
    BIOME("Biome id", false, false, false),
    COST_TOTAL("Cost total", true, false, false),
    COST_SLOPE("Cost slope", true, false, false),
    COST_RIDGE("Cost ridge", true, false, false),
    COST_VALLEY("Cost valley bonus", true, false, false),
    COST_BIOME("Cost biome", true, false, false),
    COST_SOIL("Cost soil/rock", true, false, false),
    COST_FORBIDDEN("Cost forbidden", true, false, false),
    FLOW_DIRECTION("Flow direction", false, true, false),
    FLOW_DROP("Flow drop", false, true, false),
    FLOW_SINKS("Flow sinks", false, true, false),
    FLOW_SELECTED_COST("Flow selected cost", false, true, false),
    FLOW_ACCUMULATION_PREVIEW("Flow pass0 accumulation (local)", false, true, false),
    FLOW_CONVERGENCE_BONUS("Flow convergence influence", false, true, false),
    FLOW_CHANGED_BY_CONVERGENCE("Flow meaningful convergence changes", false, true, false),
    FLOW_ACCUMULATION_FINAL("Flow accumulation final", false, true, false),
    FLOW_ACCUMULATION_LOG("Flow final accumulation log", false, true, false),
    FLOW_DRAINAGE_AREA("Flow drainage area", false, true, false),
    FLOW_RIVER_PREVIEW("Flow river preview (strict)", false, true, false),
    FLOW_SINK_ACCUMULATION("Flow sink accumulation", false, true, false),
    RIVER_CELLS("River cells", false, false, true),
    RIVER_SOURCES("River sources", false, false, true),
    RIVER_CONFLUENCES("River confluences", false, false, true),
    RIVER_OUTLETS("River outlets", false, false, true),
    RIVER_WIDTH_PREVIEW("River width preview", false, false, true),
    RIVER_VECTOR_LINES("River vector lines", false, false, true),
    RIVER_VECTOR_NODES("River vector nodes", false, false, true),
    RIVER_VECTOR_WIDTH("River vector width", false, false, true),
    RIVER_VECTOR_FLOW("River vector accumulation", false, false, true),
    RIVER_SPATIAL_INDEX_CELLS("River spatial index cells", false, false, true),
    RIVER_CHUNK_QUERY("River chunk query", false, false, true),
    RIVER_CHUNK_DISTANCE("River tile distance", false, false, true),
    RIVER_CHUNK_BED("River tile bed", false, false, true),
    RIVER_CHUNK_WATER("River tile water", false, false, true),
    RIVER_CHUNK_BANKS("River tile banks", false, false, true),
    RIVER_CHUNK_MATERIALS("River tile materials", false, false, true),
    RIVER_CHUNK_VEGETATION("River tile vegetation", false, false, true),
    RIVER_CHUNK_TERRAIN_CORRECTION("River tile terrain correction", false, false, true),
    GLOBAL_RIVER_LINES("Global river stitched lines", false, false, true),
    GLOBAL_RIVER_NODES("Global river stitched nodes", false, false, true),
    GLOBAL_RIVER_DISCHARGE("Global river discharge", false, false, true),
    GLOBAL_RIVER_WIDTH("Global river corrected width", false, false, true),
    GLOBAL_RIVER_DEPTH("Global river corrected depth", false, false, true),
    GLOBAL_RIVER_REGION_BORDERS("Global river region borders", false, false, true);

    private final String label;
    private final boolean costMode;
    private final boolean flowMode;
    private final boolean riverMode;

    TerrainDebugOverlayMode(String label, boolean costMode, boolean flowMode, boolean riverMode) {
        this.label = label;
        this.costMode = costMode;
        this.flowMode = flowMode;
        this.riverMode = riverMode;
    }

    public String label() {
        return label;
    }

    public boolean isCostMode() {
        return costMode;
    }

    public boolean isFlowMode() {
        return flowMode;
    }

    public boolean isRiverMode() {
        return riverMode;
    }

    public boolean isRiverVectorMode() {
        return switch (this) {
            case RIVER_VECTOR_LINES, RIVER_VECTOR_NODES, RIVER_VECTOR_WIDTH, RIVER_VECTOR_FLOW -> true;
            default -> false;
        };
    }

    public boolean isRiverSpatialMode() {
        return switch (this) {
            case RIVER_SPATIAL_INDEX_CELLS, RIVER_CHUNK_QUERY -> true;
            default -> false;
        };
    }

    public boolean isRiverChunkOverlayMode() {
        return switch (this) {
            case RIVER_CHUNK_DISTANCE,
                    RIVER_CHUNK_BED,
                    RIVER_CHUNK_WATER,
                    RIVER_CHUNK_BANKS,
                    RIVER_CHUNK_MATERIALS,
                    RIVER_CHUNK_VEGETATION,
                    RIVER_CHUNK_TERRAIN_CORRECTION -> true;
            default -> false;
        };
    }

    public boolean isGlobalRiverMode() {
        return switch (this) {
            case GLOBAL_RIVER_LINES,
                    GLOBAL_RIVER_NODES,
                    GLOBAL_RIVER_DISCHARGE,
                    GLOBAL_RIVER_WIDTH,
                    GLOBAL_RIVER_DEPTH,
                    GLOBAL_RIVER_REGION_BORDERS -> true;
            default -> false;
        };
    }
}
