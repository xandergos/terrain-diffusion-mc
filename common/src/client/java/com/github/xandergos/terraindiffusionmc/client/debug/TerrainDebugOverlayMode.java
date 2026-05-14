package com.github.xandergos.terraindiffusionmc.client.debug;

public enum TerrainDebugOverlayMode {
    OFF("Off"),
    RIVER_TRACE_WIDTH("River trace width");

    private final String label;

    TerrainDebugOverlayMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
