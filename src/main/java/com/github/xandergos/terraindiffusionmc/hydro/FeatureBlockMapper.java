package com.github.xandergos.terraindiffusionmc.hydro;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/**
 * Maps each {@link HydrologicalFeature} to the {@link BlockState} the world-gen pass will place.
 *
 * <p>Current palette mirrors the renderer colors to make the in-world result visually identical
 * to the debug overlay :
 * <ul>
 *   <li>{@link HydrologicalFeature#STREAM} -> cyan wool</li>
 *   <li>{@link HydrologicalFeature#RIVER} -> vanilla water source (proper flow physics)</li>
 *   <li>{@link HydrologicalFeature#TRUNK_RIVER} -> red wool</li>
 *   <li>{@link HydrologicalFeature#RIVER_MOUTH} -> lime wool</li>
 *   <li>{@link HydrologicalFeature#NONE} -> {@code null} (no replacement)</li>
 * </ul>
 *
 * <p>{@link #carveDepthFor} returns how deep the carving pass should dig into the column for a
 * given feature. Larger features carve more, sketching a riverbed instead of just laying a
 * surface block. The depth is a maximum : the carver stops earlier if it hits lava, an open cave
 * void, or another protected block (handled by the worldgen pass not here).
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class FeatureBlockMapper {

    private FeatureBlockMapper() {}

    /**
     * Returns the block state to place for the given feature or {@code null} when the feature
     * should leave vanilla terrain untouched.
     */
    public static BlockState blockStateFor(HydrologicalFeature feature) {
        return switch (feature) {
            case STREAM      -> Blocks.CYAN_WOOL.getDefaultState();
            case RIVER       -> Blocks.WATER.getDefaultState();
            case TRUNK_RIVER -> Blocks.RED_WOOL.getDefaultState();
            case RIVER_MOUTH -> Blocks.LIME_WOOL.getDefaultState();
            case NONE        -> null;
        };
    }

    /**
     * Returns the carving depth (how many blocks down from the surface to replace) for the given
     * feature. Mouth/streams stay shallow while trunk rivers carve a proper bed.
     */
    public static int carveDepthFor(HydrologicalFeature feature) {
        return switch (feature) {
            case STREAM      -> 1;
            case RIVER       -> 2;
            case TRUNK_RIVER -> 3;
            case RIVER_MOUTH -> 1;
            case NONE        -> 0;
        };
    }

    /**
     * Returns {@code true} if hitting this block during carving should abort the column and
     * leave vanilla terrain intact. Protect lava, bedrock, and any non-air block that
     * isn't the natural surface (caves, spawned ores, etc. shouldn't be replaced by water for now).
     *
     * <p>This deliberately keeps the carving cautious ; would rather skip a column than create
     * a dam of water hovering over a lava lake.
     */
    public static boolean isProtected(BlockState existing) {
        Block block = existing.getBlock();
        return block == Blocks.LAVA
                || block == Blocks.BEDROCK
                || block == Blocks.WATER; // already water, nothing to do
    }

    /**
     * Returns {@code true} if encountering this block while scanning a column meaning left
     * the natural surface and entered a cave / void / fluid pocket. The carver should abort
     * the column when this happens before placing any river block.
     */
    public static boolean isCaveOrVoid(BlockState existing) {
        Block block = existing.getBlock();
        return block == Blocks.AIR
                || block == Blocks.CAVE_AIR
                || block == Blocks.VOID_AIR
                || block == Blocks.LAVA;
    }
}