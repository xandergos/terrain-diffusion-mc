package com.github.xandergos.terraindiffusionmc.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import static com.github.xandergos.terraindiffusionmc.TerrainDiffusionMc.MOD_ID;

/**
 * Central registry for all custom blocks added by this mod.
 *
 * <p>Layered blocks (thin-to-almost-full sediment layers) are registered in bulk via
 * {@link #registerLayered(String, Block, AbstractBlock.Settings)} or its falling variant.
 * Each registration also wires the layered block to its vanilla full-cube counterpart so
 * that placing an 8th layer promotes the position to the vanilla block instead of
 * introducing a redundant {@code layers=8} state.
 *
 * <p>Creative-tab placement is handled via {@link ItemGroupEvents} : each layer item is
 * inserted immediately before its vanilla counterpart in {@link ItemGroups#NATURAL}.
 *
 * <p>Call {@link #register()} from {@code onInitialize} to trigger static class loading
 * and populate the registry.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class ModBlocks {

    /** Thin-to-full sand layers ; common in river beds and shallow shores. */
    public static final FallingLayeredBlock SAND_LAYER = registerFallingLayered("sand", Blocks.SAND, copyOf(Blocks.SAND));

    /** Thin-to-full gravel layers ; typical for fast-flowing river channels. */
    public static final FallingLayeredBlock GRAVEL_LAYER = registerFallingLayered("gravel", Blocks.GRAVEL, copyOf(Blocks.GRAVEL));

    /** Thin-to-full dirt layers ; banks and floodplain deposits. */
    public static final LayeredBlock DIRT_LAYER = registerLayered("dirt", Blocks.DIRT, copyOf(Blocks.DIRT));

    /** Thin-to-full red sand layers ; arid / mesa river beds. */
    public static final FallingLayeredBlock RED_SAND_LAYER = registerFallingLayered("red_sand", Blocks.RED_SAND, copyOf(Blocks.RED_SAND));

    /** Thin-to-full stone layers ; bedrock-exposed riverbeds. */
    public static final LayeredBlock STONE_LAYER = registerLayered("stone", Blocks.STONE,  copyOf(Blocks.STONE));

    /** Thin-to-full diorite layers ; igneous riverbed variation. */
    public static final LayeredBlock DIORITE_LAYER = registerLayered("diorite", Blocks.DIORITE, copyOf(Blocks.DIORITE));

    /** Thin-to-full andesite layers ; igneous riverbed variation. */
    public static final LayeredBlock ANDESITE_LAYER = registerLayered("andesite", Blocks.ANDESITE, copyOf(Blocks.ANDESITE));

    /** Thin-to-full granite layers ; igneous riverbed variation. */
    public static final LayeredBlock GRANITE_LAYER = registerLayered("granite", Blocks.GRANITE, copyOf(Blocks.GRANITE));

    /** Thin-to-full clay layers ; slow-water and delta deposits. */
    public static final LayeredBlock CLAY_LAYER = registerLayered("clay", Blocks.CLAY, copyOf(Blocks.CLAY));

    /** Thin-to-full coarse dirt layers ; eroded bank material. */
    public static final LayeredBlock COARSE_DIRT_LAYER = registerLayered("coarse_dirt", Blocks.COARSE_DIRT, copyOf(Blocks.COARSE_DIRT));

    /** Thin-to-full calcite layers ; karst and limestone riverbed outcrops. */
    public static final LayeredBlock CALCITE_LAYER = registerLayered("calcite", Blocks.CALCITE, copyOf(Blocks.CALCITE));

    /**
     * Creates a {@link LayeredBlock}, registers it under
     * {@code terrain-diffusion-mc:<name>_layer} and wires its vanilla full-cube
     * counterpart used when an 8th layer is placed.
     */
    private static LayeredBlock registerLayered(String name, Block fullBlock, AbstractBlock.Settings settings) {
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, name + "_layer"));
        RegistryKey<Item> itemKey   = RegistryKey.of(RegistryKeys.ITEM,  Identifier.of(MOD_ID, name + "_layer"));
        LayeredBlock block = Registry.register(
                Registries.BLOCK,
                blockKey,
                new LayeredBlock(settings.registryKey(blockKey)));
        block.setFullBlock(fullBlock);
        Registry.register(
                Registries.ITEM,
                itemKey,
                new BlockItem(block, new Item.Settings().registryKey(itemKey)));
        return block;
    }

    /** Same as {@link #registerLayered} but for gravity-affected materials (sand, gravel and red sand). */
    private static FallingLayeredBlock registerFallingLayered(String name, Block fullBlock, AbstractBlock.Settings settings) {
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, name + "_layer"));
        RegistryKey<Item> itemKey   = RegistryKey.of(RegistryKeys.ITEM,  Identifier.of(MOD_ID, name + "_layer"));
        FallingLayeredBlock block = Registry.register(
                Registries.BLOCK,
                blockKey,
                new FallingLayeredBlock(settings.registryKey(blockKey)));
        block.setFullBlock(fullBlock);
        Registry.register(
                Registries.ITEM,
                itemKey,
                new BlockItem(block, new Item.Settings().registryKey(itemKey)));
        return block;
    }

    /** Shorthand for {@link AbstractBlock.Settings#copy(AbstractBlock)}. */
    private static AbstractBlock.Settings copyOf(Block block) {
        return AbstractBlock.Settings.copy(block);
    }

    /**
     * Triggers static initialization of this class (registering all blocks) and inserts
     * each layer item before its vanilla counterpart in the Nature creative tab.
     *
     * <p>Call once from {@code onInitialize}.
     */
    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
            entries.addBefore(Blocks.DIRT,         DIRT_LAYER);
            entries.addBefore(Blocks.COARSE_DIRT,  COARSE_DIRT_LAYER);
            entries.addBefore(Blocks.GRAVEL,       GRAVEL_LAYER);
            entries.addBefore(Blocks.SAND,         SAND_LAYER);
            entries.addBefore(Blocks.RED_SAND,     RED_SAND_LAYER);
            entries.addBefore(Blocks.CLAY,         CLAY_LAYER);
            entries.addBefore(Blocks.STONE,        STONE_LAYER);
            entries.addBefore(Blocks.GRANITE,      GRANITE_LAYER);
            entries.addBefore(Blocks.DIORITE,      DIORITE_LAYER);
            entries.addBefore(Blocks.ANDESITE,     ANDESITE_LAYER);
            entries.addBefore(Blocks.CALCITE,      CALCITE_LAYER);
        });
    }

    private ModBlocks() {}
}