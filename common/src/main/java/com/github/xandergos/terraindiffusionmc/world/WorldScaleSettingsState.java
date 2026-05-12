package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persisted per-world settings for terrain diffusion.
 *
 * <p>This is stored in the world save via Minecraft's persistent state manager.
 */
public final class WorldScaleSettingsState extends SavedData {
    public static final String DATA_NAME = "terrain_diffusion_world_settings";

    private int scale;
    private boolean explicitScale;

    private WorldScaleSettingsState(int configuredScale, boolean hasExplicitScale) {
        this.scale = WorldScaleManager.clampScale(configuredScale);
        this.explicitScale = hasExplicitScale;
    }

    /**
     * Creates a default state for worlds that do not yet have saved terrain diffusion settings.
     */
    public static WorldScaleSettingsState createDefault() {
        return new WorldScaleSettingsState(WorldScaleManager.DEFAULT_SCALE, false);
    }

    /**
     * Loads the persisted state from Minecraft 1.20.1 NBT storage.
     */
    public static WorldScaleSettingsState load(CompoundTag tag) {
        int configuredScale = tag.contains("scale") ? tag.getInt("scale") : WorldScaleManager.DEFAULT_SCALE;
        boolean hasExplicitScale = tag.contains("explicit_scale") && tag.getBoolean("explicit_scale");
        return new WorldScaleSettingsState(configuredScale, hasExplicitScale);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("scale", scale);
        tag.putBoolean("explicit_scale", explicitScale);
        return tag;
    }

    /**
     * Returns the currently persisted world scale.
     */
    public int getScale() {
        return scale;
    }

    /**
     * Returns whether this world has an explicitly chosen scale.
     */
    public boolean hasExplicitScale() {
        return explicitScale;
    }

    /**
     * Applies a new persisted world scale and marks the state dirty.
     */
    public void setScale(int configuredScale) {
        this.scale = WorldScaleManager.clampScale(configuredScale);
        this.explicitScale = true;
        setDirty();
    }
}
