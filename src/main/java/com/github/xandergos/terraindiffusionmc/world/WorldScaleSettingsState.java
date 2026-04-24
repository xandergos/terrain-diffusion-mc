package com.github.xandergos.terraindiffusionmc.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persisted per-world settings for terrain diffusion.
 *
 * <p>This is stored in the world save via Minecraft's persistent state manager.
 */
public final class WorldScaleSettingsState extends PersistentState {
    private static final Logger LOG = LoggerFactory.getLogger(WorldScaleSettingsState.class);

    private static final Codec<WorldScaleSettingsState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("scale", WorldScaleManager.DEFAULT_SCALE).forGetter(WorldScaleSettingsState::getScale),
            Codec.BOOL.optionalFieldOf("explicit_scale", false).forGetter(WorldScaleSettingsState::hasExplicitScale)
    ).apply(instance, WorldScaleSettingsState::new));

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
     * Type descriptor used by the persistent state manager.
     */
    public static final PersistentState.Type<WorldScaleSettingsState> TYPE =
            new PersistentState.Type<>(
                    WorldScaleSettingsState::createDefault,
                    WorldScaleSettingsState::readNbt,
                    null
            );

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
        CODEC.encodeStart(registryLookup.getOps(NbtOps.INSTANCE), this)
                .resultOrPartial(error -> LOG.error("Failed to save WorldScaleSettingsState: {}", error))
                .ifPresent(encoded -> {
                    if (encoded instanceof NbtCompound compound){
                        nbt.copyFrom(compound);
                    }else{
                        LOG.error("Expected NbtCompound but got {}", encoded.getClass().getSimpleName());
                    }
                });

        return nbt;
    }

    public static WorldScaleSettingsState readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        return CODEC.parse(wrapperLookup.getOps(NbtOps.INSTANCE), nbt)
                .resultOrPartial(error -> LOG.error("Failed to load WorldScaleSettingsState: {}", error))
                .orElseGet(WorldScaleSettingsState::createDefault);
    }
}
