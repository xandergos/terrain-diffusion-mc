package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.server.world.ServerWorld;

/**
 * Runtime access for world-scoped terrain scale.
 */
public final class WorldScaleManager {
    public static final int DEFAULT_SCALE = 2;
    private static final int MIN_SCALE = 1;
    public static final int MAX_SCALE = 6;

    private static volatile int currentScale = DEFAULT_SCALE;

    private WorldScaleManager() {
    }

    /**
     * Loads or creates per-world scale settings and sets the active runtime value.
     *
     * <p>If the world has no explicit stored scale yet, this applies pending
     * world-creation selection when present, otherwise falls back to {@value #DEFAULT_SCALE}.
     */
    public static void initializeForWorld(ServerWorld serverWorld) {
        WorldScaleSettingsState worldScaleSettingsState = serverWorld.getPersistentStateManager()
                .getOrCreate(WorldScaleSettingsState.TYPE, "terrain_diffusion_world_settings");

        if (!worldScaleSettingsState.hasExplicitScale()) {
            Integer pendingScale = WorldScaleSelectionState.consumePendingScale();
            int resolvedScale = pendingScale != null ? pendingScale : DEFAULT_SCALE;
            worldScaleSettingsState.setScale(resolvedScale);
        }

        currentScale = clampScale(worldScaleSettingsState.getScale());
    }

    /**
     * Returns the currently active world scale.
     */
    public static int getCurrentScale() {
        return currentScale;
    }

    /**
     * Updates world scale for the currently loaded world and persists it immediately.
     */
    public static void setCurrentScale(ServerWorld serverWorld, int configuredScale) {
        int clampedScale = clampScale(configuredScale);

        WorldScaleSettingsState worldScaleSettingsState = serverWorld.getPersistentStateManager()
                .getOrCreate(WorldScaleSettingsState.TYPE, "terrain_diffusion_world_settings");

        worldScaleSettingsState.setScale(clampedScale);
        currentScale = clampedScale;
    }

    /**
     * Clamps world scale to supported runtime bounds.
     */
    public static int clampScale(int configuredScale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, configuredScale));
    }
}
