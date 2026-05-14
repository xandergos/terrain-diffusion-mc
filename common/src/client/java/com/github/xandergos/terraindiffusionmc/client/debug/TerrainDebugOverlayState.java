package com.github.xandergos.terraindiffusionmc.client.debug;

public final class TerrainDebugOverlayState {
    private static TerrainDebugOverlayMode mode = TerrainDebugOverlayMode.RIVER_TRACE_WIDTH;
    private static int radiusTiles = 1;
    private static double yOffset = 0.35D;

    private TerrainDebugOverlayState() {
    }

    public static TerrainDebugOverlayMode mode() {
        return mode;
    }

    public static void cycleMode() {
        mode = mode == TerrainDebugOverlayMode.OFF
                ? TerrainDebugOverlayMode.RIVER_TRACE_WIDTH
                : TerrainDebugOverlayMode.OFF;
    }

    public static void setMode(TerrainDebugOverlayMode newMode) {
        if (newMode != null) {
            mode = newMode;
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
}
