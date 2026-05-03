package com.github.xandergos.terraindiffusionmc.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

/**
 * A sediment block that exists in seven thickness variants (1/8 to 7/8 of a cube).
 *
 * <p>Stacking what would be the 8th layer is not represented as a block state : the
 * block is replaced in place by its vanilla full-cube counterpart returned by
 * {@link #getFullBlock()}. This keeps the state space minimal and makes pick-block
 * and silk-touch behave naturally on a fully filled cell.
 *
 * <p>An optional {@link #SNOWY} flag adds a thin snow layer on top, mirroring the
 * snowy variant of vanilla grass blocks. The flag is updated by {@link #randomTick}
 * to track weather and may also be set by world generation.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public class LayeredBlock extends Block implements Waterloggable {

    /** Number of filled layers : 1 (thinnest) to 7 (almost full). 8 is reserved by promoting to the full block. */
    public static final IntProperty LAYERS = IntProperty.of("layers", 1, 7);

    /** True if the block is waterlogged. Only layers 2 to 6 can actually hold water. */
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    /** True if a snow layer sits on top of this block (decorative ; tracks weather). */
    public static final BooleanProperty SNOWY = Properties.SNOWY;

    /** Cached collision/outline shapes ; index 0 is unused, 1 to 7 are valid. */
    private static final VoxelShape[] LAYER_SHAPES = buildLayerShapes();

    private static VoxelShape[] buildLayerShapes() {
        VoxelShape[] shapes = new VoxelShape[8];
        for (int i = 1; i <= 7; i++) {
            shapes[i] = Block.createCuboidShape(0, 0, 0, 16, i * 2, 16);
        }
        return shapes;
    }

    public static final MapCodec<LayeredBlock> CODEC = createCodec(LayeredBlock::new);

    private Block fullBlock = Blocks.AIR;

    public LayeredBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(LAYERS, 1)
                .with(WATERLOGGED, false)
                .with(SNOWY, false));
    }

    @Override
    public MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    /**
     * Vanilla full-cube counterpart this layered block promotes to when an 8th
     * layer is added. Wired up by {@link ModBlocks} during registration.
     */
    public Block getFullBlock() {
        return fullBlock;
    }

    void setFullBlock(Block fullBlock) {
        this.fullBlock = fullBlock;
    }

    private static boolean canLayerBeWaterlogged(int layers) {
        return layers >= 2 && layers <= 6;
    }

    private static BlockState sanitizeWaterlogging(BlockState state) {
        if (!canLayerBeWaterlogged(state.get(LAYERS)) && state.get(WATERLOGGED)) {
            return state.with(WATERLOGGED, false);
        }
        return state;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, WATERLOGGED, SNOWY);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return LAYER_SHAPES[state.get(LAYERS)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return LAYER_SHAPES[state.get(LAYERS)];
    }

    @Override
    public boolean isTransparent(BlockState state) {
        return true;
    }

    /**
     * Stack an existing layer or place a fresh layer-1.
     *
     * <p>When the existing block already has 7 layers and the player tries to add
     * an 8th we return the full-block's default state instead so the placement
     * pipeline replaces the layered block by its vanilla counterpart.
     */
    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState existing = ctx.getWorld().getBlockState(ctx.getBlockPos());

        if (existing.isOf(this)) {
            int current = existing.get(LAYERS);

            if (current < 7) {
                int next = current + 1;
                return existing
                        .with(LAYERS, next)
                        .with(WATERLOGGED, canLayerBeWaterlogged(next) && existing.get(WATERLOGGED));
            }

            // 7 -> 8: promote to the vanilla full cube.
            BlockState full = fullBlock.getDefaultState();

            if (existing.get(WATERLOGGED) && full.contains(Properties.WATERLOGGED)) {
                full = full.with(Properties.WATERLOGGED, true);
            }

            return full;
        }

        FluidState fluid = ctx.getWorld().getFluidState(ctx.getBlockPos());
        boolean inWater = fluid.getFluid() == Fluids.WATER;

        // Fresh placement is always layer 1, so it must not be waterlogged.
        return getDefaultState().with(WATERLOGGED, canLayerBeWaterlogged(1) && inWater);
    }

    /**
     * Allow the layer item to keep replacing the block while it is below 7 and also at
     * exactly 7 so that {@link #getPlacementState(ItemPlacementContext)} can swap in the
     * full vanilla block.
     */
    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext ctx) {
        return ctx.getStack().isOf(asItem());
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state, boolean includeData) {
        return super.getPickStack(world, pos, state, includeData);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return canLayerBeWaterlogged(state.get(LAYERS)) && state.get(WATERLOGGED)
                ? Fluids.WATER.getStill(false)
                : Fluids.EMPTY.getDefaultState();
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView,
                                                BlockPos pos, Direction direction, BlockPos neighborPos,
                                                BlockState neighborState, Random random) {
        state = sanitizeWaterlogging(state);

        if (canLayerBeWaterlogged(state.get(LAYERS)) && state.get(WATERLOGGED)) {
            tickView.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }

        // Clear the decorative snow flag if a non-air, non-snow block is placed on top.
        if (direction == Direction.UP && state.get(SNOWY)
                && !neighborState.isAir() && !neighborState.isOf(Blocks.SNOW)) {
            state = state.with(SNOWY, false);
        }

        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    /**
     * Tracks weather : in biomes that can snow at this position the block becomes
     * snowy and in biomes that warm up again the snow flag is cleared. Only the
     * top-exposed face is considered mirroring vanilla snow placement.
     */
    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        BlockPos above = pos.up();
        Biome biome = world.getBiome(above).value();
        int seaLevel = world.getSeaLevel();

        if (!state.get(SNOWY) && world.getBlockState(above).isAir()
                && world.isRaining() && biome.canSetSnow(world, above)) {
            world.setBlockState(pos, state.with(SNOWY, true), Block.NOTIFY_ALL);
        } else if (state.get(SNOWY) && biome.doesNotSnow(pos, seaLevel)) {
            world.setBlockState(pos, state.with(SNOWY, false), Block.NOTIFY_ALL);
        }
    }

    @Override
    public boolean hasRandomTicks(BlockState state) {
        return true;
    }

    /**
     * Intercepts a right-click with a {@link Items#SNOW} item : instead of letting the
     * snow layer try to place itself in the air-block above (which would float a 2-pixel
     * snow slab on top of a non-full layer) we toggle the {@link #SNOWY} flag on this
     * block so the visual snow layer is rendered as part of the layered block itself.
     */
    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                         PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isOf(Items.SNOW) && !state.get(SNOWY)) {
            if (!world.isClient()) {
                world.setBlockState(pos, state.with(SNOWY, true), Block.NOTIFY_ALL);
                world.playSound(null, pos, SoundEvents.BLOCK_SNOW_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }

            if (!player.isCreative()) {
                stack.decrement(1);
            }

            return ActionResult.SUCCESS;
        }

        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, sanitizeWaterlogging(state), placer, stack);
    }
}