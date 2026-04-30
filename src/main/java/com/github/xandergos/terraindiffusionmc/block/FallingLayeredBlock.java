package com.github.xandergos.terraindiffusionmc.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;

/**
 * A {@link LayeredBlock} that obeys gravity like vanilla sand and gravel.
 *
 * <p>When unsupported the block schedules a tick and then spawns a {@link FallingBlockEntity}
 * carrying the current {@code layers} state. Falling behavior is identical to a regular
 * falling block ; only the geometry differs.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public class FallingLayeredBlock extends LayeredBlock {

    public static final MapCodec<FallingLayeredBlock> CODEC = createCodec(FallingLayeredBlock::new);

    public FallingLayeredBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        world.scheduleBlockTick(pos, this, 2);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView,
                                                BlockPos pos, Direction direction, BlockPos neighborPos,
                                                BlockState neighborState, Random random) {
        if (direction == Direction.DOWN && FallingBlock.canFallThrough(neighborState)) {
            tickView.scheduleBlockTick(pos, this, 2);
        }
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (FallingBlock.canFallThrough(world.getBlockState(pos.down())) && pos.getY() >= world.getBottomY()) {
            FallingBlockEntity.spawnFromBlock(world, pos, state);
        }
    }
}