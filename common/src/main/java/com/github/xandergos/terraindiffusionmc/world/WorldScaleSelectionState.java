package com.github.xandergos.terraindiffusionmc.world;

import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory handoff for world-creation scale selection.
 *
 * <p>In single-player, client and integrated server run in the same JVM, so this allows
 * the world-creation UI to pass an initial scale to server-side world initialization.
 */
public final class WorldScaleSelectionState {
    private static final AtomicReference<Integer> PENDING_SCALE = new AtomicReference<>();

    private WorldScaleSelectionState() {
    }

    /**
     * Stores a pending scale selected in world creation UI.
     */
    public static void setPendingScale(int selectedScale) {
        PENDING_SCALE.set(WorldScaleManager.clampScale(selectedScale));
    }

    /**
     * Returns and clears the pending scale, if any.
     */
    public static Integer consumePendingScale() {
        return PENDING_SCALE.getAndSet(null);
    }

    /**
     * Returns the currently selected pending scale, or the default if none is set.
     */
    public static int getPendingScaleOrDefault() {
        Integer pendingScale = PENDING_SCALE.get();
        return pendingScale != null ? pendingScale : WorldScaleManager.DEFAULT_SCALE;
    }
}
