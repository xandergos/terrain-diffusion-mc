package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persisted per-world settings for terrain diffusion.
 *
 * <p>This is stored in the world save via Minecraft's saved data manager.
 */
public final class WorldScaleSettingsState extends SavedData {
    public static final String STORAGE_NAME = "terrain_diffusion_world_settings";

    private static final String NBT_KEY_SCALE = "scale";
    private static final String NBT_KEY_EXPLICIT_SCALE = "explicit_scale";

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

    private static WorldScaleSettingsState fromNbt(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        int storedScale = nbt.contains(NBT_KEY_SCALE) ? nbt.getInt(NBT_KEY_SCALE) : WorldScaleManager.DEFAULT_SCALE;
        boolean storedExplicit = nbt.getBoolean(NBT_KEY_EXPLICIT_SCALE);
        return new WorldScaleSettingsState(storedScale, storedExplicit);
    }

    /**
     * Factory descriptor used by the saved data manager.
     */
    public static final SavedData.Factory<WorldScaleSettingsState> TYPE = new SavedData.Factory<>(
            WorldScaleSettingsState::createDefault,
            WorldScaleSettingsState::fromNbt,
            DataFixTypes.LEVEL);

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

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        nbt.putInt(NBT_KEY_SCALE, scale);
        nbt.putBoolean(NBT_KEY_EXPLICIT_SCALE, explicitScale);
        return nbt;
    }
}
