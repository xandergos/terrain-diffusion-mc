package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

/**
 * Persisted per-world settings for terrain diffusion.
 *
 * <p>This is stored in the world save via Minecraft's persistent state manager.
 */
public final class WorldScaleSettingsState extends PersistentState {
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

    private static WorldScaleSettingsState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        int storedScale = nbt.contains(NBT_KEY_SCALE) ? nbt.getInt(NBT_KEY_SCALE) : WorldScaleManager.DEFAULT_SCALE;
        boolean storedExplicit = nbt.getBoolean(NBT_KEY_EXPLICIT_SCALE);
        return new WorldScaleSettingsState(storedScale, storedExplicit);
    }

    /**
     * Type descriptor used by the persistent state manager.
     */
    public static final PersistentState.Type<WorldScaleSettingsState> TYPE = new PersistentState.Type<>(
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
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putInt(NBT_KEY_SCALE, scale);
        nbt.putBoolean(NBT_KEY_EXPLICIT_SCALE, explicitScale);
        return nbt;
    }
}
