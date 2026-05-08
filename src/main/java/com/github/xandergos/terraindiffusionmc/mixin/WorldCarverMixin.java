package com.github.xandergos.terraindiffusionmc.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * Replace water-fill with air for carved cave blocks below sea level.
 *
 * <p>Vanilla {@code WorldCarver.getCarveState} asks the aquifer what block to
 * place at a carved position. With aquifers disabled (our config — keeps
 * shorelines clean), the disabled aquifer falls through to the global fluid
 * picker, which returns water for any Y &lt; sea_level. That floods carver-driven
 * caves below sea level.
 *
 * <p>This mixin short-circuits {@code getCarveState} to return air instead of
 * water for any carved block above the configured lava level. Lava placement
 * is left untouched (vanilla checks {@code lavaLevel} first inside the method,
 * but we still gate on it ourselves so the override is explicit).
 *
 * <p>Effect:
 * <ul>
 *   <li>Cave-carver carved blocks: always air (was water below sea level).</li>
 *   <li>Ocean basins: unchanged — those are chunk-fill default-fluid placements,
 *       not carver placements, and never go through this method.</li>
 *   <li>Lava lakes/cavities below {@code lavaLevel}: unchanged (vanilla's lava
 *       branch fires before our injection's effective return).</li>
 * </ul>
 */
@Mixin(WorldCarver.class)
public abstract class WorldCarverMixin {

    /**
     * Replace water-fill with air for any carved block above lava level.
     * This is what makes underground caves dry below sea level — the disabled
     * aquifer would otherwise return water from the global fluid picker.
     */
    @Inject(method = "getCarveState", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$dryCarvedBlocks(
            CarvingContext context,
            CarverConfiguration config,
            BlockPos pos,
            Aquifer aquifer,
            CallbackInfoReturnable<BlockState> cir) {
        if (pos.getY() <= config.lavaLevel.resolveY(context)) return;
        cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    /**
     * Skip carving when a gravity block (sand / red sand / gravel) sits directly
     * above the position. Without this, the mixin above turns the carve target
     * into air, which leaves the gravity block unsupported. At chunk-load it
     * falls into the cave, opening voids where the ocean used to sit.
     *
     * <p>The skip leaves the original stone in place, which keeps the seafloor
     * intact while still letting the cave system run through the column lower
     * down (where the block above is also stone). Net effect: caves dry, sea
     * floor uniform, no collapse.
     */
    @Inject(method = "carveBlock", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$preserveSeafloor(
            CarvingContext context,
            CarverConfiguration config,
            ChunkAccess chunk,
            Function<BlockPos, Holder<Biome>> biomeGetter,
            CarvingMask mask,
            BlockPos.MutableBlockPos pos,
            BlockPos.MutableBlockPos workPos,
            Aquifer aquifer,
            MutableBoolean foundSurface,
            CallbackInfoReturnable<Boolean> cir) {
        // Never carve through water. Some mods add water to the carver_replaceables
        // tag, which lets carvers cut through the ocean column itself — every such
        // carved block becomes air via our getCarveState mixin, leaving large
        // arch-shaped air pockets visible in the ocean as "voided water".
        BlockState current = chunk.getBlockState(pos);
        if (!current.getFluidState().isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        // Don't carve THROUGH the seafloor itself: sand/gravel are gravity blocks
        // that would either be turned into air (and let water flood the cave) or
        // would be unsupported and collapse on chunk load.
        if (current.is(Blocks.SAND) || current.is(Blocks.RED_SAND) || current.is(Blocks.GRAVEL)) {
            cir.setReturnValue(false);
            return;
        }
        // Don't carve directly UNDER a gravity block — that would leave the gravity
        // block unsupported and it would collapse into the (now-air) cave on chunk load.
        workPos.setWithOffset(pos, Direction.UP);
        BlockState above = chunk.getBlockState(workPos);
        if (above.is(Blocks.SAND) || above.is(Blocks.RED_SAND) || above.is(Blocks.GRAVEL)) {
            cir.setReturnValue(false);
        }
    }
}
